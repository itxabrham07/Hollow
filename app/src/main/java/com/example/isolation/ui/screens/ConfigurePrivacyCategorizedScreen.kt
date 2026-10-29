package com.example.isolation.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.model.DeviceProfilePreset
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.pkg.InstalledAppInfo
import com.example.isolation.ui.components.rememberAppIconBitmap
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.JakartaFontFamily
import java.util.UUID

private enum class AppPrivacySectionTab(val label: String, val icon: ImageVector) {
    HARDWARE_IDS("Hardware & IDs", Icons.Default.PhoneAndroid),
    DISPLAY_WEB("GPU & Web", Icons.Default.Memory),
    GPS_TIME("GPS & Time", Icons.Default.GpsFixed),
    SHIELDS_SENSORS("Shields & Sensors", Icons.Default.Shield),
    DEADMAN("Deadman", Icons.Default.Warning)
}

private data class CityTeleportPreset(
    val label: String,
    val code: String,
    val lat: String,
    val lng: String,
    val tz: String,
    val locale: String
)

private val CITY_TELEPORT_PRESETS = listOf(
    CityTeleportPreset("New York", "US", "40.7128", "-74.0060", "America/New_York", "en-US"),
    CityTeleportPreset("London", "GB", "51.5074", "-0.1278", "Europe/London", "en-GB"),
    CityTeleportPreset("Tokyo", "JP", "35.6762", "139.6503", "Asia/Tokyo", "ja-JP"),
    CityTeleportPreset("Zurich", "CH", "47.3769", "8.5417", "Europe/Zurich", "de-CH"),
    CityTeleportPreset("Frankfurt", "DE", "50.1109", "8.6821", "Europe/Berlin", "de-DE"),
    CityTeleportPreset("Singapore", "SG", "1.3521", "103.8198", "Asia/Singapore", "en-SG")
)

@Composable
fun ConfigurePrivacyCategorizedScreen(
    appInfo: InstalledAppInfo,
    viewModel: IsoVaultViewModel,
    onBack: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val profiles by viewModel.privacyProfiles.collectAsState()
    val deadmanIncidents by viewModel.deadmanBlockedIncidents.collectAsState()
    val realHw = viewModel.realHardware

    BackHandler { onBack() }

    var activeSectionTab by remember { mutableStateOf(AppPrivacySectionTab.HARDWARE_IDS) }

    // Master Identity Spoofing & Optional Profile (Defaults to "" = Manual / No Profile)
    var isSpoofEnabled by remember(appInfo.packageName) {
        mutableStateOf(appInfo.isIdentitySpoofed)
    }
    var selectedProfileId by remember(appInfo.packageName) {
        mutableStateOf(appInfo.privacyProfileId)
    }

    // Hardware Model Preset & Build Properties
    var selectedPreset by remember(appInfo.packageName) {
        mutableStateOf(
            if (appInfo.deviceProfilePreset == DeviceProfilePreset.REAL_HARDWARE)
                DeviceProfilePreset.PIXEL_9_PRO
            else appInfo.deviceProfilePreset
        )
    }
    var brand by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofBrand.ifBlank { selectedPreset.brand })
    }
    var manufacturer by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofManufacturer.ifBlank { selectedPreset.manufacturer })
    }
    var model by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofModel.ifBlank { selectedPreset.model })
    }
    var device by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofDevice.ifBlank { selectedPreset.device })
    }
    var product by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofProduct.ifBlank { selectedPreset.product })
    }
    var board by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofBoard.ifBlank { selectedPreset.board })
    }
    var hardware by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofHardware.ifBlank { selectedPreset.board })
    }
    var bootloader by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofBootloader.ifBlank { realHw.bootloader })
    }
    var fingerprint by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofFingerprint.ifBlank { selectedPreset.fingerprint })
    }

    // Device & Telecom Identifiers
    var androidId by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofAndroidId.ifBlank { IsolationRuleManager.generateAndroidId() })
    }
    var imei by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofImei.ifBlank { IsolationRuleManager.generateLuhnImei() })
    }
    var advertisingId by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofAdvertisingId.ifBlank { UUID.randomUUID().toString() })
    }
    var macAddress by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofMacAddress.ifBlank { IsolationRuleManager.generateMacAddress() })
    }
    var serial by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofSerial.ifBlank { IsolationRuleManager.generateSerial() })
    }
    var carrierName by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofCarrierName.ifBlank { realHw.carrierName })
    }
    var simOperator by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofSimOperator.ifBlank { realHw.simOperator })
    }
    var spoofDrmWidevine by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofDrmWidevine)
    }

    // GPU, Display & Browser Cloak
    var spoofGpuFingerprint by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofGpuFingerprint)
    }
    var gpuVendor by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofGpuVendor.ifBlank { selectedPreset.glVendor })
    }
    var gpuRenderer by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofGpuRenderer.ifBlank { selectedPreset.glRenderer })
    }
    var screenResolution by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofScreenResolution.ifBlank { realHw.screenResolution })
    }
    var screenDpiText by remember(appInfo.packageName) {
        mutableStateOf((if (appInfo.spoofScreenDpi > 0) appInfo.spoofScreenDpi else realHw.screenDpi).toString())
    }
    var cpuCoresText by remember(appInfo.packageName) {
        mutableStateOf((if (appInfo.spoofCpuCores > 0) appInfo.spoofCpuCores else realHw.cpuCores).toString())
    }
    var ramGbText by remember(appInfo.packageName) {
        mutableStateOf((if (appInfo.spoofRamGb > 0) appInfo.spoofRamGb else realHw.ramGb).toString())
    }
    var spoofCanvasNoise by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofCanvasNoise)
    }
    var spoofAudioContext by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofAudioContext)
    }
    var spoofWebRtc by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofWebRtc)
    }
    var spoofClientHints by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofClientHints)
    }
    var spoofFontEnumeration by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofFontEnumeration)
    }
    var spoofBrowserUserAgent by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofBrowserUserAgent)
    }
    var customUserAgent by remember(appInfo.packageName) {
        mutableStateOf(appInfo.customUserAgent.ifBlank { realHw.userAgent })
    }

    // Virtual GPS Location & Timezone
    var spoofLocationEnabled by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofLocationEnabled)
    }
    var latitudeText by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofLatitude.toString())
    }
    var longitudeText by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofLongitude.toString())
    }
    var hideMockLocation by remember(appInfo.packageName) {
        mutableStateOf(appInfo.hideMockLocation)
    }
    var autoSyncTimezone by remember(appInfo.packageName) {
        mutableStateOf(appInfo.autoSyncTimezone)
    }
    var timezoneId by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofTimezoneId.ifBlank { realHw.timezoneId })
    }
    var localeTag by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofLocaleTag.ifBlank { realHw.localeTag })
    }

    // Sensors & Virtual Battery
    var anonymizeSensors by remember(appInfo.packageName) {
        mutableStateOf(appInfo.anonymizeSensors)
    }
    var sensorBiasNeutralizer by remember(appInfo.packageName) {
        mutableStateOf(appInfo.sensorBiasNeutralizer)
    }
    var fakeBatteryEnabled by remember(appInfo.packageName) {
        mutableStateOf(appInfo.fakeBatteryEnabled)
    }
    var fakeBatteryLevel by remember(appInfo.packageName) {
        mutableFloatStateOf(appInfo.fakeBatteryLevel.coerceIn(5, 100).toFloat())
    }

    // Root, LSPosed & App Shields
    var hideRootAndXposed by remember(appInfo.packageName) {
        mutableStateOf(appInfo.hideRootAndXposed)
    }
    var hideAdbAndDevOptions by remember(appInfo.packageName) {
        mutableStateOf(appInfo.hideAdbAndDevOptions)
    }
    var hideVpnAndProxy by remember(appInfo.packageName) {
        mutableStateOf(appInfo.hideVpnAndProxy)
    }
    var knoxWarrantyBitSpoof by remember(appInfo.packageName) {
        mutableStateOf(appInfo.knoxWarrantyBitSpoof)
    }
    var blockCellTowerTracking by remember(appInfo.packageName) {
        mutableStateOf(appInfo.blockCellTowerTracking)
    }
    var spoofWifiBssid by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofWifiBssid)
    }
    var blockClipboardRead by remember(appInfo.packageName) {
        mutableStateOf(appInfo.blockClipboardRead)
    }
    var hideOtherApps by remember(appInfo.packageName) {
        mutableStateOf(appInfo.hideOtherApps)
    }
    var preventScreenshots by remember(appInfo.packageName) {
        mutableStateOf(appInfo.preventScreenshots)
    }
    var disableLogcatLogging by remember(appInfo.packageName) {
        mutableStateOf(appInfo.disableLogcatLogging)
    }
    var disableAllNetworking by remember(appInfo.packageName) {
        mutableStateOf(appInfo.disableAllNetworking)
    }

    // Deadman Switch
    var deadmanSwitchEnabled by remember(appInfo.packageName) {
        mutableStateOf(appInfo.deadmanSwitchEnabled)
    }

    val saveCurrentConfiguration = {
        viewModel.saveAppIdentitySpoofConfig(
            appInfo = appInfo,
            enabled = isSpoofEnabled,
            preset = if (isSpoofEnabled) selectedPreset else DeviceProfilePreset.REAL_HARDWARE,
            brand = if (isSpoofEnabled) brand else realHw.brand,
            model = if (isSpoofEnabled) model else realHw.model,
            device = if (isSpoofEnabled) device else realHw.device,
            androidId = if (isSpoofEnabled) androidId else realHw.androidId,
            imei = if (isSpoofEnabled) imei else "",
            advertisingId = if (isSpoofEnabled) advertisingId else "",
            macAddress = if (isSpoofEnabled) macAddress else "",
            carrierName = if (isSpoofEnabled) carrierName else realHw.carrierName,
            simOperator = if (isSpoofEnabled) simOperator else realHw.simOperator,
            serial = if (isSpoofEnabled) serial else realHw.serial,
            hideRootAndXposed = hideRootAndXposed,
            hideAdbAndDevOptions = hideAdbAndDevOptions,
            hideVpnAndProxy = hideVpnAndProxy,
            blockClipboardRead = blockClipboardRead,
            spoofLocationEnabled = spoofLocationEnabled,
            spoofLatitude = latitudeText.toDoubleOrNull() ?: 40.7128,
            spoofLongitude = longitudeText.toDoubleOrNull() ?: -74.0060,
            hideMockLocation = hideMockLocation,
            preventScreenshots = preventScreenshots,
            hideOtherApps = hideOtherApps,
            disableLogcatLogging = disableLogcatLogging,
            fakeBatteryEnabled = fakeBatteryEnabled,
            fakeBatteryLevel = fakeBatteryLevel.toInt(),
            knoxWarrantyBitSpoof = knoxWarrantyBitSpoof,
            disableAllNetworking = disableAllNetworking,
            spoofGpuFingerprint = spoofGpuFingerprint,
            spoofBrowserUserAgent = spoofBrowserUserAgent,
            anonymizeSensors = anonymizeSensors,
            blockCellTowerTracking = blockCellTowerTracking,
            autoSyncTimezone = autoSyncTimezone,
            spoofManufacturer = if (isSpoofEnabled) manufacturer else realHw.manufacturer,
            spoofProduct = if (isSpoofEnabled) product else realHw.product,
            spoofBoard = if (isSpoofEnabled) board else realHw.board,
            spoofHardware = if (isSpoofEnabled) hardware else realHw.hardware,
            spoofBootloader = if (isSpoofEnabled) bootloader else realHw.bootloader,
            spoofFingerprint = if (isSpoofEnabled) fingerprint else realHw.fingerprint,
            spoofGpuVendor = gpuVendor,
            spoofGpuRenderer = gpuRenderer,
            spoofCpuCores = cpuCoresText.toIntOrNull() ?: realHw.cpuCores,
            spoofRamGb = ramGbText.toIntOrNull() ?: realHw.ramGb,
            spoofScreenResolution = screenResolution,
            spoofScreenDpi = screenDpiText.toIntOrNull() ?: realHw.screenDpi,
            spoofTimezoneId = timezoneId,
            spoofLocaleTag = localeTag,
            customUserAgent = customUserAgent,
            spoofWebRtc = spoofWebRtc,
            spoofCanvasNoise = spoofCanvasNoise,
            spoofAudioContext = spoofAudioContext,
            spoofClientHints = spoofClientHints,
            spoofFontEnumeration = spoofFontEnumeration,
            privacyProfileId = selectedProfileId,
            deadmanSwitchEnabled = deadmanSwitchEnabled,
            spoofDrmWidevine = spoofDrmWidevine,
            sensorBiasNeutralizer = sensorBiasNeutralizer,
            spoofWifiBssid = spoofWifiBssid
        )
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(horizontal = 16.dp)
            .testTag("configure_privacy_categorized_screen")
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // 1. Unified App Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("privacy_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = scheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }
                val iconBitmap = rememberAppIconBitmap(appInfo.packageName, appInfo.icon, 84)
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = appInfo.appName,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appInfo.appName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = JakartaFontFamily
                    )
                    Text(
                        text = appInfo.packageName,
                        fontSize = 11.sp,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        viewModel.removeAppIdentitySpoof(appInfo)
                        onBack()
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("btn_reset_real_hardware")
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = "Reset to Real Hardware",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = saveCurrentConfiguration,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("save_privacy_config_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Save",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JakartaFontFamily
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Category Section Tabs (Instant, zero-scroll switching between the 5 modules)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AppPrivacySectionTab.entries.forEach { tab ->
                val selected = activeSectionTab == tab
                val bgColor = if (selected) scheme.primary else scheme.surface
                val textColor = if (selected) scheme.onPrimary else scheme.onSurfaceVariant
                val borderColor = if (selected) scheme.primary else scheme.outlineVariant

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        .clickable { activeSectionTab = tab }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("privacy_section_tab_${tab.name.lowercase()}")
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tab.label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor,
                        fontFamily = JakartaFontFamily
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Focused Section Content
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            when (activeSectionTab) {
                AppPrivacySectionTab.HARDWARE_IDS -> {
                    // Top Master Switch + Randomize Action + Optional Profile Row
                    item {
                        SectionMasterBanner(
                            title = "Virtual Hardware & Device IDs",
                            subtitle = if (isSpoofEnabled) "Active • Spoofing as $brand $model" else "Off • App sees real ${realHw.model}",
                            checked = isSpoofEnabled,
                            onCheckedChange = { isSpoofEnabled = it },
                            switchTag = "master_identity_switch",
                            actionLabel = "Randomize",
                            actionIcon = Icons.Default.Casino,
                            actionTag = "btn_randomize_all_ids",
                            onAction = {
                                isSpoofEnabled = true
                                androidId = IsolationRuleManager.generateAndroidId()
                                imei = IsolationRuleManager.generateLuhnImei()
                                advertisingId = UUID.randomUUID().toString()
                                macAddress = IsolationRuleManager.generateMacAddress()
                                serial = IsolationRuleManager.generateSerial()
                            }
                        )
                    }

                    // Optional Profile Preset Selector
                    item {
                        GroupedSettingsCard(header = "OPTIONAL PRIVACY PROFILE") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = selectedProfileId.isBlank(),
                                    onClick = { selectedProfileId = "" },
                                    label = { Text("Manual (None)", fontSize = 11.sp, fontFamily = JakartaFontFamily) },
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .height(30.dp)
                                        .testTag("profile_chip_none")
                                )
                                profiles.forEach { profile ->
                                    FilterChip(
                                        selected = selectedProfileId == profile.id,
                                        onClick = {
                                            selectedProfileId = profile.id
                                            isSpoofEnabled = true
                                            if (profile.spoofAndroidId) androidId = IsolationRuleManager.generateAndroidId()
                                            if (profile.spoofImei) imei = IsolationRuleManager.generateLuhnImei()
                                            if (profile.spoofAdvertisingId) advertisingId = UUID.randomUUID().toString()
                                            if (profile.spoofMacAddress) macAddress = IsolationRuleManager.generateMacAddress()
                                            if (profile.spoofSerial) serial = IsolationRuleManager.generateSerial()
                                            spoofDrmWidevine = profile.spoofDrmWidevine
                                            spoofGpuFingerprint = profile.spoofGpuFingerprint
                                            gpuVendor = profile.gpuVendor
                                            gpuRenderer = profile.gpuRenderer
                                            screenResolution = profile.spoofScreenResolution
                                            screenDpiText = profile.spoofScreenDpi.toString()
                                            spoofCanvasNoise = profile.spoofCanvasNoise
                                            spoofAudioContext = profile.spoofAudioContext
                                            anonymizeSensors = profile.anonymizeSensors
                                            sensorBiasNeutralizer = profile.sensorBiasNeutralizer
                                            fakeBatteryEnabled = profile.fakeBatteryEnabled
                                            fakeBatteryLevel = profile.fakeBatteryLevel.toFloat()
                                            spoofWebRtc = profile.spoofWebRtc
                                            blockCellTowerTracking = profile.blockCellTowerTracking
                                            spoofWifiBssid = profile.spoofWifiBssid
                                            hideVpnAndProxy = profile.hideVpnAndProxy
                                            hideRootAndXposed = profile.hideRootAndXposed
                                            hideAdbAndDevOptions = profile.hideAdbAndDevOptions
                                            blockClipboardRead = profile.blockClipboardRead
                                            preventScreenshots = profile.preventScreenshots
                                            disableLogcatLogging = profile.disableLogcatLogging
                                            autoSyncTimezone = profile.autoSyncTimezone
                                            spoofBrowserUserAgent = profile.spoofBrowserUserAgent
                                            deadmanSwitchEnabled = profile.deadmanSwitchEnabled
                                        },
                                        label = { Text(profile.name, fontSize = 11.sp, fontFamily = JakartaFontFamily) },
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier
                                            .height(30.dp)
                                            .testTag("profile_chip_${profile.id}")
                                    )
                                }
                            }
                        }
                    }

                    // Device Model Presets & Build.* Properties
                    item {
                        GroupedSettingsCard(header = "HARDWARE MODEL & BUILD PROPERTIES") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DeviceProfilePreset.entries.filter { it != DeviceProfilePreset.REAL_HARDWARE }.forEach { preset ->
                                    FilterChip(
                                        selected = selectedPreset == preset,
                                        onClick = {
                                            selectedPreset = preset
                                            isSpoofEnabled = true
                                            brand = preset.brand
                                            manufacturer = preset.manufacturer
                                            model = preset.model
                                            device = preset.device
                                            product = preset.product
                                            board = preset.board
                                            hardware = preset.board
                                            fingerprint = preset.fingerprint
                                            gpuVendor = preset.glVendor
                                            gpuRenderer = preset.glRenderer
                                        },
                                        label = { Text(preset.label, fontSize = 11.sp, fontFamily = JakartaFontFamily) },
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.height(30.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow("Brand", brand) { brand = it; isSpoofEnabled = true }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow("Manufacturer", manufacturer) { manufacturer = it; isSpoofEnabled = true }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow("Model (Build.MODEL)", model) { model = it; isSpoofEnabled = true }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow("Device Codename", device) { device = it; isSpoofEnabled = true }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow("Product", product) { product = it; isSpoofEnabled = true }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow("Board / SoC", board) { board = it; isSpoofEnabled = true }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow("Hardware", hardware) { hardware = it; isSpoofEnabled = true }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow("Bootloader", bootloader) { bootloader = it; isSpoofEnabled = true }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow("Fingerprint", fingerprint) { fingerprint = it; isSpoofEnabled = true }
                        }
                    }

                    // Device & Telecom Identifiers
                    item {
                        GroupedSettingsCard(header = "UNIQUE IDENTIFIERS & TELECOM") {
                            GroupedToggleRow(
                                title = "Spoof Widevine DRM ID",
                                subtitle = "Replace MediaDrm deviceUniqueId with per-app hash",
                                checked = spoofDrmWidevine,
                                onCheckedChange = { spoofDrmWidevine = it }
                            )
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow(
                                label = "Android ID",
                                value = androidId,
                                onRandomize = {
                                    androidId = IsolationRuleManager.generateAndroidId()
                                    isSpoofEnabled = true
                                }
                            ) { androidId = it; isSpoofEnabled = true }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow(
                                label = "IMEI (Luhn)",
                                value = imei,
                                onRandomize = {
                                    imei = IsolationRuleManager.generateLuhnImei()
                                    isSpoofEnabled = true
                                }
                            ) { imei = it; isSpoofEnabled = true }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow(
                                label = "Wi-Fi MAC",
                                value = macAddress,
                                onRandomize = {
                                    macAddress = IsolationRuleManager.generateMacAddress()
                                    isSpoofEnabled = true
                                }
                            ) { macAddress = it; isSpoofEnabled = true }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow(
                                label = "Hardware Serial",
                                value = serial,
                                onRandomize = {
                                    serial = IsolationRuleManager.generateSerial()
                                    isSpoofEnabled = true
                                }
                            ) { serial = it; isSpoofEnabled = true }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow(
                                label = "Advertising ID",
                                value = advertisingId,
                                onRandomize = {
                                    advertisingId = UUID.randomUUID().toString()
                                    isSpoofEnabled = true
                                }
                            ) { advertisingId = it; isSpoofEnabled = true }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow("Carrier Name", carrierName) { carrierName = it; isSpoofEnabled = true }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow("SIM Operator", simOperator) { simOperator = it; isSpoofEnabled = true }
                        }
                    }
                }

                AppPrivacySectionTab.DISPLAY_WEB -> {
                    item {
                        SectionMasterBanner(
                            title = "GPU, Screen & Browser Cloak",
                            subtitle = if (spoofGpuFingerprint) "Active • $gpuRenderer ($screenResolution)" else "Off • Real GPU & WebView metrics",
                            checked = spoofGpuFingerprint,
                            onCheckedChange = { spoofGpuFingerprint = it },
                            switchTag = "switch_gpu_section"
                        )
                    }

                    item {
                        GroupedSettingsCard(header = "BROWSER & WEBVIEW ANTI-FINGERPRINTING") {
                            GroupedToggleRow(
                                title = "Canvas 2D Pixel Noise",
                                subtitle = "Perturb HTMLCanvasElement readbacks against browser trackers",
                                checked = spoofCanvasNoise,
                                onCheckedChange = { spoofCanvasNoise = it }
                            )
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            GroupedToggleRow(
                                title = "WebAudio Context Entropy",
                                subtitle = "Add sub-audible jitter to AudioBuffer & OscillatorNode",
                                checked = spoofAudioContext,
                                onCheckedChange = { spoofAudioContext = it }
                            )
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            GroupedToggleRow(
                                title = "WebRTC Local IP Leak Blocker",
                                subtitle = "Strip local LAN & VPN interfaces from RTCPeerConnection",
                                checked = spoofWebRtc,
                                onCheckedChange = { spoofWebRtc = it }
                            )
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            GroupedToggleRow(
                                title = "Client Hints & Font Masking",
                                subtitle = "Align Sec-CH-UA headers and cap system font enumeration",
                                checked = spoofClientHints,
                                onCheckedChange = {
                                    spoofClientHints = it
                                    spoofFontEnumeration = it
                                }
                            )
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            GroupedToggleRow(
                                title = "Spoof Browser & WebView User-Agent",
                                subtitle = "Synchronize HTTP & WebView User-Agent with spoofed model",
                                checked = spoofBrowserUserAgent,
                                onCheckedChange = { spoofBrowserUserAgent = it }
                            )
                        }
                    }

                    item {
                        GroupedSettingsCard(header = "GPU, DISPLAY & HARDWARE SPECS") {
                            InlineEditableRow("GPU Vendor", gpuVendor) { gpuVendor = it; spoofGpuFingerprint = true }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow("GPU Renderer", gpuRenderer) { gpuRenderer = it; spoofGpuFingerprint = true }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow("Screen Resolution", screenResolution) { screenResolution = it }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow("Screen DPI", screenDpiText) { screenDpiText = it.filter { ch -> ch.isDigit() } }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow("CPU Cores", cpuCoresText) { cpuCoresText = it.filter { ch -> ch.isDigit() } }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow("Total RAM (GB)", ramGbText) { ramGbText = it.filter { ch -> ch.isDigit() } }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow("User-Agent", customUserAgent) {
                                customUserAgent = it
                                spoofBrowserUserAgent = true
                            }
                        }
                    }
                }

                AppPrivacySectionTab.GPS_TIME -> {
                    item {
                        SectionMasterBanner(
                            title = "Virtual GPS & Timezone Sync",
                            subtitle = if (spoofLocationEnabled) "Teleported to $latitudeText, $longitudeText ($timezoneId)" else "Off • Using real GPS location",
                            checked = spoofLocationEnabled,
                            onCheckedChange = { spoofLocationEnabled = it },
                            switchTag = "switch_gps_section"
                        )
                    }

                    item {
                        GroupedSettingsCard(header = "QUICK CITY TELEPORT") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CITY_TELEPORT_PRESETS.forEach { city ->
                                    val isSelected = latitudeText == city.lat && longitudeText == city.lng
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            spoofLocationEnabled = true
                                            latitudeText = city.lat
                                            longitudeText = city.lng
                                            timezoneId = city.tz
                                            localeTag = city.locale
                                        },
                                        label = {
                                            Text(
                                                text = "[${city.code}] ${city.label}",
                                                fontSize = 11.sp,
                                                fontFamily = JakartaFontFamily
                                            )
                                        },
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.height(30.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow("Latitude", latitudeText) { latitudeText = it; spoofLocationEnabled = true }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow("Longitude", longitudeText) { longitudeText = it; spoofLocationEnabled = true }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow("IANA Timezone", timezoneId) { timezoneId = it; autoSyncTimezone = true }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            InlineEditableRow("Locale Tag", localeTag) { localeTag = it; autoSyncTimezone = true }
                        }
                    }

                    item {
                        GroupedSettingsCard(header = "LOCATION & TIMEZONE GUARDS") {
                            GroupedToggleRow(
                                title = "Hide Mock Location Flag",
                                subtitle = "Force Location.isFromMockProvider() and isMock() to return false",
                                checked = hideMockLocation,
                                onCheckedChange = { hideMockLocation = it }
                            )
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            GroupedToggleRow(
                                title = "Auto-Sync Timezone & Locale",
                                subtitle = "Override TimeZone.getDefault() and Locale to match GPS / Exit IP",
                                checked = autoSyncTimezone,
                                onCheckedChange = { autoSyncTimezone = it }
                            )
                        }
                    }
                }

                AppPrivacySectionTab.SHIELDS_SENSORS -> {
                    item {
                        val activeCount = listOf(
                            hideRootAndXposed, hideAdbAndDevOptions, hideVpnAndProxy,
                            knoxWarrantyBitSpoof, blockCellTowerTracking, spoofWifiBssid,
                            blockClipboardRead, hideOtherApps, preventScreenshots,
                            disableLogcatLogging, disableAllNetworking
                        ).count { it }

                        SectionMasterBanner(
                            title = "Root, LSPosed & System Shields",
                            subtitle = "$activeCount of 11 stealth shields active",
                            checked = activeCount > 0,
                            onCheckedChange = { on ->
                                hideRootAndXposed = on
                                hideAdbAndDevOptions = on
                                hideVpnAndProxy = on
                                blockCellTowerTracking = on
                                spoofWifiBssid = on
                            },
                            switchTag = "switch_shields_section"
                        )
                    }

                    item {
                        GroupedSettingsCard(header = "ANTI-DETECTION & SYSTEM STEALTH") {
                            GroupedToggleRow(
                                title = "Hide Root, Magisk, KernelSU & LSPosed",
                                subtitle = "Cloak su binaries, mounts, Xposed stack traces & ro.build.tags",
                                checked = hideRootAndXposed,
                                onCheckedChange = { hideRootAndXposed = it }
                            )
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            GroupedToggleRow(
                                title = "Hide ADB & Developer Options",
                                subtitle = "Force ADB_ENABLED and DEVELOPMENT_SETTINGS_ENABLED to 0",
                                checked = hideAdbAndDevOptions,
                                onCheckedChange = { hideAdbAndDevOptions = it }
                            )
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            GroupedToggleRow(
                                title = "Hide VPN & Proxy Status",
                                subtitle = "Scrub tun0/ppp0 interfaces, TRANSPORT_VPN & proxy properties",
                                checked = hideVpnAndProxy,
                                onCheckedChange = { hideVpnAndProxy = it }
                            )
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            GroupedToggleRow(
                                title = "Samsung Knox 0x0 Warranty Bit",
                                subtitle = "Report intact warranty bit (0x0) to Samsung & banking apps",
                                checked = knoxWarrantyBitSpoof,
                                onCheckedChange = { knoxWarrantyBitSpoof = it }
                            )
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            GroupedToggleRow(
                                title = "Block Cell Tower CID/LAC Tracking",
                                subtitle = "Return empty CellInfo lists from TelephonyManager",
                                checked = blockCellTowerTracking,
                                onCheckedChange = { blockCellTowerTracking = it }
                            )
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            GroupedToggleRow(
                                title = "Spoof Wi-Fi Router BSSID & SSID",
                                subtitle = "Prevent physical location triangulation via router MACs",
                                checked = spoofWifiBssid,
                                onCheckedChange = { spoofWifiBssid = it }
                            )
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            GroupedToggleRow(
                                title = "Block Background Clipboard Read",
                                subtitle = "Prevent app from silently reading copied text or keys",
                                checked = blockClipboardRead,
                                onCheckedChange = { blockClipboardRead = it }
                            )
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            GroupedToggleRow(
                                title = "Hide Other Installed Apps",
                                subtitle = "Filter package queries so this app only sees itself",
                                checked = hideOtherApps,
                                onCheckedChange = { hideOtherApps = it }
                            )
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            GroupedToggleRow(
                                title = "Prevent Screenshots (FLAG_SECURE)",
                                subtitle = "Block screen capture and hide recents thumbnail",
                                checked = preventScreenshots,
                                onCheckedChange = { preventScreenshots = it }
                            )
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            GroupedToggleRow(
                                title = "Disable Logcat Output",
                                subtitle = "Silence android.util.Log calls from this app",
                                checked = disableLogcatLogging,
                                onCheckedChange = { disableLogcatLogging = it }
                            )
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            GroupedToggleRow(
                                title = "Offline Network Kill Switch",
                                subtitle = "Block all outgoing sockets and network access for this app",
                                checked = disableAllNetworking,
                                onCheckedChange = { disableAllNetworking = it }
                            )
                        }
                    }

                    item {
                        GroupedSettingsCard(header = "SENSORS & VIRTUAL BATTERY") {
                            GroupedToggleRow(
                                title = "Anonymize Sensor Vendors",
                                subtitle = "Scrub Sensor.getVendor() & block step/heart counters",
                                checked = anonymizeSensors,
                                onCheckedChange = { anonymizeSensors = it }
                            )
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            GroupedToggleRow(
                                title = "MEMS Calibration Bias Neutralizer",
                                subtitle = "Inject micro-jitter into gyroscope & accelerometer readings",
                                checked = sensorBiasNeutralizer,
                                onCheckedChange = { sensorBiasNeutralizer = it }
                            )
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            GroupedToggleRow(
                                title = "Virtual Battery Level (${fakeBatteryLevel.toInt()}%)",
                                subtitle = "Override BatteryManager level to prevent cross-app correlation",
                                checked = fakeBatteryEnabled,
                                onCheckedChange = { fakeBatteryEnabled = it }
                            )
                            if (fakeBatteryEnabled) {
                                Slider(
                                    value = fakeBatteryLevel,
                                    onValueChange = { fakeBatteryLevel = it },
                                    valueRange = 5f..100f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                AppPrivacySectionTab.DEADMAN -> {
                    item {
                        val appUid = remember(appInfo.packageName) {
                            (appInfo.packageName.hashCode() and 0x7FFF) + 10000
                        }

                        SectionMasterBanner(
                            title = "Total Identity Leak Deadman Switch",
                            subtitle = if (deadmanSwitchEnabled) "Armed • Kills app & blocks UID via iptables on leak" else "Disarmed",
                            checked = deadmanSwitchEnabled,
                            onCheckedChange = { deadmanSwitchEnabled = it },
                            switchTag = "switch_deadman_section",
                            accentColor = Color(0xFFEF4444)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        GroupedSettingsCard(header = "KERNEL WATCHDOG & LEAK RECOVERY") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "If any hook breaks or native code attempts to read real hardware IDs (Android ID, IMEI, MAC, Serial, DRM) or bypass the proxy tunnel, Hollow immediately terminates the process and drops all network packets for UID $appUid.",
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = scheme.onSurfaceVariant,
                                    fontFamily = JakartaFontFamily
                                )

                                if (deadmanIncidents.isNotEmpty()) {
                                    val latest = deadmanIncidents.first()
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = "Blocked: ${latest.appName} (UID ${latest.appUid}) • ${latest.triggerVector}: ${latest.details}",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFFEF4444)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.simulateIdentityLeak(
                                                packageName = appInfo.packageName,
                                                appName = appInfo.appName,
                                                uid = appUid
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_simulate_leak")
                                    ) {
                                        Text("Test Leak Kill", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily)
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.restoreAppNetwork(appUid) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_restore_uid_network")
                                    ) {
                                        Text("Restore Network", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionMasterBanner(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTag: String,
    accentColor: Color? = null,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    actionTag: String = "",
    onAction: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val activeColor = accentColor ?: scheme.primary

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (checked) activeColor.copy(alpha = 0.12f) else scheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (checked) activeColor.copy(alpha = 0.45f) else scheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    fontFamily = JakartaFontFamily
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = if (checked) activeColor else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = JakartaFontFamily
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (actionLabel != null && onAction != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(activeColor.copy(alpha = 0.18f))
                            .clickable { onAction() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag(actionTag)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (actionIcon != null) {
                                Icon(
                                    imageVector = actionIcon,
                                    contentDescription = null,
                                    tint = activeColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = actionLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = activeColor,
                                fontFamily = JakartaFontFamily
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = scheme.onPrimary,
                        checkedTrackColor = activeColor
                    ),
                    modifier = Modifier
                        .scale(0.85f)
                        .testTag(switchTag)
                )
            }
        }
    }
}

@Composable
private fun GroupedSettingsCard(
    header: String,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = header,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp,
            color = scheme.onSurfaceVariant,
            fontFamily = JakartaFontFamily,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = scheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, scheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun InlineEditableRow(
    label: String,
    value: String,
    onRandomize: (() -> Unit)? = null,
    onValueChange: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurfaceVariant,
            fontFamily = JakartaFontFamily,
            modifier = Modifier.width(124.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = scheme.onSurface,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            ),
            cursorBrush = SolidColor(scheme.primary),
            modifier = Modifier.weight(1f)
        )
        if (onRandomize != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(scheme.primary.copy(alpha = 0.14f))
                    .clickable { onRandomize() }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Randomize $label",
                    tint = scheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun GroupedToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                fontFamily = JakartaFontFamily
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = JakartaFontFamily
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = scheme.onPrimary,
                checkedTrackColor = scheme.primary
            ),
            modifier = Modifier.scale(0.82f)
        )
    }
}
