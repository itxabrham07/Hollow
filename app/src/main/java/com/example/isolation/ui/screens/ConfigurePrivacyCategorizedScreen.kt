package com.example.isolation.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.model.DeviceProfilePreset
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.model.PrivacyProfile
import com.example.isolation.pkg.InstalledAppInfo
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.JakartaFontFamily
import java.util.UUID

/**
 * Clean, categorized Privacy Suite screen with Reusable Profiles, Per-App Overrides,
 * Expandable Accordions, and Total Identity Leak Deadman Switch.
 */
@Composable
fun ConfigurePrivacyCategorizedScreen(
    appInfo: InstalledAppInfo,
    viewModel: IsoVaultViewModel,
    onBack: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val profiles by viewModel.privacyProfiles.collectAsState()
    val blockedIncidents by viewModel.deadmanBlockedIncidents.collectAsState()
    val realHardware = viewModel.realHardware

    // Active Profile ID state
    var selectedProfileId by remember { mutableStateOf(appInfo.privacyProfileId.ifBlank { PrivacyProfile.PROFILE_GHOST_CLOAK }) }
    val currentProfile = profiles.find { it.id == selectedProfileId } ?: PrivacyProfile.defaultGhostCloak()

    // Accordion Expansion states
    var isHardwareExpanded by remember { mutableStateOf(true) }
    var isGraphicsExpanded by remember { mutableStateOf(false) }
    var isSensorsExpanded by remember { mutableStateOf(false) }
    var isNetworkExpanded by remember { mutableStateOf(false) }
    var isSystemExpanded by remember { mutableStateOf(false) }
    var isDeadmanExpanded by remember { mutableStateOf(true) }

    // Dialog states
    var showProfileDropdown by remember { mutableStateOf(false) }
    var showSaveProfileDialog by remember { mutableStateOf(false) }
    var newProfileNameInput by remember { mutableStateOf("") }
    var newProfileDescInput by remember { mutableStateOf("") }

    // --- Granular Settings State ---
    var isIdentitySpoofed by remember { mutableStateOf(appInfo.isIdentitySpoofed || true) }
    var preset by remember { mutableStateOf(appInfo.deviceProfilePreset) }
    var brand by remember { mutableStateOf(appInfo.spoofBrand.ifBlank { realHardware.brand }) }
    var model by remember { mutableStateOf(appInfo.spoofModel.ifBlank { realHardware.model }) }
    var device by remember { mutableStateOf(appInfo.spoofDevice.ifBlank { realHardware.device }) }
    var androidId by remember { mutableStateOf(appInfo.spoofAndroidId.ifBlank { IsolationRuleManager.generateAndroidId() }) }
    var imei by remember { mutableStateOf(appInfo.spoofImei.ifBlank { IsolationRuleManager.generateLuhnImei() }) }
    var adId by remember { mutableStateOf(appInfo.spoofAdvertisingId.ifBlank { UUID.randomUUID().toString() }) }
    var mac by remember { mutableStateOf(appInfo.spoofMacAddress.ifBlank { IsolationRuleManager.generateMacAddress() }) }
    var serial by remember { mutableStateOf(appInfo.spoofSerial.ifBlank { IsolationRuleManager.generateSerial() }) }
    var carrier by remember { mutableStateOf(appInfo.spoofCarrierName.ifBlank { realHardware.carrierName }) }
    var simOp by remember { mutableStateOf(appInfo.spoofSimOperator.ifBlank { realHardware.simOperator }) }

    // Category 1: Hardware
    var spoofDrmWidevine by remember { mutableStateOf(appInfo.spoofDrmWidevine) }

    // Category 2: Display & Graphics
    var spoofGpuFingerprint by remember { mutableStateOf(appInfo.spoofGpuFingerprint) }
    var gpuVendor by remember { mutableStateOf(appInfo.spoofGpuVendor.ifBlank { "Qualcomm" }) }
    var gpuRenderer by remember { mutableStateOf(appInfo.spoofGpuRenderer.ifBlank { "Adreno (TM) 750" }) }
    var spoofCanvasNoise by remember { mutableStateOf(appInfo.spoofCanvasNoise) }
    var spoofAudioContext by remember { mutableStateOf(appInfo.spoofAudioContext) }
    var screenResolution by remember { mutableStateOf(appInfo.spoofScreenResolution.ifBlank { "1080x2400" }) }
    var screenDpi by remember { mutableIntStateOf(if (appInfo.spoofScreenDpi > 0) appInfo.spoofScreenDpi else 420) }

    // Category 3: Sensors & Battery
    var anonymizeSensors by remember { mutableStateOf(appInfo.anonymizeSensors) }
    var sensorBiasNeutralizer by remember { mutableStateOf(appInfo.sensorBiasNeutralizer) }
    var fakeBatteryEnabled by remember { mutableStateOf(appInfo.fakeBatteryEnabled) }
    var fakeBatteryLevel by remember { mutableIntStateOf(if (appInfo.fakeBatteryLevel in 1..100) appInfo.fakeBatteryLevel else 85) }
    var knoxWarrantyBitSpoof by remember { mutableStateOf(appInfo.knoxWarrantyBitSpoof) }

    // Category 4: Network & Connectivity
    var spoofWebRtc by remember { mutableStateOf(appInfo.spoofWebRtc) }
    var blockCellTowerTracking by remember { mutableStateOf(appInfo.blockCellTowerTracking) }
    var spoofWifiBssid by remember { mutableStateOf(appInfo.spoofWifiBssid) }
    var hideVpnAndProxy by remember { mutableStateOf(appInfo.hideVpnAndProxy) }
    var spoofLocationEnabled by remember { mutableStateOf(appInfo.spoofLocationEnabled) }
    var latInput by remember { mutableStateOf(appInfo.spoofLatitude.toString()) }
    var lngInput by remember { mutableStateOf(appInfo.spoofLongitude.toString()) }
    var hideMockLocation by remember { mutableStateOf(appInfo.hideMockLocation) }
    var disableAllNetworking by remember { mutableStateOf(appInfo.disableAllNetworking) }

    // Category 5: System & Environment
    var hideRootAndXposed by remember { mutableStateOf(appInfo.hideRootAndXposed) }
    var hideAdbAndDevOptions by remember { mutableStateOf(appInfo.hideAdbAndDevOptions) }
    var blockClipboardRead by remember { mutableStateOf(appInfo.blockClipboardRead) }
    var preventScreenshots by remember { mutableStateOf(appInfo.preventScreenshots) }
    var disableLogcatLogging by remember { mutableStateOf(appInfo.disableLogcatLogging) }
    var autoSyncTimezone by remember { mutableStateOf(appInfo.autoSyncTimezone) }
    var spoofBrowserUserAgent by remember { mutableStateOf(appInfo.spoofBrowserUserAgent) }
    var customUserAgent by remember { mutableStateOf(appInfo.customUserAgent) }

    // Category 6: Deadman Switch
    var deadmanSwitchEnabled by remember { mutableStateOf(appInfo.deadmanSwitchEnabled) }

    // Overrides calculator: count how many settings differ from the active profile
    val overrideCount = remember(
        spoofDrmWidevine, spoofGpuFingerprint, gpuVendor, gpuRenderer, spoofCanvasNoise,
        spoofAudioContext, anonymizeSensors, sensorBiasNeutralizer, fakeBatteryEnabled,
        fakeBatteryLevel, spoofWebRtc, blockCellTowerTracking, spoofWifiBssid, hideVpnAndProxy,
        hideRootAndXposed, hideAdbAndDevOptions, blockClipboardRead, preventScreenshots,
        disableLogcatLogging, autoSyncTimezone, spoofBrowserUserAgent, deadmanSwitchEnabled
    ) {
        var count = 0
        if (spoofDrmWidevine != currentProfile.spoofDrmWidevine) count++
        if (spoofGpuFingerprint != currentProfile.spoofGpuFingerprint) count++
        if (gpuVendor != currentProfile.gpuVendor) count++
        if (gpuRenderer != currentProfile.gpuRenderer) count++
        if (spoofCanvasNoise != currentProfile.spoofCanvasNoise) count++
        if (spoofAudioContext != currentProfile.spoofAudioContext) count++
        if (anonymizeSensors != currentProfile.anonymizeSensors) count++
        if (sensorBiasNeutralizer != currentProfile.sensorBiasNeutralizer) count++
        if (fakeBatteryEnabled != currentProfile.fakeBatteryEnabled) count++
        if (spoofWebRtc != currentProfile.spoofWebRtc) count++
        if (blockCellTowerTracking != currentProfile.blockCellTowerTracking) count++
        if (spoofWifiBssid != currentProfile.spoofWifiBssid) count++
        if (hideVpnAndProxy != currentProfile.hideVpnAndProxy) count++
        if (hideRootAndXposed != currentProfile.hideRootAndXposed) count++
        if (hideAdbAndDevOptions != currentProfile.hideAdbAndDevOptions) count++
        if (blockClipboardRead != currentProfile.blockClipboardRead) count++
        if (preventScreenshots != currentProfile.preventScreenshots) count++
        if (disableLogcatLogging != currentProfile.disableLogcatLogging) count++
        if (autoSyncTimezone != currentProfile.autoSyncTimezone) count++
        if (spoofBrowserUserAgent != currentProfile.spoofBrowserUserAgent) count++
        if (deadmanSwitchEnabled != currentProfile.deadmanSwitchEnabled) count++
        count
    }

    val applyProfile = { prof: PrivacyProfile ->
        selectedProfileId = prof.id
        spoofDrmWidevine = prof.spoofDrmWidevine
        spoofGpuFingerprint = prof.spoofGpuFingerprint
        gpuVendor = prof.gpuVendor
        gpuRenderer = prof.gpuRenderer
        spoofCanvasNoise = prof.spoofCanvasNoise
        spoofAudioContext = prof.spoofAudioContext
        screenResolution = prof.spoofScreenResolution
        screenDpi = prof.spoofScreenDpi
        anonymizeSensors = prof.anonymizeSensors
        sensorBiasNeutralizer = prof.sensorBiasNeutralizer
        fakeBatteryEnabled = prof.fakeBatteryEnabled
        fakeBatteryLevel = prof.fakeBatteryLevel
        spoofWebRtc = prof.spoofWebRtc
        blockCellTowerTracking = prof.blockCellTowerTracking
        spoofWifiBssid = prof.spoofWifiBssid
        hideVpnAndProxy = prof.hideVpnAndProxy
        hideRootAndXposed = prof.hideRootAndXposed
        hideAdbAndDevOptions = prof.hideAdbAndDevOptions
        blockClipboardRead = prof.blockClipboardRead
        preventScreenshots = prof.preventScreenshots
        disableLogcatLogging = prof.disableLogcatLogging
        autoSyncTimezone = prof.autoSyncTimezone
        spoofBrowserUserAgent = prof.spoofBrowserUserAgent
        deadmanSwitchEnabled = prof.deadmanSwitchEnabled
    }

    val randomizeAllHardware = {
        androidId = IsolationRuleManager.generateAndroidId()
        imei = IsolationRuleManager.generateLuhnImei()
        adId = UUID.randomUUID().toString()
        mac = IsolationRuleManager.generateMacAddress()
        serial = IsolationRuleManager.generateSerial()
    }

    val triggerSave = {
        val parsedLat = latInput.toDoubleOrNull()?.coerceIn(-90.0, 90.0) ?: 37.7749
        val parsedLng = lngInput.toDoubleOrNull()?.coerceIn(-180.0, 180.0) ?: -122.4194

        viewModel.saveAppIdentitySpoofConfig(
            appInfo = appInfo,
            enabled = isIdentitySpoofed,
            preset = preset,
            brand = brand,
            model = model,
            device = device,
            androidId = androidId,
            imei = imei,
            advertisingId = adId,
            macAddress = mac,
            carrierName = carrier,
            simOperator = simOp,
            serial = serial,
            hideRootAndXposed = hideRootAndXposed,
            hideAdbAndDevOptions = hideAdbAndDevOptions,
            hideVpnAndProxy = hideVpnAndProxy,
            blockClipboardRead = blockClipboardRead,
            spoofLocationEnabled = spoofLocationEnabled,
            spoofLatitude = parsedLat,
            spoofLongitude = parsedLng,
            hideMockLocation = hideMockLocation,
            preventScreenshots = preventScreenshots,
            hideOtherApps = appInfo.hideOtherApps,
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
            spoofGpuVendor = gpuVendor,
            spoofGpuRenderer = gpuRenderer,
            spoofScreenResolution = screenResolution,
            spoofScreenDpi = screenDpi,
            customUserAgent = customUserAgent,
            spoofWebRtc = spoofWebRtc,
            spoofCanvasNoise = spoofCanvasNoise,
            spoofAudioContext = spoofAudioContext,
            privacyProfileId = selectedProfileId,
            deadmanSwitchEnabled = deadmanSwitchEnabled,
            spoofDrmWidevine = spoofDrmWidevine,
            sensorBiasNeutralizer = sensorBiasNeutralizer,
            spoofWifiBssid = spoofWifiBssid
        )
        onBack()
    }

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .testTag("configure_privacy_screen")
    ) {
        // Top App Bar
        Surface(
            color = scheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = appInfo.appName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            fontFamily = JakartaFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Privacy Suite & Anonymity Cloak",
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariant,
                            fontFamily = JakartaFontFamily
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = triggerSave,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("save_privacy_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply", fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Main Scrollable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Profile Selector & Quick Action Bar
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Active Privacy Profile",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.primary,
                                fontFamily = JakartaFontFamily
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentProfile.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                                fontFamily = JakartaFontFamily
                            )
                        }

                        // Profile Dropdown Button
                        Box {
                            FilledTonalButton(
                                onClick = { showProfileDropdown = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("profile_dropdown_button")
                            ) {
                                Text("Switch Profile", fontSize = 12.sp, fontFamily = JakartaFontFamily)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                            }

                            DropdownMenu(
                                expanded = showProfileDropdown,
                                onDismissRequest = { showProfileDropdown = false }
                            ) {
                                profiles.forEach { prof ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = prof.name + if (prof.isBuiltIn) " (Preset)" else "",
                                                    fontWeight = if (prof.id == selectedProfileId) FontWeight.Bold else FontWeight.Normal,
                                                    fontFamily = JakartaFontFamily
                                                )
                                                Text(
                                                    text = prof.description,
                                                    fontSize = 11.sp,
                                                    color = scheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        },
                                        onClick = {
                                            showProfileDropdown = false
                                            applyProfile(prof)
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Reset Predefined Profiles", fontFamily = JakartaFontFamily) },
                                    leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                                    onClick = {
                                        showProfileDropdown = false
                                        viewModel.resetPrivacyProfilesToDefaults()
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = currentProfile.description,
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                        fontFamily = JakartaFontFamily
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Status Pill & Overrides Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            color = if (overrideCount == 0) scheme.primaryContainer else scheme.tertiaryContainer,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = if (overrideCount == 0) "Profile Match • 0 Overrides"
                                       else "Customized • $overrideCount Overrides Active",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (overrideCount == 0) scheme.onPrimaryContainer else scheme.onTertiaryContainer,
                                fontFamily = JakartaFontFamily,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // 1-Tap Cloak / Randomize IDs
                            FilledTonalButton(
                                onClick = { randomizeAllHardware() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("1-Tap Cloak", fontSize = 11.sp, fontFamily = JakartaFontFamily)
                            }

                            // Save as Profile
                            OutlinedButton(
                                onClick = {
                                    newProfileNameInput = "${appInfo.appName} Shield"
                                    newProfileDescInput = "Custom settings saved from ${appInfo.appName}"
                                    showSaveProfileDialog = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save As", fontSize = 11.sp, fontFamily = JakartaFontFamily)
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // CATEGORY 1: Hardware & Device IDs
            // =========================================================================
            PrivacyCategoryAccordionCard(
                title = "Hardware & Device IDs",
                subtitle = "Android ID, IMEI, IMSI, Serial, MAC & Widevine DRM Unique ID",
                icon = Icons.Default.PhoneAndroid,
                isExpanded = isHardwareExpanded,
                onToggle = { isHardwareExpanded = !isHardwareExpanded },
                badgeCount = if (isIdentitySpoofed) 6 else 0
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Master Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Virtual Hardware Cloak", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Spoof all low-level device identifiers", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                        }
                        Switch(checked = isIdentitySpoofed, onCheckedChange = { isIdentitySpoofed = it })
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // DRM Widevine Unique ID Spoof (Crucial anti-fingerprint shield)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Widevine DRM ID Spoofing", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Masks MediaDrm.deviceUniqueId tracked by Netflix, banking & DRM SDKs", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                        }
                        Switch(checked = spoofDrmWidevine, onCheckedChange = { spoofDrmWidevine = it })
                    }

                    OutlinedTextField(
                        value = androidId,
                        onValueChange = { androidId = it },
                        label = { Text("Android ID (16 hex)") },
                        trailingIcon = {
                            IconButton(onClick = { androidId = IsolationRuleManager.generateAndroidId() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Randomize")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = imei,
                        onValueChange = { imei = it },
                        label = { Text("IMEI / MEID (Luhn-compliant)") },
                        trailingIcon = {
                            IconButton(onClick = { imei = IsolationRuleManager.generateLuhnImei() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Randomize")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = mac,
                        onValueChange = { mac = it },
                        label = { Text("Wi-Fi MAC Address") },
                        trailingIcon = {
                            IconButton(onClick = { mac = IsolationRuleManager.generateMacAddress() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Randomize")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = adId,
                        onValueChange = { adId = it },
                        label = { Text("Google Advertising ID (GAID UUID)") },
                        trailingIcon = {
                            IconButton(onClick = { adId = UUID.randomUUID().toString() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Randomize")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = brand,
                            onValueChange = { brand = it },
                            label = { Text("Brand") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            label = { Text("Model") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }

            // =========================================================================
            // CATEGORY 2: Display & Graphics (Anti-Canvas Fingerprinting)
            // =========================================================================
            PrivacyCategoryAccordionCard(
                title = "Display & Graphics Fingerprint",
                subtitle = "Anti-Canvas Noise, OpenGL ES / WebGL Renderer & Resolution Masking",
                icon = Icons.Default.Computer,
                isExpanded = isGraphicsExpanded,
                onToggle = { isGraphicsExpanded = !isGraphicsExpanded },
                badgeCount = if (spoofGpuFingerprint) 4 else 0
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("GPU & Canvas Shield", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Breaks WebGL and Canvas 2D HTML5 fingerprinting", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                        }
                        Switch(checked = spoofGpuFingerprint, onCheckedChange = { spoofGpuFingerprint = it })
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("WebGL Canvas Noise Injection", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Adds microscopic mathematical jitter to getImageData & toDataURL", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                        }
                        Switch(checked = spoofCanvasNoise, onCheckedChange = { spoofCanvasNoise = it })
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AudioContext Entropy Scrambler", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Masks audio hardware synthesis signatures and frequency bins", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                        }
                        Switch(checked = spoofAudioContext, onCheckedChange = { spoofAudioContext = it })
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = gpuVendor,
                            onValueChange = { gpuVendor = it },
                            label = { Text("GPU Vendor") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = gpuRenderer,
                            onValueChange = { gpuRenderer = it },
                            label = { Text("GPU Renderer") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = screenResolution,
                            onValueChange = { screenResolution = it },
                            label = { Text("Screen Resolution") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = screenDpi.toString(),
                            onValueChange = { screenDpi = it.toIntOrNull() ?: screenDpi },
                            label = { Text("DPI") },
                            modifier = Modifier.weight(0.6f),
                            singleLine = true
                        )
                    }
                }
            }

            // =========================================================================
            // CATEGORY 3: Sensors & Hardware Environment
            // =========================================================================
            PrivacyCategoryAccordionCard(
                title = "Sensors & Hardware Environment",
                subtitle = "Vendor Scrubbing, MEMS Calibration Bias Neutralizer & Battery Entropy",
                icon = Icons.Default.Sensors,
                isExpanded = isSensorsExpanded,
                onToggle = { isSensorsExpanded = !isSensorsExpanded },
                badgeCount = if (anonymizeSensors) 3 else 0
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sensor Vendor Scrubbing", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Replaces physical MEMS serials (Bosch/InvenSense) with generic tags", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                        }
                        Switch(checked = anonymizeSensors, onCheckedChange = { anonymizeSensors = it })
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sensor Calibration Bias Neutralizer", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Removes silicon fabrication micro-offsets used for cross-app linking", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                        }
                        Switch(checked = sensorBiasNeutralizer, onCheckedChange = { sensorBiasNeutralizer = it })
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Battery Status & Power Entropy Mask", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Prevents fractional discharge rate profiling ($fakeBatteryLevel%)", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                        }
                        Switch(checked = fakeBatteryEnabled, onCheckedChange = { fakeBatteryEnabled = it })
                    }

                    if (fakeBatteryEnabled) {
                        Column {
                            Text("Masked Battery Level: $fakeBatteryLevel%", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                            Slider(
                                value = fakeBatteryLevel.toFloat(),
                                onValueChange = { fakeBatteryLevel = it.toInt() },
                                valueRange = 1f..100f
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // CATEGORY 4: Network & Connectivity Shields
            // =========================================================================
            PrivacyCategoryAccordionCard(
                title = "Network & Connectivity Shields",
                subtitle = "WebRTC IP Leak Blocker, Wi-Fi BSSID Spoof & Cell Tower Trilateration",
                icon = Icons.Default.Wifi,
                isExpanded = isNetworkExpanded,
                onToggle = { isNetworkExpanded = !isNetworkExpanded },
                badgeCount = if (spoofWebRtc) 4 else 0
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("WebRTC Local IP Leak Blocker", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Blocks STUN & RFC 1918 host candidate leaks in WebViews & Browsers", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                        }
                        Switch(checked = spoofWebRtc, onCheckedChange = { spoofWebRtc = it })
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cell Tower Trilateration Blocker", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Feeds dummy CID/LAC to TelephonyManager to block cellular geo-tracking", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                        }
                        Switch(checked = blockCellTowerTracking, onCheckedChange = { blockCellTowerTracking = it })
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Wi-Fi BSSID / SSID Spoofing", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Prevents location discovery via public Wi-Fi mapping databases", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                        }
                        Switch(checked = spoofWifiBssid, onCheckedChange = { spoofWifiBssid = it })
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hide VPN & Proxy from App", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Hides tun0 and VPN network capabilities from anti-bot detectors", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                        }
                        Switch(checked = hideVpnAndProxy, onCheckedChange = { hideVpnAndProxy = it })
                    }
                }
            }

            // =========================================================================
            // CATEGORY 5: System & Environment
            // =========================================================================
            PrivacyCategoryAccordionCard(
                title = "System & Environment",
                subtitle = "Root & Dev Options Stealth, Timezone Sync, Clipboard Guard & User-Agent",
                icon = Icons.Default.Shield,
                isExpanded = isSystemExpanded,
                onToggle = { isSystemExpanded = !isSystemExpanded },
                badgeCount = if (hideRootAndXposed) 5 else 0
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hide Root, KSU, APatch & LSPosed", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Conceals su binaries, module mounts and Magisk/Zygisk traces", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                        }
                        Switch(checked = hideRootAndXposed, onCheckedChange = { hideRootAndXposed = it })
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Clipboard Access Guard & Scrubber", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Blocks background apps from reading pasted passwords or seed phrases", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                        }
                        Switch(checked = blockClipboardRead, onCheckedChange = { blockClipboardRead = it })
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Sync Timezone with Exit IP", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Aligns JavaScript and Java TimeZone with active proxy geolocation", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                        }
                        Switch(checked = autoSyncTimezone, onCheckedChange = { autoSyncTimezone = it })
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hide ADB & Developer Options", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Spoofs development_settings_enabled = 0 and adb_enabled = 0", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                        }
                        Switch(checked = hideAdbAndDevOptions, onCheckedChange = { hideAdbAndDevOptions = it })
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Browser & WebView User-Agent Cloak", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Synchronizes Chrome build version with spoofed device properties", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                        }
                        Switch(checked = spoofBrowserUserAgent, onCheckedChange = { spoofBrowserUserAgent = it })
                    }
                }
            }

            // =========================================================================
            // CATEGORY 6: Total Identity Leak Deadman Switch (Fail-Safe)
            // =========================================================================
            PrivacyCategoryAccordionCard(
                title = "Total Identity Leak Deadman Switch",
                subtitle = "Triple Fail-Safe: Instant Process Kill + iptables Network Sever + Alert",
                icon = Icons.Default.Warning,
                isExpanded = isDeadmanExpanded,
                onToggle = { isDeadmanExpanded = !isDeadmanExpanded },
                badgeCount = if (deadmanSwitchEnabled) 1 else 0,
                accentColor = Color(0xFFEF4444)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Arm Deadman Fail-Safe", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Immediate nuclear shutdown if any identity probe or hook bypass occurs", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                        }
                        Switch(checked = deadmanSwitchEnabled, onCheckedChange = { deadmanSwitchEnabled = it })
                    }

                    Surface(
                        color = scheme.errorContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "HOW TOTAL IDENTITY DEFENSE WORKS:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onErrorContainer,
                                fontFamily = JakartaFontFamily
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "1. Process Kill: 'am force-stop' & 'kill -9' terminate the offending app instantly.\n" +
                                        "2. Network Blackhole: Root 'iptables -I OUTPUT -m owner --uid-owner <uid> -j DROP' severs outbound traffic at the kernel.\n" +
                                        "3. Security Alert: High-priority system notification sounds an alarm with the exact bypass vector.",
                                fontSize = 11.sp,
                                color = scheme.onErrorContainer,
                                fontFamily = JakartaFontFamily
                            )
                        }
                    }

                    // Test Simulation and Network Restoration Controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(
                            onClick = {
                                viewModel.simulateIdentityLeak(appInfo.packageName, appInfo.appName, 10099)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Fail-Safe", fontSize = 11.sp, fontFamily = JakartaFontFamily)
                        }

                        OutlinedButton(
                            onClick = { viewModel.restoreAllNetworks() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restore Network", fontSize = 11.sp, fontFamily = JakartaFontFamily)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // Save As Profile Dialog
    if (showSaveProfileDialog) {
        AlertDialog(
            onDismissRequest = { showSaveProfileDialog = false },
            title = { Text("Save as New Profile", fontFamily = JakartaFontFamily, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Save this configuration as a reusable Privacy Profile for other apps.", fontSize = 12.sp)
                    OutlinedTextField(
                        value = newProfileNameInput,
                        onValueChange = { newProfileNameInput = it },
                        label = { Text("Profile Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newProfileDescInput,
                        onValueChange = { newProfileDescInput = it },
                        label = { Text("Description") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newProf = PrivacyProfile(
                            id = "custom_" + UUID.randomUUID().toString().take(8),
                            name = newProfileNameInput.ifBlank { "Custom Profile" },
                            description = newProfileDescInput,
                            isBuiltIn = false,
                            spoofDrmWidevine = spoofDrmWidevine,
                            spoofGpuFingerprint = spoofGpuFingerprint,
                            gpuVendor = gpuVendor,
                            gpuRenderer = gpuRenderer,
                            spoofCanvasNoise = spoofCanvasNoise,
                            spoofAudioContext = spoofAudioContext,
                            spoofScreenResolution = screenResolution,
                            spoofScreenDpi = screenDpi,
                            anonymizeSensors = anonymizeSensors,
                            sensorBiasNeutralizer = sensorBiasNeutralizer,
                            fakeBatteryEnabled = fakeBatteryEnabled,
                            fakeBatteryLevel = fakeBatteryLevel,
                            spoofWebRtc = spoofWebRtc,
                            blockCellTowerTracking = blockCellTowerTracking,
                            spoofWifiBssid = spoofWifiBssid,
                            hideVpnAndProxy = hideVpnAndProxy,
                            hideRootAndXposed = hideRootAndXposed,
                            hideAdbAndDevOptions = hideAdbAndDevOptions,
                            blockClipboardRead = blockClipboardRead,
                            preventScreenshots = preventScreenshots,
                            disableLogcatLogging = disableLogcatLogging,
                            autoSyncTimezone = autoSyncTimezone,
                            spoofBrowserUserAgent = spoofBrowserUserAgent,
                            deadmanSwitchEnabled = deadmanSwitchEnabled
                        )
                        viewModel.savePrivacyProfile(newProf)
                        selectedProfileId = newProf.id
                        showSaveProfileDialog = false
                    }
                ) {
                    Text("Save Profile", fontFamily = JakartaFontFamily)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveProfileDialog = false }) {
                    Text("Cancel", fontFamily = JakartaFontFamily)
                }
            }
        )
    }
}

/**
 * Modern Accordion Card with expandable body, status badges, and rotation animation.
 */
@Composable
private fun PrivacyCategoryAccordionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    badgeCount: Int = 0,
    accentColor: Color? = null,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "rotation")

    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (isExpanded) (accentColor ?: scheme.primary).copy(alpha = 0.5f) else scheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background((accentColor ?: scheme.primary).copy(alpha = 0.12f))
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor ?: scheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                                fontFamily = JakartaFontFamily
                            )
                            if (badgeCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = (accentColor ?: scheme.primary).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = "$badgeCount Active",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accentColor ?: scheme.primary,
                                        fontFamily = JakartaFontFamily,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = subtitle,
                            fontSize = 11.sp,
                            color = scheme.onSurfaceVariant,
                            fontFamily = JakartaFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        modifier = Modifier.rotate(rotation)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
                    content()
                }
            }
        }
    }
}
