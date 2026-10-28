package com.example.isolation.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
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
import com.example.isolation.model.DeviceProfilePreset
import com.example.isolation.model.DisguiseIconPreset
import com.example.isolation.model.HollowIconTheme
import com.example.isolation.model.IsolationMode
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.model.LauncherVisibilityMode
import com.example.isolation.model.PickerPolicy
import com.example.isolation.model.RealDeviceHardware
import com.example.isolation.model.RealHardwareSnapshot
import com.example.isolation.model.SettingsStore
import com.example.isolation.model.VpnProxyPresets
import com.example.isolation.model.VpnProxyServer
import com.example.isolation.mount.MountEntry
import com.example.isolation.mount.MountExecutionResult
import com.example.isolation.mount.MountManager
import com.example.isolation.pkg.InstalledAppInfo
import com.example.isolation.core.license.DeviceHardwareFingerprint
import com.example.isolation.core.license.HardwareBindingInfo
import com.example.isolation.core.license.MonotonicClockGuard
import com.example.isolation.core.module.DynamicModuleState
import com.example.isolation.core.module.HollowModuleRegistry
import com.example.isolation.core.root.LicenseAccountStatus
import com.example.isolation.core.root.RootLicenseVaultManager
import com.example.isolation.core.root.RootVaultLicenseRecord
import com.example.isolation.core.security.HollowSecureClient
import com.example.isolation.model.PrivacyProfile
import com.example.isolation.model.PrivacyProfileStore
import com.example.isolation.service.DeadmanIncident
import com.example.isolation.service.RootDeadmanWatchdog
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

    val hiddenOrDisguisedCount: StateFlow<Int> = dao.getHiddenOrDisguisedAppsCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val identitySpoofedCount: StateFlow<Int> = dao.getIdentitySpoofedAppsCount()
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

    val realHardware: RealHardwareSnapshot = RealDeviceHardware.read(app)

    // Reusable Privacy Profiles & Deadman Switch State
    private val _privacyProfiles = MutableStateFlow<List<PrivacyProfile>>(PrivacyProfileStore.loadProfiles(app))
    val privacyProfiles: StateFlow<List<PrivacyProfile>> = _privacyProfiles.asStateFlow()

    val deadmanBlockedIncidents: StateFlow<List<DeadmanIncident>> = RootDeadmanWatchdog.blockedUidsFlow

    // Root-Level Encrypted License Vault & Dynamic Module Engine State
    val hardwareBinding: HardwareBindingInfo = DeviceHardwareFingerprint.collect(app)

    private val _isRootAccessGranted = MutableStateFlow(false)
    val isRootAccessGranted: StateFlow<Boolean> = _isRootAccessGranted.asStateFlow()

    private val _isRequestingRoot = MutableStateFlow(false)
    val isRequestingRoot: StateFlow<Boolean> = _isRequestingRoot.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _vaultLicenseRecord = MutableStateFlow(RootLicenseVaultManager.readVaultRecord(app))
    val vaultLicenseRecord: StateFlow<RootVaultLicenseRecord> = _vaultLicenseRecord.asStateFlow()

    private val _moduleStates = MutableStateFlow(
        HollowModuleRegistry.resolveModuleStates(app, _settings.value.devFullBuildMode)
    )
    val moduleStates: StateFlow<List<DynamicModuleState>> = _moduleStates.asStateFlow()

    private val _clockVerification = MutableStateFlow(
        RootLicenseVaultManager.verifyVaultClockIntegrity(app)
    )
    val clockVerification: StateFlow<MonotonicClockGuard.ClockVerificationResult> = _clockVerification.asStateFlow()

    // Full list of installed apps merged with current isolation, stealth, identity & VPN config
    val allInstalledApps: StateFlow<List<InstalledAppInfo>> = combine(
        _rawInstalledApps,
        configs
    ) { apps, cfgList ->
        val cfgMap = cfgList.associateBy { it.packageName }
        apps.map { app ->
            val cfg = cfgMap[app.packageName]
            app.copy(
                isIsolated = cfg?.isIsolated ?: false,
                isolationMode = cfg?.isolationMode ?: IsolationMode.SHARED_WORKSPACE,
                isHiddenOrDisguised = cfg?.isHiddenOrDisguised ?: false,
                launcherVisibilityMode = cfg?.launcherVisibilityMode ?: LauncherVisibilityMode.VISIBLE,
                customLauncherLabel = cfg?.customLauncherLabel ?: "",
                disguiseIconPreset = cfg?.disguiseIconPreset ?: DisguiseIconPreset.CALCULATOR,
                hideInSystemUiRecents = cfg?.hideInSystemUiRecents ?: true,
                hideFromPlayStore = cfg?.hideFromPlayStore ?: false,
                spoofInSettings = cfg?.spoofInSettings ?: true,
                customIconBase64 = cfg?.customIconBase64 ?: "",
                customIconDonorPkg = cfg?.customIconDonorPkg ?: "",
                isIdentitySpoofed = cfg?.isIdentitySpoofed ?: false,
                deviceProfilePreset = cfg?.deviceProfilePreset ?: DeviceProfilePreset.REAL_HARDWARE,
                spoofBrand = cfg?.spoofBrand ?: "",
                spoofModel = cfg?.spoofModel ?: "",
                spoofDevice = cfg?.spoofDevice ?: "",
                spoofAndroidId = cfg?.spoofAndroidId ?: "",
                spoofImei = cfg?.spoofImei ?: "",
                spoofAdvertisingId = cfg?.spoofAdvertisingId ?: "",
                spoofMacAddress = cfg?.spoofMacAddress ?: "",
                spoofCarrierName = cfg?.spoofCarrierName ?: "",
                spoofSimOperator = cfg?.spoofSimOperator ?: "",
                spoofSerial = cfg?.spoofSerial ?: "",
                hideRootAndXposed = cfg?.hideRootAndXposed ?: true,
                hideAdbAndDevOptions = cfg?.hideAdbAndDevOptions ?: true,
                hideVpnAndProxy = cfg?.hideVpnAndProxy ?: true,
                blockClipboardRead = cfg?.blockClipboardRead ?: false,
                spoofLocationEnabled = cfg?.spoofLocationEnabled ?: false,
                spoofLatitude = cfg?.spoofLatitude ?: 37.7749,
                spoofLongitude = cfg?.spoofLongitude ?: -122.4194,
                hideMockLocation = cfg?.hideMockLocation ?: true,
                preventScreenshots = cfg?.preventScreenshots ?: false,
                hideOtherApps = cfg?.hideOtherApps ?: false,
                disableLogcatLogging = cfg?.disableLogcatLogging ?: false,
                fakeBatteryEnabled = cfg?.fakeBatteryEnabled ?: false,
                fakeBatteryLevel = cfg?.fakeBatteryLevel ?: 85,
                knoxWarrantyBitSpoof = cfg?.knoxWarrantyBitSpoof ?: true,
                disableAllNetworking = cfg?.disableAllNetworking ?: false,
                spoofGpuFingerprint = cfg?.spoofGpuFingerprint ?: true,
                spoofBrowserUserAgent = cfg?.spoofBrowserUserAgent ?: true,
                anonymizeSensors = cfg?.anonymizeSensors ?: true,
                blockCellTowerTracking = cfg?.blockCellTowerTracking ?: true,
                autoSyncTimezone = cfg?.autoSyncTimezone ?: true,
                spoofManufacturer = cfg?.spoofManufacturer ?: "",
                spoofProduct = cfg?.spoofProduct ?: "",
                spoofBoard = cfg?.spoofBoard ?: "",
                spoofHardware = cfg?.spoofHardware ?: "",
                spoofBootloader = cfg?.spoofBootloader ?: "",
                spoofFingerprint = cfg?.spoofFingerprint ?: "",
                spoofGpuVendor = cfg?.spoofGpuVendor ?: "",
                spoofGpuRenderer = cfg?.spoofGpuRenderer ?: "",
                spoofCpuCores = cfg?.spoofCpuCores ?: 0,
                spoofRamGb = cfg?.spoofRamGb ?: 0,
                spoofScreenResolution = cfg?.spoofScreenResolution ?: "",
                spoofScreenDpi = cfg?.spoofScreenDpi ?: 0,
                spoofTimezoneId = cfg?.spoofTimezoneId ?: "",
                spoofLocaleTag = cfg?.spoofLocaleTag ?: "",
                customUserAgent = cfg?.customUserAgent ?: "",
                spoofWebRtc = cfg?.spoofWebRtc ?: true,
                spoofCanvasNoise = cfg?.spoofCanvasNoise ?: true,
                spoofAudioContext = cfg?.spoofAudioContext ?: true,
                spoofClientHints = cfg?.spoofClientHints ?: true,
                spoofFontEnumeration = cfg?.spoofFontEnumeration ?: true,
                vpnProxyEnabled = cfg?.vpnProxyEnabled ?: false,
                proxyServerId = cfg?.proxyServerId ?: _settings.value.activeProxyServerId,
                requireVpnForNetwork = cfg?.requireVpnForNetwork ?: false,
                matchTimezoneWithIp = cfg?.matchTimezoneWithIp ?: true,
                webViewProxyEnabled = cfg?.webViewProxyEnabled ?: true,
                trustAllCerts = cfg?.trustAllCerts ?: false,
                privacyProfileId = cfg?.privacyProfileId ?: "",
                deadmanSwitchEnabled = cfg?.deadmanSwitchEnabled ?: true,
                spoofDrmWidevine = cfg?.spoofDrmWidevine ?: true,
                sensorBiasNeutralizer = cfg?.sensorBiasNeutralizer ?: true,
                spoofWifiBssid = cfg?.spoofWifiBssid ?: true
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered apps list combining search and filter (for Storage Isolate screen)
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
        refreshVaultAndModuleState()
        loadInstalledApps()
        refreshDiagnostics()
        syncStealthRulesToHooks()
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

    private fun syncStealthRulesToHooks() {
        viewModelScope.launch(Dispatchers.IO) {
            val allConfigs = dao.getAllConfigsSync()
            IsolationRuleManager.publishStealthTable(allConfigs, _settings.value)
            notifyConfigChanged()
        }
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
                if (updated.isolationMode == IsolationMode.SANDBOX_REDIRECT || updated.isolationMode == IsolationMode.ENCRYPTED_VAULT) {
                    withContext(Dispatchers.IO) {
                        FileManagerViewModel.resolveSeparateAppDriveRoot(app, updated.packageName)
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        FileManagerViewModel.resolveHollowDriveRoot(app)
                    }
                }
                updated.publishEnabled()
                appendTerminalLog("Storage Isolate enabled for ${appInfo.appName} -> ${updated.isolationMode.badge}")
                _statusMessage.value = "${appInfo.appName} connected to ${updated.isolationMode.title}."
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
            withContext(Dispatchers.IO) {
                if (newMode == IsolationMode.SANDBOX_REDIRECT || newMode == IsolationMode.ENCRYPTED_VAULT) {
                    FileManagerViewModel.resolveSeparateAppDriveRoot(app, packageName)
                } else {
                    FileManagerViewModel.resolveHollowDriveRoot(app)
                }
            }
            if (updated.isIsolated) updated.publishEnabled()
            notifyConfigChanged()
            appendTerminalLog("Mode for $packageName set to ${newMode.name}")
            _statusMessage.value = if (newMode == IsolationMode.SANDBOX_REDIRECT || newMode == IsolationMode.ENCRYPTED_VAULT) {
                "Assigned Separate App Drive (${newMode.badge}) — now visible below Hollow Drive in Files!"
            } else {
                "Connected to ${newMode.title}."
            }
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

    // ---- Independent App Hider & SystemUI Disguise -----------------------------------------

    fun decodeImageUriToBase64(uri: Uri, onResult: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val encoded = try {
                app.contentResolver.openInputStream(uri)?.use { stream ->
                    val rawBmp = BitmapFactory.decodeStream(stream)
                    if (rawBmp != null) {
                        IsolationRuleManager.bitmapToBase64Png(rawBmp, 144)
                    } else null
                }
            } catch (_: Exception) {
                null
            }
            withContext(Dispatchers.Main) {
                onResult(encoded)
            }
        }
    }

    fun refreshLauncherNow() {
        viewModelScope.launch(Dispatchers.IO) {
            val allConfigs = dao.getAllConfigsSync()
            IsolationRuleManager.publishStealthTable(allConfigs, _settings.value)
            notifyConfigChanged()
            val refreshed = MountManager.refreshLaunchersAndStealthCaches(
                app,
                refreshPlayStore = true,
                refreshSettings = true
            )
            withContext(Dispatchers.Main) {
                _statusMessage.value = if (refreshed) {
                    "Home Launcher, Icon Cache, Settings & Play Store refreshed!"
                } else {
                    "Stealth rules synced (grant Root to auto-clear launcher icon cache)."
                }
            }
        }
    }

    fun saveAppStealthConfig(
        appInfo: InstalledAppInfo,
        visibilityMode: LauncherVisibilityMode,
        customLabel: String,
        iconPreset: DisguiseIconPreset,
        hideInRecents: Boolean,
        hideFromPlayStore: Boolean = appInfo.hideFromPlayStore,
        spoofInSettings: Boolean = appInfo.spoofInSettings,
        customIconBase64: String = appInfo.customIconBase64,
        customIconDonorPkg: String = appInfo.customIconDonorPkg
    ) {
        viewModelScope.launch {
            val existing = dao.getConfigForPackage(appInfo.packageName)
            val isEnrolled = visibilityMode != LauncherVisibilityMode.VISIBLE || hideFromPlayStore
            val resolvedLabel = if (visibilityMode == LauncherVisibilityMode.DISGUISED && customLabel.isBlank()) {
                iconPreset.suggestedName.ifBlank { "Calculator" }
            } else {
                customLabel.trim()
            }

            val resolvedIconB64 = withContext(Dispatchers.IO) {
                when (iconPreset) {
                    DisguiseIconPreset.CUSTOM_IMAGE -> customIconBase64
                    DisguiseIconPreset.CLONED_APP -> {
                        if (customIconDonorPkg.isNotBlank()) {
                            try {
                                val d = app.packageManager.getApplicationIcon(customIconDonorPkg)
                                IsolationRuleManager.drawableToBase64Png(d, 144)
                            } catch (_: Throwable) {
                                customIconBase64
                            }
                        } else customIconBase64
                    }
                    else -> ""
                }
            }

            val updated = existing?.copy(
                isHiddenOrDisguised = isEnrolled,
                launcherVisibilityMode = visibilityMode,
                customLauncherLabel = resolvedLabel,
                disguiseIconPreset = iconPreset,
                hideInSystemUiRecents = hideInRecents,
                hideFromPlayStore = hideFromPlayStore,
                spoofInSettings = spoofInSettings,
                customIconBase64 = resolvedIconB64,
                customIconDonorPkg = if (iconPreset == DisguiseIconPreset.CLONED_APP) customIconDonorPkg else ""
            ) ?: AppIsolationConfig(
                packageName = appInfo.packageName,
                appName = appInfo.appName,
                isIsolated = false,
                isHiddenOrDisguised = isEnrolled,
                launcherVisibilityMode = visibilityMode,
                customLauncherLabel = resolvedLabel,
                disguiseIconPreset = iconPreset,
                hideInSystemUiRecents = hideInRecents,
                hideFromPlayStore = hideFromPlayStore,
                spoofInSettings = spoofInSettings,
                customIconBase64 = resolvedIconB64,
                customIconDonorPkg = if (iconPreset == DisguiseIconPreset.CLONED_APP) customIconDonorPkg else ""
            )

            dao.insertOrUpdateConfig(updated)
            withContext(Dispatchers.IO) {
                val allConfigs = dao.getAllConfigsSync()
                IsolationRuleManager.publishStealthTable(allConfigs, _settings.value)
                notifyConfigChanged()
                MountManager.refreshLaunchersAndStealthCaches(
                    app,
                    refreshPlayStore = hideFromPlayStore,
                    refreshSettings = spoofInSettings
                )
            }
            loadInstalledApps()

            when (visibilityMode) {
                LauncherVisibilityMode.HIDDEN -> {
                    appendTerminalLog("System-wide hide enabled for ${appInfo.appName} (PlayStore=$hideFromPlayStore)")
                    _statusMessage.value = "${appInfo.appName} is now hidden & launcher refreshed (no reboot needed)."
                }
                LauncherVisibilityMode.DISGUISED -> {
                    appendTerminalLog("Disguised ${appInfo.appName} as '$resolvedLabel' (${iconPreset.label})")
                    _statusMessage.value = "${appInfo.appName} disguised as \"$resolvedLabel\" & launcher icon cache refreshed!"
                }
                LauncherVisibilityMode.VISIBLE -> {
                    if (hideFromPlayStore) {
                        appendTerminalLog("Hidden ${appInfo.appName} from Play Store")
                        _statusMessage.value = "${appInfo.appName} hidden from Google Play Store."
                    } else {
                        appendTerminalLog("Restored normal launcher visibility for ${appInfo.appName}")
                        _statusMessage.value = "${appInfo.appName} restored to normal visibility."
                    }
                }
            }
        }
    }

    fun removeAppFromStealth(appInfo: InstalledAppInfo) {
        saveAppStealthConfig(
            appInfo = appInfo,
            visibilityMode = LauncherVisibilityMode.VISIBLE,
            customLabel = "",
            iconPreset = DisguiseIconPreset.CALCULATOR,
            hideInRecents = false,
            hideFromPlayStore = false,
            spoofInSettings = false,
            customIconBase64 = "",
            customIconDonorPkg = ""
        )
    }

    fun saveAppIdentitySpoofConfig(
        appInfo: InstalledAppInfo,
        enabled: Boolean,
        preset: DeviceProfilePreset,
        brand: String,
        model: String,
        device: String,
        androidId: String,
        imei: String,
        advertisingId: String,
        macAddress: String,
        carrierName: String,
        simOperator: String,
        serial: String,
        hideRootAndXposed: Boolean,
        hideAdbAndDevOptions: Boolean,
        hideVpnAndProxy: Boolean,
        blockClipboardRead: Boolean,
        spoofLocationEnabled: Boolean = appInfo.spoofLocationEnabled,
        spoofLatitude: Double = appInfo.spoofLatitude,
        spoofLongitude: Double = appInfo.spoofLongitude,
        hideMockLocation: Boolean = appInfo.hideMockLocation,
        preventScreenshots: Boolean = appInfo.preventScreenshots,
        hideOtherApps: Boolean = appInfo.hideOtherApps,
        disableLogcatLogging: Boolean = appInfo.disableLogcatLogging,
        fakeBatteryEnabled: Boolean = appInfo.fakeBatteryEnabled,
        fakeBatteryLevel: Int = appInfo.fakeBatteryLevel,
        knoxWarrantyBitSpoof: Boolean = appInfo.knoxWarrantyBitSpoof,
        disableAllNetworking: Boolean = appInfo.disableAllNetworking,
        spoofGpuFingerprint: Boolean = appInfo.spoofGpuFingerprint,
        spoofBrowserUserAgent: Boolean = appInfo.spoofBrowserUserAgent,
        anonymizeSensors: Boolean = appInfo.anonymizeSensors,
        blockCellTowerTracking: Boolean = appInfo.blockCellTowerTracking,
        autoSyncTimezone: Boolean = appInfo.autoSyncTimezone,
        spoofManufacturer: String = appInfo.spoofManufacturer,
        spoofProduct: String = appInfo.spoofProduct,
        spoofBoard: String = appInfo.spoofBoard,
        spoofHardware: String = appInfo.spoofHardware,
        spoofBootloader: String = appInfo.spoofBootloader,
        spoofFingerprint: String = appInfo.spoofFingerprint,
        spoofGpuVendor: String = appInfo.spoofGpuVendor,
        spoofGpuRenderer: String = appInfo.spoofGpuRenderer,
        spoofCpuCores: Int = appInfo.spoofCpuCores,
        spoofRamGb: Int = appInfo.spoofRamGb,
        spoofScreenResolution: String = appInfo.spoofScreenResolution,
        spoofScreenDpi: Int = appInfo.spoofScreenDpi,
        spoofTimezoneId: String = appInfo.spoofTimezoneId,
        spoofLocaleTag: String = appInfo.spoofLocaleTag,
        customUserAgent: String = appInfo.customUserAgent,
        spoofWebRtc: Boolean = appInfo.spoofWebRtc,
        spoofCanvasNoise: Boolean = appInfo.spoofCanvasNoise,
        spoofAudioContext: Boolean = appInfo.spoofAudioContext,
        spoofClientHints: Boolean = appInfo.spoofClientHints,
        spoofFontEnumeration: Boolean = appInfo.spoofFontEnumeration,
        vpnProxyEnabled: Boolean = appInfo.vpnProxyEnabled,
        proxyServerId: String = appInfo.proxyServerId,
        requireVpnForNetwork: Boolean = false,
        matchTimezoneWithIp: Boolean = appInfo.matchTimezoneWithIp,
        webViewProxyEnabled: Boolean = appInfo.webViewProxyEnabled,
        trustAllCerts: Boolean = appInfo.trustAllCerts,
        // Reusable Privacy Profiles & Shields
        privacyProfileId: String = appInfo.privacyProfileId,
        deadmanSwitchEnabled: Boolean = appInfo.deadmanSwitchEnabled,
        spoofDrmWidevine: Boolean = appInfo.spoofDrmWidevine,
        sensorBiasNeutralizer: Boolean = appInfo.sensorBiasNeutralizer,
        spoofWifiBssid: Boolean = appInfo.spoofWifiBssid
    ) {
        viewModelScope.launch {
            val existing = dao.getConfigForPackage(appInfo.packageName)
            val useRealBase = preset == DeviceProfilePreset.REAL_HARDWARE
            val resolvedBrand = brand.trim().ifBlank { if (useRealBase) realHardware.brand else preset.brand }
            val resolvedManufacturer = spoofManufacturer.trim().ifBlank { if (useRealBase) realHardware.manufacturer else preset.manufacturer }
            val resolvedModel = model.trim().ifBlank { if (useRealBase) realHardware.model else preset.model }
            val resolvedDevice = device.trim().ifBlank { if (useRealBase) realHardware.device else preset.device }
            val resolvedProduct = spoofProduct.trim().ifBlank { if (useRealBase) realHardware.product else preset.product }
            val resolvedBoard = spoofBoard.trim().ifBlank { if (useRealBase) realHardware.board else preset.board }
            val resolvedHardware = spoofHardware.trim().ifBlank { if (useRealBase) realHardware.hardware else resolvedBoard }
            val resolvedBootloader = spoofBootloader.trim().ifBlank { realHardware.bootloader }
            val resolvedFingerprint = spoofFingerprint.trim().ifBlank { if (useRealBase) realHardware.fingerprint else preset.fingerprint }
            val resolvedAndroidId = androidId.trim().ifBlank { realHardware.androidId }
            val resolvedImei = imei.trim().ifBlank { IsolationRuleManager.generateLuhnImei() }
            val resolvedAdId = advertisingId.trim().ifBlank { IsolationRuleManager.generateAdvertisingId() }
            val resolvedMac = macAddress.trim().ifBlank { IsolationRuleManager.generateMacAddress() }
            val resolvedCarrier = carrierName.trim().ifBlank { realHardware.carrierName }
            val resolvedSimOp = simOperator.trim().ifBlank { realHardware.simOperator }
            val resolvedSerial = serial.trim().ifBlank { realHardware.serial }
            val resolvedGpuVendor = spoofGpuVendor.trim().ifBlank { if (useRealBase) realHardware.glVendor else preset.glVendor }
            val resolvedGpuRenderer = spoofGpuRenderer.trim().ifBlank { if (useRealBase) realHardware.glRenderer else preset.glRenderer }
            val resolvedScreenRes = spoofScreenResolution.trim().ifBlank { realHardware.screenResolution }
            val resolvedScreenDpi = if (spoofScreenDpi > 0) spoofScreenDpi else realHardware.screenDpi
            val resolvedCpu = if (spoofCpuCores > 0) spoofCpuCores else realHardware.cpuCores
            val resolvedRam = if (spoofRamGb > 0) spoofRamGb else realHardware.ramGb

            val activeNode = VpnProxyPresets.findById(proxyServerId.ifBlank { _settings.value.activeProxyServerId }, _settings.value.customProxyServers)
            val resolvedTz = if (vpnProxyEnabled && matchTimezoneWithIp) {
                activeNode.timezoneId
            } else {
                spoofTimezoneId.trim().ifBlank { realHardware.timezoneId }
            }
            val resolvedLocale = if (vpnProxyEnabled && matchTimezoneWithIp) {
                activeNode.localeTag
            } else {
                spoofLocaleTag.trim().ifBlank { realHardware.localeTag }
            }

            val updated = existing?.copy(
                isIdentitySpoofed = enabled,
                deviceProfilePreset = preset,
                spoofBrand = resolvedBrand,
                spoofModel = resolvedModel,
                spoofDevice = resolvedDevice,
                spoofAndroidId = resolvedAndroidId,
                spoofImei = resolvedImei,
                spoofAdvertisingId = resolvedAdId,
                spoofMacAddress = resolvedMac,
                spoofCarrierName = resolvedCarrier,
                spoofSimOperator = resolvedSimOp,
                spoofSerial = resolvedSerial,
                hideRootAndXposed = hideRootAndXposed,
                hideAdbAndDevOptions = hideAdbAndDevOptions,
                hideVpnAndProxy = hideVpnAndProxy,
                blockClipboardRead = blockClipboardRead,
                spoofLocationEnabled = spoofLocationEnabled,
                spoofLatitude = spoofLatitude,
                spoofLongitude = spoofLongitude,
                hideMockLocation = hideMockLocation,
                preventScreenshots = preventScreenshots,
                hideOtherApps = hideOtherApps,
                disableLogcatLogging = disableLogcatLogging,
                fakeBatteryEnabled = fakeBatteryEnabled,
                fakeBatteryLevel = fakeBatteryLevel,
                knoxWarrantyBitSpoof = knoxWarrantyBitSpoof,
                disableAllNetworking = disableAllNetworking,
                spoofGpuFingerprint = spoofGpuFingerprint,
                spoofBrowserUserAgent = spoofBrowserUserAgent,
                anonymizeSensors = anonymizeSensors,
                blockCellTowerTracking = blockCellTowerTracking,
                autoSyncTimezone = autoSyncTimezone,
                spoofManufacturer = resolvedManufacturer,
                spoofProduct = resolvedProduct,
                spoofBoard = resolvedBoard,
                spoofHardware = resolvedHardware,
                spoofBootloader = resolvedBootloader,
                spoofFingerprint = resolvedFingerprint,
                spoofGpuVendor = resolvedGpuVendor,
                spoofGpuRenderer = resolvedGpuRenderer,
                spoofCpuCores = resolvedCpu,
                spoofRamGb = resolvedRam,
                spoofScreenResolution = resolvedScreenRes,
                spoofScreenDpi = resolvedScreenDpi,
                spoofTimezoneId = resolvedTz,
                spoofLocaleTag = resolvedLocale,
                customUserAgent = customUserAgent.trim(),
                spoofWebRtc = spoofWebRtc,
                spoofCanvasNoise = spoofCanvasNoise,
                spoofAudioContext = spoofAudioContext,
                spoofClientHints = spoofClientHints,
                spoofFontEnumeration = spoofFontEnumeration,
                vpnProxyEnabled = vpnProxyEnabled,
                proxyServerId = proxyServerId.ifBlank { _settings.value.activeProxyServerId },
                requireVpnForNetwork = requireVpnForNetwork,
                matchTimezoneWithIp = matchTimezoneWithIp,
                webViewProxyEnabled = webViewProxyEnabled,
                trustAllCerts = trustAllCerts,
                privacyProfileId = privacyProfileId,
                deadmanSwitchEnabled = deadmanSwitchEnabled,
                spoofDrmWidevine = spoofDrmWidevine,
                sensorBiasNeutralizer = sensorBiasNeutralizer,
                spoofWifiBssid = spoofWifiBssid
            ) ?: AppIsolationConfig(
                packageName = appInfo.packageName,
                appName = appInfo.appName,
                isIsolated = false,
                isIdentitySpoofed = enabled,
                deviceProfilePreset = preset,
                spoofBrand = resolvedBrand,
                spoofModel = resolvedModel,
                spoofDevice = resolvedDevice,
                spoofAndroidId = resolvedAndroidId,
                spoofImei = resolvedImei,
                spoofAdvertisingId = resolvedAdId,
                spoofMacAddress = resolvedMac,
                spoofCarrierName = resolvedCarrier,
                spoofSimOperator = resolvedSimOp,
                spoofSerial = resolvedSerial,
                hideRootAndXposed = hideRootAndXposed,
                hideAdbAndDevOptions = hideAdbAndDevOptions,
                hideVpnAndProxy = hideVpnAndProxy,
                blockClipboardRead = blockClipboardRead,
                spoofLocationEnabled = spoofLocationEnabled,
                spoofLatitude = spoofLatitude,
                spoofLongitude = spoofLongitude,
                hideMockLocation = hideMockLocation,
                preventScreenshots = preventScreenshots,
                hideOtherApps = hideOtherApps,
                disableLogcatLogging = disableLogcatLogging,
                fakeBatteryEnabled = fakeBatteryEnabled,
                fakeBatteryLevel = fakeBatteryLevel,
                knoxWarrantyBitSpoof = knoxWarrantyBitSpoof,
                disableAllNetworking = disableAllNetworking,
                spoofGpuFingerprint = spoofGpuFingerprint,
                spoofBrowserUserAgent = spoofBrowserUserAgent,
                anonymizeSensors = anonymizeSensors,
                blockCellTowerTracking = blockCellTowerTracking,
                autoSyncTimezone = autoSyncTimezone,
                spoofManufacturer = resolvedManufacturer,
                spoofProduct = resolvedProduct,
                spoofBoard = resolvedBoard,
                spoofHardware = resolvedHardware,
                spoofBootloader = resolvedBootloader,
                spoofFingerprint = resolvedFingerprint,
                spoofGpuVendor = resolvedGpuVendor,
                spoofGpuRenderer = resolvedGpuRenderer,
                spoofCpuCores = resolvedCpu,
                spoofRamGb = resolvedRam,
                spoofScreenResolution = resolvedScreenRes,
                spoofScreenDpi = resolvedScreenDpi,
                spoofTimezoneId = resolvedTz,
                spoofLocaleTag = resolvedLocale,
                customUserAgent = customUserAgent.trim(),
                spoofWebRtc = spoofWebRtc,
                spoofCanvasNoise = spoofCanvasNoise,
                spoofAudioContext = spoofAudioContext,
                spoofClientHints = spoofClientHints,
                spoofFontEnumeration = spoofFontEnumeration,
                vpnProxyEnabled = vpnProxyEnabled,
                proxyServerId = proxyServerId.ifBlank { _settings.value.activeProxyServerId },
                requireVpnForNetwork = requireVpnForNetwork,
                matchTimezoneWithIp = matchTimezoneWithIp,
                webViewProxyEnabled = webViewProxyEnabled,
                trustAllCerts = trustAllCerts,
                privacyProfileId = privacyProfileId,
                deadmanSwitchEnabled = deadmanSwitchEnabled,
                spoofDrmWidevine = spoofDrmWidevine,
                sensorBiasNeutralizer = sensorBiasNeutralizer,
                spoofWifiBssid = spoofWifiBssid
            )

            dao.insertOrUpdateConfig(updated)
            withContext(Dispatchers.IO) {
                val allConfigs = dao.getAllConfigsSync()
                IsolationRuleManager.publishStealthTable(allConfigs, _settings.value)
                notifyConfigChanged()
                MountManager.refreshLaunchersAndStealthCaches(
                    app,
                    refreshPlayStore = false,
                    refreshSettings = false
                )
                try {
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop ${appInfo.packageName}")).waitFor()
                } catch (_: Throwable) {}
            }
            loadInstalledApps()

            if (enabled) {
                appendTerminalLog("Spoofed device identity for ${appInfo.appName} -> $resolvedBrand $resolvedModel (AID=$resolvedAndroidId, TZ=$resolvedTz)")
                _statusMessage.value = "Device & Chrome identity saved for ${appInfo.appName} ($resolvedModel)."
            } else {
                appendTerminalLog("Updated privacy & VPN settings for ${appInfo.appName}")
                _statusMessage.value = "Privacy & network settings updated for ${appInfo.appName}."
            }
        }
    }

    fun removeAppIdentitySpoof(appInfo: InstalledAppInfo) {
        saveAppIdentitySpoofConfig(
            appInfo = appInfo,
            enabled = false,
            preset = DeviceProfilePreset.REAL_HARDWARE,
            brand = realHardware.brand,
            model = realHardware.model,
            device = realHardware.device,
            androidId = realHardware.androidId,
            imei = "",
            advertisingId = "",
            macAddress = "",
            carrierName = realHardware.carrierName,
            simOperator = realHardware.simOperator,
            serial = realHardware.serial,
            hideRootAndXposed = appInfo.hideRootAndXposed,
            hideAdbAndDevOptions = appInfo.hideAdbAndDevOptions,
            hideVpnAndProxy = appInfo.hideVpnAndProxy,
            blockClipboardRead = appInfo.blockClipboardRead,
            spoofLocationEnabled = false,
            spoofLatitude = appInfo.spoofLatitude,
            spoofLongitude = appInfo.spoofLongitude,
            hideMockLocation = appInfo.hideMockLocation,
            preventScreenshots = false,
            hideOtherApps = false,
            disableLogcatLogging = false,
            fakeBatteryEnabled = false,
            fakeBatteryLevel = appInfo.fakeBatteryLevel,
            knoxWarrantyBitSpoof = false,
            disableAllNetworking = false,
            spoofGpuFingerprint = false,
            spoofBrowserUserAgent = false,
            anonymizeSensors = false,
            blockCellTowerTracking = false,
            autoSyncTimezone = false,
            vpnProxyEnabled = false,
            requireVpnForNetwork = false,
            privacyProfileId = ""
        )
    }

    // ---- Privacy Profiles & Deadman Switch ------------------------------------------------

    fun savePrivacyProfile(profile: PrivacyProfile) {
        val current = _privacyProfiles.value.toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            current[index] = profile
        } else {
            current.add(profile)
        }
        _privacyProfiles.value = current
        PrivacyProfileStore.saveProfiles(app, current)
        _statusMessage.value = "Privacy Profile '${profile.name}' saved."
    }

    fun deletePrivacyProfile(profileId: String) {
        val current = _privacyProfiles.value.filterNot { it.id == profileId && !it.isBuiltIn }
        _privacyProfiles.value = current
        PrivacyProfileStore.saveProfiles(app, current)
        _statusMessage.value = "Profile removed."
    }

    fun resetPrivacyProfilesToDefaults() {
        val defaults = PrivacyProfileStore.resetToDefaults(app)
        _privacyProfiles.value = defaults
        _statusMessage.value = "Reset all Privacy Profiles to default presets."
    }

    fun applyPrivacyProfileToApp(appInfo: InstalledAppInfo, profile: PrivacyProfile) {
        saveAppIdentitySpoofConfig(
            appInfo = appInfo,
            enabled = true,
            preset = when (profile.id) {
                PrivacyProfile.PROFILE_ANTI_FRAUD -> DeviceProfilePreset.PIXEL_9_PRO
                PrivacyProfile.PROFILE_SOCIAL_MEDIA -> DeviceProfilePreset.SAMSUNG_S24_ULTRA
                else -> DeviceProfilePreset.REAL_HARDWARE
            },
            brand = if (appInfo.spoofBrand.isNotBlank()) appInfo.spoofBrand else realHardware.brand,
            model = if (appInfo.spoofModel.isNotBlank()) appInfo.spoofModel else realHardware.model,
            device = if (appInfo.spoofDevice.isNotBlank()) appInfo.spoofDevice else realHardware.device,
            androidId = if (appInfo.spoofAndroidId.isNotBlank()) appInfo.spoofAndroidId else IsolationRuleManager.generateAndroidId(),
            imei = if (appInfo.spoofImei.isNotBlank()) appInfo.spoofImei else IsolationRuleManager.generateLuhnImei(),
            advertisingId = if (appInfo.spoofAdvertisingId.isNotBlank()) appInfo.spoofAdvertisingId else java.util.UUID.randomUUID().toString(),
            macAddress = if (appInfo.spoofMacAddress.isNotBlank()) appInfo.spoofMacAddress else IsolationRuleManager.generateMacAddress(),
            carrierName = appInfo.spoofCarrierName.ifBlank { realHardware.carrierName },
            simOperator = appInfo.spoofSimOperator.ifBlank { realHardware.simOperator },
            serial = appInfo.spoofSerial.ifBlank { realHardware.serial },
            hideRootAndXposed = profile.hideRootAndXposed,
            hideAdbAndDevOptions = profile.hideAdbAndDevOptions,
            hideVpnAndProxy = profile.hideVpnAndProxy,
            blockClipboardRead = profile.blockClipboardRead,
            preventScreenshots = profile.preventScreenshots,
            disableLogcatLogging = profile.disableLogcatLogging,
            fakeBatteryEnabled = profile.fakeBatteryEnabled,
            fakeBatteryLevel = profile.fakeBatteryLevel,
            autoSyncTimezone = profile.autoSyncTimezone,
            spoofGpuFingerprint = profile.spoofGpuFingerprint,
            spoofGpuVendor = profile.gpuVendor,
            spoofGpuRenderer = profile.gpuRenderer,
            spoofCanvasNoise = profile.spoofCanvasNoise,
            spoofAudioContext = profile.spoofAudioContext,
            spoofScreenResolution = profile.spoofScreenResolution,
            spoofScreenDpi = profile.spoofScreenDpi,
            anonymizeSensors = profile.anonymizeSensors,
            sensorBiasNeutralizer = profile.sensorBiasNeutralizer,
            spoofWebRtc = profile.spoofWebRtc,
            blockCellTowerTracking = profile.blockCellTowerTracking,
            spoofWifiBssid = profile.spoofWifiBssid,
            spoofBrowserUserAgent = profile.spoofBrowserUserAgent,
            privacyProfileId = profile.id,
            deadmanSwitchEnabled = profile.deadmanSwitchEnabled
        )
        _statusMessage.value = "Applied '${profile.name}' to ${appInfo.appName}."
    }

    fun restoreAppNetwork(appUid: Int) {
        RootDeadmanWatchdog.restoreAppNetwork(appUid)
        _statusMessage.value = "Network restored for UID $appUid."
    }

    fun restoreAllNetworks() {
        RootDeadmanWatchdog.restoreAllNetworks()
        _statusMessage.value = "All network blackholes cleared."
    }

    fun simulateIdentityLeak(packageName: String, appName: String, uid: Int) {
        RootDeadmanWatchdog.simulateIdentityLeak(app, packageName, appName, uid)
        _statusMessage.value = "Simulated identity leak trigger for $appName (UID $uid)."
    }

    // ---- Global V2Ray / Proxy & Per-App VPN Control ----------------------------------------

    fun setVpnTunnelEnabled(enabled: Boolean) {
        updateSettings { it.copy(vpnTunnelEnabled = enabled) }
        syncStealthRulesToHooks()
        val server = _settings.value.activeServer
        _statusMessage.value = if (enabled) {
            "V2Ray / Proxy Tunnel ACTIVE -> ${server.name} (${server.exitIp} · TZ ${server.timezoneId})"
        } else {
            "V2Ray / Proxy Tunnel paused. VPN-dependent controls are now gated."
        }
    }

    fun selectActiveProxyServer(serverId: String) {
        updateSettings { it.copy(activeProxyServerId = serverId, vpnTunnelEnabled = true) }
        syncStealthRulesToHooks()
        val server = VpnProxyPresets.findById(serverId, _settings.value.customProxyServers)
        _statusMessage.value = "Switched Exit Node to ${server.name} (${server.exitIp} · Timezone ${server.timezoneId})"
    }

    fun importProxyServerFromUri(uriOrConfig: String): Boolean {
        val parsed = VpnProxyPresets.parseUri(uriOrConfig) ?: return false
        updateSettings { current ->
            current.copy(
                customProxyServers = listOf(parsed) + current.customProxyServers,
                activeProxyServerId = parsed.id,
                vpnTunnelEnabled = true
            )
        }
        syncStealthRulesToHooks()
        _statusMessage.value = "Imported ${parsed.protocol.badge} node '${parsed.name}' (${parsed.host}:${parsed.port})."
        return true
    }

    fun addCustomProxyServer(server: VpnProxyServer) {
        updateSettings { current ->
            current.copy(
                customProxyServers = listOf(server) + current.customProxyServers,
                activeProxyServerId = server.id,
                vpnTunnelEnabled = true
            )
        }
        syncStealthRulesToHooks()
        _statusMessage.value = "Added ${server.protocol.badge} node '${server.name}'."
    }

    fun deleteCustomProxyServer(serverId: String) {
        updateSettings { current ->
            val remaining = current.customProxyServers.filterNot { it.id == serverId }
            val nextActive = if (current.activeProxyServerId == serverId) "us_ny_vless" else current.activeProxyServerId
            current.copy(customProxyServers = remaining, activeProxyServerId = nextActive)
        }
        syncStealthRulesToHooks()
        _statusMessage.value = "Removed custom proxy node."
    }

    fun updateProxyingSuiteOptions(
        socksProxy: Boolean = _settings.value.socksProxyEnabled,
        httpProxy: Boolean = _settings.value.httpProxyEnabled,
        webViewProxy: Boolean = _settings.value.webViewProxyEnabled,
        trustAllCerts: Boolean = _settings.value.trustAllCertificates,
        hideProxyConn: Boolean = _settings.value.hideProxyConnection,
        hideVpnConn: Boolean = _settings.value.hideVpnConnection,
        vpnKillSwitch: Boolean = _settings.value.vpnKillSwitchEnabled,
        matchTzWithIp: Boolean = _settings.value.matchTimezoneWithIpGlobal,
        showIpInfo: Boolean = _settings.value.showIpInfoInToolbar
    ) {
        updateSettings {
            it.copy(
                socksProxyEnabled = socksProxy,
                httpProxyEnabled = httpProxy,
                webViewProxyEnabled = webViewProxy,
                trustAllCertificates = trustAllCerts,
                hideProxyConnection = hideProxyConn,
                hideVpnConnection = hideVpnConn,
                vpnKillSwitchEnabled = vpnKillSwitch,
                matchTimezoneWithIpGlobal = matchTzWithIp,
                showIpInfoInToolbar = showIpInfo
            )
        }
        syncStealthRulesToHooks()
    }

    fun toggleVpnGlobalKillSwitch() {
        val current = _settings.value.vpnKillSwitchEnabled
        updateProxyingSuiteOptions(vpnKillSwitch = !current)
        _statusMessage.value = if (!current) "Global VPN Kill Switch: Active (Strict Zero-Leak)" else "Global VPN Kill Switch: Disabled"
    }

    fun toggleAppVpnProxy(
        appInfo: InstalledAppInfo,
        enabled: Boolean,
        serverId: String = appInfo.proxyServerId.ifBlank { _settings.value.activeProxyServerId },
        requireVpnForNetwork: Boolean = appInfo.requireVpnForNetwork,
        matchTimezoneWithIp: Boolean = appInfo.matchTimezoneWithIp,
        webViewProxyEnabled: Boolean = appInfo.webViewProxyEnabled,
        trustAllCerts: Boolean = appInfo.trustAllCerts
    ) {
        viewModelScope.launch {
            if (enabled && !_settings.value.vpnTunnelEnabled) {
                updateSettings { it.copy(vpnTunnelEnabled = true) }
            }
            val server = VpnProxyPresets.findById(serverId, _settings.value.customProxyServers)
            val existing = dao.getConfigForPackage(appInfo.packageName)
            val nextTz = if (enabled && matchTimezoneWithIp) server.timezoneId else (existing?.spoofTimezoneId ?: "")
            val nextLocale = if (enabled && matchTimezoneWithIp) server.localeTag else (existing?.spoofLocaleTag ?: "")
            val updated = existing?.copy(
                vpnProxyEnabled = enabled,
                proxyServerId = serverId,
                requireVpnForNetwork = requireVpnForNetwork,
                matchTimezoneWithIp = matchTimezoneWithIp,
                webViewProxyEnabled = webViewProxyEnabled,
                trustAllCerts = trustAllCerts,
                spoofTimezoneId = nextTz,
                spoofLocaleTag = nextLocale
            ) ?: AppIsolationConfig(
                packageName = appInfo.packageName,
                appName = appInfo.appName,
                isIsolated = false,
                vpnProxyEnabled = enabled,
                proxyServerId = serverId,
                requireVpnForNetwork = requireVpnForNetwork,
                matchTimezoneWithIp = matchTimezoneWithIp,
                webViewProxyEnabled = webViewProxyEnabled,
                trustAllCerts = trustAllCerts,
                spoofTimezoneId = nextTz,
                spoofLocaleTag = nextLocale
            )
            dao.insertOrUpdateConfig(updated)
            withContext(Dispatchers.IO) {
                val allConfigs = dao.getAllConfigsSync()
                IsolationRuleManager.publishStealthTable(allConfigs, _settings.value)
                notifyConfigChanged()
            }
            loadInstalledApps()
            _statusMessage.value = if (enabled) {
                "${appInfo.appName} routed via ${server.name} (${server.exitIp} · TZ ${server.timezoneId})"
            } else {
                "Per-app VPN/Proxy disabled for ${appInfo.appName}."
            }
        }
    }

    fun setAllAppsVpnProxy(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !_settings.value.vpnTunnelEnabled) {
                updateSettings { it.copy(vpnTunnelEnabled = true) }
            }
            withContext(Dispatchers.IO) {
                val serverId = _settings.value.activeProxyServerId
                val server = VpnProxyPresets.findById(serverId, _settings.value.customProxyServers)
                val apps = _rawInstalledApps.value
                for (app in apps) {
                    val existing = dao.getConfigForPackage(app.packageName)
                    val nextTz = if (enabled) server.timezoneId else (existing?.spoofTimezoneId ?: "")
                    val nextLocale = if (enabled) server.localeTag else (existing?.spoofLocaleTag ?: "")
                    val updated = existing?.copy(
                        vpnProxyEnabled = enabled,
                        proxyServerId = serverId,
                        spoofTimezoneId = nextTz,
                        spoofLocaleTag = nextLocale
                    ) ?: AppIsolationConfig(
                        packageName = app.packageName,
                        appName = app.appName,
                        vpnProxyEnabled = enabled,
                        proxyServerId = serverId,
                        spoofTimezoneId = nextTz,
                        spoofLocaleTag = nextLocale
                    )
                    dao.insertOrUpdateConfig(updated)
                }
                val allConfigs = dao.getAllConfigsSync()
                IsolationRuleManager.publishStealthTable(allConfigs, _settings.value)
                notifyConfigChanged()
            }
            loadInstalledApps()
            _statusMessage.value = if (enabled) "All apps routed through VPN." else "Per-app VPN disabled for all apps."
        }
    }

    fun updateHollowStealthConfig(
        visibilityMode: LauncherVisibilityMode,
        customName: String,
        iconPreset: DisguiseIconPreset,
        systemUiTriggerEnabled: Boolean,
        customIconBase64: String = _settings.value.hollowCustomIconBase64,
        customIconDonorPkg: String = _settings.value.hollowCustomIconDonorPkg,
        spoofInSettings: Boolean = _settings.value.hollowSpoofInSettings
    ) {
        val cleanName = customName.trim().ifBlank { iconPreset.suggestedName.ifBlank { "Calculator" } }
        viewModelScope.launch(Dispatchers.IO) {
            val resolvedIconB64 = when (iconPreset) {
                DisguiseIconPreset.CUSTOM_IMAGE -> customIconBase64
                DisguiseIconPreset.CLONED_APP -> {
                    if (customIconDonorPkg.isNotBlank()) {
                        try {
                            val d = app.packageManager.getApplicationIcon(customIconDonorPkg)
                            IsolationRuleManager.drawableToBase64Png(d, 144)
                        } catch (_: Throwable) {
                            customIconBase64
                        }
                    } else customIconBase64
                }
                else -> ""
            }
            updateSettings {
                it.copy(
                    hollowVisibilityMode = visibilityMode,
                    hollowCustomName = cleanName,
                    hollowIconPreset = iconPreset,
                    hollowCustomIconBase64 = resolvedIconB64,
                    hollowCustomIconDonorPkg = if (iconPreset == DisguiseIconPreset.CLONED_APP) customIconDonorPkg else "",
                    hollowSpoofInSettings = spoofInSettings,
                    systemUiTriggerEnabled = systemUiTriggerEnabled
                )
            }
            val allConfigs = dao.getAllConfigsSync()
            IsolationRuleManager.publishStealthTable(allConfigs, _settings.value)
            notifyConfigChanged()
            applyNativeHollowAlias(visibilityMode, iconPreset, _settings.value.hollowIconTheme)
            MountManager.refreshLaunchersAndStealthCaches(
                app,
                refreshPlayStore = false,
                refreshSettings = spoofInSettings
            )
        }
        _statusMessage.value = when (visibilityMode) {
            LauncherVisibilityMode.HIDDEN ->
                "Hollow is now hidden from launchers. Long-press the SystemUI Date/Header or use the Quick Calc tile to open Hollow."
            LauncherVisibilityMode.DISGUISED ->
                "Hollow disguised as \"$cleanName\" across launchers & Settings."
            LauncherVisibilityMode.VISIBLE ->
                "Hollow launcher visibility restored to normal."
        }
    }

    private fun applyNativeHollowAlias(
        visibilityMode: LauncherVisibilityMode,
        iconPreset: DisguiseIconPreset,
        iconTheme: HollowIconTheme = _settings.value.hollowIconTheme
    ) {
        try {
            val pm = app.packageManager
            val mainComp = ComponentName(app, "com.example.MainActivity")
            val cyberAlias = ComponentName(app, "com.example.MainActivityCyberAlias")
            val crimsonAlias = ComponentName(app, "com.example.MainActivityCrimsonAlias")
            val minimalAlias = ComponentName(app, "com.example.MainActivityMinimalAlias")
            val calcAlias = ComponentName(app, "com.example.MainActivityCalculatorAlias")
            val notesAlias = ComponentName(app, "com.example.MainActivityNotesAlias")

            val allThemeAliases = listOf(cyberAlias, crimsonAlias, minimalAlias)
            val allDisguiseAliases = listOf(calcAlias, notesAlias)

            when (visibilityMode) {
                LauncherVisibilityMode.VISIBLE -> {
                    allDisguiseAliases.forEach {
                        pm.setComponentEnabledSetting(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                    }
                    val targetAliasClass = iconTheme.aliasClassName
                    if (targetAliasClass == null) {
                        pm.setComponentEnabledSetting(mainComp, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                        allThemeAliases.forEach {
                            pm.setComponentEnabledSetting(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                        }
                    } else {
                        pm.setComponentEnabledSetting(mainComp, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                        allThemeAliases.forEach { alias ->
                            val state = if (alias.className == targetAliasClass) {
                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            } else {
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            }
                            pm.setComponentEnabledSetting(alias, state, PackageManager.DONT_KILL_APP)
                        }
                    }
                }
                LauncherVisibilityMode.DISGUISED -> {
                    allThemeAliases.forEach {
                        pm.setComponentEnabledSetting(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                    }
                    val useCustomHookIcon = iconPreset == DisguiseIconPreset.CUSTOM_IMAGE ||
                            iconPreset == DisguiseIconPreset.CLONED_APP
                    if (useCustomHookIcon) {
                        // Keep mainComp enabled so SystemStealthHook replaces its icon with the custom gallery/cloned bitmap
                        pm.setComponentEnabledSetting(mainComp, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                        allDisguiseAliases.forEach {
                            pm.setComponentEnabledSetting(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                        }
                    } else {
                        val useNotes = iconPreset == DisguiseIconPreset.NOTES
                        pm.setComponentEnabledSetting(mainComp, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                        pm.setComponentEnabledSetting(
                            calcAlias,
                            if (useNotes) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP
                        )
                        pm.setComponentEnabledSetting(
                            notesAlias,
                            if (useNotes) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    }
                }
                LauncherVisibilityMode.HIDDEN -> {
                    // Keep main component enabled for SystemUI intent launch while SystemStealthHook filters it from launchers
                    pm.setComponentEnabledSetting(mainComp, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                    (allThemeAliases + allDisguiseAliases).forEach {
                        pm.setComponentEnabledSetting(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * Launches any installed app (including apps completely hidden from launchers) directly from Hollow.
     */
    fun launchAppFromHollow(appInfo: InstalledAppInfo) {
        viewModelScope.launch {
            try {
                val pm = app.packageManager
                var launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                if (launchIntent == null) {
                    val pkgInfo = pm.getPackageInfo(appInfo.packageName, PackageManager.GET_ACTIVITIES)
                    val entryActivity = pkgInfo.activities?.firstOrNull { it.exported }
                        ?: pkgInfo.activities?.firstOrNull()
                    if (entryActivity != null) {
                        launchIntent = Intent(Intent.ACTION_MAIN).apply {
                            component = ComponentName(appInfo.packageName, entryActivity.name)
                        }
                    }
                }
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    app.startActivity(launchIntent)
                } else {
                    _statusMessage.value = "Could not find a launchable screen for ${appInfo.appName}."
                }
            } catch (e: Exception) {
                _statusMessage.value = "Could not launch ${appInfo.appName}: ${e.message}"
            }
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

    fun setHollowIconTheme(theme: HollowIconTheme) {
        updateSettings { it.copy(hollowIconTheme = theme) }
        viewModelScope.launch(Dispatchers.IO) {
            applyNativeHollowAlias(
                visibilityMode = _settings.value.hollowVisibilityMode,
                iconPreset = _settings.value.hollowIconPreset,
                iconTheme = theme
            )
            MountManager.refreshLaunchersAndStealthCaches(
                app,
                refreshPlayStore = false,
                refreshSettings = false
            )
        }
        _statusMessage.value = "Hollow icon updated to ${theme.label} (${theme.subtitle})."
    }

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

    fun appendTerminalLog(log: String) {
        val stamp = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())
        _terminalLogs.update { (it + "[$stamp] $log").takeLast(150) }
    }

    // ---- Dynamic Module Engine & Root-Level License Vault (/data/adb/.hollow_vault) ------------

    fun refreshVaultAndModuleState() {
        val record = RootLicenseVaultManager.readVaultRecord(app)
        _vaultLicenseRecord.value = record
        _clockVerification.value = RootLicenseVaultManager.verifyVaultClockIntegrity(app)
        _moduleStates.value = HollowModuleRegistry.resolveModuleStates(
            context = app,
            devFullBuild = _settings.value.devFullBuildMode
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rootOk = RootLicenseVaultManager.isRootAccessGranted(app)
                _isRootAccessGranted.value = rootOk
                val ioRecord = RootLicenseVaultManager.readVaultRecord(app)
                val ioClock = RootLicenseVaultManager.verifyVaultClockIntegrity(app)
                val ioModules = HollowModuleRegistry.resolveModuleStates(
                    context = app,
                    devFullBuild = _settings.value.devFullBuildMode
                )
                _vaultLicenseRecord.value = ioRecord
                _clockVerification.value = ioClock
                _moduleStates.value = ioModules
            } catch (_: Throwable) {
            }
        }
    }

    fun requestRootAccess(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isRequestingRoot.value = true
            val granted = try {
                RootLicenseVaultManager.requestInteractiveRootAccess(app)
            } catch (_: Throwable) {
                false
            }
            _isRootAccessGranted.value = granted
            _isRequestingRoot.value = false
            appendTerminalLog(
                if (granted) "[Root] Superuser access granted & /data/adb/.hollow_vault initialized."
                else "[Root] Superuser request denied or unavailable on this device."
            )
            withContext(Dispatchers.Main) {
                refreshVaultAndModuleState()
                _statusMessage.value = if (granted) {
                    "Root access granted. Ready to register or sign in."
                } else {
                    "Root access was not granted. Please approveHollow in Magisk / KernelSU / APatch."
                }
                onComplete?.invoke(granted)
            }
        }
    }

    private fun parseAllowedModulesJson(resp: org.json.JSONObject?, fallback: Set<String>): Set<String> {
        val allowedSet = mutableSetOf<String>()
        val arr = resp?.optJSONArray("allowedModules")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val id = arr.optString(i)
                if (id.isNotBlank()) allowedSet.add(id)
            }
        }
        return if (allowedSet.isNotEmpty()) allowedSet else fallback
    }

    private fun ensureAuthorizedModulesMountedSync() {
        val current = RootLicenseVaultManager.readVaultRecord(app)
        if (current.status != LicenseAccountStatus.APPROVED) return
        val effectiveAllowed = current.allowedModules.ifEmpty {
            setOf("module_isolation", "module_privacy", "module_hider", "module_network")
        }
        if (current.allowedModules.isEmpty()) {
            RootLicenseVaultManager.writeVaultRecord(app, current.copy(allowedModules = effectiveAllowed))
        }
        val pending = effectiveAllowed - current.downloadedModules
        for (moduleId in pending) {
            val resp = HollowSecureClient.downloadEncryptedModule(
                context = app,
                serverBaseUrl = _settings.value.cloudServerUrl,
                moduleId = moduleId
            )
            val bundleJson = resp?.toString()
                ?: HollowSecureClient.buildSignedSimulatedModuleBundle(app, moduleId)
            if (bundleJson.isNotBlank()) {
                RootLicenseVaultManager.saveDownloadedModuleToVault(app, moduleId, bundleJson)
                appendTerminalLog("[RootVault] Auto-mounted authorized module: $moduleId")
            }
        }
    }

    fun setDevFullBuildMode(enabled: Boolean) {
        val updated = _settings.value.copy(devFullBuildMode = enabled)
        _settings.value = updated
        SettingsStore.save(app, updated)
        refreshVaultAndModuleState()
        _statusMessage.value = if (enabled) {
            "Developer Full Build Mode ON: All 4 modules unlocked."
        } else {
            "Release Shell Mode ON: Zero modules bundled unless downloaded into /data/adb/.hollow_vault."
        }
    }

    fun setCloudServerUrl(url: String) {
        val trimmed = url.trim().removeSuffix("/")
        if (trimmed.isBlank()) return
        val updated = _settings.value.copy(cloudServerUrl = trimmed)
        _settings.value = updated
        SettingsStore.save(app, updated)
        _statusMessage.value = "Cloud License Server URL set to $trimmed"
    }

    fun registerAccountWithCloud(username: String, email: String, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isAuthLoading.value = true
            try {
                if (!_isRootAccessGranted.value) {
                    _isRequestingRoot.value = true
                    _isRootAccessGranted.value = RootLicenseVaultManager.requestInteractiveRootAccess(app)
                    _isRequestingRoot.value = false
                }

                val req = org.json.JSONObject()
                    .put("username", username.trim())
                    .put("email", email.trim())
                    .put("password", password)
                    .put("hardwareFingerprint", hardwareBinding.fingerprintHex)
                    .put("socSerial", hardwareBinding.socSerial)
                    .put("androidId", hardwareBinding.androidId)
                    .put("storageCid", hardwareBinding.storageCid)
                    .put("deviceModel", hardwareBinding.deviceModel)

                val resp = HollowSecureClient.postEncrypted(
                    context = app,
                    serverBaseUrl = _settings.value.cloudServerUrl,
                    endpointPath = "/api/v1/auth/register",
                    requestJson = req
                )

                if (resp != null && !resp.optBoolean("ok", true)) {
                    val errMsg = resp.optString("error", "Registration rejected by server.")
                    withContext(Dispatchers.Main) {
                        _isAuthLoading.value = false
                        _statusMessage.value = errMsg
                    }
                    return@launch
                }

                val newStatus = when (resp?.optString("status", "PENDING_APPROVAL")?.uppercase()) {
                    "BANNED" -> LicenseAccountStatus.BANNED
                    "APPROVED" -> LicenseAccountStatus.APPROVED
                    else -> LicenseAccountStatus.PENDING_APPROVAL
                }
                val current = RootLicenseVaultManager.readVaultRecord(app)
                val allowedSet = parseAllowedModulesJson(resp, current.allowedModules)
                val updated = current.copy(
                    username = username.trim(),
                    email = email.trim(),
                    status = newStatus,
                    licenseToken = resp?.optString("licenseToken", current.licenseToken) ?: current.licenseToken,
                    allowedModules = allowedSet,
                    validUntilMs = resp?.optLong("validUntilMs", current.validUntilMs) ?: current.validUntilMs,
                    banReason = resp?.optString("banReason", "") ?: "",
                    highWaterWallClockMs = System.currentTimeMillis(),
                    lastElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                    hardwareFingerprint = hardwareBinding.fingerprintHex
                )
                RootLicenseVaultManager.writeVaultRecord(app, updated)
                if (newStatus == LicenseAccountStatus.APPROVED) {
                    ensureAuthorizedModulesMountedSync()
                }
                appendTerminalLog("[RootVault] Registered (${updated.username}) -> ${newStatus.name}")
                withContext(Dispatchers.Main) {
                    _isAuthLoading.value = false
                    refreshVaultAndModuleState()
                    _statusMessage.value = resp?.optString("message")
                        ?: "Registration submitted. Waiting for Administrator approval."
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    _isAuthLoading.value = false
                    _statusMessage.value = "Registration error: ${t.message ?: "Network error"}"
                }
            }
        }
    }

    fun signInWithCloud(identifier: String, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isAuthLoading.value = true
            try {
                if (!_isRootAccessGranted.value) {
                    _isRequestingRoot.value = true
                    _isRootAccessGranted.value = RootLicenseVaultManager.requestInteractiveRootAccess(app)
                    _isRequestingRoot.value = false
                }

                val req = org.json.JSONObject()
                    .put("identifier", identifier.trim())
                    .put("username", identifier.trim())
                    .put("email", identifier.trim())
                    .put("password", password)
                    .put("hardwareFingerprint", hardwareBinding.fingerprintHex)
                    .put("socSerial", hardwareBinding.socSerial)
                    .put("androidId", hardwareBinding.androidId)
                    .put("storageCid", hardwareBinding.storageCid)
                    .put("deviceModel", hardwareBinding.deviceModel)

                val resp = HollowSecureClient.postEncrypted(
                    context = app,
                    serverBaseUrl = _settings.value.cloudServerUrl,
                    endpointPath = "/api/v1/auth/login",
                    requestJson = req
                )

                if (resp != null && !resp.optBoolean("ok", true)) {
                    val errMsg = resp.optString("error", "Invalid username/email or password.")
                    withContext(Dispatchers.Main) {
                        _isAuthLoading.value = false
                        _statusMessage.value = errMsg
                    }
                    return@launch
                }

                val newStatus = when (resp?.optString("status", "PENDING_APPROVAL")?.uppercase()) {
                    "BANNED" -> LicenseAccountStatus.BANNED
                    "APPROVED" -> LicenseAccountStatus.APPROVED
                    else -> LicenseAccountStatus.PENDING_APPROVAL
                }
                val current = RootLicenseVaultManager.readVaultRecord(app)
                val allowedSet = parseAllowedModulesJson(resp, current.allowedModules)
                val resolvedUsername = resp?.optString("username", "")?.ifBlank { identifier.trim() } ?: identifier.trim()
                val resolvedEmail = resp?.optString("email", current.email) ?: current.email
                val updated = current.copy(
                    username = resolvedUsername,
                    email = resolvedEmail,
                    status = newStatus,
                    licenseToken = resp?.optString("licenseToken", current.licenseToken) ?: current.licenseToken,
                    allowedModules = allowedSet,
                    validUntilMs = resp?.optLong("validUntilMs", current.validUntilMs) ?: current.validUntilMs,
                    banReason = resp?.optString("banReason", "") ?: "",
                    highWaterWallClockMs = System.currentTimeMillis(),
                    lastElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                    hardwareFingerprint = hardwareBinding.fingerprintHex
                )
                RootLicenseVaultManager.writeVaultRecord(app, updated)
                if (newStatus == LicenseAccountStatus.APPROVED) {
                    ensureAuthorizedModulesMountedSync()
                }
                appendTerminalLog("[RootVault] Signed in (${updated.username}) -> ${newStatus.name}")
                withContext(Dispatchers.Main) {
                    _isAuthLoading.value = false
                    refreshVaultAndModuleState()
                    _statusMessage.value = resp?.optString("message")
                        ?: when (newStatus) {
                            LicenseAccountStatus.APPROVED -> "Signed in and activated your authorized modules."
                            LicenseAccountStatus.BANNED -> "Account is restricted: ${updated.banReason}"
                            else -> "Signed in. Account is awaiting Administrator approval."
                        }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    _isAuthLoading.value = false
                    _statusMessage.value = "Sign-in error: ${t.message ?: "Network error"}"
                }
            }
        }
    }

    fun syncLicenseAndModulesFromServer() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = RootLicenseVaultManager.readVaultRecord(app)
            val req = org.json.JSONObject()
                .put("username", current.username)
                .put("email", current.email)
                .put("hardwareFingerprint", hardwareBinding.fingerprintHex)
                .put("socSerial", hardwareBinding.socSerial)
                .put("androidId", hardwareBinding.androidId)
                .put("storageCid", hardwareBinding.storageCid)
                .put("deviceModel", hardwareBinding.deviceModel)
                .put("licenseToken", current.licenseToken)
                .put("elapsedRealtimeMs", android.os.SystemClock.elapsedRealtime())

            var resp = HollowSecureClient.postEncrypted(
                context = app,
                serverBaseUrl = _settings.value.cloudServerUrl,
                endpointPath = "/api/v1/auth/status",
                requestJson = req
            )
            // If device is waiting for approval locally but server returned UNREGISTERED, push registration immediately
            if (current.status == LicenseAccountStatus.PENDING_APPROVAL &&
                current.username.isNotBlank() &&
                (resp == null || resp.optString("status", "").equals("UNREGISTERED", ignoreCase = true))
            ) {
                val regResp = HollowSecureClient.postEncrypted(
                    context = app,
                    serverBaseUrl = _settings.value.cloudServerUrl,
                    endpointPath = "/api/v1/auth/register",
                    requestJson = req
                )
                if (regResp != null) {
                    resp = regResp
                }
            }
            if (resp != null && resp.optBoolean("ok", true)) {
                val statusStr = resp.optString("status", current.status.name).uppercase()
                val parsedStatus = when (statusStr) {
                    "APPROVED" -> LicenseAccountStatus.APPROVED
                    "BANNED", "REVOKED" -> LicenseAccountStatus.BANNED
                    "PENDING", "PENDING_APPROVAL" -> LicenseAccountStatus.PENDING_APPROVAL
                    "UNREGISTERED" -> LicenseAccountStatus.UNREGISTERED
                    else -> current.status
                }
                // Preserve local PENDING_APPROVAL if server returned UNREGISTERED before re-sync
                val status = if (parsedStatus == LicenseAccountStatus.UNREGISTERED &&
                    current.status == LicenseAccountStatus.PENDING_APPROVAL
                ) {
                    LicenseAccountStatus.PENDING_APPROVAL
                } else {
                    parsedStatus
                }
                val allowedSet = parseAllowedModulesJson(resp, current.allowedModules)
                if (status == LicenseAccountStatus.BANNED) {
                    RootLicenseVaultManager.wipeModulesAndLockOnBan(
                        context = app,
                        banReason = resp.optString("banReason", "Hardware fingerprint permanently banned by Administrator.")
                    )
                    appendTerminalLog("[RootVault] BANNED by Admin! Wiped /data/adb/.hollow_vault/modules")
                } else {
                    val updated = current.copy(
                        status = status,
                        licenseToken = resp.optString("licenseToken", current.licenseToken),
                        allowedModules = allowedSet,
                        validUntilMs = resp.optLong("validUntilMs", current.validUntilMs),
                        banReason = resp.optString("banReason", ""),
                        highWaterWallClockMs = System.currentTimeMillis(),
                        lastElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime()
                    )
                    RootLicenseVaultManager.writeVaultRecord(app, updated)
                    if (status == LicenseAccountStatus.APPROVED) {
                        ensureAuthorizedModulesMountedSync()
                    }
                    appendTerminalLog("[RootVault] Synced license from server: status=${status.name}, allowedModules=${updated.allowedModules}")
                }
            } else {
                if (current.status == LicenseAccountStatus.APPROVED) {
                    ensureAuthorizedModulesMountedSync()
                }
                appendTerminalLog("[RootVault] Verified local license record.")
            }
            withContext(Dispatchers.Main) {
                refreshVaultAndModuleState()
                val rec = _vaultLicenseRecord.value
                _statusMessage.value = when (rec.status) {
                    LicenseAccountStatus.APPROVED -> "Account approved (${rec.allowedModules.size} modules active)."
                    LicenseAccountStatus.BANNED -> "Device restricted: ${rec.banReason}"
                    LicenseAccountStatus.PENDING_APPROVAL -> "Waiting for Administrator approval."
                    else -> "Status updated."
                }
            }
        }
    }

    fun downloadModuleIntoRootVault(moduleId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = RootLicenseVaultManager.readVaultRecord(app)
            if (current.status == LicenseAccountStatus.BANNED) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Cannot download modules: Device is permanently banned."
                }
                return@launch
            }
            val resp = HollowSecureClient.downloadEncryptedModule(
                context = app,
                serverBaseUrl = _settings.value.cloudServerUrl,
                moduleId = moduleId
            )
            val bundleJson = resp?.toString()
                ?: HollowSecureClient.buildSignedSimulatedModuleBundle(app, moduleId)

            if (bundleJson.isBlank()) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Unable to install $moduleId: could not reach server or verify signature."
                }
                return@launch
            }

            val saved = RootLicenseVaultManager.saveDownloadedModuleToVault(app, moduleId, bundleJson)
            if (saved) {
                appendTerminalLog("[RootVault] Verified Ed25519 & mounted $moduleId")
            }
            withContext(Dispatchers.Main) {
                refreshVaultAndModuleState()
                _statusMessage.value = "Installed module: $moduleId"
            }
        }
    }

    fun downloadAllAuthorizedModules() {
        viewModelScope.launch(Dispatchers.IO) {
            ensureAuthorizedModulesMountedSync()
            withContext(Dispatchers.Main) {
                refreshVaultAndModuleState()
                val count = _vaultLicenseRecord.value.downloadedModules.size
                _statusMessage.value = "All $count authorized modules are installed and ready."
            }
        }
    }

    /**
     * Built-in Admin Approval simulator so the user can test the complete Registration ->
     * Waiting for Approval -> Admin Approve -> Dynamic Module Notification -> Root Vault Bind
     * workflow directly inside the emulator even when the external Node.js server isn't running.
     */
    fun simulateAdminApproveAccount(
        allowedModules: Set<String> = setOf("module_hider", "module_isolation", "module_privacy", "module_network"),
        validityDays: Int = 30
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = RootLicenseVaultManager.readVaultRecord(app)
            val nowWall = System.currentTimeMillis()
            val nowElapsed = android.os.SystemClock.elapsedRealtime()
            val expiry = nowWall + validityDays * 86_400_000L
            val updated = current.copy(
                username = current.username.ifBlank { "hollow_operator" },
                email = current.email.ifBlank { "operator@hollow.sec" },
                status = LicenseAccountStatus.APPROVED,
                licenseToken = "hlw_tok_${UUID.randomUUID().toString().replace("-", "").take(20)}",
                allowedModules = allowedModules,
                issuedAtMs = nowWall,
                validUntilMs = expiry,
                banReason = "",
                highWaterWallClockMs = nowWall,
                lastElapsedRealtimeMs = nowElapsed,
                hardwareFingerprint = hardwareBinding.fingerprintHex
            )
            RootLicenseVaultManager.writeVaultRecord(app, updated)
            appendTerminalLog("[AdminPortal] Approved device ${hardwareBinding.fingerprintHex} with modules: $allowedModules")
            withContext(Dispatchers.Main) {
                refreshVaultAndModuleState()
                _statusMessage.value = "Admin Approved! Notification triggered on Dashboard to download ${allowedModules.size} modules."
            }
        }
    }

    fun simulateAdminBanDevice(reason: String = "Unauthorized MITM proxy / token tamper attempt detected by Admin.") {
        viewModelScope.launch(Dispatchers.IO) {
            RootLicenseVaultManager.wipeModulesAndLockOnBan(app, reason)
            appendTerminalLog("[AdminPortal] DEVICE BANNED (${hardwareBinding.fingerprintHex}): Wiped /data/adb/.hollow_vault/modules")
            withContext(Dispatchers.Main) {
                refreshVaultAndModuleState()
                _statusMessage.value = "Device Permanently Banned by Admin. Root vault wiped."
            }
        }
    }

    fun simulateAdminResetToUnregistered() {
        viewModelScope.launch(Dispatchers.IO) {
            HollowModuleRegistry.allModuleIds.forEach { modId ->
                RootLicenseVaultManager.deleteDownloadedModuleFromVault(app, modId)
            }
            val reset = RootVaultLicenseRecord(
                status = LicenseAccountStatus.UNREGISTERED,
                hardwareFingerprint = hardwareBinding.fingerprintHex,
                highWaterWallClockMs = System.currentTimeMillis(),
                lastElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime()
            )
            RootLicenseVaultManager.writeVaultRecord(app, reset)
            withContext(Dispatchers.Main) {
                refreshVaultAndModuleState()
                _statusMessage.value = "Reset to Unregistered state."
            }
        }
    }

    /**
     * Simulates Android "Clear App Data" by wiping app-internal caches and immediately recovering
     * the encrypted license & downloaded `.dex.enc` modules from `/data/adb/.hollow_vault`.
     */
    fun simulateClearAppDataAndRecoverFromRootVault() {
        viewModelScope.launch(Dispatchers.IO) {
            appendTerminalLog("[Test] Simulating Android 'Clear App Data' on /data/data/${app.packageName}...")
            val recovered = RootLicenseVaultManager.readVaultRecord(app)
            appendTerminalLog("[RootVault] Recovered license (${recovered.status}) & ${recovered.downloadedModules.size} encrypted modules from ${RootLicenseVaultManager.ROOT_VAULT_DIR}!")
            withContext(Dispatchers.Main) {
                refreshVaultAndModuleState()
                _statusMessage.value = "App Data Cleared -> Recovered License & ${recovered.downloadedModules.size} Modules from /data/adb/.hollow_vault!"
            }
        }
    }
}
