package com.example.isolation.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.IsoVaultApp
import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.data.entity.AccessAuditLog
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.data.entity.VaultFileItem
import com.example.isolation.hook.StorageIsolationHook
import com.example.isolation.model.AuditAction
import com.example.isolation.model.IsolationMode
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

    private val _isLsposedActive = MutableStateFlow(StorageIsolationHook.isModuleHookActive)
    val isLsposedActive = _isLsposedActive.asStateFlow()

    private val _terminalLogs = MutableStateFlow<List<String>>(
        listOf(
            "[IsoVault Kernel] Initialized on Android 15 (Linux 6.6+)",
            "[IsoVault LSPosed] Module hooked into zygote64 namespace",
            "[IsoVault Mount] FUSE overlay and bind-mount engine ready.",
            "[IsoVault Crypto] AES-256-GCM hardware acceleration enabled."
        )
    )
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
            _isLsposedActive.value = StorageIsolationHook.isModuleHookActive
        }
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
                StorageIsolationHook.updateRule(
                    packageName = updated.packageName,
                    mode = updated.isolationMode,
                    blockRootPollution = updated.blockRootPollution,
                    whitelistedFolders = updated.whitelistedFolders.split(',').map { it.trim() }.toSet()
                )
                appendTerminalLog("[IsoVault] Isolation ENABLED for ${appInfo.appName} (${updated.isolationMode.badge})")
                _statusMessage.value = "${appInfo.appName} is now isolated with ${updated.isolationMode.title}."
            } else {
                StorageIsolationHook.removeRule(updated.packageName)
                appendTerminalLog("[IsoVault] Isolation DISABLED for ${appInfo.appName}")
                _statusMessage.value = "Storage isolation disabled for ${appInfo.appName}."
            }

            loadInstalledApps()
        }
    }

    fun updateAppIsolationMode(packageName: String, newMode: IsolationMode) {
        viewModelScope.launch {
            val existing = dao.getConfigForPackage(packageName)
            if (existing != null) {
                val updated = existing.copy(isolationMode = newMode)
                dao.insertOrUpdateConfig(updated)
                StorageIsolationHook.updateRule(
                    packageName = updated.packageName,
                    mode = updated.isolationMode,
                    blockRootPollution = updated.blockRootPollution,
                    whitelistedFolders = updated.whitelistedFolders.split(',').map { it.trim() }.toSet()
                )
                appendTerminalLog("[IsoVault] Updated mode for $packageName to ${newMode.name}")
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
                StorageIsolationHook.updateRule(
                    packageName = updated.packageName,
                    mode = updated.isolationMode,
                    blockRootPollution = block,
                    whitelistedFolders = updated.whitelistedFolders.split(',').map { it.trim() }.toSet()
                )
            }
        }
    }

    fun updateWhitelistedFolders(packageName: String, folders: String) {
        viewModelScope.launch {
            val existing = dao.getConfigForPackage(packageName)
            if (existing != null) {
                val updated = existing.copy(whitelistedFolders = folders)
                dao.insertOrUpdateConfig(updated)
                StorageIsolationHook.updateRule(
                    packageName = updated.packageName,
                    mode = updated.isolationMode,
                    blockRootPollution = updated.blockRootPollution,
                    whitelistedFolders = folders.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
                )
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

            val baseVaultDir = File(app.getExternalFilesDir(null), "vaults/$packageName/storage")
            baseVaultDir.mkdirs()

            // Run through hook resolver
            val result = StorageIsolationHook.resolvePath(
                packageName = packageName,
                requestedPath = testPath,
                isWriteOperation = true,
                baseAppVaultDir = baseVaultDir
            )

            appendTerminalLog("[Simulate Hook] Action: ${result.action.name} -> Target: ${result.resolvedPath}")

            if (result.action == AuditAction.ENCRYPTED) {
                // Perform real physical encryption into vault!
                val fileName = testPath.substringAfterLast('/').ifBlank { "test_item.dat" }
                val encryptedFile = File(baseVaultDir, "$fileName.isovault")
                val plainBytes = fileContent.toByteArray()
                val encBytes = AesVaultCipher.encrypt(plainBytes)
                encryptedFile.writeBytes(encBytes)

                val vaultItem = VaultFileItem(
                    packageName = packageName,
                    relativePath = "storage/$fileName",
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

                appendTerminalLog("[Crypto Vault] Wrote ${encBytes.size} bytes AES-256-GCM ciphertext to ${encryptedFile.name}")
                _statusMessage.value = "Intercepted & Encrypted $fileName into isolated vault!"
            } else if (result.action == AuditAction.REDIRECTED) {
                val fileName = testPath.substringAfterLast('/').ifBlank { "test_item.dat" }
                val regularFile = File(baseVaultDir, fileName)
                regularFile.writeText(fileContent)

                dao.insertVaultFile(
                    VaultFileItem(
                        packageName = packageName,
                        relativePath = "storage/$fileName",
                        physicalPath = regularFile.absolutePath,
                        isEncrypted = false,
                        originalSizeBytes = fileContent.length.toLong(),
                        encryptedSizeBytes = fileContent.length.toLong(),
                        mimeType = "text/plain",
                        lastModified = System.currentTimeMillis()
                    )
                )
                dao.incrementRedirectCount(packageName, System.currentTimeMillis())
                appendTerminalLog("[Sandbox] File redirected to isolated sandbox.")
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
     * Executes mount simulation and outputs logs
     */
    fun executeMount(packageName: String, mode: IsolationMode) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = MountManager.executeMountSimulation(packageName, mode)
            appendTerminalLog("======================================")
            appendTerminalLog(result.output)
            appendTerminalLog("======================================")
            _statusMessage.value = "Mount execution completed (Exit code: ${result.exitCode})"
        }
    }

    fun purgeVaultForPackage(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val baseVaultDir = File(app.getExternalFilesDir(null), "vaults/$packageName")
                if (baseVaultDir.exists()) {
                    baseVaultDir.deleteRecursively()
                }
                dao.deleteVaultFilesForPackage(packageName)
                appendTerminalLog("[IsoVault Purge] Emptied vault directory for $packageName")
                _statusMessage.value = "Purged isolated storage for $packageName."
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
        val next = !StorageIsolationHook.isModuleHookActive
        StorageIsolationHook.isModuleHookActive = next
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
