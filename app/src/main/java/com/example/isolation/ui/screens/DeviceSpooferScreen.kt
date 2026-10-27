package com.example.isolation.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
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

private enum class PrivacyAppFilter {
    SPOOFED_ONLY,
    USER_APPS,
    SYSTEM_APPS,
    ALL
}

@Composable
fun DeviceSpooferScreen(
    viewModel: IsoVaultViewModel,
    onBack: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val allApps by viewModel.allInstalledApps.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showAllAppsMode by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf(PrivacyAppFilter.SPOOFED_ONLY) }
    var editingApp by remember { mutableStateOf<InstalledAppInfo?>(null) }

    BackHandler {
        when {
            editingApp != null -> editingApp = null
            showAllAppsMode -> {
                showAllAppsMode = false
                activeFilter = PrivacyAppFilter.SPOOFED_ONLY
                searchQuery = ""
            }
            else -> onBack()
        }
    }

    // If user is configuring a specific app's identity, show full-size configuration screen
    editingApp?.let { target ->
        ConfigureDeviceIdentityFullScreen(
            appInfo = target,
            onBack = { editingApp = null },
            onSave = { isIdentityOn, preset, brand, model, device, androidId, imei, adId, mac, carrier, simOp, serial,
                       hideRoot, hideAdb, hideVpn, blockClip,
                       spoofLoc, lat, lng, hideMock,
                       noScreenshots, hideApps, noLogcat,
                       fakeBat, batLevel, knoxSpoof, noNet ->
                viewModel.saveAppIdentitySpoofConfig(
                    appInfo = target,
                    enabled = isIdentityOn,
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
                    hideRootAndXposed = hideRoot,
                    hideAdbAndDevOptions = hideAdb,
                    hideVpnAndProxy = hideVpn,
                    blockClipboardRead = blockClip,
                    spoofLocationEnabled = spoofLoc,
                    spoofLatitude = lat,
                    spoofLongitude = lng,
                    hideMockLocation = hideMock,
                    preventScreenshots = noScreenshots,
                    hideOtherApps = hideApps,
                    disableLogcatLogging = noLogcat,
                    fakeBatteryEnabled = fakeBat,
                    fakeBatteryLevel = batLevel,
                    knoxWarrantyBitSpoof = knoxSpoof,
                    disableAllNetworking = noNet
                )
                editingApp = null
            },
            onDisable = {
                viewModel.removeAppIdentitySpoof(target)
                editingApp = null
            }
        )
        return
    }

    val spoofedAppsCount = remember(allApps) {
        allApps.count {
            it.isIdentitySpoofed || it.spoofLocationEnabled || it.preventScreenshots ||
                    it.hideOtherApps || it.disableLogcatLogging || it.fakeBatteryEnabled || it.disableAllNetworking
        }
    }

    val displayedApps = remember(allApps, searchQuery, showAllAppsMode, activeFilter) {
        allApps.filter { app ->
            val hasShields = app.isIdentitySpoofed || app.spoofLocationEnabled || app.preventScreenshots ||
                    app.hideOtherApps || app.disableLogcatLogging || app.fakeBatteryEnabled || app.disableAllNetworking
            val matchesFilter = when (activeFilter) {
                PrivacyAppFilter.SPOOFED_ONLY -> hasShields
                PrivacyAppFilter.USER_APPS -> !app.isSystemApp
                PrivacyAppFilter.SYSTEM_APPS -> app.isSystemApp
                PrivacyAppFilter.ALL -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true) ||
                    app.spoofModel.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("device_spoofer_screen")
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Top Header (matches Hide App & Storage Isolate full-screen header)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = {
                        if (showAllAppsMode) {
                            showAllAppsMode = false
                            activeFilter = PrivacyAppFilter.SPOOFED_ONLY
                            searchQuery = ""
                        } else {
                            onBack()
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("device_spoofer_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = scheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (showAllAppsMode) "Select Apps" else "Privacy",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = JakartaFontFamily
                    )
                    Text(
                        text = if (showAllAppsMode) {
                            "Tap any app or switch to spoof IMEI, Android ID, DRM & Root/VPN"
                        } else {
                            "$spoofedAppsCount app${if (spoofedAppsCount == 1) "" else "s"} using virtual hardware identity"
                        },
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = JakartaFontFamily
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (!showAllAppsMode) {
                Button(
                    onClick = {
                        showAllAppsMode = true
                        activeFilter = PrivacyAppFilter.USER_APPS
                    },
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("btn_add_spoof_app")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Apps",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add Apps",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = JakartaFontFamily
                    )
                }
            } else {
                OutlinedButton(
                    onClick = {
                        showAllAppsMode = false
                        activeFilter = PrivacyAppFilter.SPOOFED_ONLY
                        searchQuery = ""
                    },
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("btn_done_spoof_select")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Done",
                        fontSize = 13.sp,
                        fontFamily = JakartaFontFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Full-width Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = if (showAllAppsMode) "Search installed apps..." else "Search spoofed apps...",
                    color = scheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = JakartaFontFamily
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = scheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = scheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = scheme.primary,
                unfocusedBorderColor = scheme.outlineVariant,
                focusedContainerColor = scheme.surface,
                unfocusedContainerColor = scheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("device_spoofer_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        val filterChipColors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = scheme.primaryContainer,
            selectedLabelColor = scheme.onPrimaryContainer,
            containerColor = scheme.surface,
            labelColor = scheme.onSurfaceVariant
        )

        // Filter Chips row (identical to Storage Isolate & Hide App)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = activeFilter == PrivacyAppFilter.SPOOFED_ONLY,
                    onClick = {
                        activeFilter = PrivacyAppFilter.SPOOFED_ONLY
                        showAllAppsMode = false
                    },
                    label = { Text("Spoofed ($spoofedAppsCount)", fontFamily = JakartaFontFamily) },
                    shape = RoundedCornerShape(50),
                    colors = filterChipColors
                )
            }
            item {
                FilterChip(
                    selected = activeFilter == PrivacyAppFilter.USER_APPS,
                    onClick = {
                        activeFilter = PrivacyAppFilter.USER_APPS
                        showAllAppsMode = true
                    },
                    label = { Text("Downloaded", fontFamily = JakartaFontFamily) },
                    shape = RoundedCornerShape(50),
                    colors = filterChipColors
                )
            }
            item {
                FilterChip(
                    selected = activeFilter == PrivacyAppFilter.SYSTEM_APPS,
                    onClick = {
                        activeFilter = PrivacyAppFilter.SYSTEM_APPS
                        showAllAppsMode = true
                    },
                    label = { Text("System", fontFamily = JakartaFontFamily) },
                    shape = RoundedCornerShape(50),
                    colors = filterChipColors
                )
            }
            item {
                FilterChip(
                    selected = activeFilter == PrivacyAppFilter.ALL,
                    onClick = {
                        activeFilter = PrivacyAppFilter.ALL
                        showAllAppsMode = true
                    },
                    label = { Text("All (${allApps.size})", fontFamily = JakartaFontFamily) },
                    shape = RoundedCornerShape(50),
                    colors = filterChipColors
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (displayedApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 28.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(scheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = scheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (activeFilter == PrivacyAppFilter.SPOOFED_ONLY && searchQuery.isBlank()) {
                            "No spoofed apps yet"
                        } else {
                            "No matching apps found"
                        },
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        fontFamily = JakartaFontFamily
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap '+ Add Apps' to choose apps and assign virtual hardware profiles (Pixel 9 Pro, Galaxy S24 Ultra), randomized IMEI/Android ID/DRM, and Root/VPN shields.",
                        fontSize = 13.sp,
                        color = scheme.onSurfaceVariant,
                        fontFamily = JakartaFontFamily
                    )
                    if (!showAllAppsMode) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                showAllAppsMode = true
                                activeFilter = PrivacyAppFilter.USER_APPS
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.testTag("empty_state_add_spoof_app")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Select Apps to Spoof Identity",
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = JakartaFontFamily
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                items(displayedApps, key = { it.packageName }) { appInfo ->
                    SpoofedAppCard(
                        appInfo = appInfo,
                        onConfigure = { editingApp = appInfo },
                        onToggleSpoof = { enabled ->
                            if (enabled) {
                                // Quick-enable with randomized profile or open full config
                                val preset = if (appInfo.deviceProfilePreset == DeviceProfilePreset.CUSTOM) {
                                    DeviceProfilePreset.PIXEL_9_PRO
                                } else {
                                    appInfo.deviceProfilePreset
                                }
                                viewModel.saveAppIdentitySpoofConfig(
                                    appInfo = appInfo,
                                    enabled = true,
                                    preset = preset,
                                    brand = appInfo.spoofBrand.ifBlank { preset.brand },
                                    model = appInfo.spoofModel.ifBlank { preset.model },
                                    device = appInfo.spoofDevice.ifBlank { preset.device },
                                    androidId = appInfo.spoofAndroidId.ifBlank { IsolationRuleManager.generateAndroidId() },
                                    imei = appInfo.spoofImei.ifBlank { IsolationRuleManager.generateLuhnImei() },
                                    advertisingId = appInfo.spoofAdvertisingId.ifBlank { IsolationRuleManager.generateAdvertisingId() },
                                    macAddress = appInfo.spoofMacAddress.ifBlank { IsolationRuleManager.generateMacAddress() },
                                    carrierName = appInfo.spoofCarrierName.ifBlank { "T-Mobile" },
                                    simOperator = appInfo.spoofSimOperator.ifBlank { "310260" },
                                    serial = appInfo.spoofSerial.ifBlank { IsolationRuleManager.generateSerial() },
                                    hideRootAndXposed = appInfo.hideRootAndXposed,
                                    hideAdbAndDevOptions = appInfo.hideAdbAndDevOptions,
                                    hideVpnAndProxy = appInfo.hideVpnAndProxy,
                                    blockClipboardRead = appInfo.blockClipboardRead
                                )
                            } else {
                                viewModel.removeAppIdentitySpoof(appInfo)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SpoofedAppCard(
    appInfo: InstalledAppInfo,
    onConfigure: () -> Unit,
    onToggleSpoof: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isSpoofed = appInfo.isIdentitySpoofed
    val hasActiveShields = isSpoofed ||
            appInfo.spoofLocationEnabled ||
            appInfo.preventScreenshots ||
            appInfo.hideOtherApps ||
            appInfo.disableLogcatLogging ||
            appInfo.fakeBatteryEnabled ||
            appInfo.disableAllNetworking

    val displayBrand = appInfo.spoofBrand.ifBlank { appInfo.deviceProfilePreset.brand }
    val displayModel = appInfo.spoofModel.ifBlank { appInfo.deviceProfilePreset.model }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (hasActiveShields) scheme.primaryContainer.copy(alpha = 0.22f) else scheme.surface
        ),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (hasActiveShields) scheme.primary.copy(alpha = 0.45f) else scheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConfigure() }
            .testTag("spoofed_app_card_${appInfo.packageName}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
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
                    val iconBitmap = remember(appInfo.packageName, appInfo.icon) {
                        try {
                            appInfo.icon?.toBitmap(96, 96)?.asImageBitmap()
                        } catch (_: Throwable) {
                            null
                        }
                    }
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = appInfo.appName,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(scheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = scheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appInfo.appName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                            fontFamily = JakartaFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        when {
                            isSpoofed -> {
                                Text(
                                    text = "$displayBrand $displayModel • Tap to customize",
                                    fontSize = 12.sp,
                                    color = scheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = JakartaFontFamily,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            hasActiveShields -> {
                                Text(
                                    text = "Privacy Shields Active (Real Hardware ID) • Tap to edit",
                                    fontSize = 12.sp,
                                    color = scheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = JakartaFontFamily,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            else -> {
                                Text(
                                    text = "Not spoofed • Tap to configure privacy & identity",
                                    fontSize = 12.sp,
                                    color = scheme.onSurfaceVariant,
                                    fontFamily = JakartaFontFamily,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = hasActiveShields,
                    onCheckedChange = { onToggleSpoof(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = scheme.onPrimary,
                        checkedTrackColor = scheme.primary
                    ),
                    modifier = Modifier.testTag("switch_spoof_${appInfo.packageName}")
                )
            }

            if (hasActiveShields) {
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    if (isSpoofed && appInfo.spoofAndroidId.isNotBlank()) {
                        IdentityMetaChip("AID: ${appInfo.spoofAndroidId.take(8)}…")
                    }
                    if (isSpoofed && appInfo.spoofImei.isNotBlank()) {
                        IdentityMetaChip("IMEI: ${appInfo.spoofImei.take(6)}…${appInfo.spoofImei.takeLast(3)}")
                    }
                    if (isSpoofed && appInfo.spoofMacAddress.isNotBlank()) {
                        IdentityMetaChip("MAC: ${appInfo.spoofMacAddress}")
                    }
                    if (appInfo.spoofLocationEnabled) {
                        IdentityMetaChip("GPS: ${"%.2f".format(appInfo.spoofLatitude)}, ${"%.2f".format(appInfo.spoofLongitude)}")
                    }
                    if (appInfo.preventScreenshots) IdentityMetaChip("No Screenshots")
                    if (appInfo.hideOtherApps) IdentityMetaChip("Apps Hidden")
                    if (appInfo.disableLogcatLogging) IdentityMetaChip("No Logcat")
                    if (appInfo.fakeBatteryEnabled) IdentityMetaChip("Bat ${appInfo.fakeBatteryLevel}%")
                    if (appInfo.knoxWarrantyBitSpoof) IdentityMetaChip("Knox 0x0")
                    if (appInfo.disableAllNetworking) IdentityMetaChip("Net Blocked")
                    if (appInfo.hideRootAndXposed) IdentityMetaChip("Root/LSPosed Hidden")
                    if (appInfo.hideAdbAndDevOptions) IdentityMetaChip("ADB Hidden")
                    if (appInfo.hideVpnAndProxy) IdentityMetaChip("VPN Hidden")
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        onClick = { onToggleSpoof(false) },
                        modifier = Modifier.testTag("btn_restore_identity_${appInfo.packageName}")
                    ) {
                        Text(
                            text = "Reset All",
                            fontSize = 12.sp,
                            fontFamily = JakartaFontFamily
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onConfigure,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_edit_identity_${appInfo.packageName}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Configure Privacy",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JakartaFontFamily
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IdentityMetaChip(label: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.65f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = scheme.onSurfaceVariant
        )
    }
}

private data class CityLocationPreset(val name: String, val lat: Double, val lng: Double)

private val FAVORITE_CITY_PRESETS = listOf(
    CityLocationPreset("San Francisco", 37.7749, -122.4194),
    CityLocationPreset("New York", 40.7128, -74.0060),
    CityLocationPreset("London", 51.5074, -0.1278),
    CityLocationPreset("Tokyo", 35.6762, 139.6503),
    CityLocationPreset("Paris", 48.8566, 2.3522),
    CityLocationPreset("Sydney", -33.8688, 151.2093)
)

@Composable
private fun ConfigureDeviceIdentityFullScreen(
    appInfo: InstalledAppInfo,
    onBack: () -> Unit,
    onSave: (
        isIdentityOn: Boolean,
        preset: DeviceProfilePreset,
        brand: String,
        model: String,
        device: String,
        androidId: String,
        imei: String,
        adId: String,
        mac: String,
        carrier: String,
        simOp: String,
        serial: String,
        hideRoot: Boolean,
        hideAdb: Boolean,
        hideVpn: Boolean,
        blockClip: Boolean,
        spoofLoc: Boolean,
        lat: Double,
        lng: Double,
        hideMock: Boolean,
        noScreenshots: Boolean,
        hideApps: Boolean,
        noLogcat: Boolean,
        fakeBat: Boolean,
        batLevel: Int,
        knoxSpoof: Boolean,
        noNet: Boolean
    ) -> Unit,
    onDisable: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    // Master toggle for Hardware Identity Spoofing (User can choose to spoof identity OR keep real ID while using other privacy options)
    var isIdentitySpoofed by remember {
        mutableStateOf(
            if (!appInfo.isIdentitySpoofed &&
                !appInfo.spoofLocationEnabled &&
                !appInfo.preventScreenshots &&
                !appInfo.hideOtherApps &&
                !appInfo.disableLogcatLogging &&
                !appInfo.fakeBatteryEnabled &&
                !appInfo.disableAllNetworking
            ) {
                true // Default to true when opening a fresh unconfigured app
            } else {
                appInfo.isIdentitySpoofed
            }
        )
    }

    var preset by remember { mutableStateOf(appInfo.deviceProfilePreset) }
    var brand by remember { mutableStateOf(appInfo.spoofBrand.ifBlank { preset.brand }) }
    var model by remember { mutableStateOf(appInfo.spoofModel.ifBlank { preset.model }) }
    var device by remember { mutableStateOf(appInfo.spoofDevice.ifBlank { preset.device }) }
    var androidId by remember { mutableStateOf(appInfo.spoofAndroidId.ifBlank { IsolationRuleManager.generateAndroidId() }) }
    var imei by remember { mutableStateOf(appInfo.spoofImei.ifBlank { IsolationRuleManager.generateLuhnImei() }) }
    var adId by remember { mutableStateOf(appInfo.spoofAdvertisingId.ifBlank { IsolationRuleManager.generateAdvertisingId() }) }
    var mac by remember { mutableStateOf(appInfo.spoofMacAddress.ifBlank { IsolationRuleManager.generateMacAddress() }) }
    var carrier by remember { mutableStateOf(appInfo.spoofCarrierName.ifBlank { "T-Mobile" }) }
    var simOp by remember { mutableStateOf(appInfo.spoofSimOperator.ifBlank { "310260" }) }
    var serial by remember { mutableStateOf(appInfo.spoofSerial.ifBlank { IsolationRuleManager.generateSerial() }) }

    // Security & Environment Shields
    var hideRoot by remember { mutableStateOf(appInfo.hideRootAndXposed) }
    var hideAdb by remember { mutableStateOf(appInfo.hideAdbAndDevOptions) }
    var hideVpn by remember { mutableStateOf(appInfo.hideVpnAndProxy) }
    var blockClip by remember { mutableStateOf(appInfo.blockClipboardRead) }

    // Mock Location Options
    var spoofLocationEnabled by remember { mutableStateOf(appInfo.spoofLocationEnabled) }
    var latInput by remember { mutableStateOf(appInfo.spoofLatitude.toString()) }
    var lngInput by remember { mutableStateOf(appInfo.spoofLongitude.toString()) }
    var hideMockLocation by remember { mutableStateOf(appInfo.hideMockLocation) }

    // Privacy & System Guards
    var preventScreenshots by remember { mutableStateOf(appInfo.preventScreenshots) }
    var hideOtherApps by remember { mutableStateOf(appInfo.hideOtherApps) }
    var disableLogcatLogging by remember { mutableStateOf(appInfo.disableLogcatLogging) }
    var fakeBatteryEnabled by remember { mutableStateOf(appInfo.fakeBatteryEnabled) }
    var fakeBatteryLevel by remember { mutableStateOf(appInfo.fakeBatteryLevel) }
    var knoxWarrantyBitSpoof by remember { mutableStateOf(appInfo.knoxWarrantyBitSpoof) }

    // Network Kill Switch
    var disableAllNetworking by remember { mutableStateOf(appInfo.disableAllNetworking) }

    val triggerSave = {
        val parsedLat = latInput.toDoubleOrNull()?.coerceIn(-90.0, 90.0) ?: 37.7749
        val parsedLng = lngInput.toDoubleOrNull()?.coerceIn(-180.0, 180.0) ?: -122.4194
        onSave(
            isIdentitySpoofed,
            preset,
            brand,
            model,
            device,
            androidId,
            imei,
            adId,
            mac,
            carrier,
            simOp,
            serial,
            hideRoot,
            hideAdb,
            hideVpn,
            blockClip,
            spoofLocationEnabled,
            parsedLat,
            parsedLng,
            hideMockLocation,
            preventScreenshots,
            hideOtherApps,
            disableLogcatLogging,
            fakeBatteryEnabled,
            fakeBatteryLevel.coerceIn(1, 100),
            knoxWarrantyBitSpoof,
            disableAllNetworking
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("configure_identity_fullscreen")
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Top Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = scheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                val iconBitmap = remember(appInfo.packageName, appInfo.icon) {
                    try {
                        appInfo.icon?.toBitmap(80, 80)?.asImageBitmap()
                    } catch (_: Throwable) {
                        null
                    }
                }
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = appInfo.appName,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appInfo.appName,
                        fontSize = 18.sp,
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
                        fontFamily = JakartaFontFamily
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = triggerSave,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.testTag("btn_save_identity_spoof")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
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

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // =========================================================================
            // SECTION 1: HARDWARE IDENTITY SPOOFING (Optional Master Toggle)
            // =========================================================================
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isIdentitySpoofed) scheme.primaryContainer.copy(alpha = 0.16f) else scheme.surface
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.dp,
                    if (isIdentitySpoofed) scheme.primary.copy(alpha = 0.45f) else scheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PrivacyShieldToggleRow(
                        title = "Spoof Hardware Device Identity",
                        subtitle = if (isIdentitySpoofed) {
                            "Active: Spoofing Model, Android ID, IMEI, DRM, MAC & Serial"
                        } else {
                            "Disabled: App sees your real phone model & hardware IDs"
                        },
                        checked = isIdentitySpoofed,
                        onCheckedChange = { isIdentitySpoofed = it }
                    )

                    if (isIdentitySpoofed) {
                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

                        FilledTonalButton(
                            onClick = {
                                val presets = DeviceProfilePreset.entries.filter { it != DeviceProfilePreset.CUSTOM }
                                val randomPreset = presets.random()
                                preset = randomPreset
                                brand = randomPreset.brand
                                model = randomPreset.model
                                device = randomPreset.device
                                androidId = IsolationRuleManager.generateAndroidId()
                                imei = IsolationRuleManager.generateLuhnImei()
                                adId = IsolationRuleManager.generateAdvertisingId()
                                mac = IsolationRuleManager.generateMacAddress()
                                serial = IsolationRuleManager.generateSerial()
                                val (cName, cCode) = IsolationRuleManager.generateRandomCarrier()
                                carrier = cName
                                simOp = cCode
                            },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_randomize_all_identity")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Casino,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "1-Tap Randomize Full Device Identity",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = JakartaFontFamily
                            )
                        }

                        Text(
                            text = "Device Model Preset",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            fontFamily = JakartaFontFamily
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            DeviceProfilePreset.entries.forEach { item ->
                                FilterChip(
                                    selected = preset == item,
                                    onClick = {
                                        preset = item
                                        if (item != DeviceProfilePreset.CUSTOM) {
                                            brand = item.brand
                                            model = item.model
                                            device = item.device
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = item.label,
                                            fontSize = 12.sp,
                                            fontFamily = JakartaFontFamily
                                        )
                                    },
                                    shape = RoundedCornerShape(50)
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = brand,
                                onValueChange = {
                                    brand = it
                                    preset = DeviceProfilePreset.CUSTOM
                                },
                                label = { Text("Brand", fontSize = 11.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = model,
                                onValueChange = {
                                    model = it
                                    preset = DeviceProfilePreset.CUSTOM
                                },
                                label = { Text("Model", fontSize = 11.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1.3f)
                            )
                        }

                        IdentifierFieldWithRandomize(
                            label = "Android ID (16-hex) & Widevine DRM Seed",
                            value = androidId,
                            onValueChange = { androidId = it },
                            onRandomize = { androidId = IsolationRuleManager.generateAndroidId() }
                        )

                        IdentifierFieldWithRandomize(
                            label = "IMEI / MEID (15-digit Luhn Valid)",
                            value = imei,
                            onValueChange = { imei = it },
                            onRandomize = { imei = IsolationRuleManager.generateLuhnImei() }
                        )

                        IdentifierFieldWithRandomize(
                            label = "Google Advertising ID (AAID)",
                            value = adId,
                            onValueChange = { adId = it },
                            onRandomize = { adId = IsolationRuleManager.generateAdvertisingId() }
                        )

                        IdentifierFieldWithRandomize(
                            label = "Wi-Fi & Bluetooth MAC Address",
                            value = mac,
                            onValueChange = { mac = it },
                            onRandomize = { mac = IsolationRuleManager.generateMacAddress() }
                        )

                        IdentifierFieldWithRandomize(
                            label = "Hardware Serial (Build.SERIAL)",
                            value = serial,
                            onValueChange = { serial = it },
                            onRandomize = { serial = IsolationRuleManager.generateSerial() }
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = carrier,
                                onValueChange = { carrier = it },
                                label = { Text("SIM Carrier", fontSize = 11.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = simOp,
                                onValueChange = { simOp = it },
                                label = { Text("MCC/MNC", fontSize = 11.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(0.8f)
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // SECTION 2: MOCK / SPOOF LOCATION (GPS & Geolocation)
            // =========================================================================
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Mock & Geolocation Options",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        fontFamily = JakartaFontFamily
                    )

                    PrivacyShieldToggleRow(
                        title = "Spoof GPS Location",
                        subtitle = "Overrides LocationManager & GPS coordinates for this app",
                        checked = spoofLocationEnabled,
                        onCheckedChange = { spoofLocationEnabled = it }
                    )

                    if (spoofLocationEnabled) {
                        Text(
                            text = "Quick Favorite Locations",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurfaceVariant,
                            fontFamily = JakartaFontFamily
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            FAVORITE_CITY_PRESETS.forEach { city ->
                                val isSelected = latInput == city.lat.toString() && lngInput == city.lng.toString()
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        latInput = city.lat.toString()
                                        lngInput = city.lng.toString()
                                    },
                                    label = {
                                        Text(
                                            text = city.name,
                                            fontSize = 12.sp,
                                            fontFamily = JakartaFontFamily
                                        )
                                    },
                                    shape = RoundedCornerShape(50)
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = latInput,
                                onValueChange = { latInput = it },
                                label = { Text("Latitude", fontSize = 11.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = lngInput,
                                onValueChange = { lngInput = it },
                                label = { Text("Longitude", fontSize = 11.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

                    PrivacyShieldToggleRow(
                        title = "Hide Mock Location Status",
                        subtitle = "Forces Location.isFromMockProvider() & isMock() to return false",
                        checked = hideMockLocation,
                        onCheckedChange = { hideMockLocation = it }
                    )
                }
            }

            // =========================================================================
            // SECTION 3: PRIVACY & SYSTEM GUARDS (App Cloner Options)
            // =========================================================================
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Privacy & System Guards",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        fontFamily = JakartaFontFamily
                    )

                    PrivacyShieldToggleRow(
                        title = "Prevent Screenshots & Screen Recording",
                        subtitle = "Enforces WindowManager FLAG_SECURE on all app activities",
                        checked = preventScreenshots,
                        onCheckedChange = { preventScreenshots = it }
                    )

                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

                    PrivacyShieldToggleRow(
                        title = "Hide Other Installed Apps",
                        subtitle = "Blocks package snooping (getInstalledPackages sees only system & self)",
                        checked = hideOtherApps,
                        onCheckedChange = { hideOtherApps = it }
                    )

                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

                    PrivacyShieldToggleRow(
                        title = "Disable Logcat Logging",
                        subtitle = "Silences android.util.Log calls so app cannot leak data to logs",
                        checked = disableLogcatLogging,
                        onCheckedChange = { disableLogcatLogging = it }
                    )

                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

                    PrivacyShieldToggleRow(
                        title = "Fake Battery Level & Charging State",
                        subtitle = "Spoofs BatteryManager capacity ($fakeBatteryLevel%) & AC charging",
                        checked = fakeBatteryEnabled,
                        onCheckedChange = { fakeBatteryEnabled = it }
                    )

                    if (fakeBatteryEnabled) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(100, 85, 65, 40, 20).forEach { lvl ->
                                FilterChip(
                                    selected = fakeBatteryLevel == lvl,
                                    onClick = { fakeBatteryLevel = lvl },
                                    label = {
                                        Text(
                                            text = "$lvl%",
                                            fontSize = 12.sp,
                                            fontFamily = JakartaFontFamily
                                        )
                                    },
                                    shape = RoundedCornerShape(50)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

                    PrivacyShieldToggleRow(
                        title = "Samsung Knox 0x0 Warranty Bit",
                        subtitle = "Spoofs ro.boot.warranty_bit=0 & ro.warranty_bit=0",
                        checked = knoxWarrantyBitSpoof,
                        onCheckedChange = { knoxWarrantyBitSpoof = it }
                    )
                }
            }

            // =========================================================================
            // SECTION 4: SECURITY & ENVIRONMENT SHIELDS
            // =========================================================================
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Security & Environment Shields",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        fontFamily = JakartaFontFamily
                    )

                    PrivacyShieldToggleRow(
                        title = "Hide Root, Magisk, KernelSU & LSPosed",
                        subtitle = "Blocks root/Xposed package queries, stacktraces & spoofs release-keys",
                        checked = hideRoot,
                        onCheckedChange = { hideRoot = it }
                    )

                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

                    PrivacyShieldToggleRow(
                        title = "Hide Developer Options & USB Debugging",
                        subtitle = "Spoofs adb_enabled=0 & development_settings_enabled=0",
                        checked = hideAdb,
                        onCheckedChange = { hideAdb = it }
                    )

                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

                    PrivacyShieldToggleRow(
                        title = "Hide VPN & Proxy Status",
                        subtitle = "Hides tun0/wg0 interfaces & TRANSPORT_VPN capability",
                        checked = hideVpn,
                        onCheckedChange = { hideVpn = it }
                    )

                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

                    PrivacyShieldToggleRow(
                        title = "Block Background Clipboard Snooping",
                        subtitle = "Prevents this app from silently reading copied text",
                        checked = blockClip,
                        onCheckedChange = { blockClip = it }
                    )
                }
            }

            // =========================================================================
            // SECTION 5: NETWORK KILL SWITCH
            // =========================================================================
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (disableAllNetworking) scheme.errorContainer.copy(alpha = 0.25f) else scheme.surface
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.dp,
                    if (disableAllNetworking) scheme.error.copy(alpha = 0.5f) else scheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Network & Connectivity Control",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        fontFamily = JakartaFontFamily
                    )

                    PrivacyShieldToggleRow(
                        title = "Disable All Networking (Firewall Kill Switch)",
                        subtitle = "Blocks all outbound Socket & HTTP connections for this app",
                        checked = disableAllNetworking,
                        onCheckedChange = { disableAllNetworking = it }
                    )
                }
            }

            // Bottom Action Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                OutlinedButton(
                    onClick = onDisable,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, scheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Reset All",
                        color = scheme.error,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = JakartaFontFamily
                    )
                }
                Button(
                    onClick = triggerSave,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Save & Apply",
                        fontWeight = FontWeight.Bold,
                        fontFamily = JakartaFontFamily
                    )
                }
            }
        }
    }
}

@Composable
private fun IdentifierFieldWithRandomize(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onRandomize: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        trailingIcon = {
            IconButton(onClick = onRandomize) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Randomize $label",
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PrivacyShieldToggleRow(
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
            .padding(vertical = 2.dp)
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
                fontFamily = JakartaFontFamily
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
