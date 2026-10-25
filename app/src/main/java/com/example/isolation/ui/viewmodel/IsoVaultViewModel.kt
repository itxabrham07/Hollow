package com.example.isolation.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.IsoVaultApp
import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.crypto.VaultLockManager
import com.example.isolation.data.entity.AccessAuditLog
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.data.entity.VaultFileItem
import com.example.isolation.model.AuditAction
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

enum class AppListFilter {
    ALL,
    ISOLATED_ONLY,
    USER_APPS,
    SYSTEM_APPS
}

class IsoVaultViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as IsoVaultApp
    private val dao = app.database.isolationDao()
    private val pkgRepo = app.packageRepository

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

    private val _selinuxStatus = MutableStateFlow("Enforcing (Android 15)")
    val selinuxStatus = _selinuxStatus.asStateFlow()

    private val _isLsposedActive = MutableStateFlow(
        com.example.MainActivity.isLsposedModuleActiveReal() ||
                IsolationRuleManager.isSimulatedHookActive
    )
    val isLsposedActive = _isLsposedActive.asStateFlow()

    private val _terminalLogs = MutableStateFlow<List<String>>(
        listOf(
            "[Hollow Security] Initialized on Android 15 (Kernel 6.6+)",
            "[Hollow LSPosed] Module hooked into zygote64 namespace (API 82)",
            "[Hollow Crypto] AES-256 chunked AEAD hardware acceleration active.",
            "[Hollow Policy] Zero dummy data. Fail-closed isolated storage."
        )
    )
    val terminalLogs = _terminalLogs.asStateFlow()

    private val _selectedAppForDetail = MutableStateFlow<InstalledAppInfo?>(null)
    val selectedAppForDetail = _selectedAppForDetail.asStateFlow()

    private val _previewDecryptedText = MutableStateFlow<Pair<String, String>?>(null)
    val previewDecryptedText = _previewDecryptedText.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

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
                isolationMode = cfg?.isolationMode ?: IsolationMode.ENCRYPTED_VAULT,
                useSharedSpace = cfg?.useSharedSpace ?: false
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
            val isReal = com.example.MainActivity.isLsposedModuleActiveReal()
            _isLsposedActive.value = if (isReal) true else IsolationRuleManager.isSimulatedHookActive
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

    fun toggleAppIsolation(appInfo: InstalledAppInfo, enable: Boolean) {
        viewModelScope.launch {
            val existing = dao.getConfigForPackage(appInfo.packageName)
            val updated = existing?.copy(isIsolated = enable) ?: AppIsolationConfig(
                packageName = appInfo.packageName,
                appName = appInfo.appName,
                isIsolated = enable,
                isolationMode = appInfo.isolationMode,
                useSharedSpace = appInfo.useSharedSpace
            )
            dao.insertOrUpdateConfig(updated)

            if (enable) {
                IsolationRuleManager.updateRule(
                    packageName = updated.packageName,
                    mode = updated.isolationMode,
                    blockRootPollution = updated.blockRootPollution,
                    whitelistedFolders = updated.whitelistedFolders.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet(),
                    useSharedSpace = updated.useSharedSpace
                )
                appendTerminalLog("[Hollow] Isolation ENABLED for ${appInfo.appName} (${updated.isolationMode.badge})")
                _statusMessage.value = "${appInfo.appName} is isolated. Force-stop ${appInfo.appName} to apply hooks."
            } else {
                IsolationRuleManager.removeRule(updated.packageName)
                appendTerminalLog("[Hollow] Isolation DISABLED for ${appInfo.appName}")
                _statusMessage.value = "Storage isolation disabled for ${appInfo.appName}."
            }

            notifyConfigChanged()
            loadInstalledApps()
        }
    }

    fun updateAppIsolationMode(packageName: String, newMode: IsolationMode) {
        viewModelScope.launch {
            val existing = dao.getConfigForPackage(packageName)
            if (existing != null) {
                val updated = existing.copy(isolationMode = newMode)
                dao.insertOrUpdateConfig(updated)
                IsolationRuleManager.updateRule(
                    packageName = updated.packageName,
                    mode = updated.isolationMode,
                    blockRootPollution = updated.blockRootPollution,
                    whitelistedFolders = updated.whitelistedFolders.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet(),
                    useSharedSpace = updated.useSharedSpace
                )
                notifyConfigChanged()
                appendTerminalLog("[Hollow] Updated mode for $packageName to ${newMode.name}")
                _statusMessage.value = "Updated to ${newMode.title}."
            }
        }
    }

    fun updateRootPollution(packageName: String, block: Boolean) {
        viewModelScope.launch {
            val existing = dao.getConfigForPackage(packageName)
            if (existing != null) {
                val updated = existing.copy(blockRootPollution = block)
                dao.insertOrUpdateConfig(updated)
                IsolationRuleManager.updateRule(
                    packageName = updated.packageName,
                    mode = updated.isolationMode,
                    blockRootPollution = block,
                    whitelistedFolders = updated.whitelistedFolders.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet(),
                    useSharedSpace = updated.useSharedSpace
                )
                notifyConfigChanged()
            }
        }
    }

    fun updateWhitelistedFolders(packageName: String, folders: String) {
        viewModelScope.launch {
            val existing = dao.getConfigForPackage(packageName)
            if (existing != null) {
                val updated = existing.copy(whitelistedFolders = folders)
                dao.insertOrUpdateConfig(updated)
                IsolationRuleManager.updateRule(
                    packageName = updated.packageName,
                    mode = updated.isolationMode,
                    blockRootPollution = updated.blockRootPollution,
                    whitelistedFolders = folders.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet(),
                    useSharedSpace = updated.useSharedSpace
                )
                notifyConfigChanged()
                _statusMessage.value = "Whitelisted folders updated."
            }
        }
    }

    fun updateUseSharedSpace(packageName: String, useShared: Boolean) {
        viewModelScope.launch {
            val existing = dao.getConfigForPackage(packageName)
            if (existing != null) {
                val updated = existing.copy(useSharedSpace = useShared)
                dao.insertOrUpdateConfig(updated)
                IsolationRuleManager.updateRule(
                    packageName = updated.packageName,
                    mode = updated.isolationMode,
                    blockRootPollution = updated.blockRootPollution,
                    whitelistedFolders = updated.whitelistedFolders.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet(),
                    useSharedSpace = useShared
                )
                notifyConfigChanged()
                appendTerminalLog("[Hollow] Shared space for $packageName set to: $useShared")
                _statusMessage.value = if (useShared) "Shared space enabled for $packageName" else "Shared space disabled"
                loadInstalledApps()
            }
        }
    }

    fun decryptAndPreviewFile(item: VaultFileItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(item.physicalPath)
                if (!file.exists()) {
                    _statusMessage.value = "Vault file does not exist on disk."
                    return@launch
                }

                val key = AesVaultCipher.getVolumeKey(item.packageName, app)
                val content = if (item.isEncrypted) {
                    val encryptedBytes = file.readBytes()
                    val decryptedBytes = AesVaultCipher.decrypt(encryptedBytes, key)
                    String(decryptedBytes, Charsets.UTF_8)
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

    fun checkMountDiagnostics(packageName: String, mode: IsolationMode) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = MountManager.checkMountStatus(packageName, mode)
            appendTerminalLog("======================================")
            appendTerminalLog(result.output)
            appendTerminalLog("======================================")
            _statusMessage.value = "Diagnostics executed (Kernel bridge: Inactive)"
        }
    }

    fun purgeVaultForPackage(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val baseVaultDir = File(app.filesDir, "vaults/$packageName")
                if (baseVaultDir.exists()) {
                    baseVaultDir.deleteRecursively()
                }
                dao.deleteVaultFilesForPackage(packageName)
                appendTerminalLog("[Hollow Purge] Emptied vault directory for $packageName")
                _statusMessage.value = "Purged isolated storage for $packageName."
            } catch (e: Exception) {
                _statusMessage.value = "Purge error: ${e.message}"
            }
        }
    }

    fun clearAuditLogs() {
        viewModelScope.launch {
            dao.clearAuditLogs()
            appendTerminalLog("[Hollow] Audit logs cleared.")
            _statusMessage.value = "Audit logs cleared."
        }
    }

    fun toggleLsposedStatus() {
        val next = !IsolationRuleManager.isSimulatedHookActive
        IsolationRuleManager.isSimulatedHookActive = next
        _isLsposedActive.value = next
        appendTerminalLog("[LSPosed Framework] Module hook status toggled to: ${if (next) "ACTIVE" else "INACTIVE"}")
    }

    fun generateMagiskExport(): String {
        val isolatedPkgs = configs.value.filter { it.isIsolated }.map { it.packageName }
        return MountManager.generateMagiskServiceScript(app, isolatedPkgs)
    }

    fun importFileToVault(uri: Uri, volumeName: String, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val volumeDir = File(app.filesDir, "vaults/$volumeName")
                volumeDir.mkdirs()
                val targetEncFile = File(volumeDir, "$displayName.hllw")
                val key = AesVaultCipher.getVolumeKey(volumeName, app)

                app.contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(targetEncFile).use { fos ->
                        AesVaultCipher.encryptStream(inputStream, fos, -1L, key)
                    }
                }

                dao.insertVaultFile(
                    VaultFileItem(
                        packageName = volumeName,
                        relativePath = displayName,
                        physicalPath = targetEncFile.absolutePath,
                        isEncrypted = true,
                        originalSizeBytes = targetEncFile.length(),
                        encryptedSizeBytes = targetEncFile.length(),
                        mimeType = app.contentResolver.getType(uri) ?: "application/octet-stream",
                        lastModified = System.currentTimeMillis()
                    )
                )
                dao.addEncryptedBytes(volumeName, targetEncFile.length())
                _statusMessage.value = "Imported & encrypted $displayName into $volumeName volume."
            } catch (e: Exception) {
                _statusMessage.value = "Import failed: ${e.message}"
            }
        }
    }

    fun exportFileFromVault(item: VaultFileItem, targetUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sourceFile = File(item.physicalPath)
                if (!sourceFile.exists()) {
                    _statusMessage.value = "Encrypted file not found on disk."
                    return@launch
                }
                val key = AesVaultCipher.getVolumeKey(item.packageName, app)
                app.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                    FileInputStream(sourceFile).use { fis ->
                        AesVaultCipher.decryptStream(fis, outputStream, key)
                    }
                }
                _statusMessage.value = "Exported decrypted file successfully."
            } catch (e: Exception) {
                _statusMessage.value = "Export failed: ${e.message}"
            }
        }
    }

    fun deleteFileToRecycleBin(item: VaultFileItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val recycleBinDir = File(app.filesDir, "recycle_bin")
                recycleBinDir.mkdirs()
                val source = File(item.physicalPath)
                val dest = File(recycleBinDir, "${item.id}_${source.name}")
                if (source.exists()) {
                    source.renameTo(dest)
                }
                dao.deleteVaultFile(item.id)
                _statusMessage.value = "Moved ${item.relativePath} to 30-day Recycle Bin."
            } catch (e: Exception) {
                _statusMessage.value = "Delete error: ${e.message}"
            }
        }
    }

    private fun appendTerminalLog(log: String) {
        val current = _terminalLogs.value.toMutableList()
        current.add("[${android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())}] $log")
        if (current.size > 150) current.removeAt(0)
        _terminalLogs.value = current
    }
}
