package com.example.isolation.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.pdf.PdfDocument
import android.net.Uri
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.crypto.SecretKey

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
    private val prefs = app.getSharedPreferences("isovault_ui_prefs", Context.MODE_PRIVATE)

    val configs: StateFlow<List<AppIsolationConfig>> = dao.getAllConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentAuditLogs: StateFlow<List<AccessAuditLog>> = dao.getRecentAuditLogs(150)
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

    private val _selinuxStatus = MutableStateFlow("Enforcing")
    val selinuxStatus = _selinuxStatus.asStateFlow()

    private val _isLsposedActive = MutableStateFlow(
        com.example.MainActivity.isLsposedModuleActiveReal() ||
                IsolationRuleManager.isSimulatedHookActive
    )
    val isLsposedActive = _isLsposedActive.asStateFlow()

    private val _isRootGranted = MutableStateFlow(false)
    val isRootGranted = _isRootGranted.asStateFlow()

    private val _autoForceStopOnRuleChange = MutableStateFlow(true)
    val autoForceStopOnRuleChange = _autoForceStopOnRuleChange.asStateFlow()

    private val _autoRootBindMount = MutableStateFlow(false)
    val autoRootBindMount = _autoRootBindMount.asStateFlow()

    private val _explorerTargetDir = MutableStateFlow<String?>(null)
    val explorerTargetDir = _explorerTargetDir.asStateFlow()

    private val _selectedVaultAppPackage = MutableStateFlow<String?>(null)
    val selectedVaultAppPackage = _selectedVaultAppPackage.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    // First-Run Onboarding State
    private val _showOnboardingCard = MutableStateFlow(!prefs.getBoolean("onboarding_dismissed_v2", false))
    val showOnboardingCard = _showOnboardingCard.asStateFlow()

    // Shared Space Volume Quota (MB, 0 = unlimited)
    private val _sharedVolumeQuotaMb = MutableStateFlow(prefs.getInt("shared_volume_quota_mb", 2048))
    val sharedVolumeQuotaMb = _sharedVolumeQuotaMb.asStateFlow()

    // Theme & Security Preferences
    private val _isDarkTheme = MutableStateFlow(prefs.getBoolean("dark_theme", true))
    val isDarkTheme = _isDarkTheme.asStateFlow()

    private val _isPanicModeActive = MutableStateFlow(false)
    val isPanicModeActive = _isPanicModeActive.asStateFlow()

    private val _primaryPin = MutableStateFlow(prefs.getString("primary_pin", "1234") ?: "1234")
    val primaryPin = _primaryPin.asStateFlow()

    private val _panicPin = MutableStateFlow(prefs.getString("panic_pin", "9999") ?: "9999")
    val panicPin = _panicPin.asStateFlow()

    private val _decoyPinEnabled = MutableStateFlow(prefs.getBoolean("decoy_pin_enabled", true))
    val decoyPinEnabled = _decoyPinEnabled.asStateFlow()

    private val _fingerprintEnabled = MutableStateFlow(prefs.getBoolean("fingerprint_enabled", true))
    val fingerprintEnabled = _fingerprintEnabled.asStateFlow()

    private val _autoLockMinutes = MutableStateFlow(prefs.getInt("auto_lock_minutes", 5))
    val autoLockMinutes = _autoLockMinutes.asStateFlow()

    private val _blockScreenshots = MutableStateFlow(prefs.getBoolean("block_screenshots_v3", false))
    val blockScreenshots = _blockScreenshots.asStateFlow()

    // Secondary PIN locks per folder path
    private val _folderPins = MutableStateFlow<Map<String, String>>(loadFolderPins())
    val folderPins = _folderPins.asStateFlow()

    // Gallery Favorites (set of file paths)
    private val _favoritePaths = MutableStateFlow<Set<String>>(loadFavoritePaths())
    val favoritePaths = _favoritePaths.asStateFlow()

    private val _unlockedFoldersInSession = MutableStateFlow<Set<String>>(emptySet())
    val unlockedFoldersInSession = _unlockedFoldersInSession.asStateFlow()

    private val _vaultRefreshTick = MutableStateFlow(0)
    val vaultRefreshTick = _vaultRefreshTick.asStateFlow()

    private fun loadFolderPins(): Map<String, String> {
        val raw = prefs.getString("folder_pins_map", "") ?: ""
        if (raw.isBlank()) return emptyMap()
        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
    }

    private fun saveFolderPins(map: Map<String, String>) {
        val serialized = map.entries.joinToString(";") { "${it.key}|${it.value}" }
        prefs.edit().putString("folder_pins_map", serialized).apply()
    }

    private fun loadFavoritePaths(): Set<String> {
        return prefs.getStringSet("gallery_favorites", emptySet()) ?: emptySet()
    }

    fun toggleFavorite(path: String) {
        val current = _favoritePaths.value
        val updated = if (current.contains(path)) current - path else current + path
        _favoritePaths.value = updated
        prefs.edit().putStringSet("gallery_favorites", updated).apply()
    }

    fun dismissOnboarding() {
        _showOnboardingCard.value = false
        prefs.edit().putBoolean("onboarding_dismissed_v2", true).apply()
    }

    fun reopenOnboarding() {
        _showOnboardingCard.value = true
        prefs.edit().putBoolean("onboarding_dismissed_v2", false).apply()
    }

    fun setSharedVolumeQuotaMb(quotaMb: Int) {
        _sharedVolumeQuotaMb.value = quotaMb.coerceAtLeast(0)
        prefs.edit().putInt("shared_volume_quota_mb", _sharedVolumeQuotaMb.value).apply()
        _statusMessage.value = if (quotaMb > 0) {
            "Shared Space quota set to $quotaMb MB"
        } else {
            "Shared Space quota set to Unlimited"
        }
    }

    /**
     * Obtains or creates the independent 256-bit Data Encryption Key (DEK) for a given volume
     * (either a per-app volume or the `shared.vault` volume), wrapped by the hardware Master Key.
     */
    fun getOrCreateVolumeKey(packageName: String): SecretKey {
        AesVaultCipher.initializeMasterKey(app)
        if (packageName == IsolationRuleManager.SHARED_VOLUME_ID) {
            val existingSharedWrapped = prefs.getString("shared_volume_wrapped_dek", null)
            if (!existingSharedWrapped.isNullOrBlank()) {
                try {
                    return AesVaultCipher.unwrapVolumeKey(existingSharedWrapped)
                } catch (_: Exception) {}
            }
            val newSharedKey = AesVaultCipher.generateVolumeKey()
            val wrapped = AesVaultCipher.wrapVolumeKey(newSharedKey)
            prefs.edit().putString("shared_volume_wrapped_dek", wrapped).apply()
            return newSharedKey
        }

        val cfg = configs.value.find { it.packageName == packageName }
        if (cfg != null && cfg.wrappedVolumeKey.isNotBlank()) {
            try {
                return AesVaultCipher.unwrapVolumeKey(cfg.wrappedVolumeKey)
            } catch (_: Exception) {}
        }

        val perAppPrefKey = "vol_dek_$packageName"
        val existingWrapped = prefs.getString(perAppPrefKey, null)
        if (!existingWrapped.isNullOrBlank()) {
            try {
                return AesVaultCipher.unwrapVolumeKey(existingWrapped)
            } catch (_: Exception) {}
        }

        val generated = AesVaultCipher.generateVolumeKey()
        val wrapped = AesVaultCipher.wrapVolumeKey(generated)
        prefs.edit().putString(perAppPrefKey, wrapped).apply()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dao.setWrappedVolumeKey(packageName, wrapped)
            } catch (_: Exception) {}
        }
        return generated
    }

    fun setFolderSecondaryPin(folderPath: String, pin: String?) {
        val updated = _folderPins.value.toMutableMap()
        if (pin.isNullOrBlank()) {
            updated.remove(folderPath)
            _statusMessage.value = "Removed secondary folder lock"
        } else {
            updated[folderPath] = pin
            _statusMessage.value = "Folder locked with secondary PIN"
        }
        _folderPins.value = updated
        saveFolderPins(updated)
    }

    fun unlockFolderInSession(folderPath: String) {
        _unlockedFoldersInSession.value = _unlockedFoldersInSession.value + folderPath
    }

    fun lockFolderInSession(folderPath: String) {
        _unlockedFoldersInSession.value = _unlockedFoldersInSession.value - folderPath
    }

    fun onVaultLocked() {
        _unlockedFoldersInSession.value = emptySet()
        viewModelScope.launch(Dispatchers.IO) {
            AesVaultCipher.wipeEphemeralCache(app)
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        _isDarkTheme.value = enabled
        prefs.edit().putBoolean("dark_theme", enabled).apply()
    }

    fun setFingerprintEnabled(enabled: Boolean) {
        _fingerprintEnabled.value = enabled
        prefs.edit().putBoolean("fingerprint_enabled", enabled).apply()
    }

    fun setDecoyPinEnabled(enabled: Boolean) {
        _decoyPinEnabled.value = enabled
        prefs.edit().putBoolean("decoy_pin_enabled", enabled).apply()
    }

    fun setAutoLockMinutes(minutes: Int) {
        _autoLockMinutes.value = minutes
        prefs.edit().putInt("auto_lock_minutes", minutes).apply()
    }

    fun setBlockScreenshots(enabled: Boolean) {
        _blockScreenshots.value = enabled
        prefs.edit().putBoolean("block_screenshots_v3", enabled).apply()
    }

    fun triggerPanicMode() {
        _isPanicModeActive.value = true
        _unlockedFoldersInSession.value = emptySet()
        _statusMessage.value = "Unlocked"
    }

    fun exitPanicMode() {
        _isPanicModeActive.value = false
        _statusMessage.value = "Primary vault restored"
    }

    fun updatePins(primary: String, panic: String) {
        if (primary.length == 4) {
            _primaryPin.value = primary
            prefs.edit().putString("primary_pin", primary).apply()
        }
        if (panic.length == 4) {
            _panicPin.value = panic
            prefs.edit().putString("panic_pin", panic).apply()
        }
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

    fun selectVaultAppPackage(packageName: String?) {
        _selectedVaultAppPackage.value = packageName
    }

    fun notifyVaultFilesChanged() {
        _vaultRefreshTick.value += 1
    }

    fun requestRootAccess() {
        viewModelScope.launch(Dispatchers.IO) {
            val granted = MountManager.checkAndRequestRoot(getVaultRootDirectory())
            _isRootGranted.value = granted
            _selinuxStatus.value = MountManager.getSelinuxStatus()
            _statusMessage.value = if (granted) {
                "Root namespace helper active"
            } else {
                "Root unavailable (inactive); LSPosed user-space hooks active"
            }
        }
    }

    fun forceStopPackage(packageName: String, appName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = MountManager.forceStopPackageViaRoot(packageName)
            _statusMessage.value = if (res.isSuccess) {
                "Restarted $appName with isolated volume"
            } else {
                "Rules saved for $appName (applies on next launch)"
            }
        }
    }

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
        seedInitialEncryptedVaultFilesIfNeeded()
    }

    /**
     * Resolves the writable root directory for encrypted volumes using app-scoped storage
     * (never requires MANAGE_EXTERNAL_STORAGE).
     */
    fun getVaultRootDirectory(): File {
        val base = app.getExternalFilesDir(null) ?: app.filesDir
        val root = File(base, "IsoVault")
        if (!root.exists()) root.mkdirs()
        return root
    }

    /**
     * Returns the directory for a per-app volume or the `shared.vault` Shared Space volume,
     * automatically creating the standard default folders (DCIM, Pictures, Download, Movies, Music, Documents).
     */
    fun getAppSandboxDirectory(packageName: String): File {
        val root = getVaultRootDirectory()
        val dir = File(root, "sandboxes/$packageName")
        if (!dir.exists()) {
            IsolationRuleManager.ensureStandardVolumeFolders(dir)
        }
        return dir
    }

    fun getSharedVolumeDirectory(): File {
        return getAppSandboxDirectory(IsolationRuleManager.SHARED_VOLUME_ID)
    }

    fun getRecycleBinDirectory(): File {
        val dir = File(getVaultRootDirectory(), ".recycle_bin")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun seedInitialEncryptedVaultFilesIfNeeded() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AesVaultCipher.initializeMasterKey(app)
                val root = getVaultRootDirectory()
                val marker = File(root, ".seeded_v3")
                if (marker.exists()) return@launch

                val snapDir = getAppSandboxDirectory("com.snapchat.android")
                val whatsDir = getAppSandboxDirectory("com.whatsapp")
                val teleDir = getAppSandboxDirectory("org.telegram.messenger")
                val sharedDir = getSharedVolumeDirectory()

                val snapKey = getOrCreateVolumeKey("com.snapchat.android")
                val whatsKey = getOrCreateVolumeKey("com.whatsapp")
                val teleKey = getOrCreateVolumeKey("org.telegram.messenger")
                val sharedKey = getOrCreateVolumeKey(IsolationRuleManager.SHARED_VOLUME_ID)

                // 1. Generate encrypted sample images in per-app & Shared Space volumes
                val bmp1 = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
                val canvas1 = Canvas(bmp1)
                val paint1 = Paint().apply {
                    shader = LinearGradient(0f, 0f, 640f, 480f, 0xFF0EA5E9.toInt(), 0xFF0F172A.toInt(), Shader.TileMode.CLAMP)
                }
                canvas1.drawRect(0f, 0f, 640f, 480f, paint1)
                val imgBytes1 = ByteArrayOutputStream().also { bmp1.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
                File(snapDir, "Pictures/IMG_2025_Snap_01.jpg.isovault").apply {
                    parentFile?.mkdirs()
                    writeBytes(AesVaultCipher.encrypt(imgBytes1, snapKey))
                }

                val bmp2 = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
                val canvas2 = Canvas(bmp2)
                val paint2 = Paint().apply {
                    shader = LinearGradient(0f, 0f, 640f, 480f, 0xFF10B981.toInt(), 0xFF064E3B.toInt(), Shader.TileMode.CLAMP)
                }
                canvas2.drawRect(0f, 0f, 640f, 480f, paint2)
                val imgBytes2 = ByteArrayOutputStream().also { bmp2.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
                File(whatsDir, "Pictures/WA_Photo_Private.jpg.isovault").apply {
                    parentFile?.mkdirs()
                    writeBytes(AesVaultCipher.encrypt(imgBytes2, whatsKey))
                }

                // Shared Space sample photo (visible to apps that opt into "Use shared space")
                val bmpShared = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
                val canvasShared = Canvas(bmpShared)
                val paintShared = Paint().apply {
                    shader = LinearGradient(0f, 0f, 640f, 480f, 0xFF14B8A6.toInt(), 0xFF1E293B.toInt(), Shader.TileMode.CLAMP)
                }
                canvasShared.drawRect(0f, 0f, 640f, 480f, paintShared)
                val imgBytesShared = ByteArrayOutputStream().also { bmpShared.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
                File(sharedDir, "Pictures/Shared_Album_Cover.jpg.isovault").apply {
                    parentFile?.mkdirs()
                    writeBytes(AesVaultCipher.encrypt(imgBytesShared, sharedKey))
                }

                // 2. Generate a real PDF document encrypted with the volume key
                val pdfDoc = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = pdfDoc.startPage(pageInfo)
                val pdfPaint = Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 18f
                    isAntiAlias = true
                }
                page.canvas.drawColor(android.graphics.Color.WHITE)
                page.canvas.drawText("IsoVault Encrypted Volume Document", 50f, 80f, pdfPaint)
                pdfPaint.textSize = 13f
                page.canvas.drawText("Encrypted with AES256_GCM_HKDF_4KB (Streaming AEAD).", 50f, 120f, pdfPaint)
                page.canvas.drawText("Each volume uses its own 256-bit DEK wrapped by Android Keystore.", 50f, 145f, pdfPaint)
                pdfDoc.finishPage(page)
                val pdfBytes = ByteArrayOutputStream().also { pdfDoc.writeTo(it) }.toByteArray()
                pdfDoc.close()
                File(whatsDir, "Documents/Statement_Encrypted.pdf.isovault").apply {
                    parentFile?.mkdirs()
                    writeBytes(AesVaultCipher.encrypt(pdfBytes, whatsKey))
                }

                // 3. Generate an encrypted text note
                val noteBytes = """
                    Vault Private Volume & Security Notes
                    -------------------------------------
                    • Cipher: AES256_GCM_HKDF_4KB (Streaming 4KB chunks)
                    • Master Key: Random 256-bit key wrapped by Android Keystore
                    • Volume Key: Independent 256-bit DEK per volume
                    • Shared Space: Linked only to apps where "Use shared space" is enabled
                """.trimIndent().toByteArray(Charsets.UTF_8)
                File(teleDir, "Documents/Private_Notes.txt.isovault").apply {
                    parentFile?.mkdirs()
                    writeBytes(AesVaultCipher.encrypt(noteBytes, teleKey))
                }

                // Seed initial readable activity logs using .first() (never blocking .stateIn(viewModelScope))
                if (dao.getRecentAuditLogs(5).first().isEmpty()) {
                    val now = System.currentTimeMillis()
                    dao.insertAuditLog(
                        AccessAuditLog(
                            packageName = "com.snapchat.android",
                            appName = "Snapchat",
                            requestedPath = "/storage/emulated/0/DCIM/Camera/IMG_2025_Snap_01.jpg",
                            targetResolvedPath = "Volume(com.snapchat.android)/Pictures",
                            action = AuditAction.ENCRYPTED,
                            operation = "SAVED_3_FILES",
                            timestamp = now - 120_000L,
                            details = "Snapchat saved 3 files"
                        )
                    )
                    dao.insertAuditLog(
                        AccessAuditLog(
                            packageName = "com.whatsapp",
                            appName = "WhatsApp",
                            requestedPath = "/storage/emulated/0/WhatsApp/Media/WA_Photo_Private.jpg",
                            targetResolvedPath = "Volume(com.whatsapp)/Pictures",
                            action = AuditAction.ENCRYPTED,
                            operation = "SAVED_2_FILES",
                            timestamp = now - 900_000L,
                            details = "WhatsApp saved 2 files (Shared Space linked)"
                        )
                    )
                    dao.insertAuditLog(
                        AccessAuditLog(
                            packageName = "org.telegram.messenger",
                            appName = "Telegram",
                            requestedPath = "/storage/emulated/0/Download/Private_Notes.txt",
                            targetResolvedPath = "Volume(org.telegram.messenger)/Documents",
                            action = AuditAction.REDIRECTED,
                            operation = "SAVED_1_FILE",
                            timestamp = now - 3_600_000L,
                            details = "Telegram saved 1 file"
                        )
                    )
                }

                marker.createNewFile()
                _vaultRefreshTick.value += 1
            } catch (_: Throwable) {}
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(newFilter: AppListFilter) {
        _filter.value = newFilter
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = pkgRepo.getInstalledApps(configs.value, includeSystemApps = true)
            _rawInstalledApps.value = apps
        }
    }

    /**
     * Non-blocking startup diagnostics (never invokes `su` automatically on launch).
     */
    fun refreshDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            MountManager.ensureVaultDirectories(getVaultRootDirectory())
            _systemMounts.value = MountManager.readSystemMounts()
            _selinuxStatus.value = MountManager.getSelinuxStatus()
            val isReal = com.example.MainActivity.isLsposedModuleActiveReal()
            _isLsposedActive.value = if (isReal) true else IsolationRuleManager.isSimulatedHookActive
            purgeExpiredRecycleBinItems()
        }
    }

    fun setSimulatedHookPreview(enabled: Boolean) {
        IsolationRuleManager.isSimulatedHookActive = enabled
        _isLsposedActive.value = com.example.MainActivity.isLsposedModuleActiveReal() || enabled
    }

    /**
     * Automatically deletes items in Recycle Bin older than 30 days.
     */
    fun purgeExpiredRecycleBinItems() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val binDir = getRecycleBinDirectory()
                val thirtyDaysMs = 30L * 24L * 60L * 60L * 1000L
                val now = System.currentTimeMillis()
                binDir.listFiles()?.forEach { file ->
                    if (now - file.lastModified() > thirtyDaysMs) {
                        file.deleteRecursively()
                    }
                }
            } catch (_: Exception) {}
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

    /**
     * Toggles whether a protected app is linked to the Shared Space volume ("Use shared space").
     */
    fun toggleAppSharedSpace(packageName: String, appName: String, useShared: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getConfigForPackage(packageName) ?: return@launch
            val updated = existing.copy(useSharedSpace = useShared)
            dao.insertOrUpdateConfig(updated)
            getSharedVolumeDirectory()

            if (updated.isIsolated) {
                IsolationRuleManager.updateRule(
                    packageName = updated.packageName,
                    mode = updated.isolationMode,
                    blockRootPollution = updated.blockRootPollution,
                    whitelistedFolders = updated.whitelistedFolders.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet(),
                    useSharedSpace = useShared,
                    volumeQuotaMb = updated.volumeQuotaMb
                )
            }
            notifyConfigChanged()
            _vaultRefreshTick.value += 1
            _statusMessage.value = if (useShared) {
                "$appName linked to Shared Space volume"
            } else {
                "$appName isolated to private volume only"
            }
        }
    }

    /**
     * Creates or configures a Per-App or Shared encrypted volume with its own wrapped 256-bit DEK
     * and automatic standard folders (DCIM, Pictures, Download, Movies, Music, Documents).
     */
    fun createOrUpdateVolume(
        appName: String,
        packageName: String,
        isSharedVolume: Boolean,
        quotaMb: Int,
        useSharedSpace: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isSharedVolume || packageName == IsolationRuleManager.SHARED_VOLUME_ID) {
                val sharedDir = getSharedVolumeDirectory()
                IsolationRuleManager.ensureStandardVolumeFolders(sharedDir)
                getOrCreateVolumeKey(IsolationRuleManager.SHARED_VOLUME_ID)
                setSharedVolumeQuotaMb(quotaMb)
                _vaultRefreshTick.value += 1
                _statusMessage.value = "Shared Space volume ready (DCIM, Pictures, Download, Movies, Music, Documents)"
                return@launch
            }

            val existing = dao.getConfigForPackage(packageName)
            val wrappedKey = existing?.wrappedVolumeKey?.takeIf { it.isNotBlank() }
                ?: AesVaultCipher.wrapVolumeKey(AesVaultCipher.generateVolumeKey())

            val updated = (existing ?: AppIsolationConfig(
                packageName = packageName,
                appName = appName
            )).copy(
                appName = appName,
                isIsolated = true,
                isolationMode = IsolationMode.ENCRYPTED_VAULT,
                useSharedSpace = useSharedSpace,
                volumeQuotaMb = quotaMb.coerceAtLeast(0),
                wrappedVolumeKey = wrappedKey,
                activeHooksMask = 0x1F
            )
            dao.insertOrUpdateConfig(updated)
            val volDir = getAppSandboxDirectory(packageName)
            IsolationRuleManager.ensureStandardVolumeFolders(volDir)

            IsolationRuleManager.updateRule(
                packageName = updated.packageName,
                mode = updated.isolationMode,
                blockRootPollution = updated.blockRootPollution,
                whitelistedFolders = updated.whitelistedFolders.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet(),
                useSharedSpace = updated.useSharedSpace,
                volumeQuotaMb = updated.volumeQuotaMb
            )
            notifyConfigChanged()
            loadInstalledApps()
            _vaultRefreshTick.value += 1
            _statusMessage.value = "Created encrypted volume for $appName"
        }
    }

    /**
     * Deletes a single volume and rotates/wipes its independent 256-bit DEK without affecting other volumes.
     */
    fun deleteVolume(packageName: String, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = getAppSandboxDirectory(packageName)
            dir.deleteRecursively()
            IsolationRuleManager.ensureStandardVolumeFolders(dir)

            if (packageName == IsolationRuleManager.SHARED_VOLUME_ID) {
                // Rotate Shared Space key so old ciphertexts are cryptographically unrecoverable
                val newKey = AesVaultCipher.generateVolumeKey()
                prefs.edit().putString("shared_volume_wrapped_dek", AesVaultCipher.wrapVolumeKey(newKey)).apply()
            } else {
                val newWrapped = AesVaultCipher.wrapVolumeKey(AesVaultCipher.generateVolumeKey())
                prefs.edit().putString("vol_dek_$packageName", newWrapped).apply()
                dao.setWrappedVolumeKey(packageName, newWrapped)
            }
            _vaultRefreshTick.value += 1
            _statusMessage.value = "Wiped volume & rotated key for $displayName"
        }
    }

    /**
     * Adds or toggles an app's protection immediately without asking any path/folder questions.
     */
    fun toggleAppIsolation(appInfo: InstalledAppInfo, enable: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getConfigForPackage(appInfo.packageName)
            val wrappedKey = existing?.wrappedVolumeKey?.takeIf { it.isNotBlank() }
                ?: AesVaultCipher.wrapVolumeKey(AesVaultCipher.generateVolumeKey())

            val updated = existing?.copy(
                isIsolated = enable,
                wrappedVolumeKey = wrappedKey
            ) ?: AppIsolationConfig(
                packageName = appInfo.packageName,
                appName = appInfo.appName,
                isIsolated = enable,
                isolationMode = IsolationMode.ENCRYPTED_VAULT,
                totalBytesEncrypted = 0L,
                useSharedSpace = false,
                volumeQuotaMb = 0,
                wrappedVolumeKey = wrappedKey,
                activeHooksMask = 0x1F
            )
            dao.insertOrUpdateConfig(updated)

            // Ensure standard default folders exist inside the volume immediately
            getAppSandboxDirectory(updated.packageName)

            if (enable) {
                IsolationRuleManager.updateRule(
                    packageName = updated.packageName,
                    mode = updated.isolationMode,
                    blockRootPollution = updated.blockRootPollution,
                    whitelistedFolders = updated.whitelistedFolders.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet(),
                    useSharedSpace = updated.useSharedSpace,
                    volumeQuotaMb = updated.volumeQuotaMb
                )
                if (_autoRootBindMount.value && _isRootGranted.value) {
                    MountManager.prepareVolumeAndOptionalRootMount(
                        getVaultRootDirectory(),
                        updated.packageName,
                        updated.isolationMode
                    )
                }
                if (_isRootGranted.value && _autoForceStopOnRuleChange.value) {
                    MountManager.forceStopPackageViaRoot(updated.packageName)
                }
                dao.insertAuditLog(
                    AccessAuditLog(
                        packageName = updated.packageName,
                        appName = updated.appName,
                        requestedPath = "/storage/emulated/0",
                        targetResolvedPath = "Volume(${updated.packageName})",
                        action = AuditAction.ENCRYPTED,
                        operation = "PROTECTION_ENABLED",
                        details = "${updated.appName} protection turned on"
                    )
                )
                _statusMessage.value = "${appInfo.appName} is now protected"
            } else {
                IsolationRuleManager.removeRule(updated.packageName)
                if (_isRootGranted.value && _autoForceStopOnRuleChange.value) {
                    MountManager.forceStopPackageViaRoot(updated.packageName)
                }
                dao.insertAuditLog(
                    AccessAuditLog(
                        packageName = updated.packageName,
                        appName = updated.appName,
                        requestedPath = "/storage/emulated/0",
                        targetResolvedPath = "/storage/emulated/0",
                        action = AuditAction.PERMITTED,
                        operation = "PROTECTION_PAUSED",
                        details = "${updated.appName} protection paused"
                    )
                )
                _statusMessage.value = "${appInfo.appName} protection paused"
            }

            notifyConfigChanged()
            loadInstalledApps()
            _vaultRefreshTick.value += 1
        }
    }

    /**
     * Handles incoming files from Android's "Share to Vault" intent or multi-file picker,
     * streaming-encrypting them directly into the chosen volume with that volume's 256-bit DEK.
     */
    fun importUrisToVault(
        uris: List<Uri>,
        targetDir: File,
        sourceAppName: String = "Shared Space",
        sourcePackage: String = IsolationRuleManager.SHARED_VOLUME_ID
    ) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            targetDir.mkdirs()
            val volumeKey = getOrCreateVolumeKey(sourcePackage)
            val cfg = dao.getConfigForPackage(sourcePackage)
            val quotaMb = if (sourcePackage == IsolationRuleManager.SHARED_VOLUME_ID) {
                _sharedVolumeQuotaMb.value
            } else {
                cfg?.volumeQuotaMb ?: 0
            }

            val volumeRoot = getAppSandboxDirectory(sourcePackage)
            var currentVolumeBytes = volumeRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }

            var importedCount = 0
            var totalBytesAdded = 0L
            var quotaExceeded = false

            for ((index, uri) in uris.withIndex()) {
                try {
                    val resolver = app.contentResolver
                    val mime = resolver.getType(uri) ?: ""
                    val originalName = queryDisplayName(uri) ?: run {
                        val ext = when {
                            mime.contains("png") -> "png"
                            mime.contains("jpg") || mime.contains("jpeg") -> "jpg"
                            mime.contains("webp") -> "webp"
                            mime.contains("mp4") || mime.contains("video") -> "mp4"
                            mime.contains("mp3") || mime.contains("audio") -> "mp3"
                            mime.contains("pdf") -> "pdf"
                            mime.contains("text") -> "txt"
                            else -> "bin"
                        }
                        "Vault_${System.currentTimeMillis()}_$index.$ext"
                    }

                    val finalFileName = if (originalName.endsWith(".isovault", ignoreCase = true)) {
                        originalName
                    } else {
                        "$originalName.isovault"
                    }

                    val outFile = File(targetDir, finalFileName)
                    resolver.openInputStream(uri)?.use { inputStream ->
                        outFile.outputStream().use { fos ->
                            AesVaultCipher.encryptStream(inputStream, fos, volumeKey)
                        }
                    } ?: continue

                    val writtenBytes = outFile.length()
                    if (quotaMb > 0 && currentVolumeBytes + writtenBytes > quotaMb * 1024L * 1024L) {
                        outFile.delete()
                        quotaExceeded = true
                        continue
                    }

                    currentVolumeBytes += writtenBytes
                    importedCount++
                    totalBytesAdded += writtenBytes
                } catch (_: Exception) {}
            }

            if (importedCount > 0) {
                dao.addEncryptedBytes(sourcePackage, totalBytesAdded)
                dao.insertAuditLog(
                    AccessAuditLog(
                        packageName = sourcePackage,
                        appName = sourceAppName,
                        requestedPath = targetDir.name,
                        targetResolvedPath = targetDir.absolutePath,
                        action = AuditAction.ENCRYPTED,
                        operation = "IMPORTED_FILES",
                        details = "$sourceAppName saved $importedCount ${if (importedCount == 1) "file" else "files"}"
                    )
                )
            }

            withContext(Dispatchers.Main) {
                _vaultRefreshTick.value += 1
                _statusMessage.value = when {
                    quotaExceeded && importedCount == 0 -> "Volume quota ($quotaMb MB) reached"
                    importedCount > 0 -> "Encrypted & saved $importedCount ${if (importedCount == 1) "file" else "files"} to $sourceAppName"
                    else -> "Could not import selected files"
                }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun recordVaultActivity(appName: String, packageName: String, summary: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertAuditLog(
                AccessAuditLog(
                    packageName = packageName,
                    appName = appName,
                    requestedPath = summary,
                    targetResolvedPath = "/.IsoVault",
                    action = AuditAction.ENCRYPTED,
                    operation = "VAULT_OP",
                    details = summary
                )
            )
        }
    }

    fun clearAuditLogs() {
        viewModelScope.launch {
            dao.clearAuditLogs()
            _statusMessage.value = "Activity log cleared"
        }
    }
}
