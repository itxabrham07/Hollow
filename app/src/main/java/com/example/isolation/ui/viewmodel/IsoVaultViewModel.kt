package com.example.isolation.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.IsoVaultApp
import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.data.entity.AccessAuditLog
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.data.entity.VaultFileItem
import com.example.isolation.model.IsolationMode
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.mount.MountEntry
import com.example.isolation.mount.MountExecutionResult
import com.example.isolation.mount.MountManager
import com.example.isolation.pkg.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

enum class AppListFilter {
    ALL,
    ISOLATED_ONLY,
    USER_APPS,
    SYSTEM_APPS
}

class IsoVaultViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /** Largest locker file that is decrypted into memory for the text preview. */
        private const val MAX_PREVIEW_BYTES = 1024 * 1024
    }

    private val app = application as IsoVaultApp
    private val dao = app.database.isolationDao()
    private val pkgRepo = app.packageRepository

    /** Locker entries belong to the manager itself; per-app volumes are managed separately. */
    private val lockerPackage = app.packageName
    private val lockerDir: File get() = File(app.filesDir, "locker").apply { mkdirs() }

    val configs: StateFlow<List<AppIsolationConfig>> = dao.getAllConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentAuditLogs: StateFlow<List<AccessAuditLog>> = dao.getRecentAuditLogs(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaultFiles: StateFlow<List<VaultFileItem>> = dao.getAllVaultFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isolatedCount: StateFlow<Int> = dao.getIsolatedAppsCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalEncryptedBytes: StateFlow<Long> = dao.getTotalEncryptedBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _rawInstalledApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(AppListFilter.ALL)
    val filter = _filter.asStateFlow()

    private val _systemMounts = MutableStateFlow<List<MountEntry>>(emptyList())
    val systemMounts = _systemMounts.asStateFlow()

    private val _selinuxStatus = MutableStateFlow("Unknown")
    val selinuxStatus = _selinuxStatus.asStateFlow()

    private val _isLsposedActive = MutableStateFlow(
        com.example.MainActivity.isLsposedModuleActiveReal() ||
                IsolationRuleManager.isXSharedPrefsHooked(app)
    )
    val isLsposedActive = _isLsposedActive.asStateFlow()

    private val _terminalLogs = MutableStateFlow<List<String>>(emptyList())
    val terminalLogs = _terminalLogs.asStateFlow()

    private val _selectedAppForDetail = MutableStateFlow<InstalledAppInfo?>(null)
    val selectedAppForDetail = _selectedAppForDetail.asStateFlow()

    private val _previewDecryptedText = MutableStateFlow<Pair<String, String>?>(null) // (Title, Content)
    val previewDecryptedText = _previewDecryptedText.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    // Filtered apps list combining search and filter
    val filteredApps: StateFlow<List<InstalledAppInfo>> = combine(
        _rawInstalledApps,
        configs,
        _searchQuery,
        _filter
    ) { apps, cfgList, query, flt ->
        val cfgMap = cfgList.associateBy { it.packageName }
        apps.map { app ->
            val cfg = cfgMap[app.packageName]
            app.copy(
                isIsolated = cfg?.isIsolated ?: false,
                isolationMode = cfg?.isolationMode ?: IsolationMode.ENCRYPTED_VAULT
            )
        }.filter { app ->
            val matchesQuery = query.isBlank() ||
                    app.appName.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)

            val matchesFilter = when (flt) {
                AppListFilter.ALL -> true
                AppListFilter.ISOLATED_ONLY -> app.isIsolated
                AppListFilter.USER_APPS -> !app.isSystemApp
                AppListFilter.SYSTEM_APPS -> app.isSystemApp
            }

            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadInstalledApps()
        refreshDiagnostics()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(newFilter: AppListFilter) {
        _filter.value = newFilter
    }

    fun selectAppForDetail(appInfo: InstalledAppInfo?) {
        _selectedAppForDetail.value = appInfo
    }

    fun clearPreview() {
        _previewDecryptedText.value = null
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = pkgRepo.getInstalledApps(configs.value, includeSystemApps = true)
            _rawInstalledApps.value = apps
        }
    }

    fun refreshDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            _systemMounts.value = MountManager.readSystemMounts()
            _selinuxStatus.value = MountManager.getSelinuxStatus()
            _isLsposedActive.value = com.example.MainActivity.isLsposedModuleActiveReal() ||
                    IsolationRuleManager.isXSharedPrefsHooked(app)
        }
    }

    private fun notifyConfigChanged() {
        try {
            app.contentResolver.notifyChange(
                com.example.isolation.provider.IsoVaultConfigProvider.CONTENT_URI,
                null
            )
        } catch (_: Throwable) {}
    }

    private fun AppIsolationConfig.whitelistSet(): Set<String> =
        whitelistedFolders.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()

    private fun AppIsolationConfig.publishEnabled() {
        IsolationRuleManager.updateRule(
            packageName = packageName,
            mode = isolationMode,
            blockRootPollution = blockRootPollution,
            whitelistedFolders = whitelistSet()
        )
    }

    fun toggleAppIsolation(appInfo: InstalledAppInfo, enable: Boolean) {
        viewModelScope.launch {
            val existing = dao.getConfigForPackage(appInfo.packageName)
            val updated = existing?.copy(isIsolated = enable) ?: AppIsolationConfig(
                packageName = appInfo.packageName,
                appName = appInfo.appName,
                isIsolated = enable,
                isolationMode = appInfo.isolationMode
            )
            dao.insertOrUpdateConfig(updated)

            if (enable) {
                updated.publishEnabled()
                appendTerminalLog("Isolation enabled for ${appInfo.appName} (${updated.isolationMode.badge})")
                _statusMessage.value = "${appInfo.appName} is now isolated (${updated.isolationMode.title}). Force-stop ${appInfo.appName} to apply hooks immediately."
            } else {
                IsolationRuleManager.disableRule(
                    packageName = updated.packageName,
                    mode = updated.isolationMode,
                    blockRootPollution = updated.blockRootPollution,
                    whitelistedFolders = updated.whitelistSet()
                )
                appendTerminalLog("Isolation disabled for ${appInfo.appName}")
                _statusMessage.value = "Storage isolation disabled for ${appInfo.appName}."
            }

            notifyConfigChanged()
            loadInstalledApps()
        }
    }

    fun updateAppIsolationMode(packageName: String, newMode: IsolationMode) {
        viewModelScope.launch {
            val existing = dao.getConfigForPackage(packageName) ?: return@launch
            val updated = existing.copy(isolationMode = newMode)
            dao.insertOrUpdateConfig(updated)
            if (updated.isIsolated) updated.publishEnabled()
            notifyConfigChanged()
            appendTerminalLog("Mode for $packageName set to ${newMode.name}")
            _statusMessage.value = "Updated to ${newMode.title}."
        }
    }

    fun updateRootPollution(packageName: String, block: Boolean) {
        viewModelScope.launch {
            val existing = dao.getConfigForPackage(packageName) ?: return@launch
            val updated = existing.copy(blockRootPollution = block)
            dao.insertOrUpdateConfig(updated)
            if (updated.isIsolated) updated.publishEnabled()
            notifyConfigChanged()
        }
    }

    fun updateWhitelistedFolders(packageName: String, folders: String) {
        viewModelScope.launch {
            val existing = dao.getConfigForPackage(packageName) ?: return@launch
            val updated = existing.copy(whitelistedFolders = folders)
            dao.insertOrUpdateConfig(updated)
            if (updated.isIsolated) updated.publishEnabled()
            notifyConfigChanged()
            _statusMessage.value = "Whitelisted folders updated."
        }
    }

    // ---- Encrypted locker ------------------------------------------------------------------

    private suspend fun storeInLocker(displayName: String, mimeType: String, write: (java.io.OutputStream) -> Long) {
        val target = File(lockerDir, "${UUID.randomUUID()}.isovault")
        var plainSize = 0L
        try {
            FileOutputStream(target).use { out -> plainSize = write(out) }
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
        dao.insertVaultFile(
            VaultFileItem(
                packageName = lockerPackage,
                relativePath = displayName,
                physicalPath = target.absolutePath,
                isEncrypted = true,
                originalSizeBytes = plainSize,
                encryptedSizeBytes = target.length(),
                mimeType = mimeType,
                lastModified = System.currentTimeMillis()
            )
        )
    }

    /** Encrypts a typed note into the locker. */
    fun addNote(name: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = name.trim().substringAfterLast('/').ifBlank { "note" }
                    .let { if (it.contains('.')) it else "$it.txt" }
                storeInLocker(fileName, "text/plain") { out ->
                    AesVaultCipher.encryptStream(content.toByteArray(Charsets.UTF_8).inputStream(), out)
                }
                appendTerminalLog("Locked note $fileName")
                _statusMessage.value = "Saved $fileName to the locker."
            } catch (e: Exception) {
                _statusMessage.value = "Could not save note: ${e.message}"
            }
        }
    }

    /** Encrypts a file picked by the user into the locker. */
    fun importFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = app.contentResolver
                val queriedName: String? = resolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                val name = queriedName?.takeIf { it.isNotBlank() } ?: "file_${System.currentTimeMillis()}"
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                val input = resolver.openInputStream(uri) ?: throw IllegalStateException("File could not be opened")
                input.use { src ->
                    storeInLocker(name, mime) { out -> AesVaultCipher.encryptStream(src, out) }
                }
                appendTerminalLog("Locked file $name")
                _statusMessage.value = "Locked $name in the private locker."
            } catch (e: Exception) {
                _statusMessage.value = "Import failed: ${e.message}"
            }
        }
    }

    /** Decrypts a locker file to a location the user chose. */
    fun exportFile(item: VaultFileItem, destination: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val out = app.contentResolver.openOutputStream(destination, "w")
                    ?: throw IllegalStateException("Destination could not be opened")
                out.use { dst ->
                    FileInputStream(File(item.physicalPath)).use { src -> AesVaultCipher.decryptStream(src, dst) }
                }
                _statusMessage.value = "Exported ${item.relativePath.substringAfterLast('/')}."
            } catch (e: Exception) {
                _statusMessage.value = "Export failed: ${e.message}"
            }
        }
    }

    fun deleteVaultFile(item: VaultFileItem) {
        viewModelScope.launch(Dispatchers.IO) {
            File(item.physicalPath).delete()
            dao.deleteVaultFile(item.id)
            _statusMessage.value = "Deleted ${item.relativePath.substringAfterLast('/')}."
        }
    }

    /** Decrypts a locker file and shows it as text. */
    fun decryptAndPreviewFile(item: VaultFileItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(item.physicalPath)
                if (!file.exists()) {
                    _statusMessage.value = "Locker file does not exist on disk."
                    return@launch
                }
                if (item.originalSizeBytes > MAX_PREVIEW_BYTES) {
                    _statusMessage.value = "Too large to preview. Export it instead."
                    return@launch
                }

                val content = if (item.isEncrypted) {
                    val buffer = ByteArrayOutputStream()
                    FileInputStream(file).use { AesVaultCipher.decryptStream(it, buffer) }
                    if (!item.mimeType.startsWith("text/")) {
                        withContext(Dispatchers.Main) {
                            _statusMessage.value = "${item.mimeType} cannot be previewed. Export it instead."
                        }
                        return@launch
                    }
                    String(buffer.toByteArray(), Charsets.UTF_8)
                } else {
                    file.readText()
                }

                withContext(Dispatchers.Main) {
                    _previewDecryptedText.value = Pair(item.relativePath, content)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Decryption failed: ${e.message}"
                }
            }
        }
    }

    // ---- Root volumes ----------------------------------------------------------------------

    private fun reportMount(result: MountExecutionResult, success: String, failure: String) {
        appendTerminalLog("${result.command} (exit ${result.exitCode})")
        result.output.lines().forEach { appendTerminalLog("  $it") }
        _statusMessage.value = if (result.isSuccess) success else "$failure (exit ${result.exitCode})"
    }

    /** Mounts the app's private volume over its storage root. Needs root and a running app. */
    fun attachVolume(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = MountManager.attachVolume(app, packageName)
            reportMount(result, "Volume attached to $packageName.", "Attach failed")
        }
    }

    fun detachVolume(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = MountManager.detachVolume(app, packageName)
            reportMount(result, "Volume detached from $packageName.", "Detach failed")
        }
    }

    /** Reports root access and the real attach state of every isolated app. */
    fun runRootCheck() {
        viewModelScope.launch(Dispatchers.IO) {
            val root = MountManager.isRootAvailable()
            appendTerminalLog(if (root) "Root access: granted" else "Root access: not available (volumes disabled, hooks still work)")
            if (!root) return@launch
            for (cfg in configs.value.filter { it.isIsolated }) {
                val result = MountManager.volumeStatus(app, cfg.packageName)
                appendTerminalLog("${cfg.appName}: ${result.output.replace('\n', ' ')}")
            }
        }
    }

    fun purgeVaultForPackage(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = MountManager.purgeVolume(app, packageName)
            // Locker-style records for this package, if any, are removed regardless of root.
            dao.deleteVaultFilesForPackage(packageName)
            reportMount(result, "Purged isolated storage for $packageName.", "Purge failed (volume needs root to delete)")
        }
    }

    fun clearAuditLogs() {
        viewModelScope.launch {
            dao.clearAuditLogs()
            appendTerminalLog("Audit logs cleared.")
            _statusMessage.value = "Audit logs cleared."
        }
    }

    fun generateBootScript(): String {
        val isolatedPkgs = configs.value.filter { it.isIsolated }.map { it.packageName }
        return MountManager.generateBootService(app, isolatedPkgs)
    }

    private fun appendTerminalLog(log: String) {
        val stamp = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())
        _terminalLogs.update { (it + "[$stamp] $log").takeLast(150) }
    }
}
