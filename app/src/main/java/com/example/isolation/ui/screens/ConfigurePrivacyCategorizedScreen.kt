package com.example.isolation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.isolation.model.DeviceProfilePreset
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.model.PrivacyProfile
import com.example.isolation.model.VpnProxyPresets
import com.example.isolation.pkg.InstalledAppInfo
import com.example.isolation.ui.components.ContentCard
import com.example.isolation.ui.components.ListRow
import com.example.isolation.ui.components.MonoDataText
import com.example.isolation.ui.components.PrimaryButton
import com.example.isolation.ui.components.SecondaryButton
import com.example.isolation.ui.components.SectionHeader
import com.example.isolation.ui.components.StatusIndicator
import com.example.isolation.ui.components.rememberAppIconBitmap
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.roundToInt
import kotlin.random.Random

private data class CityLocationPreset(
    val city: String,
    val country: String,
    val lat: Double,
    val lon: Double,
    val tz: String,
    val locale: String
)

private val QUICK_CITIES = listOf(
    CityLocationPreset("New York", "USA", 40.7128, -74.0060, "America/New_York", "en-US"),
    CityLocationPreset("Zurich", "CH", 47.3769, 8.5417, "Europe/Zurich", "de-CH"),
    CityLocationPreset("London", "UK", 51.5074, -0.1278, "Europe/London", "en-GB"),
    CityLocationPreset("Tokyo", "Japan", 35.6762, 139.6503, "Asia/Tokyo", "ja-JP"),
    CityLocationPreset("Frankfurt", "Germany", 50.1109, 8.6821, "Europe/Berlin", "de-DE"),
    CityLocationPreset("Singapore", "SG", 1.3521, 103.8198, "Asia/Singapore", "en-SG"),
    CityLocationPreset("Dubai", "UAE", 25.2048, 55.2708, "Asia/Dubai", "en-AE"),
    CityLocationPreset("Sydney", "AU", -33.8688, 151.2093, "Australia/Sydney", "en-AU"),
    CityLocationPreset("Paris", "FR", 48.8566, 2.3522, "Europe/Paris", "fr-FR"),
    CityLocationPreset("Toronto", "CA", 43.6532, -79.3832, "America/Toronto", "en-CA")
)

private enum class PrivacyStudioTab(val label: String) {
    ALL("All Features"),
    PROFILES_MODELS("Profiles & Models"),
    VPN_PROXY("VPN & Proxy"),
    HARDWARE_IDS("Hardware & IDs"),
    BROWSER_WEBVIEW("Browser & WebView"),
    STEALTH_SYSTEM("Stealth & System"),
    SENSORS_BATTERY("Sensors & Battery"),
    GPS_DEADMAN("GPS & Deadman")
}

/**
 * Per-App Privacy, Hardware Identity, Browser Fingerprint & VPN Studio.
 * Uses the unified design system components, typography tokens, and Indigo primary accent.
 */
@Composable
fun ConfigurePrivacyCategorizedScreen(
    app: InstalledAppInfo,
    viewModel: IsoVaultViewModel,
    onBack: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    val settings by viewModel.settings.collectAsState()
    val privacyProfiles by viewModel.privacyProfiles.collectAsState()
    val deadmanIncidents by viewModel.deadmanBlockedIncidents.collectAsState()
    val realHw = viewModel.realHardware

    var activeTab by remember { mutableStateOf(PrivacyStudioTab.ALL) }
    var showSaveProfileDialog by remember { mutableStateOf(false) }
    var showImportProxyDialog by remember { mutableStateOf(false) }

    // Master Spoof Toggle & Device Preset
    var isSpoofEnabled by remember(app.packageName) { mutableStateOf(app.isIdentitySpoofed) }
    var selectedPreset by remember(app.packageName) {
        mutableStateOf(app.deviceProfilePreset)
    }
    var selectedProfileId by remember(app.packageName) { mutableStateOf(app.privacyProfileId) }

    // Complete Hardware & Build Props
    var brand by remember(app.packageName) {
        mutableStateOf(app.spoofBrand.ifBlank { realHw.brand })
    }
    var manufacturer by remember(app.packageName) {
        mutableStateOf(app.spoofManufacturer.ifBlank { realHw.manufacturer })
    }
    var model by remember(app.packageName) {
        mutableStateOf(app.spoofModel.ifBlank { realHw.model })
    }
    var deviceCode by remember(app.packageName) {
        mutableStateOf(app.spoofDevice.ifBlank { realHw.device })
    }
    var product by remember(app.packageName) {
        mutableStateOf(app.spoofProduct.ifBlank { realHw.product })
    }
    var board by remember(app.packageName) {
        mutableStateOf(app.spoofBoard.ifBlank { realHw.board })
    }
    var hardware by remember(app.packageName) {
        mutableStateOf(app.spoofHardware.ifBlank { realHw.hardware })
    }
    var bootloader by remember(app.packageName) {
        mutableStateOf(app.spoofBootloader.ifBlank { realHw.bootloader })
    }
    var fingerprint by remember(app.packageName) {
        mutableStateOf(app.spoofFingerprint.ifBlank { realHw.fingerprint })
    }

    // Unique Device Identifiers & SIM/Carrier
    var androidId by remember(app.packageName) {
        mutableStateOf(app.spoofAndroidId.ifBlank { realHw.androidId })
    }
    var imei by remember(app.packageName) {
        mutableStateOf(app.spoofImei.ifBlank { IsolationRuleManager.generateLuhnImei() })
    }
    var adId by remember(app.packageName) {
        mutableStateOf(app.spoofAdvertisingId.ifBlank { IsolationRuleManager.generateAdvertisingId() })
    }
    var macAddress by remember(app.packageName) {
        mutableStateOf(app.spoofMacAddress.ifBlank { IsolationRuleManager.generateMacAddress() })
    }
    var serialNumber by remember(app.packageName) {
        mutableStateOf(app.spoofSerial.ifBlank { realHw.serial })
    }
    var carrierName by remember(app.packageName) {
        mutableStateOf(app.spoofCarrierName.ifBlank { realHw.carrierName })
    }
    var simOperator by remember(app.packageName) {
        mutableStateOf(app.spoofSimOperator.ifBlank { realHw.simOperator })
    }

    // GPU, CPU, RAM & Display Specs
    var gpuVendor by remember(app.packageName) {
        mutableStateOf(app.spoofGpuVendor.ifBlank { realHw.glVendor })
    }
    var gpuRenderer by remember(app.packageName) {
        mutableStateOf(app.spoofGpuRenderer.ifBlank { realHw.glRenderer })
    }
    var cpuCoresText by remember(app.packageName) {
        mutableStateOf((if (app.spoofCpuCores > 0) app.spoofCpuCores else realHw.cpuCores).toString())
    }
    var ramGbText by remember(app.packageName) {
        mutableStateOf((if (app.spoofRamGb > 0) app.spoofRamGb else realHw.ramGb).toString())
    }
    var screenResolution by remember(app.packageName) {
        mutableStateOf(app.spoofScreenResolution.ifBlank { realHw.screenResolution })
    }
    var screenDpiText by remember(app.packageName) {
        mutableStateOf((if (app.spoofScreenDpi > 0) app.spoofScreenDpi else realHw.screenDpi).toString())
    }

    // Chrome / Browser & WebView Deep Anti-Fingerprinting
    var spoofBrowserUa by remember(app.packageName) { mutableStateOf(app.spoofBrowserUserAgent) }
    var customUserAgent by remember(app.packageName) {
        mutableStateOf(app.customUserAgent.ifBlank { realHw.userAgent })
    }
    var spoofGpuFingerprint by remember(app.packageName) { mutableStateOf(app.spoofGpuFingerprint) }
    var spoofCanvasNoise by remember(app.packageName) { mutableStateOf(app.spoofCanvasNoise) }
    var spoofAudioContext by remember(app.packageName) { mutableStateOf(app.spoofAudioContext) }
    var spoofWebRtc by remember(app.packageName) { mutableStateOf(app.spoofWebRtc) }
    var spoofClientHints by remember(app.packageName) { mutableStateOf(app.spoofClientHints) }
    var spoofFontEnumeration by remember(app.packageName) { mutableStateOf(app.spoofFontEnumeration) }
    var spoofDrmWidevine by remember(app.packageName) { mutableStateOf(app.spoofDrmWidevine) }

    // System Stealth, Environment & Anti-Detection Shields
    var hideRootAndXposed by remember(app.packageName) { mutableStateOf(app.hideRootAndXposed) }
    var hideAdbAndDev by remember(app.packageName) { mutableStateOf(app.hideAdbAndDevOptions) }
    var hideVpnAndProxy by remember(app.packageName) { mutableStateOf(app.hideVpnAndProxy) }
    var knoxWarrantyBitSpoof by remember(app.packageName) { mutableStateOf(app.knoxWarrantyBitSpoof) }
    var hideOtherApps by remember(app.packageName) { mutableStateOf(app.hideOtherApps) }
    var blockClipboard by remember(app.packageName) { mutableStateOf(app.blockClipboardRead) }
    var preventScreenshots by remember(app.packageName) { mutableStateOf(app.preventScreenshots) }
    var disableLogcatLogging by remember(app.packageName) { mutableStateOf(app.disableLogcatLogging) }
    var disableAllNetworking by remember(app.packageName) { mutableStateOf(app.disableAllNetworking) }

    // Sensors, Battery & Radio Telemetry
    var anonymizeSensors by remember(app.packageName) { mutableStateOf(app.anonymizeSensors) }
    var sensorBiasNeutralizer by remember(app.packageName) { mutableStateOf(app.sensorBiasNeutralizer) }
    var fakeBatteryEnabled by remember(app.packageName) { mutableStateOf(app.fakeBatteryEnabled) }
    var fakeBatteryLevel by remember(app.packageName) {
        mutableFloatStateOf(app.fakeBatteryLevel.coerceIn(5, 100).toFloat())
    }
    var blockCellTracking by remember(app.packageName) { mutableStateOf(app.blockCellTowerTracking) }
    var spoofWifiBssid by remember(app.packageName) { mutableStateOf(app.spoofWifiBssid) }

    // Per-App VPN / Proxy (V2Ray / VLESS / VMess / Shadowsocks / SOCKS5 / HTTP)
    var vpnProxyEnabled by remember(app.packageName) { mutableStateOf(app.vpnProxyEnabled) }
    var proxyServerId by remember(app.packageName) {
        mutableStateOf(app.proxyServerId.ifBlank { settings.activeProxyServerId })
    }
    var requireVpnForNetwork by remember(app.packageName) { mutableStateOf(app.requireVpnForNetwork) }
    var matchTimezoneWithIp by remember(app.packageName) { mutableStateOf(app.matchTimezoneWithIp) }
    var webViewProxyEnabled by remember(app.packageName) { mutableStateOf(app.webViewProxyEnabled) }
    var trustAllCerts by remember(app.packageName) { mutableStateOf(app.trustAllCerts) }

    // GPS Location, Timezone & Locale
    var spoofLocationEnabled by remember(app.packageName) { mutableStateOf(app.spoofLocationEnabled) }
    var latitudeText by remember(app.packageName) { mutableStateOf(app.spoofLatitude.toString()) }
    var longitudeText by remember(app.packageName) { mutableStateOf(app.spoofLongitude.toString()) }
    var hideMockLocation by remember(app.packageName) { mutableStateOf(app.hideMockLocation) }
    var autoSyncTimezone by remember(app.packageName) { mutableStateOf(app.autoSyncTimezone) }
    var timezoneId by remember(app.packageName) {
        mutableStateOf(app.spoofTimezoneId.ifBlank { realHw.timezoneId })
    }
    var localeTag by remember(app.packageName) {
        mutableStateOf(app.spoofLocaleTag.ifBlank { realHw.localeTag })
    }

    // Deadman Switch
    var deadmanSwitchEnabled by remember(app.packageName) { mutableStateOf(app.deadmanSwitchEnabled) }

    val allProxyNodes = remember(settings.customProxyServers) {
        settings.customProxyServers + VpnProxyPresets.defaultServers
    }
    val selectedProxyNode = remember(proxyServerId, allProxyNodes) {
        VpnProxyPresets.findById(proxyServerId, settings.customProxyServers)
    }

    fun applyPresetValues(preset: DeviceProfilePreset) {
        selectedPreset = preset
        isSpoofEnabled = true
        if (preset == DeviceProfilePreset.REAL_HARDWARE) {
            brand = realHw.brand
            manufacturer = realHw.manufacturer
            model = realHw.model
            deviceCode = realHw.device
            product = realHw.product
            board = realHw.board
            hardware = realHw.hardware
            bootloader = realHw.bootloader
            fingerprint = realHw.fingerprint
            gpuVendor = realHw.glVendor
            gpuRenderer = realHw.glRenderer
        } else {
            brand = preset.brand
            manufacturer = preset.manufacturer
            model = preset.model
            deviceCode = preset.device
            product = preset.product
            board = preset.board
            hardware = preset.board
            fingerprint = preset.fingerprint
            gpuVendor = preset.glVendor
            gpuRenderer = preset.glRenderer
            customUserAgent = "Mozilla/5.0 (Linux; Android 15; ${preset.model}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.200 Mobile Safari/537.36"
        }
    }

    fun applyReusableProfile(profile: PrivacyProfile) {
        selectedProfileId = profile.id
        isSpoofEnabled = true
        if (profile.spoofAndroidId) androidId = IsolationRuleManager.generateAndroidId()
        if (profile.spoofImei) imei = IsolationRuleManager.generateLuhnImei()
        if (profile.spoofAdvertisingId) adId = IsolationRuleManager.generateAdvertisingId()
        if (profile.spoofMacAddress) macAddress = IsolationRuleManager.generateMacAddress()
        if (profile.spoofSerial) serialNumber = IsolationRuleManager.generateSerial()
        spoofDrmWidevine = profile.spoofDrmWidevine
        spoofGpuFingerprint = profile.spoofGpuFingerprint
        gpuVendor = profile.gpuVendor
        gpuRenderer = profile.gpuRenderer
        spoofCanvasNoise = profile.spoofCanvasNoise
        spoofAudioContext = profile.spoofAudioContext
        screenResolution = profile.spoofScreenResolution
        screenDpiText = profile.spoofScreenDpi.toString()
        anonymizeSensors = profile.anonymizeSensors
        sensorBiasNeutralizer = profile.sensorBiasNeutralizer
        fakeBatteryEnabled = profile.fakeBatteryEnabled
        fakeBatteryLevel = profile.fakeBatteryLevel.toFloat()
        spoofWebRtc = profile.spoofWebRtc
        blockCellTracking = profile.blockCellTowerTracking
        spoofWifiBssid = profile.spoofWifiBssid
        hideVpnAndProxy = profile.hideVpnAndProxy
        hideRootAndXposed = profile.hideRootAndXposed
        hideAdbAndDev = profile.hideAdbAndDevOptions
        blockClipboard = profile.blockClipboardRead
        preventScreenshots = profile.preventScreenshots
        disableLogcatLogging = profile.disableLogcatLogging
        autoSyncTimezone = profile.autoSyncTimezone
        spoofBrowserUa = profile.spoofBrowserUserAgent
        deadmanSwitchEnabled = profile.deadmanSwitchEnabled
    }

    fun randomizeEverything() {
        val presets = listOf(
            DeviceProfilePreset.PIXEL_9_PRO,
            DeviceProfilePreset.SAMSUNG_S24_ULTRA,
            DeviceProfilePreset.ONEPLUS_12,
            DeviceProfilePreset.XIAOMI_14_ULTRA,
            DeviceProfilePreset.NOTHING_PHONE_2
        )
        val picked = presets.random()
        applyPresetValues(picked)
        androidId = IsolationRuleManager.generateAndroidId()
        imei = IsolationRuleManager.generateLuhnImei()
        adId = IsolationRuleManager.generateAdvertisingId()
        macAddress = IsolationRuleManager.generateMacAddress()
        serialNumber = IsolationRuleManager.generateSerial()
        val (rndCarrier, rndMccMnc) = IsolationRuleManager.generateRandomCarrier()
        carrierName = rndCarrier
        simOperator = rndMccMnc
        val (rndGpuV, rndGpuR) = IsolationRuleManager.generateRandomGpu()
        gpuVendor = rndGpuV
        gpuRenderer = rndGpuR
        val (rndRes, rndDpi) = IsolationRuleManager.generateRandomScreen()
        screenResolution = rndRes
        screenDpiText = rndDpi.toString()
        cpuCoresText = listOf("8", "10", "12").random()
        ramGbText = listOf("8", "12", "16").random()
        fakeBatteryEnabled = true
        fakeBatteryLevel = Random.nextInt(48, 96).toFloat()
    }

    fun saveAllChanges(closeAfterSave: Boolean = false) {
        val lat = latitudeText.toDoubleOrNull() ?: 40.7128
        val lon = longitudeText.toDoubleOrNull() ?: -74.0060
        val cpuCores = cpuCoresText.toIntOrNull()?.coerceIn(2, 16) ?: 8
        val ramGb = ramGbText.toIntOrNull()?.coerceIn(2, 32) ?: 8
        val dpi = screenDpiText.toIntOrNull()?.coerceIn(160, 720) ?: 420

        viewModel.saveAppIdentitySpoofConfig(
            appInfo = app,
            enabled = isSpoofEnabled,
            preset = selectedPreset,
            brand = brand.trim(),
            model = model.trim(),
            device = deviceCode.trim(),
            androidId = androidId.trim(),
            imei = imei.trim(),
            advertisingId = adId.trim(),
            macAddress = macAddress.trim(),
            carrierName = carrierName.trim(),
            simOperator = simOperator.trim(),
            serial = serialNumber.trim(),
            hideRootAndXposed = hideRootAndXposed,
            hideAdbAndDevOptions = hideAdbAndDev,
            hideVpnAndProxy = hideVpnAndProxy,
            blockClipboardRead = blockClipboard,
            spoofLocationEnabled = spoofLocationEnabled,
            spoofLatitude = lat,
            spoofLongitude = lon,
            hideMockLocation = hideMockLocation,
            preventScreenshots = preventScreenshots,
            hideOtherApps = hideOtherApps,
            disableLogcatLogging = disableLogcatLogging,
            fakeBatteryEnabled = fakeBatteryEnabled,
            fakeBatteryLevel = fakeBatteryLevel.roundToInt(),
            knoxWarrantyBitSpoof = knoxWarrantyBitSpoof,
            disableAllNetworking = disableAllNetworking,
            spoofGpuFingerprint = spoofGpuFingerprint,
            spoofBrowserUserAgent = spoofBrowserUa,
            anonymizeSensors = anonymizeSensors,
            blockCellTowerTracking = blockCellTracking,
            autoSyncTimezone = autoSyncTimezone,
            spoofManufacturer = manufacturer.trim(),
            spoofProduct = product.trim(),
            spoofBoard = board.trim(),
            spoofHardware = hardware.trim(),
            spoofBootloader = bootloader.trim(),
            spoofFingerprint = fingerprint.trim(),
            spoofGpuVendor = gpuVendor.trim(),
            spoofGpuRenderer = gpuRenderer.trim(),
            spoofCpuCores = cpuCores,
            spoofRamGb = ramGb,
            spoofScreenResolution = screenResolution.trim(),
            spoofScreenDpi = dpi,
            spoofTimezoneId = timezoneId.trim(),
            spoofLocaleTag = localeTag.trim(),
            customUserAgent = customUserAgent.trim(),
            spoofWebRtc = spoofWebRtc,
            spoofCanvasNoise = spoofCanvasNoise,
            spoofAudioContext = spoofAudioContext,
            spoofClientHints = spoofClientHints,
            spoofFontEnumeration = spoofFontEnumeration,
            vpnProxyEnabled = vpnProxyEnabled,
            proxyServerId = proxyServerId,
            requireVpnForNetwork = requireVpnForNetwork,
            matchTimezoneWithIp = matchTimezoneWithIp,
            webViewProxyEnabled = webViewProxyEnabled,
            trustAllCerts = trustAllCerts,
            privacyProfileId = selectedProfileId,
            deadmanSwitchEnabled = deadmanSwitchEnabled,
            spoofDrmWidevine = spoofDrmWidevine,
            sensorBiasNeutralizer = sensorBiasNeutralizer,
            spoofWifiBssid = spoofWifiBssid
        )
        if (closeAfterSave) {
            onBack()
        }
    }

    val appUid = remember(app.packageName) {
        (app.packageName.hashCode() and 0x7FFF) + 10000
    }
    val isAppBlackholed = remember(deadmanIncidents, app.packageName, appUid) {
        deadmanIncidents.any { it.packageName == app.packageName || it.appUid == appUid }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(horizontal = 16.dp)
            .testTag("configure_privacy_screen")
    ) {
        // Top App Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = scheme.surfaceContainerLow,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onBack() }
                        .testTag("btn_back_from_privacy_config")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = scheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = app.appName,
                        style = typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    MonoDataText(
                        text = app.packageName,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    text = "Save Profile",
                    onClick = { showSaveProfileDialog = true }
                )
                PrimaryButton(
                    text = "Apply",
                    icon = Icons.Default.Save,
                    onClick = { saveAllChanges(closeAfterSave = true) },
                    modifier = Modifier.testTag("btn_save_privacy_config")
                )
            }
        }

        // Category Jump Filter Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrivacyStudioTab.entries.forEach { tab ->
                val selected = activeTab == tab
                FilterChip(
                    selected = selected,
                    onClick = { activeTab = tab },
                    label = {
                        Text(
                            text = tab.label,
                            style = typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    shape = shapes.extraSmall,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = scheme.primaryContainer,
                        selectedLabelColor = scheme.onPrimaryContainer
                    )
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. App Identity Hero Card (Always Visible)
            item {
                ContentCard(
                    shape = shapes.large,
                    borderColor = scheme.primary
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            val iconBitmap = rememberAppIconBitmap(app.packageName, app.icon)
                            if (iconBitmap != null) {
                                Image(
                                    bitmap = iconBitmap,
                                    contentDescription = app.appName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(shapes.small)
                                )
                            } else {
                                Surface(
                                    color = scheme.primary.copy(alpha = 0.12f),
                                    shape = shapes.small,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Android,
                                            contentDescription = null,
                                            tint = scheme.primary
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                StatusIndicator(
                                    label = if (isSpoofEnabled) "Privacy Cloaking Active" else "Real Device Identity",
                                    color = if (isSpoofEnabled) scheme.secondary else scheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$brand $model",
                                    style = typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurface
                                )
                                MonoDataText(
                                    text = "AID ${androidId.take(8)}… · ${if (vpnProxyEnabled) selectedProxyNode.protocol.badge else "Direct"}",
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isSpoofEnabled,
                            onCheckedChange = { isSpoofEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = scheme.onPrimary,
                                checkedTrackColor = scheme.primary
                            ),
                            modifier = Modifier.testTag("switch_master_spoof")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PrimaryButton(
                            text = "Randomize All",
                            icon = Icons.Default.AutoFixHigh,
                            onClick = { randomizeEverything() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_one_tap_randomize")
                        )
                        SecondaryButton(
                            text = "Load Real Phone",
                            icon = Icons.Default.RestartAlt,
                            onClick = {
                                applyPresetValues(DeviceProfilePreset.REAL_HARDWARE)
                                androidId = realHw.androidId
                                serialNumber = realHw.serial
                                carrierName = realHw.carrierName
                                simOperator = realHw.simOperator
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_reset_real_hardware")
                        )
                    }
                }
            }

            // 2. Reusable Privacy Profiles & Phone Model Presets
            if (activeTab == PrivacyStudioTab.ALL || activeTab == PrivacyStudioTab.PROFILES_MODELS) {
                item {
                    ContentCard {
                        SectionHeader(
                            title = "Reusable Privacy Profiles",
                            subtitle = "Apply a complete anti-tracking template in 1 tap"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            privacyProfiles.forEach { profile ->
                                val isSelected = selectedProfileId == profile.id
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) scheme.primaryContainer.copy(alpha = 0.35f) else scheme.surfaceContainerLow
                                    ),
                                    shape = shapes.medium,
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) scheme.primary else scheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .width(192.dp)
                                        .clip(shapes.medium)
                                        .clickable { applyReusableProfile(profile) }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = profile.name,
                                                style = typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) scheme.primary else scheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = scheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = profile.description,
                                            style = typography.bodySmall,
                                            color = scheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    ContentCard {
                        SectionHeader(
                            title = "Phone Model Preset",
                            subtitle = "Choose a preset phone model or start from your real hardware"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DeviceProfilePreset.entries.forEach { preset ->
                                val isSelected = selectedPreset == preset
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) scheme.primaryContainer.copy(alpha = 0.35f) else scheme.surfaceContainerLow
                                    ),
                                    shape = shapes.medium,
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) scheme.primary else scheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .width(160.dp)
                                        .clip(shapes.medium)
                                        .clickable { applyPresetValues(preset) }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = preset.label,
                                                style = typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) scheme.primary else scheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = scheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${preset.brand} · ${preset.model}",
                                            style = typography.bodySmall,
                                            color = scheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Per-App VPN / Proxy (V2Ray / VLESS / VMess / Shadowsocks / SOCKS5 / HTTP)
            if (activeTab == PrivacyStudioTab.ALL || activeTab == PrivacyStudioTab.VPN_PROXY) {
                item {
                    ContentCard {
                        SectionHeader(
                            title = "Per-App VPN & Proxy Routing",
                            subtitle = "Route this app through V2Ray (VLESS/VMess), Shadowsocks, Trojan, or SOCKS5"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrivacyToggleRow(
                                title = "Enable Per-App VPN / Proxy Tunnel",
                                subtitle = "Routes ${app.appName}'s network traffic through the selected exit node",
                                checked = vpnProxyEnabled,
                                onCheckedChange = { vpnProxyEnabled = it }
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Select Exit Node (${allProxyNodes.size} available)",
                                    style = typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.onSurface
                                )
                                TextButton(onClick = { showImportProxyDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Import URI / Node",
                                        style = typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                allProxyNodes.forEach { node ->
                                    val selected = proxyServerId == node.id
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (selected) scheme.primaryContainer.copy(alpha = 0.35f) else scheme.surfaceContainerLow
                                        ),
                                        shape = shapes.small,
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (selected) scheme.primary else scheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .width(192.dp)
                                            .clip(shapes.small)
                                            .clickable {
                                                proxyServerId = node.id
                                                vpnProxyEnabled = true
                                                if (matchTimezoneWithIp) {
                                                    timezoneId = node.timezoneId
                                                    localeTag = node.localeTag
                                                }
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                StatusIndicator(
                                                    label = node.protocol.badge,
                                                    color = scheme.primary,
                                                    showDot = false
                                                )
                                                MonoDataText(
                                                    text = "${node.latencyMs} ms",
                                                    color = scheme.secondary
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = node.name,
                                                style = typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = scheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            MonoDataText(
                                                text = "${node.exitIp} · ${node.timezoneId}",
                                                color = scheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = scheme.outlineVariant
                            )

                            PrivacyToggleRow(
                                title = "Per-App VPN Kill Switch",
                                subtitle = "Block all internet traffic for this app if the VPN/Proxy tunnel disconnects",
                                checked = requireVpnForNetwork,
                                isDestructive = true,
                                onCheckedChange = { requireVpnForNetwork = it }
                            )
                            PrivacyToggleRow(
                                title = "Auto-Match Timezone & Locale with Exit IP",
                                subtitle = "Automatically sets IANA timezone (${selectedProxyNode.timezoneId}) & locale (${selectedProxyNode.localeTag})",
                                checked = matchTimezoneWithIp,
                                onCheckedChange = {
                                    matchTimezoneWithIp = it
                                    if (it) {
                                        timezoneId = selectedProxyNode.timezoneId
                                        localeTag = selectedProxyNode.localeTag
                                    }
                                }
                            )
                            PrivacyToggleRow(
                                title = "Route Embedded WebView Through Proxy",
                                subtitle = "Forces in-app Chromium WebViews and custom tabs through the proxy node",
                                checked = webViewProxyEnabled,
                                onCheckedChange = { webViewProxyEnabled = it }
                            )
                            PrivacyToggleRow(
                                title = "Trust Custom SSL/TLS Certificates",
                                subtitle = "Bypasses SSL pinning for local proxy debugging and traffic inspection",
                                checked = trustAllCerts,
                                onCheckedChange = { trustAllCerts = it }
                            )
                        }
                    }
                }
            }

            // 4. Complete Hardware Build Props, Device IDs, SIM & GPU/Screen Specs
            if (activeTab == PrivacyStudioTab.ALL || activeTab == PrivacyStudioTab.HARDWARE_IDS) {
                item {
                    ContentCard {
                        SectionHeader(
                            title = "Unique Hardware & SIM Identifiers",
                            subtitle = "Android ID, Luhn IMEI, Advertising ID, Wi-Fi MAC, Serial & Carrier"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            IdTextFieldWithRandomizer(
                                label = "Android ID (16-hex)",
                                value = androidId,
                                onValueChange = { androidId = it },
                                onRandomize = { androidId = IsolationRuleManager.generateAndroidId() }
                            )
                            IdTextFieldWithRandomizer(
                                label = "IMEI (Luhn-Verified 15-digit)",
                                value = imei,
                                onValueChange = { imei = it },
                                onRandomize = { imei = IsolationRuleManager.generateLuhnImei() }
                            )
                            IdTextFieldWithRandomizer(
                                label = "Google Advertising ID (GAID)",
                                value = adId,
                                onValueChange = { adId = it },
                                onRandomize = { adId = IsolationRuleManager.generateAdvertisingId() }
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(modifier = Modifier.weight(1f)) {
                                    IdTextFieldWithRandomizer(
                                        label = "Wi-Fi MAC Address",
                                        value = macAddress,
                                        onValueChange = { macAddress = it },
                                        onRandomize = { macAddress = IsolationRuleManager.generateMacAddress() }
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    IdTextFieldWithRandomizer(
                                        label = "Hardware Serial",
                                        value = serialNumber,
                                        onValueChange = { serialNumber = it },
                                        onRandomize = { serialNumber = IsolationRuleManager.generateSerial() }
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = carrierName,
                                    onValueChange = { carrierName = it },
                                    label = { Text("SIM Carrier") },
                                    singleLine = true,
                                    shape = shapes.small,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = simOperator,
                                    onValueChange = { simOperator = it },
                                    label = { Text("MCC/MNC") },
                                    singleLine = true,
                                    textStyle = typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                    shape = shapes.small,
                                    modifier = Modifier.weight(0.8f)
                                )
                                IconButton(
                                    onClick = {
                                        val (c, op) = IsolationRuleManager.generateRandomCarrier()
                                        carrierName = c
                                        simOperator = op
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Randomize Carrier",
                                        tint = scheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    ContentCard {
                        SectionHeader(
                            title = "Build Properties, GPU & Display Specs",
                            subtitle = "Fine-tune android.os.Build fields, OpenGL renderer, CPU cores, RAM & screen DPI"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = brand,
                                    onValueChange = {
                                        brand = it
                                        isSpoofEnabled = true
                                    },
                                    label = { Text("Brand (ro.product.brand)") },
                                    singleLine = true,
                                    shape = shapes.small,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = manufacturer,
                                    onValueChange = {
                                        manufacturer = it
                                        isSpoofEnabled = true
                                    },
                                    label = { Text("Manufacturer") },
                                    singleLine = true,
                                    shape = shapes.small,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = model,
                                    onValueChange = {
                                        model = it
                                        isSpoofEnabled = true
                                    },
                                    label = { Text("Model (ro.product.model)") },
                                    singleLine = true,
                                    shape = shapes.small,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = deviceCode,
                                    onValueChange = {
                                        deviceCode = it
                                        isSpoofEnabled = true
                                    },
                                    label = { Text("Device Codename") },
                                    singleLine = true,
                                    shape = shapes.small,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = product,
                                    onValueChange = { product = it },
                                    label = { Text("Product Name") },
                                    singleLine = true,
                                    shape = shapes.small,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = board,
                                    onValueChange = { board = it },
                                    label = { Text("Board Name") },
                                    singleLine = true,
                                    shape = shapes.small,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = hardware,
                                    onValueChange = { hardware = it },
                                    label = { Text("SoC Hardware (qcom/tensor)") },
                                    singleLine = true,
                                    shape = shapes.small,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = bootloader,
                                    onValueChange = { bootloader = it },
                                    label = { Text("Bootloader Version") },
                                    singleLine = true,
                                    textStyle = typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                    shape = shapes.small,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedTextField(
                                value = fingerprint,
                                onValueChange = { fingerprint = it },
                                label = { Text("Build Fingerprint (ro.build.fingerprint)") },
                                singleLine = true,
                                textStyle = typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                shape = shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = scheme.outlineVariant
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = gpuVendor,
                                    onValueChange = { gpuVendor = it },
                                    label = { Text("GPU Vendor") },
                                    singleLine = true,
                                    shape = shapes.small,
                                    modifier = Modifier.weight(0.8f)
                                )
                                OutlinedTextField(
                                    value = gpuRenderer,
                                    onValueChange = { gpuRenderer = it },
                                    label = { Text("OpenGL / Vulkan Renderer") },
                                    singleLine = true,
                                    shape = shapes.small,
                                    modifier = Modifier.weight(1.2f)
                                )
                                IconButton(
                                    onClick = {
                                        val (v, r) = IsolationRuleManager.generateRandomGpu()
                                        gpuVendor = v
                                        gpuRenderer = r
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Randomize GPU",
                                        tint = scheme.primary
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = cpuCoresText,
                                    onValueChange = { cpuCoresText = it },
                                    label = { Text("CPU Cores") },
                                    singleLine = true,
                                    shape = shapes.small,
                                    modifier = Modifier.weight(0.7f)
                                )
                                OutlinedTextField(
                                    value = ramGbText,
                                    onValueChange = { ramGbText = it },
                                    label = { Text("RAM (GB)") },
                                    singleLine = true,
                                    shape = shapes.small,
                                    modifier = Modifier.weight(0.7f)
                                )
                                OutlinedTextField(
                                    value = screenResolution,
                                    onValueChange = { screenResolution = it },
                                    label = { Text("Resolution") },
                                    singleLine = true,
                                    textStyle = typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                    shape = shapes.small,
                                    modifier = Modifier.weight(1.1f)
                                )
                                OutlinedTextField(
                                    value = screenDpiText,
                                    onValueChange = { screenDpiText = it },
                                    label = { Text("DPI") },
                                    singleLine = true,
                                    textStyle = typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                    shape = shapes.small,
                                    modifier = Modifier.weight(0.7f)
                                )
                                IconButton(
                                    onClick = {
                                        val (res, dpi) = IsolationRuleManager.generateRandomScreen()
                                        screenResolution = res
                                        screenDpiText = dpi.toString()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Randomize Screen",
                                        tint = scheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Chrome, Browser & WebView Deep Anti-Fingerprinting
            if (activeTab == PrivacyStudioTab.ALL || activeTab == PrivacyStudioTab.BROWSER_WEBVIEW) {
                item {
                    ContentCard {
                        SectionHeader(
                            title = "Browser, WebView & DRM Anti-Fingerprinting",
                            subtitle = "Defeat WebGL, Canvas, AudioContext, WebRTC, Client Hints & Widevine DRM tracking"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrivacyToggleRow(
                                title = "Spoof Browser & WebView User-Agent",
                                subtitle = "Aligns HTTP User-Agent and navigator.userAgent with the spoofed device model",
                                checked = spoofBrowserUa,
                                onCheckedChange = { spoofBrowserUa = it }
                            )
                            if (spoofBrowserUa) {
                                IdTextFieldWithRandomizer(
                                    label = "Custom User-Agent String",
                                    value = customUserAgent,
                                    onValueChange = { customUserAgent = it },
                                    onRandomize = {
                                        val chromeMinor = Random.nextInt(6700, 6850)
                                        val chromePatch = Random.nextInt(100, 240)
                                        customUserAgent = "Mozilla/5.0 (Linux; Android 15; $model) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.$chromeMinor.$chromePatch Mobile Safari/537.36"
                                    }
                                )
                            }
                            PrivacyToggleRow(
                                title = "Spoof GPU & WebGL Fingerprint",
                                subtitle = "Replaces UNMASKED_VENDOR_WEBGL and UNMASKED_RENDERER_WEBGL with $gpuRenderer",
                                checked = spoofGpuFingerprint,
                                onCheckedChange = { spoofGpuFingerprint = it }
                            )
                            PrivacyToggleRow(
                                title = "Canvas 2D Noise Injection",
                                subtitle = "Adds imperceptible deterministic pixel entropy to HTML5 Canvas readbacks",
                                checked = spoofCanvasNoise,
                                onCheckedChange = { spoofCanvasNoise = it }
                            )
                            PrivacyToggleRow(
                                title = "WebAudio AudioContext Entropy Shield",
                                subtitle = "Neutralizes oscillator and DynamicsCompressor audio fingerprinting",
                                checked = spoofAudioContext,
                                onCheckedChange = { spoofAudioContext = it }
                            )
                            PrivacyToggleRow(
                                title = "WebRTC Local & STUN IP Leak Shield",
                                subtitle = "Prevents WebRTC ICE candidates from exposing real LAN or cellular IP addresses",
                                checked = spoofWebRtc,
                                onCheckedChange = { spoofWebRtc = it }
                            )
                            PrivacyToggleRow(
                                title = "Spoof Sec-CH-UA Client Hints",
                                subtitle = "Synchronizes high-entropy Chromium Client Hints headers with $model",
                                checked = spoofClientHints,
                                onCheckedChange = { spoofClientHints = it }
                            )
                            PrivacyToggleRow(
                                title = "Block System Font Enumeration",
                                subtitle = "Prevents scripts from measuring installed system fonts to build a device hash",
                                checked = spoofFontEnumeration,
                                onCheckedChange = { spoofFontEnumeration = it }
                            )
                            PrivacyToggleRow(
                                title = "Spoof MediaDrm Widevine Device ID",
                                subtitle = "Randomizes the hardware-backed Widevine DRM identifier per app",
                                checked = spoofDrmWidevine,
                                onCheckedChange = { spoofDrmWidevine = it }
                            )
                        }
                    }
                }
            }

            // 6. System Stealth, Environment & Anti-Detection Shields
            if (activeTab == PrivacyStudioTab.ALL || activeTab == PrivacyStudioTab.STEALTH_SYSTEM) {
                item {
                    ContentCard {
                        SectionHeader(
                            title = "System Stealth & Environment Shields",
                            subtitle = "Hide Root, LSPosed, ADB, Knox bit, other installed apps, clipboard & network access"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrivacyToggleRow(
                                title = "Hide Root, Magisk, KernelSU & LSPosed",
                                subtitle = "Blocks detection of su binaries, root mounts, Xposed stacks, and Zygisk",
                                checked = hideRootAndXposed,
                                onCheckedChange = { hideRootAndXposed = it }
                            )
                            PrivacyToggleRow(
                                title = "Hide ADB & Developer Options",
                                subtitle = "Reports USB debugging, ADB, and developer settings as disabled",
                                checked = hideAdbAndDev,
                                onCheckedChange = { hideAdbAndDev = it }
                            )
                            PrivacyToggleRow(
                                title = "Hide VPN & Proxy Transport (tun0)",
                                subtitle = "Reports a clean residential Wi-Fi/5G interface instead of VPN transport flags",
                                checked = hideVpnAndProxy,
                                onCheckedChange = { hideVpnAndProxy = it }
                            )
                            PrivacyToggleRow(
                                title = "Spoof Samsung Knox Warranty Bit (0x0)",
                                subtitle = "Reports intact Knox warranty bit 0x0 and green Verified Boot state",
                                checked = knoxWarrantyBitSpoof,
                                onCheckedChange = { knoxWarrantyBitSpoof = it }
                            )
                            PrivacyToggleRow(
                                title = "Isolate Installed App List",
                                subtitle = "Prevents this app from scanning other installed apps on your phone",
                                checked = hideOtherApps,
                                onCheckedChange = { hideOtherApps = it }
                            )
                            PrivacyToggleRow(
                                title = "Block Background Clipboard Snooping",
                                subtitle = "Returns empty clipboard data when the app tries to read copied text/links",
                                checked = blockClipboard,
                                onCheckedChange = { blockClipboard = it }
                            )
                            PrivacyToggleRow(
                                title = "Bypass Screenshot Restrictions (FLAG_SECURE)",
                                subtitle = "Controls screenshot and screen-capture flags for this app window",
                                checked = preventScreenshots,
                                onCheckedChange = { preventScreenshots = it }
                            )
                            PrivacyToggleRow(
                                title = "Silence Logcat & Crash Telemetry",
                                subtitle = "Drops android.util.Log output from this app so SDKs cannot inspect logs",
                                checked = disableLogcatLogging,
                                onCheckedChange = { disableLogcatLogging = it }
                            )
                            PrivacyToggleRow(
                                title = "Complete Offline Firewall (Block All Network)",
                                subtitle = "Blocks all internet and socket connections for this app while keeping it functional",
                                checked = disableAllNetworking,
                                isDestructive = true,
                                onCheckedChange = { disableAllNetworking = it }
                            )
                        }
                    }
                }
            }

            // 7. Sensors, Battery & Radio Telemetry
            if (activeTab == PrivacyStudioTab.ALL || activeTab == PrivacyStudioTab.SENSORS_BATTERY) {
                item {
                    ContentCard {
                        SectionHeader(
                            title = "Sensors, Battery & Radio Entropy",
                            subtitle = "Prevent motion sensor fingerprinting, battery level tracking, cell tower & Wi-Fi BSSID leaks"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrivacyToggleRow(
                                title = "Anonymize Motion & Orientation Sensors",
                                subtitle = "Adds subtle micro-jitter to Gyroscope, Accelerometer & Magnetometer readings",
                                checked = anonymizeSensors,
                                onCheckedChange = { anonymizeSensors = it }
                            )
                            PrivacyToggleRow(
                                title = "Sensor Calibration Bias Neutralizer",
                                subtitle = "Removes factory MEMS hardware offset signatures used to link accounts across reinstalls",
                                checked = sensorBiasNeutralizer,
                                onCheckedChange = { sensorBiasNeutralizer = it }
                            )
                            PrivacyToggleRow(
                                title = "Block Cell Tower CID/LAC Tracking",
                                subtitle = "Hides neighboring GSM/LTE/5G cell tower IDs from location SDKs",
                                checked = blockCellTracking,
                                onCheckedChange = { blockCellTracking = it }
                            )
                            PrivacyToggleRow(
                                title = "Spoof Wi-Fi Router BSSID & Scan List",
                                subtitle = "Randomizes nearby Wi-Fi access point MAC addresses so indoor positioning cannot locate you",
                                checked = spoofWifiBssid,
                                onCheckedChange = { spoofWifiBssid = it }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = scheme.outlineVariant
                            )

                            PrivacyToggleRow(
                                title = "Spoof Battery Level & Charging Telemetry",
                                subtitle = "Reports a synthetic battery percentage (${fakeBatteryLevel.roundToInt()}%) to prevent cross-app correlation",
                                checked = fakeBatteryEnabled,
                                onCheckedChange = { fakeBatteryEnabled = it }
                            )

                            if (fakeBatteryEnabled) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BatteryChargingFull,
                                        contentDescription = null,
                                        tint = scheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    MonoDataText(
                                        text = "${fakeBatteryLevel.roundToInt()}%",
                                        color = scheme.primary,
                                        modifier = Modifier.width(48.dp)
                                    )
                                    Slider(
                                        value = fakeBatteryLevel,
                                        onValueChange = { fakeBatteryLevel = it },
                                        valueRange = 15f..100f,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 8. GPS Location, Timezone, Locale & Deadman Switch
            if (activeTab == PrivacyStudioTab.ALL || activeTab == PrivacyStudioTab.GPS_DEADMAN) {
                item {
                    ContentCard {
                        SectionHeader(
                            title = "Virtual GPS Location, Timezone & Locale",
                            subtitle = "Set virtual coordinates, 1-tap world cities, IANA timezone & language locale"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PrivacyToggleRow(
                                title = "Enable Virtual GPS Location",
                                subtitle = "Overrides GPS, FusedLocationProvider & Network location coordinates",
                                checked = spoofLocationEnabled,
                                onCheckedChange = { spoofLocationEnabled = it }
                            )

                            Text(
                                text = "Quick World City Presets",
                                style = typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                QUICK_CITIES.forEach { preset ->
                                    Surface(
                                        color = scheme.surfaceContainerLow,
                                        shape = shapes.extraSmall,
                                        border = BorderStroke(1.dp, scheme.outlineVariant),
                                        modifier = Modifier
                                            .clip(shapes.extraSmall)
                                            .clickable {
                                                spoofLocationEnabled = true
                                                latitudeText = preset.lat.toString()
                                                longitudeText = preset.lon.toString()
                                                timezoneId = preset.tz
                                                localeTag = preset.locale
                                            }
                                    ) {
                                        Text(
                                            text = "${preset.city}, ${preset.country}",
                                            style = typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = scheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = latitudeText,
                                    onValueChange = {
                                        latitudeText = it
                                        spoofLocationEnabled = true
                                    },
                                    label = { Text("Latitude") },
                                    singleLine = true,
                                    textStyle = typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                    shape = shapes.small,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = longitudeText,
                                    onValueChange = {
                                        longitudeText = it
                                        spoofLocationEnabled = true
                                    },
                                    label = { Text("Longitude") },
                                    singleLine = true,
                                    textStyle = typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                    shape = shapes.small,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        val baseLat = latitudeText.toDoubleOrNull() ?: 40.7128
                                        val baseLon = longitudeText.toDoubleOrNull() ?: -74.0060
                                        val jitterLat = ((baseLat + Random.nextDouble(-0.008, 0.008)) * 10000.0).roundToInt() / 10000.0
                                        val jitterLon = ((baseLon + Random.nextDouble(-0.008, 0.008)) * 10000.0).roundToInt() / 10000.0
                                        latitudeText = jitterLat.toString()
                                        longitudeText = jitterLon.toString()
                                        spoofLocationEnabled = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Add GPS Drift",
                                        tint = scheme.primary
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = timezoneId,
                                    onValueChange = { timezoneId = it },
                                    label = { Text("IANA Timezone") },
                                    singleLine = true,
                                    shape = shapes.small,
                                    modifier = Modifier.weight(1.2f)
                                )
                                OutlinedTextField(
                                    value = localeTag,
                                    onValueChange = { localeTag = it },
                                    label = { Text("Locale (e.g. en-US)") },
                                    singleLine = true,
                                    shape = shapes.small,
                                    modifier = Modifier.weight(0.8f)
                                )
                            }

                            PrivacyToggleRow(
                                title = "Hide Mock Location Flag",
                                subtitle = "Strips isFromMockProvider and mock location indicators from Location objects",
                                checked = hideMockLocation,
                                onCheckedChange = { hideMockLocation = it }
                            )
                            PrivacyToggleRow(
                                title = "Auto-Align Timezone with Virtual Location",
                                subtitle = "Keeps system clock timezone consistent with your spoofed region",
                                checked = autoSyncTimezone,
                                onCheckedChange = { autoSyncTimezone = it }
                            )
                        }
                    }
                }

                item {
                    ContentCard {
                        SectionHeader(
                            title = "Identity Leak Deadman Switch",
                            subtitle = "Kernel iptables UID kill-switch if the app attempts to bypass hooks or read real IDs"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PrivacyToggleRow(
                                title = "Enable Total Leak Deadman Switch",
                                subtitle = "Instantly terminates ${app.appName} and severs its network via iptables if an unhooked hardware read occurs",
                                checked = deadmanSwitchEnabled,
                                isDestructive = true,
                                onCheckedChange = { deadmanSwitchEnabled = it }
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SecondaryButton(
                                    text = "Test Leak Trigger",
                                    onClick = {
                                        viewModel.simulateIdentityLeak(app.packageName, app.appName, appUid)
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                SecondaryButton(
                                    text = if (isAppBlackholed) "Restore Severed Network" else "Reset UID Firewall",
                                    onClick = {
                                        viewModel.restoreAppNetwork(appUid)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveProfileDialog) {
        var profileName by remember { mutableStateOf("${app.appName} Preset") }
        var profileDesc by remember { mutableStateOf("$brand $model · $timezoneId") }
        AlertDialog(
            onDismissRequest = { showSaveProfileDialog = false },
            shape = shapes.extraLarge,
            title = {
                Text(
                    text = "Save as Reusable Privacy Profile",
                    style = typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Save these exact privacy, browser, sensor, and stealth settings so you can apply them to any other app in 1 tap.",
                        style = typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("Profile Name") },
                        singleLine = true,
                        shape = shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = profileDesc,
                        onValueChange = { profileDesc = it },
                        label = { Text("Short Description") },
                        singleLine = true,
                        shape = shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = "Save Profile",
                    onClick = {
                        val id = "custom_${System.currentTimeMillis()}"
                        val newProfile = PrivacyProfile(
                            id = id,
                            name = profileName.ifBlank { "Custom Profile" },
                            description = profileDesc.ifBlank { "Custom user privacy profile" },
                            isBuiltIn = false,
                            spoofAndroidId = true,
                            spoofImei = true,
                            spoofAdvertisingId = true,
                            spoofMacAddress = true,
                            spoofSerial = true,
                            spoofDrmWidevine = spoofDrmWidevine,
                            spoofGpuFingerprint = spoofGpuFingerprint,
                            gpuVendor = gpuVendor,
                            gpuRenderer = gpuRenderer,
                            spoofCanvasNoise = spoofCanvasNoise,
                            spoofAudioContext = spoofAudioContext,
                            spoofScreenResolution = screenResolution,
                            spoofScreenDpi = screenDpiText.toIntOrNull() ?: 420,
                            anonymizeSensors = anonymizeSensors,
                            sensorBiasNeutralizer = sensorBiasNeutralizer,
                            fakeBatteryEnabled = fakeBatteryEnabled,
                            fakeBatteryLevel = fakeBatteryLevel.roundToInt(),
                            spoofWebRtc = spoofWebRtc,
                            blockCellTowerTracking = blockCellTracking,
                            spoofWifiBssid = spoofWifiBssid,
                            hideVpnAndProxy = hideVpnAndProxy,
                            hideRootAndXposed = hideRootAndXposed,
                            hideAdbAndDevOptions = hideAdbAndDev,
                            blockClipboardRead = blockClipboard,
                            preventScreenshots = preventScreenshots,
                            disableLogcatLogging = disableLogcatLogging,
                            autoSyncTimezone = autoSyncTimezone,
                            spoofBrowserUserAgent = spoofBrowserUa,
                            deadmanSwitchEnabled = deadmanSwitchEnabled
                        )
                        viewModel.savePrivacyProfile(newProfile)
                        selectedProfileId = id
                        showSaveProfileDialog = false
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showSaveProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showImportProxyDialog) {
        var uriInput by remember { mutableStateOf("") }
        var importError by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showImportProxyDialog = false },
            shape = shapes.extraLarge,
            title = {
                Text(
                    text = "Import V2Ray / Proxy Node",
                    style = typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Paste a vless://, vmess://, ss://, trojan://, socks5:// URI or host:port#name to add and assign it to ${app.appName}.",
                        style = typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = uriInput,
                        onValueChange = {
                            uriInput = it
                            importError = false
                        },
                        label = { Text("vless:// or socks5://127.0.0.1:10808#MyNode") },
                        textStyle = typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        shape = shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (importError) {
                        Text(
                            text = "Could not parse proxy URI. Try vless://uuid@host:443#Name or host:port",
                            style = typography.bodySmall,
                            color = scheme.error
                        )
                    }
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = "Import & Select",
                    onClick = {
                        val parsed = VpnProxyPresets.parseUri(uriInput)
                        if (parsed != null) {
                            viewModel.addCustomProxyServer(parsed)
                            proxyServerId = parsed.id
                            vpnProxyEnabled = true
                            showImportProxyDialog = false
                        } else {
                            importError = true
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showImportProxyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PrivacyToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    isDestructive: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val trackColor = if (isDestructive) scheme.error else scheme.primary
    ListRow(
        title = title,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = scheme.onPrimary,
                    checkedTrackColor = trackColor
                )
            )
        }
    )
}

@Composable
private fun IdTextFieldWithRandomizer(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onRandomize: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        textStyle = typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
        shape = shapes.small,
        trailingIcon = {
            IconButton(onClick = onRandomize) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Randomize $label",
                    tint = scheme.primary
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}
