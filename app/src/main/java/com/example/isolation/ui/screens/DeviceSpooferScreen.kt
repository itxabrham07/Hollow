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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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

    // If user is configuring a specific app's identity, show full-size categorized privacy screen
    editingApp?.let { target ->
        ConfigurePrivacyCategorizedScreen(
            appInfo = target,
            viewModel = viewModel,
            onBack = { editingApp = null }
        )
        return
    }

    val spoofedAppsCount = remember(allApps) {
        allApps.count {
            it.isIdentitySpoofed || it.spoofLocationEnabled || it.preventScreenshots ||
                    it.hideOtherApps || it.disableLogcatLogging || it.fakeBatteryEnabled || it.disableAllNetworking ||
                    it.spoofGpuFingerprint || it.spoofBrowserUserAgent || it.anonymizeSensors || it.blockCellTowerTracking || it.autoSyncTimezone
        }
    }

    val displayedApps = remember(allApps, searchQuery, showAllAppsMode, activeFilter) {
        allApps.filter { app ->
            val hasShields = app.isIdentitySpoofed || app.spoofLocationEnabled || app.preventScreenshots ||
                    app.hideOtherApps || app.disableLogcatLogging || app.fakeBatteryEnabled || app.disableAllNetworking ||
                    app.spoofGpuFingerprint || app.spoofBrowserUserAgent || app.anonymizeSensors || app.blockCellTowerTracking || app.autoSyncTimezone
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

        // Top Header
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

        // Filter Chips row
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
            appInfo.disableAllNetworking ||
            appInfo.spoofGpuFingerprint ||
            appInfo.spoofBrowserUserAgent ||
            appInfo.anonymizeSensors ||
            appInfo.blockCellTowerTracking ||
            appInfo.autoSyncTimezone

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
                    if (appInfo.spoofGpuFingerprint) IdentityMetaChip("GPU: ${appInfo.deviceProfilePreset.glRenderer.take(12)}")
                    if (appInfo.spoofBrowserUserAgent) IdentityMetaChip("UA Cloaked")
                    if (appInfo.anonymizeSensors) IdentityMetaChip("Sensors Scrubbed")
                    if (appInfo.blockCellTowerTracking) IdentityMetaChip("Cell Shield")
                    if (appInfo.autoSyncTimezone) IdentityMetaChip("TZ Synced")
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
