package com.example.isolation.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.IsoVaultApp
import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.data.entity.AccessAuditLog
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.data.entity.VaultFileItem
import com.example.isolation.model.AppSettings
import com.example.isolation.model.IsolationMode
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.model.PickerPolicy
import com.example.isolation.model.SettingsStore
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
        private const val MAX_PREVIEW_BYTES = 2 * 1024 * 1024
        private const val MAX_IMAGE_PREVIEW_BYTES = 25 * 1024 * 1024
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

    private val _filter = MutableStateFlow(AppListFilter.ISOLATED_ONLY)
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

    private val _settings = MutableStateFlow(SettingsStore.load(app))
    val settings = _settings.asStateFlow()

    private val _rootStatus = MutableStateFlow("Not checked")
    val rootStatus = _rootStatus.asStateFlow()

    private val _terminalLogs = MutableStateFlow<List<String>>(emptyList())
    val terminalLogs = _terminalLogs.asStateFlow()

    private val _selectedAppForDetail = MutableStateFlow<InstalledAppInfo?>(null)
    val selectedAppForDetail = _selectedAppForDetail.asStateFlow()

    private val _previewDecryptedText = MutableStateFlow<Pair<String, String>?>(null) // (Title, Content)
    val previewDecryptedText = _previewDecryptedText.asStateFlow()

    private val _previewDecryptedBitmap = MutableStateFlow<Pair<String, Bitmap>?>(null)
    val previewDecryptedBitmap = _previewDecryptedBitmap.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    // Full list of installed apps merged with current isolation config (used by "+ Add Apps" picker)
    val allInstalledApps: StateFlow<List<InstalledAppInfo>> = combine(
        _rawInstalledApps,
        configs
    ) { apps, cfgList ->
        val cfgMap = cfgList.associateBy { it.packageName }
        apps.map { app ->
            val cfg = cfgMap[app.packageName]
            app.copy(
                isIsolated = cfg?.isIsolated ?: false,
                isolationMode = cfg?.isolationMode ?: IsolationMode.ENCRYPTED_VAULT
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered apps list combining search and filter
    val filteredApps: StateFlow<List<InstalledAppInfo>> = combine(
        allInstalledApps,
        _searchQuery,
        _filter
    ) { apps, query, flt ->
        apps.filter { app ->
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
        _previewDecryptedBitmap.value = null
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun postStatusMessage(msg: String) {
        _statusMessage.value = msg
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

    // ---- Settings --------------------------------------------------------------------------

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        viewModelScope.launch(Dispatchers.IO) { SettingsStore.save(app, next) }
    }

    fun setRootVolumesEnabled(enabled: Boolean) = updateSettings { it.copy(rootVolumesEnabled = enabled) }

    fun setEncryptVolumesAtRest(enabled: Boolean) = updateSettings { it.copy(encryptVolumesAtRest = enabled) }

    fun setDarkTheme(enabled: Boolean) = updateSettings { it.copy(darkTheme = enabled) }

    fun setDynamicColor(enabled: Boolean) = updateSettings { it.copy(dynamicColor = enabled) }

    fun setPickerPolicy(policy: PickerPolicy) {
        updateSettings { it.copy(pickerPolicy = policy) }
        _statusMessage.value = "System picker: ${policy.title}. Force-stop isolated apps to apply."
    }

    fun checkRoot() {
        viewModelScope.launch(Dispatchers.IO) {
            _rootStatus.value = "Checking..."
            _rootStatus.value = if (MountManager.isRootAvailable()) "Root access granted" else "No root access"
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
                _statusMessage.value = "Saved $fileName to the encrypted locker."
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
                val mime = resolver.getType(uri) ?: inferMimeType(name)
                val input = resolver.openInputStream(uri) ?: throw IllegalStateException("File could not be opened")
                input.use { src ->
                    storeInLocker(name, mime) { out -> AesVaultCipher.encryptStream(src, out) }
                }
                appendTerminalLog("Locked file $name")
                _statusMessage.value = "Encrypted $name into the Private Locker."
            } catch (e: Exception) {
                _statusMessage.value = "Import failed: ${e.message}"
            }
        }
    }

    /** Encrypts a local File into the Private Locker, optionally deleting the plaintext original. */
    fun encryptLocalFileToLocker(sourceFile: File, deleteOriginal: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!sourceFile.exists() || !sourceFile.isFile) {
                    _statusMessage.value = "File not found."
                    return@launch
                }
                val name = sourceFile.name
                val mime = inferMimeType(name)
                FileInputStream(sourceFile).use { src ->
                    storeInLocker(name, mime) { out -> AesVaultCipher.encryptStream(src, out) }
                }
                if (deleteOriginal) {
                    sourceFile.delete()
                }
                appendTerminalLog("Encrypted $name into Locker (deleteOriginal=$deleteOriginal)")
                _statusMessage.value = if (deleteOriginal) {
                    "Encrypted & shredded original: $name"
                } else {
                    "Encrypted copy of $name into Private Locker."
                }
            } catch (e: Exception) {
                _statusMessage.value = "Encryption failed: ${e.message}"
            }
        }
    }

    private fun inferMimeType(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4", "mkv", "mov", "webm" -> "video/mp4"
            "mp3", "m4a", "wav", "ogg", "flac" -> "audio/mpeg"
            "txt", "md", "log", "json", "xml", "csv", "sh", "ini", "conf", "properties" -> "text/plain"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
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

    /** Decrypts a locker file into the workspace directory so it can be managed in File Explorer. */
    fun decryptVaultFileToWorkspace(item: VaultFileItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val workspaceExports = File(app.filesDir, "workspace/Decrypted").apply { mkdirs() }
                val cleanName = item.relativePath.substringAfterLast('/').ifBlank { "decrypted_${item.id}" }
                val dest = File(workspaceExports, cleanName)
                FileOutputStream(dest).use { dst ->
                    FileInputStream(File(item.physicalPath)).use { src -> AesVaultCipher.decryptStream(src, dst) }
                }
                appendTerminalLog("Decrypted ${cleanName} to Workspace/Decrypted")
                _statusMessage.value = "Decrypted $cleanName to Workspace/Decrypted."
            } catch (e: Exception) {
                _statusMessage.value = "Decryption failed: ${e.message}"
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

    /** Decrypts a locker file and shows it as text or image bitmap. */
    fun decryptAndPreviewFile(item: VaultFileItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(item.physicalPath)
                if (!file.exists()) {
                    _statusMessage.value = "Locker file does not exist on disk."
                    return@launch
                }

                val isImage = item.mimeType.startsWith("image/") ||
                        item.relativePath.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
                val isText = item.mimeType.startsWith("text/") ||
                        item.relativePath.substringAfterLast('.', "").lowercase() in setOf("txt", "md", "json", "xml", "log", "csv", "sh", "properties", "ini")

                if (isImage) {
                    if (item.originalSizeBytes > MAX_IMAGE_PREVIEW_BYTES) {
                        _statusMessage.value = "Image too large to preview. Export it instead."
                        return@launch
                    }
                    val bytes = if (item.isEncrypted) {
                        val buffer = ByteArrayOutputStream()
                        FileInputStream(file).use { AesVaultCipher.decryptStream(it, buffer) }
                        buffer.toByteArray()
                    } else {
                        file.readBytes()
                    }
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        withContext(Dispatchers.Main) {
                            _previewDecryptedBitmap.value = Pair(item.relativePath, bmp)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            _statusMessage.value = "Could not decode image preview."
                        }
                    }
                    return@launch
                }

                if (item.originalSizeBytes > MAX_PREVIEW_BYTES) {
                    _statusMessage.value = "Too large to preview. Export it instead."
                    return@launch
                }

                val content = if (item.isEncrypted) {
                    val buffer = ByteArrayOutputStream()
                    FileInputStream(file).use { AesVaultCipher.decryptStream(it, buffer) }
                    if (!isText) {
                        withContext(Dispatchers.Main) {
                            _statusMessage.value = "${item.mimeType} cannot be previewed as text. Decrypt to Workspace or Export instead."
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

    private fun rootFeatureEnabled(): Boolean {
        if (_settings.value.rootVolumesEnabled) return true
        _statusMessage.value = "Turn on Root volumes in Settings first."
        return false
    }

    /**
     * Unlocks the app's volume if it is sealed, then mounts it over the app's storage root.
     * Needs root and a running app.
     */
    fun attachVolume(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!rootFeatureEnabled()) return@launch

            if (MountManager.isSealed(app, packageName)) {
                val unsealed = MountManager.unsealVolume(app, packageName)
                if (!unsealed.isSuccess) {
                    reportMount(unsealed, "", "Could not unlock the encrypted volume")
                    return@launch
                }
                appendTerminalLog("Encrypted volume unlocked for $packageName")
            }

            val result = MountManager.attachVolume(app, packageName)
            reportMount(result, "Volume attached to $packageName.", "Attach failed")
            if (!result.isSuccess && result.exitCode == 5) {
                _statusMessage.value = "Open the app first, then press Attach again. Its volume is unlocked (not encrypted) until you detach."
            }
        }
    }

    /**
     * Unmounts the volume. With "Encrypt volumes when detached" on, the app is then stopped and
     * its volume is sealed (encrypted, plaintext deleted).
     */
    fun detachVolume(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!rootFeatureEnabled()) return@launch

            val detached = MountManager.detachVolume(app, packageName)
            reportMount(detached, "Volume detached from $packageName.", "Detach failed")
            if (!detached.isSuccess || !_settings.value.encryptVolumesAtRest) return@launch

            val sealed = MountManager.sealVolume(app, packageName)
            reportMount(sealed, "Volume of $packageName is now encrypted. The app was stopped.", "Could not encrypt the volume")
        }
    }

    /** Reports root access and the real attach state of every isolated app. */
    fun runRootCheck() {
        viewModelScope.launch(Dispatchers.IO) {
            val root = MountManager.isRootAvailable()
            _rootStatus.value = if (root) "Root access granted" else "No root access"
            appendTerminalLog(if (root) "Root access: granted" else "Root access: not available (volumes disabled, hooks still work)")
            if (!root) return@launch
            for (cfg in configs.value.filter { it.isIsolated }) {
                val sealed = MountManager.isSealed(app, cfg.packageName)
                val state = if (sealed) "encrypted (sealed)" else MountManager.volumeStatus(app, cfg.packageName).output.replace('\n', ' ')
                appendTerminalLog("${cfg.appName}: $state")
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
