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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.model.DeviceProfilePreset
import com.example.isolation.pkg.InstalledAppInfo
import com.example.isolation.ui.components.rememberAppIconBitmap
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.M3AmberAccent
import com.example.ui.theme.M3IndigoAccent
import com.example.ui.theme.M3RoseAccent
import com.example.ui.theme.M3TealAccent
import com.example.ui.theme.M3VioletAccent
import java.util.UUID
import kotlin.random.Random

private data class CityLocationPreset(
    val city: String,
    val country: String,
    val lat: Double,
    val lon: Double,
    val tz: String
)

private val QUICK_CITIES = listOf(
    CityLocationPreset("New York", "USA", 40.7128, -74.0060, "America/New_York"),
    CityLocationPreset("London", "UK", 51.5074, -0.1278, "Europe/London"),
    CityLocationPreset("Tokyo", "Japan", 35.6762, 139.6503, "Asia/Tokyo"),
    CityLocationPreset("Frankfurt", "Germany", 50.1109, 8.6821, "Europe/Berlin"),
    CityLocationPreset("Singapore", "SG", 1.3521, 103.8198, "Asia/Singapore"),
    CityLocationPreset("Dubai", "UAE", 25.2048, 55.2708, "Asia/Dubai")
)

/**
 * Clean, Colorful Material 3 Per-App Privacy & Identity Editor.
 */
@Composable
fun ConfigurePrivacyCategorizedScreen(
    app: InstalledAppInfo,
    viewModel: IsoVaultViewModel,
    onBack: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    var isSpoofEnabled by remember(app.packageName) { mutableStateOf(app.isIdentitySpoofed) }
    var selectedPreset by remember(app.packageName) {
        mutableStateOf(
            if (app.deviceProfilePreset == DeviceProfilePreset.REAL_HARDWARE) {
                DeviceProfilePreset.PIXEL_9_PRO
            } else {
                app.deviceProfilePreset
            }
        )
    }

    var brand by remember(app.packageName) {
        mutableStateOf(app.spoofBrand.ifBlank { selectedPreset.brand })
    }
    var model by remember(app.packageName) {
        mutableStateOf(app.spoofModel.ifBlank { selectedPreset.model })
    }
    var deviceCode by remember(app.packageName) {
        mutableStateOf(app.spoofDevice.ifBlank { selectedPreset.device })
    }
    var androidId by remember(app.packageName) {
        mutableStateOf(app.spoofAndroidId.ifBlank { randomHex(16) })
    }
    var imei by remember(app.packageName) {
        mutableStateOf(app.spoofImei.ifBlank { randomImei() })
    }
    var adId by remember(app.packageName) {
        mutableStateOf(app.spoofAdvertisingId.ifBlank { UUID.randomUUID().toString() })
    }
    var macAddress by remember(app.packageName) {
        mutableStateOf(app.spoofMacAddress.ifBlank { randomMac() })
    }
    var carrierName by remember(app.packageName) {
        mutableStateOf(app.spoofCarrierName.ifBlank { "T-Mobile US" })
    }
    var simOperator by remember(app.packageName) {
        mutableStateOf(app.spoofSimOperator.ifBlank { "310260" })
    }
    var serialNumber by remember(app.packageName) {
        mutableStateOf(app.spoofSerial.ifBlank { randomSerial() })
    }

    // Core Stealth & Shield Toggles
    var hideRootAndXposed by remember(app.packageName) { mutableStateOf(app.hideRootAndXposed) }
    var hideAdbAndDev by remember(app.packageName) { mutableStateOf(app.hideAdbAndDevOptions) }
    var hideVpnAndProxy by remember(app.packageName) { mutableStateOf(app.hideVpnAndProxy) }
    var hideOtherApps by remember(app.packageName) { mutableStateOf(app.hideOtherApps) }
    var preventScreenshots by remember(app.packageName) { mutableStateOf(app.preventScreenshots) }
    var blockClipboard by remember(app.packageName) { mutableStateOf(app.blockClipboardRead) }

    // Location & Timezone
    var spoofLocationEnabled by remember(app.packageName) { mutableStateOf(app.spoofLocationEnabled) }
    var latitudeText by remember(app.packageName) { mutableStateOf(app.spoofLatitude.toString()) }
    var longitudeText by remember(app.packageName) { mutableStateOf(app.spoofLongitude.toString()) }
    var hideMockLocation by remember(app.packageName) { mutableStateOf(app.hideMockLocation) }
    var autoSyncTimezone by remember(app.packageName) { mutableStateOf(app.autoSyncTimezone) }
    var timezoneId by remember(app.packageName) {
        mutableStateOf(app.spoofTimezoneId.ifBlank { "America/New_York" })
    }

    // Tracking & Sensors
    var spoofGpuFingerprint by remember(app.packageName) { mutableStateOf(app.spoofGpuFingerprint) }
    var spoofBrowserUa by remember(app.packageName) { mutableStateOf(app.spoofBrowserUserAgent) }
    var anonymizeSensors by remember(app.packageName) { mutableStateOf(app.anonymizeSensors) }
    var blockCellTracking by remember(app.packageName) { mutableStateOf(app.blockCellTowerTracking) }
    var spoofWebRtc by remember(app.packageName) { mutableStateOf(app.spoofWebRtc) }
    var spoofCanvasNoise by remember(app.packageName) { mutableStateOf(app.spoofCanvasNoise) }
    var deadmanSwitchEnabled by remember(app.packageName) { mutableStateOf(app.deadmanSwitchEnabled) }
    var spoofDrmWidevine by remember(app.packageName) { mutableStateOf(app.spoofDrmWidevine) }

    var showCustomIdsEditor by remember { mutableStateOf(false) }

    fun saveAllChanges(closeAfterSave: Boolean = false) {
        val lat = latitudeText.toDoubleOrNull() ?: 40.7128
        val lon = longitudeText.toDoubleOrNull() ?: -74.0060
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
            disableLogcatLogging = app.disableLogcatLogging,
            fakeBatteryEnabled = app.fakeBatteryEnabled,
            fakeBatteryLevel = app.fakeBatteryLevel,
            knoxWarrantyBitSpoof = app.knoxWarrantyBitSpoof,
            disableAllNetworking = app.disableAllNetworking,
            spoofGpuFingerprint = spoofGpuFingerprint,
            spoofBrowserUserAgent = spoofBrowserUa,
            anonymizeSensors = anonymizeSensors,
            blockCellTowerTracking = blockCellTracking,
            autoSyncTimezone = autoSyncTimezone,
            spoofManufacturer = brand.trim(),
            spoofProduct = deviceCode.trim(),
            spoofBoard = deviceCode.trim(),
            spoofHardware = deviceCode.trim(),
            spoofBootloader = app.spoofBootloader,
            spoofFingerprint = selectedPreset.fingerprint,
            spoofGpuVendor = selectedPreset.glVendor,
            spoofGpuRenderer = selectedPreset.glRenderer,
            spoofCpuCores = 8,
            spoofRamGb = 12,
            spoofScreenResolution = "1440x3120",
            spoofScreenDpi = 480,
            spoofTimezoneId = timezoneId.trim(),
            spoofLocaleTag = "en-US",
            customUserAgent = app.customUserAgent,
            spoofWebRtc = spoofWebRtc,
            spoofCanvasNoise = spoofCanvasNoise,
            spoofAudioContext = true,
            spoofClientHints = true,
            spoofFontEnumeration = true,
            privacyProfileId = app.privacyProfileId,
            deadmanSwitchEnabled = deadmanSwitchEnabled,
            spoofDrmWidevine = spoofDrmWidevine,
            sensorBiasNeutralizer = true,
            spoofWifiBssid = true
        )
        if (closeAfterSave) {
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(horizontal = 16.dp)
            .testTag("configure_privacy_screen")
    ) {
        // Clean Material 3 Top App Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = scheme.surfaceContainerLow,
                    shape = CircleShape,
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = JakartaFontFamily
                    )
                    Text(
                        text = "Privacy & Device Identity",
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                        fontFamily = JakartaFontFamily
                    )
                }
            }

            Button(
                onClick = { saveAllChanges(closeAfterSave = true) },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.testTag("btn_save_privacy_config")
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Save",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            // 1. Colorful App Identity Hero Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    M3IndigoAccent,
                                    M3VioletAccent
                                )
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val iconBitmap = rememberAppIconBitmap(app.packageName, app.icon)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.20f))
                            ) {
                                if (iconBitmap != null) {
                                    Image(
                                        bitmap = iconBitmap,
                                        contentDescription = app.appName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Android,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isSpoofEnabled) "Appears as $brand $model" else "Using Real Device Info",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = JakartaFontFamily
                                )
                                Text(
                                    text = "Android ID: ${androidId.take(8)}... • Carrier: $carrierName",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                            Switch(
                                checked = isSpoofEnabled,
                                onCheckedChange = { isSpoofEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = M3IndigoAccent,
                                    checkedTrackColor = Color.White
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    isSpoofEnabled = true
                                    androidId = randomHex(16)
                                    imei = randomImei()
                                    adId = UUID.randomUUID().toString()
                                    macAddress = randomMac()
                                    serialNumber = randomSerial()
                                },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.22f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_randomize_identity")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Generate New Identity",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                        }
                    }
                }
            }

            // 2. Choose Device Model Preset (Colorful M3 Cards)
            item {
                Column {
                    Text(
                        text = "Choose Device Model",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        fontFamily = JakartaFontFamily
                    )
                    Text(
                        text = "Tap any phone model below to make ${app.appName} see that device",
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                        fontFamily = JakartaFontFamily
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val presets = listOf(
                        DeviceProfilePreset.PIXEL_9_PRO,
                        DeviceProfilePreset.SAMSUNG_S24_ULTRA,
                        DeviceProfilePreset.ONEPLUS_12,
                        DeviceProfilePreset.XIAOMI_14_ULTRA,
                        DeviceProfilePreset.NOTHING_PHONE_2
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        presets.forEachIndexed { idx, preset ->
                            val isSelected = isSpoofEnabled && selectedPreset == preset
                            val cardColor = when (idx % 5) {
                                0 -> M3IndigoAccent
                                1 -> M3TealAccent
                                2 -> M3RoseAccent
                                3 -> M3AmberAccent
                                else -> M3VioletAccent
                            }
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) cardColor.copy(alpha = 0.16f) else scheme.surface
                                ),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) cardColor else scheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .width(156.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        isSpoofEnabled = true
                                        selectedPreset = preset
                                        brand = preset.brand
                                        model = preset.model
                                        deviceCode = preset.device
                                    }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(cardColor.copy(alpha = 0.18f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PhoneAndroid,
                                                contentDescription = null,
                                                tint = cardColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        if (isSelected) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(cardColor)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = preset.label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = scheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontFamily = JakartaFontFamily
                                    )
                                    Text(
                                        text = "${preset.brand} • ${preset.model}",
                                        fontSize = 11.sp,
                                        color = scheme.onSurfaceVariant,
                                        maxLines = 1,
                                        fontFamily = JakartaFontFamily
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Stealth & Root Shields Card (Teal Accent)
            item {
                ColorfulSectionCard(
                    title = "Stealth & Root Shields",
                    subtitle = "Hide root, LSPosed, VPN, and system flags from this app",
                    icon = Icons.Default.Shield,
                    accentColor = M3TealAccent
                ) {
                    PrivacySwitchRow(
                        title = "Hide Root & LSPosed",
                        subtitle = "Blocks Magisk, KernelSU, and Xposed detection checks",
                        checked = hideRootAndXposed,
                        accentColor = M3TealAccent,
                        onCheckedChange = { hideRootAndXposed = it }
                    )
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                    PrivacySwitchRow(
                        title = "Hide Developer Options & ADB",
                        subtitle = "Masks USB debugging and developer mode status",
                        checked = hideAdbAndDev,
                        accentColor = M3TealAccent,
                        onCheckedChange = { hideAdbAndDev = it }
                    )
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                    PrivacySwitchRow(
                        title = "Hide VPN & Proxy Status",
                        subtitle = "Makes network appear as direct Wi-Fi or mobile data",
                        checked = hideVpnAndProxy,
                        accentColor = M3TealAccent,
                        onCheckedChange = { hideVpnAndProxy = it }
                    )
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                    PrivacySwitchRow(
                        title = "Hide Other Installed Apps",
                        subtitle = "Prevents this app from scanning your installed app list",
                        checked = hideOtherApps,
                        accentColor = M3TealAccent,
                        onCheckedChange = { hideOtherApps = it }
                    )
                }
            }

            // 4. Location & Timezone Card (Rose Accent)
            item {
                ColorfulSectionCard(
                    title = "Location & Timezone",
                    subtitle = "Set a virtual GPS location and matching timezone for this app",
                    icon = Icons.Default.LocationOn,
                    accentColor = M3RoseAccent
                ) {
                    PrivacySwitchRow(
                        title = "Use Virtual GPS Location",
                        subtitle = if (spoofLocationEnabled) "Active ($latitudeText, $longitudeText)" else "Off • App sees normal location",
                        checked = spoofLocationEnabled,
                        accentColor = M3RoseAccent,
                        onCheckedChange = { spoofLocationEnabled = it }
                    )

                    if (spoofLocationEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QUICK_CITIES.forEach { preset ->
                                val isCurrentCity = latitudeText == preset.lat.toString() && longitudeText == preset.lon.toString()
                                Surface(
                                    color = if (isCurrentCity) M3RoseAccent else M3RoseAccent.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable {
                                            latitudeText = preset.lat.toString()
                                            longitudeText = preset.lon.toString()
                                            timezoneId = preset.tz
                                        }
                                ) {
                                    Text(
                                        text = "${preset.city}, ${preset.country}",
                                        color = if (isCurrentCity) Color.White else M3RoseAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = JakartaFontFamily,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = latitudeText,
                                onValueChange = { latitudeText = it },
                                label = { Text("Latitude", fontSize = 11.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = longitudeText,
                                onValueChange = { longitudeText = it },
                                label = { Text("Longitude", fontSize = 11.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 5. Anti-Tracking & Hardware Sensors Card (Violet Accent)
            item {
                ColorfulSectionCard(
                    title = "Anti-Tracking & Sensors",
                    subtitle = "Protect against WebView, Canvas, DRM, and sensor fingerprinting",
                    icon = Icons.Default.Public,
                    accentColor = M3VioletAccent
                ) {
                    PrivacySwitchRow(
                        title = "WebView & Canvas Fingerprint Protection",
                        subtitle = "Adds subtle noise to HTML5 Canvas, AudioContext & WebRTC",
                        checked = spoofCanvasNoise,
                        accentColor = M3VioletAccent,
                        onCheckedChange = {
                            spoofCanvasNoise = it
                            spoofWebRtc = it
                        }
                    )
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                    PrivacySwitchRow(
                        title = "Spoof Widevine DRM & GPU Info",
                        subtitle = "Masks hardware DRM MediaDrm ID and OpenGL renderer",
                        checked = spoofDrmWidevine,
                        accentColor = M3VioletAccent,
                        onCheckedChange = {
                            spoofDrmWidevine = it
                            spoofGpuFingerprint = it
                        }
                    )
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                    PrivacySwitchRow(
                        title = "Anonymize Motion & Gyroscope Sensors",
                        subtitle = "Prevents motion-based device tracking",
                        checked = anonymizeSensors,
                        accentColor = M3VioletAccent,
                        onCheckedChange = { anonymizeSensors = it }
                    )
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                    PrivacySwitchRow(
                        title = "Block Background Clipboard Access",
                        subtitle = "Stops this app from silently reading copied text",
                        checked = blockClipboard,
                        accentColor = M3VioletAccent,
                        onCheckedChange = { blockClipboard = it }
                    )
                }
            }

            // 6. Expandable Custom Hardware Identifiers Card (Indigo Accent)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = scheme.surface),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCustomIdsEditor = !showCustomIdsEditor }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(M3IndigoAccent.copy(alpha = 0.14f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = null,
                                        tint = M3IndigoAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Fine-Tune Device IDs (Optional)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = scheme.onSurface,
                                        fontFamily = JakartaFontFamily
                                    )
                                    Text(
                                        text = "Edit custom Brand, Model, Android ID, IMEI, or MAC",
                                        fontSize = 11.sp,
                                        color = scheme.onSurfaceVariant,
                                        fontFamily = JakartaFontFamily
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (showCustomIdsEditor) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant
                            )
                        }

                        if (showCustomIdsEditor) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = brand,
                                    onValueChange = { brand = it },
                                    label = { Text("Brand", fontSize = 11.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = model,
                                    onValueChange = { model = it },
                                    label = { Text("Model", fontSize = 11.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = androidId,
                                onValueChange = { androidId = it },
                                label = { Text("Android ID (16 hex)", fontSize = 11.sp) },
                                trailingIcon = {
                                    IconButton(onClick = { androidId = randomHex(16) }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "New Android ID", modifier = Modifier.size(18.dp))
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = imei,
                                onValueChange = { imei = it },
                                label = { Text("IMEI", fontSize = 11.sp) },
                                trailingIcon = {
                                    IconButton(onClick = { imei = randomImei() }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "New IMEI", modifier = Modifier.size(18.dp))
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = carrierName,
                                    onValueChange = { carrierName = it },
                                    label = { Text("Carrier", fontSize = 11.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = macAddress,
                                    onValueChange = { macAddress = it },
                                    label = { Text("Wi-Fi MAC", fontSize = 11.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorfulSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = scheme.onSurface,
                        fontFamily = JakartaFontFamily
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = scheme.onSurfaceVariant,
                        fontFamily = JakartaFontFamily
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun PrivacySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accentColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = scheme.onSurface,
                fontFamily = JakartaFontFamily
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = scheme.onSurfaceVariant,
                fontFamily = JakartaFontFamily
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor
            )
        )
    }
}

private fun randomHex(len: Int): String {
    val chars = "0123456789abcdef"
    return (1..len).map { chars[Random.nextInt(chars.length)] }.joinToString("")
}

private fun randomImei(): String {
    val tac = listOf("35847631", "35491211", "86741004", "35290611").random()
    val body = (1..6).map { Random.nextInt(0, 10) }.joinToString("")
    return "$tac${body}5"
}

private fun randomMac(): String {
    return (1..6).joinToString(":") { "%02X".format(Random.nextInt(0, 256)) }
}

private fun randomSerial(): String {
    val chars = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    return (1..12).map { chars[Random.nextInt(chars.length)] }.joinToString("")
}
