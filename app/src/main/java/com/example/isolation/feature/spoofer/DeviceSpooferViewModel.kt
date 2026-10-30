package com.example.isolation.feature.spoofer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.IsoVaultApp
import com.example.isolation.core.data.repository.DeviceSpoofRepository
import com.example.isolation.core.data.repository.IsolationRepository
import com.example.isolation.core.data.repository.SettingsRepository
import com.example.isolation.core.result.onError
import com.example.isolation.core.result.onSuccess
import com.example.isolation.core.result.runCatchingHollow
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.model.AppSettings
import com.example.isolation.model.DeviceProfilePreset
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.model.PrivacyProfile
import com.example.isolation.model.RealHardwareSnapshot
import com.example.isolation.model.VpnProxyPresets
import com.example.isolation.mount.MountManager
import com.example.isolation.pkg.AppPackageRepository
import com.example.isolation.pkg.InstalledAppInfo
import com.example.isolation.service.RootDeadmanWatchdog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Focused ViewModel for Device Spoofer & Anti-Fingerprinting Privacy Profiles.
 * Controls hardware identity masking, battery entropy, DRM/GPU parameters, and network privacy shields.
 */
class DeviceSpooferViewModel(
    application: Application,
    private val isolationRepository: IsolationRepository,
    private val deviceSpoofRepository: DeviceSpoofRepository,
    private val settingsRepository: SettingsRepository,
    private val packageRepository: AppPackageRepository
) : AndroidViewModel(application) {

    private val app = application as IsoVaultApp

    private val _rawInstalledApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val realHardware: RealHardwareSnapshot = deviceSpoofRepository.realHardware

    val privacyProfiles: StateFlow<List<PrivacyProfile>> = deviceSpoofRepository.privacyProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val configs: StateFlow<List<AppIsolationConfig>> = isolationRepository.getAllConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allInstalledApps: StateFlow<List<InstalledAppInfo>> = combine(
        _rawInstalledApps,
        configs
    ) { apps, cfgList ->
        val cfgMap = cfgList.associateBy { it.packageName }
        apps.map { item ->
            val cfg = cfgMap[item.packageName]
            item.copy(
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
                proxyServerId = cfg?.proxyServerId ?: settings.value.activeProxyServerId,
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

    init {
        loadInstalledApps()
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _rawInstalledApps.value = packageRepository.getInstalledApps(configs.value, includeSystemApps = true)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun savePrivacyProfile(profile: PrivacyProfile) {
        viewModelScope.launch {
            deviceSpoofRepository.savePrivacyProfile(profile)
                .onSuccess {
                    _statusMessage.value = "Saved privacy profile \"${profile.name}\"."
                }
                .onError { _, message ->
                    _statusMessage.value = "Failed to save profile: $message"
                }
        }
    }

    fun deletePrivacyProfile(profileId: String) {
        viewModelScope.launch {
            deviceSpoofRepository.deletePrivacyProfile(profileId)
                .onSuccess {
                    _statusMessage.value = "Deleted privacy profile."
                }
                .onError { _, message ->
                    _statusMessage.value = "Failed to delete profile: $message"
                }
        }
    }

    fun resetPrivacyProfilesToDefaults() {
        viewModelScope.launch {
            deviceSpoofRepository.resetPrivacyProfilesToDefaults()
                .onSuccess {
                    _statusMessage.value = "Privacy profiles reset to factory presets."
                }
                .onError { _, message ->
                    _statusMessage.value = "Reset failed: $message"
                }
        }
    }

    fun applyPrivacyProfileToApp(appInfo: InstalledAppInfo, profile: PrivacyProfile) {
        val currentPreset = if (appInfo.deviceProfilePreset == DeviceProfilePreset.REAL_HARDWARE) {
            DeviceProfilePreset.PIXEL_9_PRO
        } else {
            appInfo.deviceProfilePreset
        }

        saveAppIdentitySpoofConfig(
            appInfo = appInfo,
            enabled = true,
            preset = currentPreset,
            brand = appInfo.spoofBrand.ifBlank { currentPreset.brand },
            model = appInfo.spoofModel.ifBlank { currentPreset.model },
            device = appInfo.spoofDevice.ifBlank { currentPreset.device },
            androidId = if (profile.spoofAndroidId) IsolationRuleManager.generateAndroidId() else realHardware.androidId,
            imei = if (profile.spoofImei) IsolationRuleManager.generateLuhnImei() else "",
            advertisingId = if (profile.spoofAdvertisingId) IsolationRuleManager.generateAdvertisingId() else "",
            macAddress = if (profile.spoofMacAddress) IsolationRuleManager.generateMacAddress() else "",
            carrierName = appInfo.spoofCarrierName.ifBlank { realHardware.carrierName },
            simOperator = appInfo.spoofSimOperator.ifBlank { realHardware.simOperator },
            serial = if (profile.spoofSerial) IsolationRuleManager.generateSerial() else "",
            hideRootAndXposed = profile.hideRootAndXposed,
            hideAdbAndDevOptions = profile.hideAdbAndDevOptions,
            hideVpnAndProxy = profile.hideVpnAndProxy,
            blockClipboardRead = profile.blockClipboardRead,
            preventScreenshots = profile.preventScreenshots,
            disableLogcatLogging = profile.disableLogcatLogging,
            fakeBatteryEnabled = profile.fakeBatteryEnabled,
            fakeBatteryLevel = profile.fakeBatteryLevel,
            spoofGpuFingerprint = profile.spoofGpuFingerprint,
            spoofGpuVendor = profile.gpuVendor,
            spoofGpuRenderer = profile.gpuRenderer,
            spoofCanvasNoise = profile.spoofCanvasNoise,
            spoofAudioContext = profile.spoofAudioContext,
            spoofScreenResolution = profile.spoofScreenResolution,
            spoofScreenDpi = profile.spoofScreenDpi,
            anonymizeSensors = profile.anonymizeSensors,
            sensorBiasNeutralizer = profile.sensorBiasNeutralizer,
            blockCellTowerTracking = profile.blockCellTowerTracking,
            spoofWifiBssid = profile.spoofWifiBssid,
            spoofWebRtc = profile.spoofWebRtc,
            autoSyncTimezone = profile.autoSyncTimezone,
            spoofBrowserUserAgent = profile.spoofBrowserUserAgent,
            privacyProfileId = profile.id,
            deadmanSwitchEnabled = profile.deadmanSwitchEnabled,
            spoofDrmWidevine = profile.spoofDrmWidevine
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
        privacyProfileId: String = appInfo.privacyProfileId,
        deadmanSwitchEnabled: Boolean = appInfo.deadmanSwitchEnabled,
        spoofDrmWidevine: Boolean = appInfo.spoofDrmWidevine,
        sensorBiasNeutralizer: Boolean = appInfo.sensorBiasNeutralizer,
        spoofWifiBssid: Boolean = appInfo.spoofWifiBssid
    ) {
        viewModelScope.launch {
            val existing = isolationRepository.getConfigForPackage(appInfo.packageName)
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

            val activeNode = VpnProxyPresets.findById(proxyServerId.ifBlank { settings.value.activeProxyServerId }, settings.value.customProxyServers)
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
                proxyServerId = proxyServerId.ifBlank { settings.value.activeProxyServerId },
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
                proxyServerId = proxyServerId.ifBlank { settings.value.activeProxyServerId },
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

            isolationRepository.saveIdentitySpoofConfig(updated)
            withContext(Dispatchers.IO) {
                val allConfigs = isolationRepository.getAllConfigsSync()
                IsolationRuleManager.publishStealthTable(allConfigs, settings.value)
                MountManager.refreshLaunchersAndStealthCaches(app, refreshPlayStore = false, refreshSettings = false)
                runCatchingHollow("DeviceSpoofVM") {
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop ${appInfo.packageName}")).waitFor()
                }
            }
            loadInstalledApps()

            _statusMessage.value = if (enabled) {
                "Device identity applied for ${appInfo.appName} ($resolvedModel)."
            } else {
                "Privacy settings updated for ${appInfo.appName}."
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
            serial = "",
            hideRootAndXposed = false,
            hideAdbAndDevOptions = false,
            hideVpnAndProxy = false,
            blockClipboardRead = false
        )
    }

    fun restoreAppNetwork(appUid: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            RootDeadmanWatchdog.restoreAppNetwork(appUid)
            _statusMessage.value = "Restored iptables network traffic for UID $appUid."
        }
    }

    fun restoreAllNetworks() {
        viewModelScope.launch(Dispatchers.IO) {
            RootDeadmanWatchdog.restoreAllNetworks()
            _statusMessage.value = "Flushed all iptables deadman drop rules."
        }
    }

    fun simulateIdentityLeak(packageName: String, appName: String, uid: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            RootDeadmanWatchdog.simulateIdentityLeak(app, packageName, appName, uid)
            _statusMessage.value = "Triggered deadman switch leak simulation on $appName (UID $uid)."
        }
    }
}
