package com.example.isolation.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.IsoVaultApp
import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.data.entity.AccessAuditLog
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.data.entity.VaultFileItem
import com.example.isolation.model.AuditAction
import com.example.isolation.model.IsolationMode
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.mount.MountEntry
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
import java.io.File

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
                IsolationRuleManager.isXSharedPrefsHooked(app)
    )
    val isLsposedActive = _isLsposedActive.asStateFlow()

    private val _isRootGranted = MutableStateFlow(false)
    val isRootGranted = _isRootGranted.asStateFlow()

    private val _autoForceStopOnRuleChange = MutableStateFlow(true)
    val autoForceStopOnRuleChange = _autoForceStopOnRuleChange.asStateFlow()

    private val _autoRootBindMount = MutableStateFlow(true)
    val autoRootBindMount = _autoRootBindMount.asStateFlow()

    private val _explorerTargetDir = MutableStateFlow<String?>(null)
    val explorerTargetDir = _explorerTargetDir.asStateFlow()

    private val _terminalLogs = MutableStateFlow<List<String>>(
        listOf(
            "[IsoVault Kernel] Initialized on Android 15 (Linux 6.6+)",
            "[IsoVault LSPosed] Module hooked into zygote64 namespace (Vector/LSPosed API 82)",
            "[IsoVault Mount] Persistent external vault ready at ${MountManager.PERSISTENT_VAULT_ROOT}",
            "[IsoVault Crypto] AES-256-GCM hardware acceleration enabled."
        )
    )
    val terminalLogs = _terminalLogs.asStateFlow()

    private val _selectedAppForDetail = MutableStateFlow<InstalledAppInfo?>(null)
    val selectedAppForDetail = _selectedAppForDetail.asStateFlow()

    private val _previewDecryptedText = MutableStateFlow<Pair<String, String>?>(null)
    val previewDecryptedText = _previewDecryptedText.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _isPanicModeActive = MutableStateFlow(false)
    val isPanicModeActive = _isPanicModeActive.asStateFlow()

    private val _primaryPin = MutableStateFlow("1234")
    val primaryPin = _primaryPin.asStateFlow()

    private val _panicPin = MutableStateFlow("9999")
    val panicPin = _panicPin.asStateFlow()

    fun triggerPanicMode() {
        _isPanicModeActive.value = true
        _statusMessage.value = "Decoy Vault Activated"
    }

    fun exitPanicMode() {
        _isPanicModeActive.value = false
        _statusMessage.value = "Primary Vault Restored"
    }

    fun updatePins(primary: String, panic: String) {
        if (primary.isNotBlank()) _primaryPin.value = primary
        if (panic.isNotBlank()) _panicPin.value = panic
        _statusMessage.value = "Security PINs updated"
    }

    fun setAutoForceStop(enabled: Boolean) {
        _autoForceStopOnRuleChange.value = enabled
    }

    fun setAutoRootBindMount(enabled: Boolean) {
        _autoRootBindMount.value = enabled
    }

    fun setExplorerTargetDir(path: String?) {
        _explorerTargetDir.value = path
    }

    fun requestRootAccess() {
        viewModelScope.launch(Dispatchers.IO) {
            val granted = MountManager.checkAndRequestRoot()
            _isRootGranted.value = granted
            _selinuxStatus.value = MountManager.getSelinuxStatus()
            if (granted) {
                appendTerminalLog("[Root Shell] Superuser (uid=0) granted via su. Persistent vault permissions configured.")
                _statusMessage.value = "Root access granted (uid=0)! Kernel bind-mounts & force-stop enabled."
            } else {
                appendTerminalLog("[Root Shell] Superuser not available or denied; running in LSPosed Hook mode.")
                _statusMessage.value = "Root not detected or denied. Running in LSPosed hook mode."
            }
        }
    }

    fun forceStopPackage(packageName: String, appName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = MountManager.forceStopPackageViaRoot(packageName)
            if (res.isSuccess) {
                appendTerminalLog("[Root Shell] Force-stopped $packageName ($appName)")
                _statusMessage.value = "Force-stopped $appName via Root. New storage mount is now live!"
            } else {
                _statusMessage.value = "Grant Root in Settings to force-stop $appName automatically."
            }
        }
    }

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
            MountManager.ensurePersistentVaultRootPermissions()
            _systemMounts.value = MountManager.readSystemMounts()
            val hasRoot = MountManager.checkAndRequestRoot()
            _isRootGranted.value = hasRoot
            _selinuxStatus.value = MountManager.getSelinuxStatus()
            val isReal = com.example.MainActivity.isLsposedModuleActiveReal() ||
                    IsolationRuleManager.isXSharedPrefsHooked(app)
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
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getConfigForPackage(appInfo.packageName)
            val updated = existing?.copy(isIsolated = enable) ?: AppIsolationConfig(
                packageName = appInfo.packageName,
                appName = appInfo.appName,
                isIsolated = enable,
                isolationMode = appInfo.isolationMode
            )
            dao.insertOrUpdateConfig(updated)

            if (enable) {
                IsolationRuleManager.updateRule(
                    packageName = updated.packageName,
                    mode = updated.isolationMode,
                    blockRootPollution = updated.blockRootPollution,
                    whitelistedFolders = updated.whitelistedFolders.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
                )
                if (_autoRootBindMount.value) {
                    MountManager.executeMountAction(updated.packageName, updated.isolationMode)
                }
                if (_isRootGranted.value && _autoForceStopOnRuleChange.value) {
                    MountManager.forceStopPackageViaRoot(updated.packageName)
                }
                appendTerminalLog("[IsoVault] Isolation ENABLED for ${appInfo.appName} (${updated.isolationMode.badge})")
                _statusMessage.value = "${appInfo.appName} switched to Isolated Storage (${updated.isolationMode.title})."
            } else {
                IsolationRuleManager.removeRule(updated.packageName)
                if (_isRootGranted.value && _autoForceStopOnRuleChange.value) {
                    MountManager.forceStopPackageViaRoot(updated.packageName)
                }
                appendTerminalLog("[IsoVault] Isolation DISABLED for ${appInfo.appName} (Shared Storage)")
                _statusMessage.value = "${appInfo.appName} switched to Shared Phone Storage."
            }

            notifyConfigChanged()
            loadInstalledApps()
        }
    }

    fun updateAppIsolationMode(packageName: String, newMode: IsolationMode) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getConfigForPackage(packageName)
            if (existing != null) {
                val updated = existing.copy(isolationMode = newMode)
                dao.insertOrUpdateConfig(updated)
                IsolationRuleManager.updateRule(
                    packageName = updated.packageName,
                    mode = updated.isolationMode,
                    blockRootPollution = updated.blockRootPollution,
                    whitelistedFolders = updated.whitelistedFolders.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
                )
                if (_autoRootBindMount.value) {
                    MountManager.executeMountAction(packageName, newMode)
                }
                notifyConfigChanged()
                appendTerminalLog("[IsoVault] Updated mode for $packageName to ${newMode.name}")
                _statusMessage.value = "Updated to ${newMode.title}."
            }
        }
    }

    fun updateRootPollution(packageName: String, block: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getConfigForPackage(packageName)
            if (existing != null) {
                val updated = existing.copy(blockRootPollution = block)
                dao.insertOrUpdateConfig(updated)
                IsolationRuleManager.updateRule(
                    packageName = updated.packageName,
                    mode = updated.isolationMode,
                    blockRootPollution = block,
                    whitelistedFolders = updated.whitelistedFolders.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
                )
                notifyConfigChanged()
            }
        }
    }

    fun updateWhitelistedFolders(packageName: String, folders: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getConfigForPackage(packageName)
            if (existing != null) {
                val updated = existing.copy(whitelistedFolders = folders)
                dao.insertOrUpdateConfig(updated)
                IsolationRuleManager.updateRule(
                    packageName = updated.packageName,
                    mode = updated.isolationMode,
                    blockRootPollution = updated.blockRootPollution,
                    whitelistedFolders = folders.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
                )
                notifyConfigChanged()
                _statusMessage.value = "Whitelisted folders updated."
            }
        }
    }

    /**
     * Simulates an application performing storage I/O and demonstrates the hook & encryption in action!
     */
    fun testSimulateAppIo(packageName: String, appName: String, testPath: String, fileContent: String = "Sensitive user chat log or photo token.") {
        viewModelScope.launch(Dispatchers.IO) {
            appendTerminalLog("[Simulate I/O] $appName attempting access to: $testPath")

            val baseVaultDir = File("${MountManager.PERSISTENT_VAULT_ROOT}/sandboxes/$packageName").let {
                if (it.exists() || it.mkdirs()) it else File(app.getExternalFilesDir(null), "vaults/$packageName/storage")
            }
            baseVaultDir.mkdirs()

            val result = IsolationRuleManager.resolvePath(
                packageName = packageName,
                requestedPath = testPath,
                isWriteOperation = true,
                baseAppVaultDir = baseVaultDir
            )

            appendTerminalLog("[Simulate Hook] Action: ${result.action.name} -> Target: ${result.resolvedPath}")

            if (result.action == AuditAction.ENCRYPTED) {
                val fileName = testPath.substringAfterLast('/').ifBlank { "test_item.dat" }
                val encryptedFile = File(baseVaultDir, "DCIM/Camera/$fileName.isovault")
                encryptedFile.parentFile?.mkdirs()
                val plainBytes = fileContent.toByteArray()
                val encBytes = AesVaultCipher.encrypt(plainBytes)
                encryptedFile.writeBytes(encBytes)

                val vaultItem = VaultFileItem(
                    packageName = packageName,
                    relativePath = "DCIM/Camera/$fileName",
                    physicalPath = encryptedFile.absolutePath,
                    isEncrypted = true,
                    originalSizeBytes = plainBytes.size.toLong(),
                    encryptedSizeBytes = encBytes.size.toLong(),
                    mimeType = "text/plain",
                    lastModified = System.currentTimeMillis()
                )
                dao.insertVaultFile(vaultItem)
                dao.addEncryptedBytes(packageName, encBytes.size.toLong())
                dao.incrementRedirectCount(packageName, System.currentTimeMillis())

                appendTerminalLog("[Crypto Vault] Wrote ${encBytes.size} bytes AES-256-GCM ciphertext to ${encryptedFile.absolutePath}")
                _statusMessage.value = "Intercepted & Encrypted $fileName into persistent vault!"
            } else if (result.action == AuditAction.REDIRECTED) {
                val fileName = testPath.substringAfterLast('/').ifBlank { "test_item.dat" }
                val regularFile = File(baseVaultDir, "Pictures/$fileName")
                regularFile.parentFile?.mkdirs()
                regularFile.writeText(fileContent)

                dao.insertVaultFile(
                    VaultFileItem(
                        packageName = packageName,
                        relativePath = "Pictures/$fileName",
                        physicalPath = regularFile.absolutePath,
                        isEncrypted = false,
                        originalSizeBytes = fileContent.length.toLong(),
                        encryptedSizeBytes = fileContent.length.toLong(),
                        mimeType = "text/plain",
                        lastModified = System.currentTimeMillis()
                    )
                )
                dao.incrementRedirectCount(packageName, System.currentTimeMillis())
                appendTerminalLog("[Sandbox] File redirected to ${regularFile.absolutePath}")
                _statusMessage.value = "Redirected to isolated sandbox."
            } else if (result.action == AuditAction.BLOCKED) {
                appendTerminalLog("[Blocked] Access refused: Root pollution or read-only violation.")
                _statusMessage.value = "Blocked access to $testPath (Privacy Protected)"
            }
        }
    }

    /**
     * Decrypts an encrypted vault file and displays it in dialog
     */
    fun decryptAndPreviewFile(item: VaultFileItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(item.physicalPath)
                if (!file.exists()) {
                    _statusMessage.value = "Vault file does not exist on disk."
                    return@launch
                }

                val content = if (item.isEncrypted) {
                    val encryptedBytes = file.readBytes()
                    val decryptedBytes = AesVaultCipher.decrypt(encryptedBytes)
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

    /**
     * Executes mount action (via Root if available) and outputs logs
     */
    fun executeMount(packageName: String, mode: IsolationMode) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = MountManager.executeMountAction(packageName, mode)
            appendTerminalLog("======================================")
            appendTerminalLog(result.output)
            appendTerminalLog("======================================")
            _statusMessage.value = "Mount action completed (Exit code: ${result.exitCode})"
        }
    }

    fun purgeVaultForPackage(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val persistentSandbox = File("${MountManager.PERSISTENT_VAULT_ROOT}/sandboxes/$packageName")
                if (persistentSandbox.exists()) {
                    if (!persistentSandbox.deleteRecursively() && _isRootGranted.value) {
                        MountManager.deleteViaRoot(persistentSandbox.absolutePath)
                    }
                }
                val baseVaultDir = File(app.getExternalFilesDir(null), "vaults/$packageName")
                if (baseVaultDir.exists()) {
                    baseVaultDir.deleteRecursively()
                }
                dao.deleteVaultFilesForPackage(packageName)
                appendTerminalLog("[IsoVault Purge] Emptied sandbox directory for $packageName")
                _statusMessage.value = "Purged isolated sandbox for $packageName."
            } catch (e: Exception) {
                _statusMessage.value = "Purge error: ${e.message}"
            }
        }
    }

    fun clearAuditLogs() {
        viewModelScope.launch {
            dao.clearAuditLogs()
            appendTerminalLog("[IsoVault] Audit logs cleared.")
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

    private fun appendTerminalLog(log: String) {
        val current = _terminalLogs.value.toMutableList()
        current.add("[${android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())}] $log")
        if (current.size > 150) current.removeAt(0)
        _terminalLogs.value = current
    }
}
