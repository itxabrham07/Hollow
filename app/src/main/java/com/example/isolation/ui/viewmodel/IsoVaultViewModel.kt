package com.example.isolation.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.data.IsoVaultDatabase
import com.example.isolation.data.entity.AccessAuditLog
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.data.entity.VaultFileItem
import com.example.isolation.data.repository.InstalledAppInfo
import com.example.isolation.data.repository.IsoVaultRepository
import com.example.MainActivity
import com.example.isolation.model.AuditAction
import com.example.isolation.model.IsolationMode
import com.example.isolation.model.MountEngineStatus
import com.example.isolation.ui.screens.VaultEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class DashboardUiState(
    val isLsposedActive: Boolean = false,
    val mountEngineStatus: MountEngineStatus = MountEngineStatus.STANDALONE_USERSPACE,
    val isRootAvailable: Boolean = false,
    val isolatedAppsCount: Int = 0,
    val totalAuditEvents: Int = 0,
    val totalEncryptedBytes: Long = 0L,
    val blockedAttemptsCount: Int = 0,
    val recentLogs: List<AccessAuditLog> = emptyList()
)

data class VaultSecuritySettings(
    val isVaultLocked: Boolean = false,
    val isPanicDecoyActive: Boolean = false,
    val isPinEnabled: Boolean = false,
    val isPanicPinEnabled: Boolean = false,
    val isBiometricEnabled: Boolean = true,
    val blockScreenshots: Boolean = true,
    val isDarkTheme: Boolean = true,
    val autoLockMinutes: Int = 1,
    val wrongPinAttempts: Int = 0,
    val cooldownUntilMillis: Long = 0L,
    val folderPins: Map<String, String> = emptyMap(),
    val unlockedFolderPaths: Set<String> = emptySet(),
    val favoritePaths: Set<String> = emptySet()
)

data class VaultTransferProgressState(
    val isActive: Boolean = false,
    val title: String = "",
    val currentItemName: String = "",
    val processedCount: Int = 0,
    val processedFoldersCount: Int = 0,
    val totalBytesProcessed: Long = 0L
)

class IsoVaultViewModel(application: Application) : AndroidViewModel(application) {

    private val database = IsoVaultDatabase.getDatabase(application)
    private val repository = IsoVaultRepository(database.isolationDao(), application)
    private val prefs = application.getSharedPreferences("isovault_security_prefs", Context.MODE_PRIVATE)

    val allConfigs: StateFlow<List<AppIsolationConfig>> = repository.allConfigs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentAuditLogs: StateFlow<List<AccessAuditLog>> = repository.recentAuditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allVaultFiles: StateFlow<List<VaultFileItem>> = repository.allVaultFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedAuditFilter = MutableStateFlow<String?>(null)
    val selectedAuditFilter: StateFlow<String?> = _selectedAuditFilter.asStateFlow()

    private val _decryptedPreviewText = MutableStateFlow<Pair<String, String>?>(null)
    val decryptedPreviewText: StateFlow<Pair<String, String>?> = _decryptedPreviewText.asStateFlow()

    private val _mountStatus = MutableStateFlow(MountEngineStatus.STANDALONE_USERSPACE)
    private val _isRootAvailable = MutableStateFlow(false)

    private val _vaultRefreshTick = MutableStateFlow(0)
    val vaultRefreshTick: StateFlow<Int> = _vaultRefreshTick.asStateFlow()

    private val _transferProgress = MutableStateFlow(VaultTransferProgressState())
    val transferProgress: StateFlow<VaultTransferProgressState> = _transferProgress.asStateFlow()

    // PBKDF2-HMAC-SHA256 (600,000 iterations + random 16-byte salt)
    private companion object {
        private const val PBKDF2_ITERATIONS = 600_000
        private const val PBKDF2_KEY_BITS = 256
    }

    private fun hashPinStrong(pin: String, saltBytes: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), saltBytes, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derived = skf.generateSecret(spec).encoded
        spec.clearPassword()
        val saltB64 = Base64.getEncoder().encodeToString(saltBytes)
        val hashB64 = Base64.getEncoder().encodeToString(derived)
        return "v2$$PBKDF2_ITERATIONS$$saltB64$$hashB64"
    }

    private fun verifyPinStrong(pin: String, storedRecord: String): Boolean {
        if (storedRecord.isBlank()) return false
        return try {
            if (storedRecord.startsWith("v2$")) {
                val parts = storedRecord.split("$")
                val iterations = parts[1].toInt()
                val salt = Base64.getDecoder().decode(parts[2])
                val expected = Base64.getDecoder().decode(parts[3])
                val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, PBKDF2_KEY_BITS)
                val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val actual = skf.generateSecret(spec).encoded
                spec.clearPassword()
                java.security.MessageDigest.isEqual(expected, actual)
            } else {
                // Legacy SHA-256 fallback (auto-upgraded on match)
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val legacyHex = digest.digest(pin.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                legacyHex == storedRecord
            }
        } catch (_: Exception) {
            false
        }
    }

    private var masterPinRecord: String = prefs.getString("master_pin_hash", "") ?: ""
    private var panicPinRecord: String = prefs.getString("panic_pin_hash", "") ?: ""

    private val _securitySettings = MutableStateFlow(
        VaultSecuritySettings(
            isVaultLocked = (prefs.getString("master_pin_hash", "") ?: "").isNotBlank(),
            isPanicDecoyActive = false,
            isPinEnabled = (prefs.getString("master_pin_hash", "") ?: "").isNotBlank(),
            isPanicPinEnabled = (prefs.getString("panic_pin_hash", "") ?: "").isNotBlank(),
            isBiometricEnabled = prefs.getBoolean("biometric_enabled", true),
            blockScreenshots = prefs.getBoolean("block_screenshots", true),
            isDarkTheme = prefs.getBoolean("dark_theme", true),
            autoLockMinutes = prefs.getInt("auto_lock_minutes", 1),
            folderPins = loadFolderPins(),
            favoritePaths = prefs.getStringSet("favorite_paths", emptySet()) ?: emptySet()
        )
    )
    val securitySettings: StateFlow<VaultSecuritySettings> = _securitySettings.asStateFlow()

    val dashboardState: StateFlow<DashboardUiState> = combine(
        repository.isolatedAppsCount,
        repository.totalAuditCount,
        repository.totalEncryptedBytes,
        repository.recentAuditLogs,
        combine(_mountStatus, _isRootAvailable) { m, r -> Pair(m, r) }
    ) { isolatedCount, auditCount, encryptedBytes, logs, mountPair ->
        val (mountState, hasRoot) = mountPair
        val realActive = MainActivity.isLsposedModuleActiveReal()
        val effectiveStatus = if (realActive) MountEngineStatus.ACTIVE_LSPOSED else mountState
        val blockedCount = logs.count { it.action == AuditAction.BLOCKED || it.action == AuditAction.SPOOFED_EMPTY }
        DashboardUiState(
            isLsposedActive = realActive,
            mountEngineStatus = effectiveStatus,
            isRootAvailable = hasRoot,
            isolatedAppsCount = isolatedCount,
            totalAuditEvents = auditCount,
            totalEncryptedBytes = encryptedBytes,
            blockedAttemptsCount = blockedCount,
            recentLogs = logs.take(15)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    init {
        viewModelScope.launch {
            repository.purgeLegacyDummySeedDataIfNeeded()
            refreshInstalledApps()
            ensureAppSandboxDefaultFolders()
            checkMountEngineState()
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            _installedApps.value = repository.getInstalledApps()
        }
    }

    fun getSandboxBaseDir(): File {
        return File(getApplication<Application>().filesDir, "vault_sandboxes").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getSharedSpaceDir(): File {
        return File(getApplication<Application>().filesDir, "vault_shared_space").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getRecycleBinDir(): File {
        return File(getApplication<Application>().filesDir, "vault_recycle_bin").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getDecoySandboxDir(): File {
        return File(getApplication<Application>().filesDir, "vault_decoy_empty").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Resolves or creates the 256-bit per-volume encryption key (wrapped by the Android Keystore master key).
     * Shared Space has its own dedicated volume key stored in preferences.
     */
    fun getOrCreateVolumeKey(packageName: String): SecretKey {
        if (packageName == "shared.vault") {
            var wrappedShared = prefs.getString("shared_space_wrapped_key", "") ?: ""
            if (wrappedShared.isBlank()) {
                wrappedShared = AesVaultCipher.generateWrappedVolumeKey()
                prefs.edit().putString("shared_space_wrapped_key", wrappedShared).apply()
            }
            return AesVaultCipher.unwrapVolumeKey(wrappedShared)
        }
        val config = allConfigs.value.firstOrNull { it.packageName == packageName }
        if (config != null && config.wrappedVolumeKey.isNotBlank()) {
            return AesVaultCipher.unwrapVolumeKey(config.wrappedVolumeKey)
        }
        return AesVaultCipher.getMasterKey()
    }

    private fun checkMountEngineState() {
        viewModelScope.launch(Dispatchers.IO) {
            val rootAvail = try {
                listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su").any { File(it).exists() }
            } catch (_: Exception) {
                false
            }
            _isRootAvailable.value = rootAvail
            _mountStatus.value = if (MainActivity.isLsposedModuleActiveReal()) {
                MountEngineStatus.ACTIVE_LSPOSED
            } else {
                MountEngineStatus.STANDALONE_USERSPACE
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setAuditFilter(packageName: String?) {
        _selectedAuditFilter.value = packageName
    }

    fun verifyPinAttempt(enteredPin: String): Boolean {
        val now = System.currentTimeMillis()
        val current = _securitySettings.value
        if (now < current.cooldownUntilMillis) return false

        return when {
            current.isPanicPinEnabled && verifyPinStrong(enteredPin, panicPinRecord) -> {
                _securitySettings.value = current.copy(
                    isVaultLocked = false,
                    isPanicDecoyActive = true,
                    wrongPinAttempts = 0,
                    cooldownUntilMillis = 0L
                )
                true
            }
            verifyPinStrong(enteredPin, masterPinRecord) || !current.isPinEnabled -> {
                if (current.isPinEnabled && !masterPinRecord.startsWith("v2$")) {
                    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                    masterPinRecord = hashPinStrong(enteredPin, salt)
                    prefs.edit().putString("master_pin_hash", masterPinRecord).apply()
                }
                _securitySettings.value = current.copy(
                    isVaultLocked = false,
                    isPanicDecoyActive = false,
                    wrongPinAttempts = 0,
                    cooldownUntilMillis = 0L
                )
                true
            }
            else -> {
                val attempts = current.wrongPinAttempts + 1
                val delayMs = if (attempts >= 3) 5_000L else 1_500L
                _securitySettings.value = current.copy(
                    wrongPinAttempts = attempts,
                    cooldownUntilMillis = now + delayMs
                )
                false
            }
        }
    }

    fun unlockWithBiometricSuccess() {
        _securitySettings.value = _securitySettings.value.copy(
            isVaultLocked = false,
            isPanicDecoyActive = false,
            wrongPinAttempts = 0,
            cooldownUntilMillis = 0L
        )
    }

    fun lockVaultNow() {
        wipeTemporaryPreviewCache()
        _securitySettings.value = _securitySettings.value.copy(
            isVaultLocked = true,
            isPanicDecoyActive = false,
            unlockedFolderPaths = emptySet()
        )
    }

    fun wipeTemporaryPreviewCache() {
        try {
            val cacheDir = File(getApplication<Application>().cacheDir, "vault_stream_preview")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
        } catch (_: Exception) {
        }
    }

    fun setMasterPin(newPin: String) {
        if (newPin.length == 4) {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            masterPinRecord = hashPinStrong(newPin, salt)
            prefs.edit().putString("master_pin_hash", masterPinRecord).apply()
            _securitySettings.value = _securitySettings.value.copy(isPinEnabled = true)
        }
    }

    fun disableMasterPin() {
        masterPinRecord = ""
        panicPinRecord = ""
        prefs.edit().remove("master_pin_hash").remove("panic_pin_hash").apply()
        _securitySettings.value = _securitySettings.value.copy(
            isPinEnabled = false,
            isPanicPinEnabled = false,
            isVaultLocked = false,
            isPanicDecoyActive = false
        )
    }

    fun setPanicDecoyPin(newPanicPin: String) {
        if (newPanicPin.length == 4) {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            panicPinRecord = hashPinStrong(newPanicPin, salt)
            prefs.edit().putString("panic_pin_hash", panicPinRecord).apply()
            _securitySettings.value = _securitySettings.value.copy(isPanicPinEnabled = true)
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("biometric_enabled", enabled).apply()
        _securitySettings.value = _securitySettings.value.copy(isBiometricEnabled = enabled)
    }

    fun setBlockScreenshots(enabled: Boolean) {
        prefs.edit().putBoolean("block_screenshots", enabled).apply()
        _securitySettings.value = _securitySettings.value.copy(blockScreenshots = enabled)
    }

    fun setDarkTheme(isDark: Boolean) {
        prefs.edit().putBoolean("dark_theme", isDark).apply()
        _securitySettings.value = _securitySettings.value.copy(isDarkTheme = isDark)
    }

    fun setAutoLockMinutes(minutes: Int) {
        prefs.edit().putInt("auto_lock_minutes", minutes).apply()
        _securitySettings.value = _securitySettings.value.copy(autoLockMinutes = minutes)
    }

    fun toggleFavorite(path: String) {
        val current = _securitySettings.value.favoritePaths.toMutableSet()
        if (!current.add(path)) {
            current.remove(path)
        }
        prefs.edit().putStringSet("favorite_paths", current).apply()
        _securitySettings.value = _securitySettings.value.copy(favoritePaths = current)
        _vaultRefreshTick.value += 1
    }

    private fun loadFolderPins(): Map<String, String> {
        val raw = prefs.getString("folder_pins_json", "{}") ?: "{}"
        return try {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.getString(k)
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun setFolderSecondaryPin(folderPath: String, pin: String?) {
        val updated = _securitySettings.value.folderPins.toMutableMap()
        if (pin.isNullOrBlank()) {
            updated.remove(folderPath)
        } else {
            updated[folderPath] = pin
        }
        val obj = JSONObject()
        updated.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString("folder_pins_json", obj.toString()).apply()
        _securitySettings.value = _securitySettings.value.copy(folderPins = updated)
        _vaultRefreshTick.value += 1
    }

    fun unlockFolderInSession(folderPath: String) {
        val unlocked = _securitySettings.value.unlockedFolderPaths + folderPath
        _securitySettings.value = _securitySettings.value.copy(unlockedFolderPaths = unlocked)
    }

    fun toggleAppIsolation(config: AppIsolationConfig, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleIsolation(config.packageName, enabled)
            ensureAppSandboxDefaultFolders()
        }
    }

    fun toggleAppSharedSpace(config: AppIsolationConfig, useShared: Boolean) {
        viewModelScope.launch {
            repository.toggleSharedSpace(config.packageName, useShared)
            ensureAppSandboxDefaultFolders()
            _vaultRefreshTick.value += 1
        }
    }

    fun setVolumeQuotaMb(packageName: String, quotaMb: Int) {
        viewModelScope.launch {
            repository.setVolumeQuota(packageName, quotaMb)
            _vaultRefreshTick.value += 1
        }
    }

    /**
     * Creates a new encrypted volume (either for an installed app or a custom named volume)
     * with its own 256-bit AES-GCM wrapped volume key and default media directories.
     */
    fun createVolume(
        volumeName: String,
        packageName: String,
        useSharedSpace: Boolean,
        quotaMb: Int
    ) {
        viewModelScope.launch {
            val existing = repository.getConfigForPackage(packageName)
            val wrappedKey = existing?.wrappedVolumeKey?.takeIf { it.isNotBlank() }
                ?: AesVaultCipher.generateWrappedVolumeKey()
            val config = AppIsolationConfig(
                packageName = packageName,
                appName = volumeName.trim().ifBlank { packageName },
                isIsolated = true,
                isolationMode = IsolationMode.ENCRYPTED_VAULT,
                encryptMediaOnly = false,
                blockRootPollution = true,
                whitelistedFolders = "Download,Documents",
                useSharedSpace = useSharedSpace,
                volumeQuotaMb = quotaMb,
                wrappedVolumeKey = wrappedKey,
                activeHooksMask = 0x1F
            )
            repository.saveConfig(config)
            ensureAppSandboxDefaultFolders()
            _vaultRefreshTick.value += 1
        }
    }

    fun deleteVolume(packageName: String, wipeDiskFiles: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            if (wipeDiskFiles) {
                if (packageName == "shared.vault") {
                    getSharedSpaceDir().deleteRecursively()
                    prefs.edit().remove("shared_space_wrapped_key").apply()
                } else {
                    File(getSandboxBaseDir(), packageName).deleteRecursively()
                }
            }
            if (packageName != "shared.vault") {
                repository.deleteVolumeConfig(packageName)
            }
            ensureAppSandboxDefaultFolders()
            _vaultRefreshTick.value += 1
        }
    }

    fun addInstalledAppToVault(app: InstalledAppInfo, useSharedSpace: Boolean = false) {
        createVolume(
            volumeName = app.appName,
            packageName = app.packageName,
            useSharedSpace = useSharedSpace,
            quotaMb = 0
        )
    }

    fun updateAppConfig(config: AppIsolationConfig) {
        viewModelScope.launch {
            repository.saveConfig(config)
            ensureAppSandboxDefaultFolders()
        }
    }

    fun clearAllAuditLogs() {
        viewModelScope.launch {
            repository.clearAuditLogs()
        }
    }

    fun notifyVaultFilesChanged() {
        viewModelScope.launch {
            repository.purgeLegacyDummySeedDataIfNeeded()
            _vaultRefreshTick.value += 1
        }
    }

    /**
     * Imports one or more URIs (from the system file picker, Photo Picker, or Share to Vault)
     * and encrypts them with the target volume's 256-bit key using 4KB chunked streaming encryption.
     */
    fun importUrisToVault(
        uris: List<Uri>,
        destinationDir: File? = null,
        packageName: String = "shared.vault",
        appName: String = "Shared Space",
        deleteOriginalAfterImport: Boolean = false,
        onFinished: (Int) -> Unit = {}
    ) {
        if (uris.isEmpty()) {
            onFinished(0)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val resolver = app.contentResolver
            val targetDir = destinationDir ?: File(getSharedSpaceDir(), "Pictures").apply {
                if (!exists()) mkdirs()
            }
            if (!targetDir.exists()) targetDir.mkdirs()

            val volumeKey = getOrCreateVolumeKey(packageName)
            var importedCount = 0
            var totalBytes = 0L

            _transferProgress.value = VaultTransferProgressState(
                isActive = true,
                title = "Encrypting files into $appName",
                currentItemName = "Preparing...",
                processedCount = 0,
                totalBytesProcessed = 0L
            )

            for (uri in uris) {
                try {
                    val rawName = resolveDisplayName(uri) ?: "imported_${System.currentTimeMillis()}.bin"
                    val safeName = rawName.replace(Regex("[^a-zA-Z0-9._\\- ()]"), "_")
                    _transferProgress.value = _transferProgress.value.copy(
                        currentItemName = safeName
                    )

                    val destFile = resolveUniqueEncryptedFile(targetDir, safeName)
                    var fileEncryptedOk = false
                    resolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { out ->
                            AesVaultCipher.encryptStream(input, out, volumeKey)
                        }
                        fileEncryptedOk = destFile.exists() && destFile.length() > 0L
                    }

                    if (fileEncryptedOk) {
                        val size = destFile.length()
                        importedCount++
                        totalBytes += size
                        _transferProgress.value = _transferProgress.value.copy(
                            processedCount = importedCount,
                            totalBytesProcessed = totalBytes
                        )
                        if (deleteOriginalAfterImport) {
                            try {
                                DocumentsContract.deleteDocument(resolver, uri)
                            } catch (_: Exception) {
                            }
                        }
                        repository.recordAudit(
                            packageName = packageName,
                            appName = appName,
                            originalPath = uri.toString(),
                            redirectedPath = destFile.absolutePath,
                            action = AuditAction.ENCRYPTED,
                            operationType = "IMPORT_ENCRYPT"
                        )
                    }
                } catch (_: Exception) {
                }
            }

            _transferProgress.value = VaultTransferProgressState(isActive = false)
            repository.purgeLegacyDummySeedDataIfNeeded()
            _vaultRefreshTick.value += 1
            withContext(Dispatchers.Main) {
                onFinished(importedCount)
            }
        }
    }

    /**
     * Recursively imports an entire directory tree chosen via ActivityResultContracts.OpenDocumentTree(),
     * preserving the folder and all nested subfolder hierarchies inside the encrypted volume and
     * stream-encrypting every file with AES256_GCM_HKDF_4KB.
     */
    fun importFolderTreeToVault(
        treeUri: Uri,
        destinationDir: File? = null,
        packageName: String = "shared.vault",
        appName: String = "Shared Space",
        deleteOriginalAfterImport: Boolean = false,
        onFinished: (filesCount: Int, foldersCount: Int, bytesEncrypted: Long) -> Unit = { _, _, _ -> }
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val resolver = app.contentResolver
            val baseTargetDir = destinationDir ?: getSharedSpaceDir()
            if (!baseTargetDir.exists()) baseTargetDir.mkdirs()

            val volumeKey = getOrCreateVolumeKey(packageName)
            var importedFiles = 0
            var importedFolders = 0
            var totalEncryptedBytes = 0L

            try {
                val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
                val rootFolderNameRaw = queryDocumentName(rootDocUri)
                    ?: rootDocId.substringAfterLast(':').substringAfterLast('/').ifBlank { "Imported_Folder" }
                val safeRootName = sanitizeFileName(rootFolderNameRaw).ifBlank { "Imported_Folder" }
                val rootTargetDir = File(baseTargetDir, safeRootName).apply { mkdirs() }
                File(rootTargetDir, ".nomedia").apply { if (!exists()) createNewFile() }
                importedFolders++

                _transferProgress.value = VaultTransferProgressState(
                    isActive = true,
                    title = "Importing folder \"$safeRootName\"",
                    currentItemName = safeRootName,
                    processedCount = 0,
                    processedFoldersCount = 1,
                    totalBytesProcessed = 0L
                )

                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )

                fun walkDocumentFolder(parentDocId: String, currentVaultDir: File) {
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
                    val childEntries = mutableListOf<Triple<String, String, String>>()
                    resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                        val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        while (cursor.moveToNext()) {
                            val childId = if (idIdx >= 0) cursor.getString(idIdx) ?: continue else continue
                            val childName = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "item" else "item"
                            val childMime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "" else ""
                            childEntries.add(Triple(childId, childName, childMime))
                        }
                    }

                    for ((childId, childName, childMime) in childEntries) {
                        if (childName == ".nomedia" || childName.startsWith(".trashed")) continue
                        val cleanChildName = sanitizeFileName(childName).ifBlank { "item_${System.currentTimeMillis()}" }

                        if (childMime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            val subDir = File(currentVaultDir, cleanChildName).apply { mkdirs() }
                            File(subDir, ".nomedia").apply { if (!exists()) createNewFile() }
                            importedFolders++
                            _transferProgress.value = _transferProgress.value.copy(
                                currentItemName = "Folder: $cleanChildName",
                                processedFoldersCount = importedFolders
                            )
                            walkDocumentFolder(childId, subDir)
                            if (deleteOriginalAfterImport) {
                                try {
                                    val dirDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                                    DocumentsContract.deleteDocument(resolver, dirDocUri)
                                } catch (_: Exception) {
                                }
                            }
                        } else {
                            val fileDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            _transferProgress.value = _transferProgress.value.copy(
                                currentItemName = cleanChildName
                            )
                            val destFile = resolveUniqueEncryptedFile(currentVaultDir, cleanChildName)
                            var encryptedOk = false
                            try {
                                resolver.openInputStream(fileDocUri)?.use { input ->
                                    destFile.outputStream().use { out ->
                                        AesVaultCipher.encryptStream(input, out, volumeKey)
                                    }
                                    encryptedOk = destFile.exists() && destFile.length() > 0L
                                }
                            } catch (_: Exception) {
                                destFile.delete()
                            }

                            if (encryptedOk) {
                                val bytes = destFile.length()
                                importedFiles++
                                totalEncryptedBytes += bytes
                                _transferProgress.value = _transferProgress.value.copy(
                                    processedCount = importedFiles,
                                    totalBytesProcessed = totalEncryptedBytes
                                )
                                if (deleteOriginalAfterImport) {
                                    try {
                                        DocumentsContract.deleteDocument(resolver, fileDocUri)
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        }
                    }
                }

                walkDocumentFolder(rootDocId, rootTargetDir)

                if (importedFiles > 0 || importedFolders > 0) {
                    repository.recordAudit(
                        packageName = packageName,
                        appName = appName,
                        originalPath = "Folder: $safeRootName ($importedFiles files, $importedFolders folders)",
                        redirectedPath = rootTargetDir.absolutePath,
                        action = AuditAction.ENCRYPTED,
                        operationType = "FOLDER_IMPORT_ENCRYPT"
                    )
                }
            } catch (_: Exception) {
            } finally {
                _transferProgress.value = VaultTransferProgressState(isActive = false)
                repository.purgeLegacyDummySeedDataIfNeeded()
                _vaultRefreshTick.value += 1
                withContext(Dispatchers.Main) {
                    onFinished(importedFiles, importedFolders, totalEncryptedBytes)
                }
            }
        }
    }

    /**
     * Exports selected VaultEntry items (files and entire recursive folder trees)
     * into a user-selected device folder URI from ActivityResultContracts.OpenDocumentTree().
     */
    fun exportEntriesToDeviceFolderTree(
        entries: List<VaultEntry>,
        treeUri: Uri,
        onFinished: (exportedFiles: Int, exportedFolders: Int, exportedBytes: Long) -> Unit = { _, _, _ -> }
    ) {
        if (entries.isEmpty()) {
            onFinished(0, 0, 0L)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val resolver = app.contentResolver
            var exportedFiles = 0
            var exportedFolders = 0
            var exportedBytes = 0L

            try {
                val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val rootParentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)

                _transferProgress.value = VaultTransferProgressState(
                    isActive = true,
                    title = "Exporting decrypted files to folder",
                    currentItemName = "Preparing...",
                    processedCount = 0,
                    processedFoldersCount = 0,
                    totalBytesProcessed = 0L
                )

                fun exportSingleVaultFile(srcFile: File, cleanName: String, isEncrypted: Boolean, sourcePackage: String, parentDocUri: Uri) {
                    _transferProgress.value = _transferProgress.value.copy(currentItemName = cleanName)
                    val ext = cleanName.substringAfterLast('.', "").lowercase()
                    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
                    val createdDocUri = DocumentsContract.createDocument(resolver, parentDocUri, mimeType, cleanName) ?: return
                    val volumeKey = getOrCreateVolumeKey(sourcePackage)

                    resolver.openOutputStream(createdDocUri)?.use { out ->
                        if (isEncrypted || srcFile.name.endsWith(".isovault", ignoreCase = true) || AesVaultCipher.isEncryptedIsoVaultFile(srcFile)) {
                            AesVaultCipher.newDecryptingInputStream(srcFile, volumeKey).use { plainIn ->
                                val copied = plainIn.copyTo(out)
                                exportedBytes += copied
                            }
                        } else {
                            srcFile.inputStream().use { rawIn ->
                                val copied = rawIn.copyTo(out)
                                exportedBytes += copied
                            }
                        }
                    }
                    exportedFiles++
                    _transferProgress.value = _transferProgress.value.copy(
                        processedCount = exportedFiles,
                        totalBytesProcessed = exportedBytes
                    )
                }

                fun exportDirectoryRecursive(dirFile: File, sourcePackage: String, parentDocUri: Uri) {
                    val dirCleanName = dirFile.name.removeSuffix(".isovault")
                    val createdFolderUri = DocumentsContract.createDocument(
                        resolver,
                        parentDocUri,
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        dirCleanName
                    ) ?: parentDocUri
                    exportedFolders++
                    _transferProgress.value = _transferProgress.value.copy(
                        currentItemName = "Folder: $dirCleanName",
                        processedFoldersCount = exportedFolders
                    )

                    val children = dirFile.listFiles() ?: emptyArray()
                    for (child in children) {
                        if (child.name == ".nomedia" || child.name.startsWith(".seeded")) continue
                        if (child.isDirectory) {
                            exportDirectoryRecursive(child, sourcePackage, createdFolderUri)
                        } else {
                            val cleanChild = child.name.removeSuffix(".isovault")
                            val isEnc = child.name.endsWith(".isovault", ignoreCase = true) || AesVaultCipher.isEncryptedIsoVaultFile(child)
                            exportSingleVaultFile(child, cleanChild, isEnc, sourcePackage, createdFolderUri)
                        }
                    }
                }

                for (entry in entries) {
                    if (entry.isDirectory) {
                        exportDirectoryRecursive(entry.file, entry.sourcePackage, rootParentDocUri)
                    } else {
                        exportSingleVaultFile(entry.file, entry.cleanName, entry.isEncrypted, entry.sourcePackage, rootParentDocUri)
                    }
                }
            } catch (_: Exception) {
            } finally {
                _transferProgress.value = VaultTransferProgressState(isActive = false)
                withContext(Dispatchers.Main) {
                    onFinished(exportedFiles, exportedFolders, exportedBytes)
                }
            }
        }
    }

    private fun resolveUniqueEncryptedFile(targetDir: File, cleanFileName: String): File {
        val baseEncName = if (cleanFileName.endsWith(".isovault", ignoreCase = true)) {
            cleanFileName
        } else {
            "$cleanFileName.isovault"
        }
        var candidate = File(targetDir, baseEncName)
        if (!candidate.exists()) return candidate

        val cleanWithoutIso = baseEncName.removeSuffix(".isovault")
        val dotIndex = cleanWithoutIso.lastIndexOf('.')
        val stem = if (dotIndex > 0) cleanWithoutIso.substring(0, dotIndex) else cleanWithoutIso
        val ext = if (dotIndex > 0) cleanWithoutIso.substring(dotIndex) else ""
        var counter = 1
        while (candidate.exists()) {
            candidate = File(targetDir, "${stem}_($counter)${ext}.isovault")
            counter++
        }
        return candidate
    }

    private fun sanitizeFileName(raw: String): String {
        return raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }

    private fun queryDocumentName(docUri: Uri): String? {
        return try {
            val resolver = getApplication<Application>().contentResolver
            resolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        val resolver = getApplication<Application>().contentResolver
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            } ?: uri.lastPathSegment?.substringAfterLast('/')
        } catch (_: Exception) {
            uri.lastPathSegment?.substringAfterLast('/')
        }
    }

    /**
     * Runs a live production self-test verifying Android Keystore key wrapping,
     * streaming AES256_GCM_HKDF_4KB cipher integrity, config sync, and LSPosed hook state.
     */
    fun runDiagnosticsSelfTest(onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val report = try {
                val wrapped = AesVaultCipher.generateWrappedVolumeKey()
                val volumeKey = AesVaultCipher.unwrapVolumeKey(wrapped)
                val payload = ByteArray(12_288).also { SecureRandom().nextBytes(it) } // 3 chunks (4KB each)
                val encrypted = AesVaultCipher.encrypt(payload, volumeKey)
                val decrypted = AesVaultCipher.decrypt(encrypted, volumeKey)
                val cryptoOk = payload.contentEquals(decrypted)

                repository.syncInMemoryRules()
                val hooksActive = MainActivity.isLsposedModuleActiveReal()
                val ruleCount = allConfigs.value.count { it.isIsolated }

                if (cryptoOk) {
                    val hookMsg = if (hooksActive) {
                        "LSPosed Hook Layer: ACTIVE"
                    } else {
                        "LSPosed Hook Layer: Standby (Enable in LSPosed Manager to hook external apps)"
                    }
                    "Self-Test Passed: Keystore Master Key + 4KB Streaming AES-256-GCM verified • $ruleCount isolated volume(s) synced • $hookMsg"
                } else {
                    "Self-Test Error: Cipher round-trip verification failed"
                }
            } catch (e: Exception) {
                "Self-Test Error: ${e.message}"
            }
            withContext(Dispatchers.Main) {
                onResult(report)
            }
        }
    }

    /**
     * Ensures each isolated volume and Shared Space has the standard directory structure
     * (`DCIM/Camera`, `Pictures`, `Download`, `Documents`, `Movies`, `Music`) with `.nomedia`
     * markers—without creating any fake or dummy files.
     */
    fun ensureAppSandboxDefaultFolders() {
        viewModelScope.launch(Dispatchers.IO) {
            val baseDir = getSandboxBaseDir()
            val sharedDir = getSharedSpaceDir()
            val standardFolders = listOf("DCIM/Camera", "Pictures", "Download", "Documents", "Movies", "Music")

            // Shared space standard folders
            File(sharedDir, ".nomedia").apply { if (!exists()) createNewFile() }
            standardFolders.forEach { rel ->
                val folder = File(sharedDir, rel).apply { if (!exists()) mkdirs() }
                File(folder, ".nomedia").apply { if (!exists()) createNewFile() }
            }

            // Per-app volume standard folders
            val configs = repository.allConfigs.first()
            configs.forEach { cfg ->
                val appDir = File(baseDir, cfg.packageName).apply { if (!exists()) mkdirs() }
                File(appDir, ".nomedia").apply { if (!exists()) createNewFile() }
                standardFolders.forEach { rel ->
                    val folder = File(appDir, rel).apply { if (!exists()) mkdirs() }
                    File(folder, ".nomedia").apply { if (!exists()) createNewFile() }
                }
            }
            _vaultRefreshTick.value += 1
        }
    }

    /**
     * Exports an AES-256-GCM encrypted backup bundle (.isovault.bak) containing all configurations
     * and encrypted volume metadata, returning the backup file path.
     */
    fun createEncryptedBackup(onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val configs = repository.allConfigs.first()
                val arr = JSONArray()
                configs.forEach { cfg ->
                    val obj = JSONObject().apply {
                        put("packageName", cfg.packageName)
                        put("appName", cfg.appName)
                        put("isIsolated", cfg.isIsolated)
                        put("isolationMode", cfg.isolationMode.name)
                        put("useSharedSpace", cfg.useSharedSpace)
                        put("volumeQuotaMb", cfg.volumeQuotaMb)
                        put("wrappedVolumeKey", cfg.wrappedVolumeKey)
                    }
                    arr.put(obj)
                }
                val rootJson = JSONObject().apply {
                    put("version", 2)
                    put("exportedAt", System.currentTimeMillis())
                    put("configs", arr)
                }
                val encryptedBytes = AesVaultCipher.encrypt(rootJson.toString().toByteArray(Charsets.UTF_8))
                val backupDir = File(getApplication<Application>().getExternalFilesDir(null) ?: getApplication<Application>().filesDir, "Backups").apply {
                    if (!exists()) mkdirs()
                }
                val backupFile = File(backupDir, "isovault_backup_${System.currentTimeMillis()}.isovault.bak")
                backupFile.writeBytes(encryptedBytes)
                withContext(Dispatchers.Main) {
                    onResult("Encrypted backup saved: ${backupFile.name}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult("Backup failed: ${e.message}")
                }
            }
        }
    }

    fun restoreLatestEncryptedBackup(onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val backupDir = File(getApplication<Application>().getExternalFilesDir(null) ?: getApplication<Application>().filesDir, "Backups")
                val latest = backupDir.listFiles()
                    ?.filter { it.name.endsWith(".isovault.bak") }
                    ?.maxByOrNull { it.lastModified() }

                if (latest == null) {
                    withContext(Dispatchers.Main) {
                        onResult("No encrypted backup (.isovault.bak) found in Backups folder.")
                    }
                    return@launch
                }

                val decrypted = AesVaultCipher.decryptToMemory(latest, AesVaultCipher.getMasterKey())
                val rootJson = JSONObject(String(decrypted, Charsets.UTF_8))
                val arr = rootJson.getJSONArray("configs")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val cfg = AppIsolationConfig(
                        packageName = obj.getString("packageName"),
                        appName = obj.getString("appName"),
                        isIsolated = obj.getBoolean("isIsolated"),
                        isolationMode = IsolationMode.valueOf(obj.optString("isolationMode", IsolationMode.ENCRYPTED_VAULT.name)),
                        useSharedSpace = obj.optBoolean("useSharedSpace", false),
                        volumeQuotaMb = obj.optInt("volumeQuotaMb", 0),
                        wrappedVolumeKey = obj.optString("wrappedVolumeKey", "")
                    )
                    repository.saveConfig(cfg)
                }
                ensureAppSandboxDefaultFolders()
                withContext(Dispatchers.Main) {
                    onResult("Restored ${arr.length()} volume configurations from ${latest.name}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult("Restore failed: ${e.message}")
                }
            }
        }
    }

    fun previewDecryptedFile(item: VaultFileItem) {
        viewModelScope.launch {
            val content = repository.decryptVaultFilePreview(item)
            _decryptedPreviewText.value = Pair(item.relativePath.substringAfterLast('/'), content)
        }
    }

    fun dismissPreview() {
        _decryptedPreviewText.value = null
    }

    fun deleteVaultFile(item: VaultFileItem) {
        viewModelScope.launch {
            repository.deleteVaultFile(item)
        }
    }
}
