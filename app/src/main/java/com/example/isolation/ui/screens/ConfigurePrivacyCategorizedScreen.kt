package com.example.isolation.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sensors
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.isolation.model.DeviceProfilePreset
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.pkg.InstalledAppInfo
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.JakartaFontFamily
import java.util.UUID

private data class CityTeleportPreset(
    val label: String,
    val lat: String,
    val lng: String,
    val tz: String,
    val locale: String
)

private val CITY_TELEPORT_PRESETS = listOf(
    CityTeleportPreset("New York", "40.7128", "-74.0060", "America/New_York", "en-US"),
    CityTeleportPreset("London", "51.5074", "-0.1278", "Europe/London", "en-GB"),
    CityTeleportPreset("Tokyo", "35.6762", "139.6503", "Asia/Tokyo", "ja-JP"),
    CityTeleportPreset("Zurich", "47.3769", "8.5417", "Europe/Zurich", "de-CH"),
    CityTeleportPreset("Frankfurt", "50.1109", "8.6821", "Europe/Berlin", "de-DE"),
    CityTeleportPreset("Singapore", "1.3521", "103.8198", "Asia/Singapore", "en-SG")
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

    // Optional profile (defaults to "" = None / Manual Configuration)
    var selectedProfileId by remember(appInfo.packageName) {
        mutableStateOf(appInfo.privacyProfileId)
    }

    // Section 1: Hardware Model & Build Props ON/OFF + fields
    var hardwareModelSpoofOn by remember(appInfo.packageName) {
        mutableStateOf(
            appInfo.isIdentitySpoofed && appInfo.deviceProfilePreset != DeviceProfilePreset.REAL_HARDWARE
        )
    }
    var hardwareExpanded by remember(appInfo.packageName) {
        mutableStateOf(hardwareModelSpoofOn || appInfo.isIdentitySpoofed)
    }
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

    // Section 2: Device & Telecom Identifiers ON/OFF + fields
    var identifiersSpoofOn by remember(appInfo.packageName) {
        mutableStateOf(appInfo.isIdentitySpoofed)
    }
    var identifiersExpanded by remember(appInfo.packageName) {
        mutableStateOf(appInfo.isIdentitySpoofed)
    }
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

    // Section 3: GPU, Display & WebView Anti-Fingerprinting ON/OFF + fields
    var spoofGpuFingerprint by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofGpuFingerprint)
    }
    var gpuExpanded by remember(appInfo.packageName) {
        mutableStateOf(false)
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

    // Section 4: Virtual GPS Location & Timezone ON/OFF + fields
    var spoofLocationEnabled by remember(appInfo.packageName) {
        mutableStateOf(appInfo.spoofLocationEnabled)
    }
    var gpsExpanded by remember(appInfo.packageName) {
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

    // Section 5: Sensors & Virtual Battery ON/OFF + fields
    var anonymizeSensors by remember(appInfo.packageName) {
        mutableStateOf(appInfo.anonymizeSensors)
    }
    var sensorsExpanded by remember(appInfo.packageName) {
        mutableStateOf(false)
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

    // Section 6: Anti-Detection, System & App Shields ON/OFF + toggles
    var hideRootAndXposed by remember(appInfo.packageName) {
        mutableStateOf(appInfo.hideRootAndXposed)
    }
    var shieldsExpanded by remember(appInfo.packageName) {
        mutableStateOf(false)
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

    // Section 7: Total Identity Leak Deadman Switch
    var deadmanSwitchEnabled by remember(appInfo.packageName) {
        mutableStateOf(appInfo.deadmanSwitchEnabled)
    }
    var deadmanExpanded by remember(appInfo.packageName) {
        mutableStateOf(false)
    }

    val saveCurrentConfiguration = {
        val masterIdentityOn = hardwareModelSpoofOn || identifiersSpoofOn
        val effectivePreset = if (hardwareModelSpoofOn) selectedPreset else DeviceProfilePreset.REAL_HARDWARE
        viewModel.saveAppIdentitySpoofConfig(
            appInfo = appInfo,
            enabled = masterIdentityOn,
            preset = effectivePreset,
            brand = if (hardwareModelSpoofOn) brand else realHw.brand,
            model = if (hardwareModelSpoofOn) model else realHw.model,
            device = if (hardwareModelSpoofOn) device else realHw.device,
            androidId = if (identifiersSpoofOn) androidId else realHw.androidId,
            imei = if (identifiersSpoofOn) imei else "",
            advertisingId = if (identifiersSpoofOn) advertisingId else "",
            macAddress = if (identifiersSpoofOn) macAddress else "",
            carrierName = if (identifiersSpoofOn) carrierName else realHw.carrierName,
            simOperator = if (identifiersSpoofOn) simOperator else realHw.simOperator,
            serial = if (identifiersSpoofOn) serial else realHw.serial,
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
            spoofManufacturer = if (hardwareModelSpoofOn) manufacturer else realHw.manufacturer,
            spoofProduct = if (hardwareModelSpoofOn) product else realHw.product,
            spoofBoard = if (hardwareModelSpoofOn) board else realHw.board,
            spoofHardware = if (hardwareModelSpoofOn) hardware else realHw.hardware,
            spoofBootloader = if (hardwareModelSpoofOn) bootloader else realHw.bootloader,
            spoofFingerprint = if (hardwareModelSpoofOn) fingerprint else realHw.fingerprint,
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
            .padding(horizontal = 14.dp)
            .testTag("configure_privacy_categorized_screen")
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Compact Top Bar: Back + App Icon + App Name + Reset Button + Save Button
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
                        .size(34.dp)
                        .testTag("privacy_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = scheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }
                val iconBitmap = remember(appInfo.packageName, appInfo.icon) {
                    try {
                        appInfo.icon?.toBitmap(72, 72)?.asImageBitmap()
                    } catch (_: Throwable) {
                        null
                    }
                }
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = appInfo.appName,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appInfo.appName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = JakartaFontFamily
                    )
                    Text(
                        text = appInfo.packageName,
                        fontSize = 10.sp,
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
                        tint = scheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = saveCurrentConfiguration,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("save_privacy_config_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Save",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JakartaFontFamily
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Slim Horizontal Bar: Optional Profile Selector + 1-Tap Randomize All IDs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(scheme.primaryContainer)
                    .clickable {
                        identifiersSpoofOn = true
                        identifiersExpanded = true
                        androidId = IsolationRuleManager.generateAndroidId()
                        imei = IsolationRuleManager.generateLuhnImei()
                        advertisingId = UUID.randomUUID().toString()
                        macAddress = IsolationRuleManager.generateMacAddress()
                        serial = IsolationRuleManager.generateSerial()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("btn_randomize_all_ids")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Casino,
                        contentDescription = null,
                        tint = scheme.onPrimaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Randomize IDs",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer,
                        fontFamily = JakartaFontFamily
                    )
                }
            }

            FilterChip(
                selected = selectedProfileId.isBlank(),
                onClick = { selectedProfileId = "" },
                label = {
                    Text(
                        text = "Manual (No Profile)",
                        fontSize = 11.sp,
                        fontFamily = JakartaFontFamily
                    )
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .height(30.dp)
                    .testTag("profile_chip_none")
            )

            profiles.forEach { profile ->
                val isSelected = selectedProfileId == profile.id
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedProfileId = profile.id
                        hardwareModelSpoofOn = true
                        identifiersSpoofOn = true
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
                    label = {
                        Text(
                            text = profile.name,
                            fontSize = 11.sp,
                            fontFamily = JakartaFontFamily
                        )
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("profile_chip_${profile.id}")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            // 1. HARDWARE MODEL & BUILD PROPERTIES (With Header ON/OFF Switch!)
            item {
                CompactModuleCard(
                    icon = Icons.Default.PhoneAndroid,
                    title = "Hardware Model & Build Props",
                    statusSummary = if (hardwareModelSpoofOn) "$brand $model ($device)" else "Off • Using real ${realHw.model}",
                    enabled = hardwareModelSpoofOn,
                    expanded = hardwareExpanded,
                    onToggleEnabled = { on ->
                        hardwareModelSpoofOn = on
                        if (on) hardwareExpanded = true
                    },
                    onToggleExpand = { hardwareExpanded = !hardwareExpanded },
                    switchTag = "switch_hardware_model"
                ) {
                    // Preset Selector Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DeviceProfilePreset.entries.filter { it != DeviceProfilePreset.REAL_HARDWARE }.forEach { preset ->
                            FilterChip(
                                selected = selectedPreset == preset,
                                onClick = {
                                    selectedPreset = preset
                                    hardwareModelSpoofOn = true
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
                                label = {
                                    Text(
                                        text = preset.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = JakartaFontFamily
                                    )
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactProInput(
                            label = "Brand",
                            value = brand,
                            onValueChange = { brand = it; hardwareModelSpoofOn = true },
                            modifier = Modifier.weight(1f)
                        )
                        CompactProInput(
                            label = "Manufacturer",
                            value = manufacturer,
                            onValueChange = { manufacturer = it; hardwareModelSpoofOn = true },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactProInput(
                            label = "Model (Build.MODEL)",
                            value = model,
                            onValueChange = { model = it; hardwareModelSpoofOn = true },
                            modifier = Modifier.weight(1.2f)
                        )
                        CompactProInput(
                            label = "Device Codename",
                            value = device,
                            onValueChange = { device = it; hardwareModelSpoofOn = true },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactProInput(
                            label = "Product",
                            value = product,
                            onValueChange = { product = it; hardwareModelSpoofOn = true },
                            modifier = Modifier.weight(1f)
                        )
                        CompactProInput(
                            label = "Board / SoC",
                            value = board,
                            onValueChange = { board = it; hardwareModelSpoofOn = true },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactProInput(
                            label = "Hardware",
                            value = hardware,
                            onValueChange = { hardware = it; hardwareModelSpoofOn = true },
                            modifier = Modifier.weight(1f)
                        )
                        CompactProInput(
                            label = "Bootloader",
                            value = bootloader,
                            onValueChange = { bootloader = it; hardwareModelSpoofOn = true },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    CompactProInput(
                        label = "Build Fingerprint",
                        value = fingerprint,
                        onValueChange = { fingerprint = it; hardwareModelSpoofOn = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 2. DEVICE & TELECOM IDENTIFIERS (With Header ON/OFF Switch!)
            item {
                CompactModuleCard(
                    icon = Icons.Default.Fingerprint,
                    title = "Device & Telecom Identifiers",
                    statusSummary = if (identifiersSpoofOn) "AID ${androidId.take(6)}… • IMEI ${imei.take(6)}… • DRM ${if (spoofDrmWidevine) "On" else "Off"}" else "Off • Real Android ID & IMEI",
                    enabled = identifiersSpoofOn,
                    expanded = identifiersExpanded,
                    onToggleEnabled = { on ->
                        identifiersSpoofOn = on
                        if (on) identifiersExpanded = true
                    },
                    onToggleExpand = { identifiersExpanded = !identifiersExpanded },
                    switchTag = "master_identity_switch"
                ) {
                    // Top toggle for Widevine DRM (never buried at bottom)
                    CompactSwitchRow(
                        title = "Spoof Widevine DRM Device Unique ID",
                        subtitle = "Deterministic per-app hash for MediaDrm.getPropertyByteArray",
                        checked = spoofDrmWidevine,
                        onCheckedChange = { spoofDrmWidevine = it }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactProInput(
                            label = "Android ID (16-hex)",
                            value = androidId,
                            onValueChange = { androidId = it; identifiersSpoofOn = true },
                            onRandomize = {
                                androidId = IsolationRuleManager.generateAndroidId()
                                identifiersSpoofOn = true
                            },
                            modifier = Modifier.weight(1f)
                        )
                        CompactProInput(
                            label = "IMEI (15-digit Luhn)",
                            value = imei,
                            onValueChange = { imei = it; identifiersSpoofOn = true },
                            onRandomize = {
                                imei = IsolationRuleManager.generateLuhnImei()
                                identifiersSpoofOn = true
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactProInput(
                            label = "Wi-Fi MAC Address",
                            value = macAddress,
                            onValueChange = { macAddress = it; identifiersSpoofOn = true },
                            onRandomize = {
                                macAddress = IsolationRuleManager.generateMacAddress()
                                identifiersSpoofOn = true
                            },
                            modifier = Modifier.weight(1f)
                        )
                        CompactProInput(
                            label = "Hardware Serial",
                            value = serial,
                            onValueChange = { serial = it; identifiersSpoofOn = true },
                            onRandomize = {
                                serial = IsolationRuleManager.generateSerial()
                                identifiersSpoofOn = true
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    CompactProInput(
                        label = "Google Advertising ID (GAID)",
                        value = advertisingId,
                        onValueChange = { advertisingId = it; identifiersSpoofOn = true },
                        onRandomize = {
                            advertisingId = UUID.randomUUID().toString()
                            identifiersSpoofOn = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactProInput(
                            label = "Carrier Name",
                            value = carrierName,
                            onValueChange = { carrierName = it; identifiersSpoofOn = true },
                            modifier = Modifier.weight(1f)
                        )
                        CompactProInput(
                            label = "SIM Operator (MCC/MNC)",
                            value = simOperator,
                            onValueChange = { simOperator = it; identifiersSpoofOn = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 3. GPU, DISPLAY & WEBVIEW FINGERPRINT (With Header ON/OFF Switch!)
            item {
                CompactModuleCard(
                    icon = Icons.Default.Memory,
                    title = "GPU, Screen & Browser Cloak",
                    statusSummary = if (spoofGpuFingerprint) "$gpuRenderer • $screenResolution • WebRTC/Canvas On" else "Off • Real GPU & WebView",
                    enabled = spoofGpuFingerprint,
                    expanded = gpuExpanded,
                    onToggleEnabled = { on ->
                        spoofGpuFingerprint = on
                        if (on) gpuExpanded = true
                    },
                    onToggleExpand = { gpuExpanded = !gpuExpanded },
                    switchTag = "switch_gpu_section"
                ) {
                    // Toggles at the TOP of the card
                    CompactSwitchRow(
                        title = "Canvas 2D & WebAudio Noise Injection",
                        subtitle = "Perturb HTMLCanvasElement & AudioBuffer against browser trackers",
                        checked = spoofCanvasNoise,
                        onCheckedChange = {
                            spoofCanvasNoise = it
                            spoofAudioContext = it
                        }
                    )
                    CompactSwitchRow(
                        title = "WebRTC Local IP Leak Blocker",
                        subtitle = "Strip LAN & VPN interfaces from RTCPeerConnection",
                        checked = spoofWebRtc,
                        onCheckedChange = { spoofWebRtc = it }
                    )
                    CompactSwitchRow(
                        title = "Client Hints & System Font Enumeration Mask",
                        subtitle = "Align Sec-CH-UA headers and cap enumerated font list",
                        checked = spoofClientHints,
                        onCheckedChange = {
                            spoofClientHints = it
                            spoofFontEnumeration = it
                        }
                    )
                    CompactSwitchRow(
                        title = "Spoof Browser & WebView User-Agent",
                        subtitle = "Sync WebView & HTTP User-Agent with spoofed model",
                        checked = spoofBrowserUserAgent,
                        onCheckedChange = { spoofBrowserUserAgent = it }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactProInput(
                            label = "GPU Vendor",
                            value = gpuVendor,
                            onValueChange = { gpuVendor = it; spoofGpuFingerprint = true },
                            modifier = Modifier.weight(0.9f)
                        )
                        CompactProInput(
                            label = "GPU Renderer",
                            value = gpuRenderer,
                            onValueChange = { gpuRenderer = it; spoofGpuFingerprint = true },
                            modifier = Modifier.weight(1.3f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactProInput(
                            label = "Resolution",
                            value = screenResolution,
                            onValueChange = { screenResolution = it },
                            modifier = Modifier.weight(1.1f)
                        )
                        CompactProInput(
                            label = "DPI",
                            value = screenDpiText,
                            onValueChange = { screenDpiText = it.filter { ch -> ch.isDigit() } },
                            modifier = Modifier.weight(0.7f)
                        )
                        CompactProInput(
                            label = "CPU Cores",
                            value = cpuCoresText,
                            onValueChange = { cpuCoresText = it.filter { ch -> ch.isDigit() } },
                            modifier = Modifier.weight(0.7f)
                        )
                        CompactProInput(
                            label = "RAM GB",
                            value = ramGbText,
                            onValueChange = { ramGbText = it.filter { ch -> ch.isDigit() } },
                            modifier = Modifier.weight(0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    CompactProInput(
                        label = "Custom User-Agent String",
                        value = customUserAgent,
                        onValueChange = { customUserAgent = it; spoofBrowserUserAgent = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 4. VIRTUAL GPS LOCATION & TIMEZONE (With Header ON/OFF Switch!)
            item {
                CompactModuleCard(
                    icon = Icons.Default.GpsFixed,
                    title = "Virtual GPS & Timezone",
                    statusSummary = if (spoofLocationEnabled) "GPS $latitudeText, $longitudeText • TZ $timezoneId" else "Off • Real GPS Location",
                    enabled = spoofLocationEnabled,
                    expanded = gpsExpanded,
                    onToggleEnabled = { on ->
                        spoofLocationEnabled = on
                        if (on) gpsExpanded = true
                    },
                    onToggleExpand = { gpsExpanded = !gpsExpanded },
                    switchTag = "switch_gps_section"
                ) {
                    CompactSwitchRow(
                        title = "Hide Mock Location Flag",
                        subtitle = "Force Location.isFromMockProvider() and isMock() to return false",
                        checked = hideMockLocation,
                        onCheckedChange = { hideMockLocation = it }
                    )
                    CompactSwitchRow(
                        title = "Auto-Sync Timezone & Locale",
                        subtitle = "Match TimeZone.getDefault() and Locale with virtual location",
                        checked = autoSyncTimezone,
                        onCheckedChange = { autoSyncTimezone = it }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CITY_TELEPORT_PRESETS.forEach { city ->
                            FilterChip(
                                selected = latitudeText == city.lat && longitudeText == city.lng,
                                onClick = {
                                    spoofLocationEnabled = true
                                    latitudeText = city.lat
                                    longitudeText = city.lng
                                    timezoneId = city.tz
                                    localeTag = city.locale
                                },
                                label = { Text(city.label, fontSize = 11.sp, fontFamily = JakartaFontFamily) },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactProInput(
                            label = "Latitude",
                            value = latitudeText,
                            onValueChange = { latitudeText = it; spoofLocationEnabled = true },
                            modifier = Modifier.weight(1f)
                        )
                        CompactProInput(
                            label = "Longitude",
                            value = longitudeText,
                            onValueChange = { longitudeText = it; spoofLocationEnabled = true },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactProInput(
                            label = "IANA Timezone",
                            value = timezoneId,
                            onValueChange = { timezoneId = it; autoSyncTimezone = true },
                            modifier = Modifier.weight(1.3f)
                        )
                        CompactProInput(
                            label = "Locale Tag",
                            value = localeTag,
                            onValueChange = { localeTag = it; autoSyncTimezone = true },
                            modifier = Modifier.weight(0.7f)
                        )
                    }
                }
            }

            // 5. SENSORS & VIRTUAL BATTERY (With Header ON/OFF Switch!)
            item {
                CompactModuleCard(
                    icon = Icons.Default.Sensors,
                    title = "Sensors & Virtual Battery",
                    statusSummary = if (anonymizeSensors || fakeBatteryEnabled) {
                        "Sensors Scrubbed • Battery ${if (fakeBatteryEnabled) "${fakeBatteryLevel.toInt()}%" else "Real"}"
                    } else "Off • Real Sensors & Battery",
                    enabled = anonymizeSensors || fakeBatteryEnabled,
                    expanded = sensorsExpanded,
                    onToggleEnabled = { on ->
                        anonymizeSensors = on
                        sensorBiasNeutralizer = on
                        fakeBatteryEnabled = on
                        if (on) sensorsExpanded = true
                    },
                    onToggleExpand = { sensorsExpanded = !sensorsExpanded },
                    switchTag = "switch_sensors_section"
                ) {
                    CompactSwitchRow(
                        title = "Anonymize Hardware Sensor Vendors",
                        subtitle = "Scrub Sensor.getVendor() & block step/heart-rate tracking",
                        checked = anonymizeSensors,
                        onCheckedChange = { anonymizeSensors = it }
                    )
                    CompactSwitchRow(
                        title = "MEMS Calibration Bias Neutralizer",
                        subtitle = "Add micro-jitter to accelerometer/gyroscope to defeat hardware ID",
                        checked = sensorBiasNeutralizer,
                        onCheckedChange = { sensorBiasNeutralizer = it }
                    )
                    CompactSwitchRow(
                        title = "Spoof Virtual Battery Level (${fakeBatteryLevel.toInt()}%)",
                        subtitle = "Prevent cross-app battery level & charging correlation",
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
                                .height(28.dp)
                        )
                    }
                }
            }

            // 6. ROOT, LSPOSED & SYSTEM SHIELDS (With Header ON/OFF Switch!)
            item {
                val activeShieldCount = listOf(
                    hideRootAndXposed, hideAdbAndDevOptions, hideVpnAndProxy,
                    knoxWarrantyBitSpoof, blockCellTowerTracking, spoofWifiBssid,
                    blockClipboardRead, hideOtherApps, preventScreenshots,
                    disableLogcatLogging, disableAllNetworking
                ).count { it }

                CompactModuleCard(
                    icon = Icons.Default.Shield,
                    title = "Root, LSPosed & App Shields",
                    statusSummary = if (activeShieldCount > 0) "$activeShieldCount of 11 stealth shields active" else "All shields disabled",
                    enabled = activeShieldCount > 0,
                    expanded = shieldsExpanded,
                    onToggleEnabled = { on ->
                        hideRootAndXposed = on
                        hideAdbAndDevOptions = on
                        hideVpnAndProxy = on
                        blockCellTowerTracking = on
                        spoofWifiBssid = on
                        if (on) shieldsExpanded = true
                    },
                    onToggleExpand = { shieldsExpanded = !shieldsExpanded },
                    switchTag = "switch_shields_section"
                ) {
                    CompactSwitchRow(
                        title = "Hide Root, Magisk, KernelSU & LSPosed",
                        subtitle = "Cloak su binaries, mounts, Xposed stack traces & test-keys",
                        checked = hideRootAndXposed,
                        onCheckedChange = { hideRootAndXposed = it }
                    )
                    CompactSwitchRow(
                        title = "Hide ADB & Developer Options",
                        subtitle = "Force ADB_ENABLED and DEVELOPMENT_SETTINGS_ENABLED to 0",
                        checked = hideAdbAndDevOptions,
                        onCheckedChange = { hideAdbAndDevOptions = it }
                    )
                    CompactSwitchRow(
                        title = "Hide VPN & Proxy Network Interfaces",
                        subtitle = "Scrub tun0/ppp0, TRANSPORT_VPN & system proxy properties",
                        checked = hideVpnAndProxy,
                        onCheckedChange = { hideVpnAndProxy = it }
                    )
                    CompactSwitchRow(
                        title = "Samsung Knox 0x0 Warranty Bit",
                        subtitle = "Spoof ro.boot.warranty_bit=0 for Samsung financial & Knox apps",
                        checked = knoxWarrantyBitSpoof,
                        onCheckedChange = { knoxWarrantyBitSpoof = it }
                    )
                    CompactSwitchRow(
                        title = "Block Cell Tower CID/LAC Tracking",
                        subtitle = "Return empty CellInfo lists from TelephonyManager",
                        checked = blockCellTowerTracking,
                        onCheckedChange = { blockCellTowerTracking = it }
                    )
                    CompactSwitchRow(
                        title = "Spoof Wi-Fi Router BSSID & SSID",
                        subtitle = "Prevent physical geolocation via nearby Wi-Fi MACs",
                        checked = spoofWifiBssid,
                        onCheckedChange = { spoofWifiBssid = it }
                    )
                    CompactSwitchRow(
                        title = "Block Background Clipboard Read",
                        subtitle = "Prevent app from silently reading copied passwords/keys",
                        checked = blockClipboardRead,
                        onCheckedChange = { blockClipboardRead = it }
                    )
                    CompactSwitchRow(
                        title = "Hide Other Installed Apps",
                        subtitle = "Filter getInstalledPackages() so this app only sees itself",
                        checked = hideOtherApps,
                        onCheckedChange = { hideOtherApps = it }
                    )
                    CompactSwitchRow(
                        title = "Prevent Screenshots & Recording (FLAG_SECURE)",
                        subtitle = "Block screen capture and recents thumbnail previews",
                        checked = preventScreenshots,
                        onCheckedChange = { preventScreenshots = it }
                    )
                    CompactSwitchRow(
                        title = "Disable Logcat Output",
                        subtitle = "Silence android.util.Log calls from this app",
                        checked = disableLogcatLogging,
                        onCheckedChange = { disableLogcatLogging = it }
                    )
                    CompactSwitchRow(
                        title = "Strict Offline Network Kill Switch",
                        subtitle = "Block all outgoing sockets and network access for this app",
                        checked = disableAllNetworking,
                        onCheckedChange = { disableAllNetworking = it }
                    )
                }
            }

            // 7. TOTAL IDENTITY LEAK DEADMAN SWITCH (With Header ON/OFF Switch!)
            item {
                val appUid = remember(appInfo.packageName) {
                    (appInfo.packageName.hashCode() and 0x7FFF) + 10000
                }
                CompactModuleCard(
                    icon = Icons.Default.Warning,
                    title = "Identity Leak Deadman Switch",
                    statusSummary = if (deadmanSwitchEnabled) "Armed • Kills app & blocks UID via iptables on leak" else "Disarmed",
                    enabled = deadmanSwitchEnabled,
                    expanded = deadmanExpanded,
                    accentColor = Color(0xFFEF4444),
                    onToggleEnabled = { on ->
                        deadmanSwitchEnabled = on
                        if (on) deadmanExpanded = true
                    },
                    onToggleExpand = { deadmanExpanded = !deadmanExpanded },
                    switchTag = "switch_deadman_section"
                ) {
                    if (deadmanIncidents.isNotEmpty()) {
                        val latest = deadmanIncidents.first()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Blocked: ${latest.appName} (UID ${latest.appUid}) • ${latest.triggerVector}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFEF4444)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
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
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .testTag("btn_simulate_leak")
                        ) {
                            Text("Test Leak Kill", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily)
                        }

                        OutlinedButton(
                            onClick = { viewModel.restoreAppNetwork(appUid) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .testTag("btn_restore_uid_network")
                        ) {
                            Text("Restore Network", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactModuleCard(
    icon: ImageVector,
    title: String,
    statusSummary: String,
    enabled: Boolean,
    expanded: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleExpand: () -> Unit,
    switchTag: String,
    accentColor: Color? = null,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val activeColor = accentColor ?: scheme.primary

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) activeColor.copy(alpha = 0.08f) else scheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (enabled) activeColor.copy(alpha = 0.45f) else scheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Sleek Header Row with ON/OFF Switch & Expand Chevron
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (enabled) activeColor.copy(alpha = 0.18f)
                                else scheme.surfaceVariant
                            )
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (enabled) activeColor else scheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = JakartaFontFamily
                        )
                        Text(
                            text = statusSummary,
                            fontSize = 11.sp,
                            color = if (enabled) activeColor else scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = JakartaFontFamily
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = enabled,
                        onCheckedChange = onToggleEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = scheme.onPrimary,
                            checkedTrackColor = activeColor
                        ),
                        modifier = Modifier
                            .scale(0.82f)
                            .testTag(switchTag)
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onToggleExpand() }
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                ) {
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.45f))
                    Spacer(modifier = Modifier.height(8.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun CompactProInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRandomize: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = JakartaFontFamily,
                modifier = Modifier.weight(1f)
            )
            if (onRandomize != null) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Randomize $label",
                    tint = scheme.primary,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onRandomize() }
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
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
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CompactSwitchRow(
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
            .padding(vertical = 3.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                fontFamily = JakartaFontFamily
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = JakartaFontFamily
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = scheme.onPrimary,
                checkedTrackColor = scheme.primary
            ),
            modifier = Modifier.scale(0.8f)
        )
    }
}
