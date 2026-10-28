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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.model.AppSettings
import com.example.isolation.model.DeviceProfilePreset
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.model.PrivacyProfile
import com.example.isolation.model.VpnProxyPresets
import com.example.isolation.pkg.InstalledAppInfo
import com.example.isolation.ui.components.rememberAppIconBitmap
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.M3AmberAccent
import com.example.ui.theme.M3IndigoAccent
import com.example.ui.theme.M3RoseAccent
import com.example.ui.theme.M3SkyAccent
import com.example.ui.theme.M3TealAccent
import com.example.ui.theme.M3VioletAccent

private enum class PrivacyAppFilter(val label: String) {
    USER_APPS("Installed Apps"),
    PROTECTED("Protected"),
    VPN_ROUTED("VPN Routed"),
    LOCATION_SPOOFED("GPS Spoofed"),
    ALL_APPS("All & System")
}

/**
 * Colorful Material 3 App Privacy, Hardware Identity, Per-App VPN & Anti-Tracking Screen.
 * Shows all installed apps immediately with 1-tap switches, quick profile assignment,
 * per-app VPN toggles, Global V2Ray/Proxy Suite manager, and full 50+ setting Privacy Studio.
 */
@Composable
fun DeviceSpooferScreen(
    viewModel: IsoVaultViewModel,
    onBack: (() -> Unit)? = null,
    onDetailOpenChanged: (Boolean) -> Unit = {}
) {
    val allApps by viewModel.allInstalledApps.collectAsState()
    val privacyProfiles by viewModel.privacyProfiles.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val deadmanIncidents by viewModel.deadmanBlockedIncidents.collectAsState()
    val scheme = MaterialTheme.colorScheme

    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(PrivacyAppFilter.USER_APPS) }
    var selectedAppPkg by remember { mutableStateOf<String?>(null) }
    var showProfilesDialog by remember { mutableStateOf(false) }
    var showVpnSuiteDialog by remember { mutableStateOf(false) }

    val selectedApp = remember(allApps, selectedAppPkg) {
        selectedAppPkg?.let { pkg -> allApps.firstOrNull { it.packageName == pkg } }
    }

    LaunchedEffect(selectedApp != null) {
        onDetailOpenChanged(selectedApp != null)
    }

    LaunchedEffect(Unit) {
        if (allApps.isEmpty()) {
            viewModel.loadInstalledApps()
        }
    }

    if (selectedApp != null) {
        BackHandler {
            selectedAppPkg = null
        }
        ConfigurePrivacyCategorizedScreen(
            app = selectedApp,
            viewModel = viewModel,
            onBack = { selectedAppPkg = null }
        )
        return
    }

    if (onBack != null) {
        BackHandler { onBack() }
    }

    val protectedCount = remember(allApps) {
        allApps.count { it.isIdentitySpoofed || it.spoofLocationEnabled || it.vpnProxyEnabled }
    }
    val vpnRoutedCount = remember(allApps) {
        allApps.count { it.vpnProxyEnabled }
    }
    val activeExitNode = remember(settings.activeProxyServerId, settings.customProxyServers) {
        VpnProxyPresets.findById(settings.activeProxyServerId, settings.customProxyServers)
    }

    val filteredApps = remember(allApps, searchQuery, activeFilter) {
        val q = searchQuery.trim().lowercase()
        allApps.filter { app ->
            val matchesFilter = when (activeFilter) {
                PrivacyAppFilter.USER_APPS -> !app.isSystemApp || app.isIdentitySpoofed || app.vpnProxyEnabled
                PrivacyAppFilter.PROTECTED -> app.isIdentitySpoofed || app.spoofLocationEnabled || app.vpnProxyEnabled
                PrivacyAppFilter.VPN_ROUTED -> app.vpnProxyEnabled
                PrivacyAppFilter.LOCATION_SPOOFED -> app.spoofLocationEnabled
                PrivacyAppFilter.ALL_APPS -> true
            }
            val matchesSearch = q.isEmpty() ||
                app.appName.lowercase().contains(q) ||
                app.packageName.lowercase().contains(q) ||
                app.spoofModel.lowercase().contains(q)
            matchesFilter && matchesSearch
        }.sortedWith(
            compareByDescending<InstalledAppInfo> { it.isIdentitySpoofed || it.vpnProxyEnabled || it.spoofLocationEnabled }
                .thenBy { it.appName.lowercase() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(horizontal = 16.dp)
            .testTag("device_spoofer_screen")
    ) {
        // Top Header Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    Surface(
                        color = scheme.surfaceContainerLow,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { onBack() }
                            .testTag("btn_back_from_spoofer")
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
                }
                Column {
                    Text(
                        text = "App Privacy & Identity",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = scheme.onSurface,
                        fontFamily = JakartaFontFamily
                    )
                    Text(
                        text = "Device IDs, browser fingerprint, sensors, GPS & per-app VPN",
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                        fontFamily = JakartaFontFamily
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = if (settings.vpnTunnelEnabled) M3SkyAccent.copy(alpha = 0.16f) else scheme.surfaceContainerLow,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(
                        1.dp,
                        if (settings.vpnTunnelEnabled) M3SkyAccent else scheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { showVpnSuiteDialog = true }
                        .testTag("btn_open_vpn_suite")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = if (settings.vpnTunnelEnabled) M3SkyAccent else scheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (settings.vpnTunnelEnabled) activeExitNode.protocol.badge else "VPN",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (settings.vpnTunnelEnabled) M3SkyAccent else scheme.onSurface,
                            fontFamily = JakartaFontFamily
                        )
                    }
                }

                Surface(
                    color = scheme.secondaryContainer,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { showProfilesDialog = true }
                        .testTag("btn_manage_privacy_profiles")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = scheme.onSecondaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Profiles (${privacyProfiles.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSecondaryContainer,
                            fontFamily = JakartaFontFamily
                        )
                    }
                }
            }
        }

        // Colorful Summary Hero Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            M3VioletAccent,
                            M3IndigoAccent
                        )
                    )
                )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PhonelinkSetup,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "$protectedCount Protected · $vpnRoutedCount VPN Routed",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontFamily = JakartaFontFamily
                            )
                            Text(
                                text = if (settings.vpnTunnelEnabled) {
                                    "Exit Node: ${activeExitNode.name} (${activeExitNode.exitIp})"
                                } else {
                                    "Tap any app for 50+ hardware, browser, GPS, sensor & VPN controls"
                                },
                                fontSize = 11.5.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = JakartaFontFamily
                            )
                        }
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { showVpnSuiteDialog = true }
                    ) {
                        Text(
                            text = if (settings.vpnTunnelEnabled) "Tunnel ON" else "Setup VPN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontFamily = JakartaFontFamily
                        )
                    }
                }

                if (deadmanIncidents.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = M3RoseAccent.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GppBad,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${deadmanIncidents.size} app(s) severed by Deadman Switch",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { viewModel.restoreAllNetworks() }
                            ) {
                                Text(
                                    text = "Restore All",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = M3RoseAccent,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    fontFamily = JakartaFontFamily
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Search apps, package names, or spoofed models...",
                    fontSize = 13.sp,
                    fontFamily = JakartaFontFamily
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = scheme.surfaceContainerLow,
                unfocusedContainerColor = scheme.surfaceContainerLow,
                focusedBorderColor = scheme.primary,
                unfocusedBorderColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("privacy_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrivacyAppFilter.entries.forEach { filter ->
                val selected = activeFilter == filter
                FilterChip(
                    selected = selected,
                    onClick = { activeFilter = filter },
                    label = {
                        Text(
                            text = filter.label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = JakartaFontFamily
                        )
                    },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = scheme.primaryContainer,
                        selectedLabelColor = scheme.onPrimaryContainer
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Unified App List
        if (filteredApps.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PhonelinkSetup,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No matching apps found",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        fontFamily = JakartaFontFamily
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = {
                        activeFilter = PrivacyAppFilter.ALL_APPS
                        searchQuery = ""
                    }) {
                        Text("Show All Installed Apps")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    ColorfulPrivacyAppCard(
                        app = app,
                        privacyProfiles = privacyProfiles,
                        onOpenConfig = { selectedAppPkg = app.packageName },
                        onToggleSpoof = { enabled ->
                            if (!enabled) {
                                viewModel.removeAppIdentitySpoof(app)
                            } else {
                                val preset = if (app.deviceProfilePreset == DeviceProfilePreset.REAL_HARDWARE) {
                                    DeviceProfilePreset.PIXEL_9_PRO
                                } else {
                                    app.deviceProfilePreset
                                }
                                viewModel.saveAppIdentitySpoofConfig(
                                    appInfo = app,
                                    enabled = true,
                                    preset = preset,
                                    brand = app.spoofBrand.ifBlank { preset.brand },
                                    model = app.spoofModel.ifBlank { preset.model },
                                    device = app.spoofDevice.ifBlank { preset.device },
                                    androidId = app.spoofAndroidId.ifBlank { IsolationRuleManager.generateAndroidId() },
                                    imei = app.spoofImei.ifBlank { IsolationRuleManager.generateLuhnImei() },
                                    advertisingId = app.spoofAdvertisingId.ifBlank { IsolationRuleManager.generateAdvertisingId() },
                                    macAddress = app.spoofMacAddress.ifBlank { IsolationRuleManager.generateMacAddress() },
                                    carrierName = app.spoofCarrierName.ifBlank { "T-Mobile US" },
                                    simOperator = app.spoofSimOperator.ifBlank { "310260" },
                                    serial = app.spoofSerial.ifBlank { IsolationRuleManager.generateSerial() },
                                    hideRootAndXposed = true,
                                    hideAdbAndDevOptions = true,
                                    hideVpnAndProxy = true,
                                    blockClipboardRead = app.blockClipboardRead
                                )
                            }
                        },
                        onToggleAppVpn = { enabled ->
                            viewModel.toggleAppVpnProxy(app, enabled)
                        },
                        onApplyProfile = { profile ->
                            viewModel.applyPrivacyProfileToApp(app, profile)
                        },
                        onQuickRandomize = {
                            val preset = listOf(
                                DeviceProfilePreset.PIXEL_9_PRO,
                                DeviceProfilePreset.SAMSUNG_S24_ULTRA,
                                DeviceProfilePreset.ONEPLUS_12,
                                DeviceProfilePreset.XIAOMI_14_ULTRA
                            ).random()
                            viewModel.saveAppIdentitySpoofConfig(
                                appInfo = app,
                                enabled = true,
                                preset = preset,
                                brand = preset.brand,
                                model = preset.model,
                                device = preset.device,
                                androidId = IsolationRuleManager.generateAndroidId(),
                                imei = IsolationRuleManager.generateLuhnImei(),
                                advertisingId = IsolationRuleManager.generateAdvertisingId(),
                                macAddress = IsolationRuleManager.generateMacAddress(),
                                carrierName = "T-Mobile US",
                                simOperator = "310260",
                                serial = IsolationRuleManager.generateSerial(),
                                hideRootAndXposed = true,
                                hideAdbAndDevOptions = true,
                                hideVpnAndProxy = true,
                                blockClipboardRead = true
                            )
                        }
                    )
                }
            }
        }
    }

    if (showProfilesDialog) {
        SavedPrivacyProfilesDialog(
            profiles = privacyProfiles,
            onSaveProfile = { viewModel.savePrivacyProfile(it) },
            onDeleteProfile = { viewModel.deletePrivacyProfile(it) },
            onResetDefaults = { viewModel.resetPrivacyProfilesToDefaults() },
            onDismiss = { showProfilesDialog = false }
        )
    }

    if (showVpnSuiteDialog) {
        GlobalVpnProxySuiteDialog(
            settings = settings,
            viewModel = viewModel,
            onDismiss = { showVpnSuiteDialog = false }
        )
    }
}

@Composable
private fun ColorfulPrivacyAppCard(
    app: InstalledAppInfo,
    privacyProfiles: List<PrivacyProfile>,
    onOpenConfig: () -> Unit,
    onToggleSpoof: (Boolean) -> Unit,
    onToggleAppVpn: (Boolean) -> Unit,
    onApplyProfile: (PrivacyProfile) -> Unit,
    onQuickRandomize: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isProtected = app.isIdentitySpoofed || app.spoofLocationEnabled || app.vpnProxyEnabled
    val activeModelLabel = if (app.isIdentitySpoofed) {
        app.spoofModel.ifBlank { app.deviceProfilePreset.label }
    } else {
        "Real Device"
    }
    val matchedProfile = remember(app.privacyProfileId, privacyProfiles) {
        privacyProfiles.firstOrNull { it.id == app.privacyProfileId }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isProtected) {
                scheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                scheme.surfaceContainerLow
            }
        ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isProtected) {
                M3VioletAccent.copy(alpha = 0.4f)
            } else {
                scheme.outlineVariant.copy(alpha = 0.35f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onOpenConfig() }
            .testTag("privacy_app_card_${app.packageName}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                                .size(46.dp)
                                .clip(RoundedCornerShape(13.dp))
                        )
                    } else {
                        Surface(
                            color = M3IndigoAccent.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(13.dp),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Android,
                                    contentDescription = null,
                                    tint = M3IndigoAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.appName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = JakartaFontFamily
                        )
                        Text(
                            text = app.packageName,
                            fontSize = 11.sp,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = JakartaFontFamily
                        )
                    }
                }

                Switch(
                    checked = app.isIdentitySpoofed,
                    onCheckedChange = onToggleSpoof,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = M3VioletAccent
                    ),
                    modifier = Modifier.testTag("switch_spoof_${app.packageName}")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Active Badges Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (app.isIdentitySpoofed) M3VioletAccent.copy(alpha = 0.15f) else scheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = activeModelLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (app.isIdentitySpoofed) M3VioletAccent else scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontFamily = JakartaFontFamily
                    )
                }

                if (matchedProfile != null) {
                    Surface(
                        color = M3IndigoAccent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = matchedProfile.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = M3IndigoAccent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontFamily = JakartaFontFamily
                        )
                    }
                }

                if (app.spoofLocationEnabled) {
                    Surface(
                        color = M3AmberAccent.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "GPS · ${app.spoofTimezoneId.substringAfterLast('/')}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = M3AmberAccent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontFamily = JakartaFontFamily
                        )
                    }
                }

                if (app.disableAllNetworking) {
                    Surface(
                        color = M3RoseAccent.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "Offline Firewall",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = M3RoseAccent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontFamily = JakartaFontFamily
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Action Pills Row (1-Tap VPN, Quick Profiles, Randomize, Full Studio)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                ) {
                    // 1-Tap Per-App VPN Toggle Pill
                    Surface(
                        color = if (app.vpnProxyEnabled) M3SkyAccent.copy(alpha = 0.18f) else scheme.surface,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(
                            1.dp,
                            if (app.vpnProxyEnabled) M3SkyAccent else scheme.outlineVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onToggleAppVpn(!app.vpnProxyEnabled) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = if (app.vpnProxyEnabled) M3SkyAccent else scheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (app.vpnProxyEnabled) "VPN ON" else "VPN Off",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (app.vpnProxyEnabled) M3SkyAccent else scheme.onSurfaceVariant,
                                fontFamily = JakartaFontFamily
                            )
                        }
                    }

                    // Quick Apply Built-in Profiles
                    privacyProfiles.take(2).forEach { profile ->
                        val active = app.privacyProfileId == profile.id
                        Surface(
                            color = if (active) M3VioletAccent.copy(alpha = 0.16f) else scheme.surface,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(
                                1.dp,
                                if (active) M3VioletAccent else scheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onApplyProfile(profile) }
                        ) {
                            Text(
                                text = profile.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (active) M3VioletAccent else scheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                fontFamily = JakartaFontFamily
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = M3IndigoAccent.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onQuickRandomize() }
                            .testTag("btn_quick_randomize_${app.packageName}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                tint = M3IndigoAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Random",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = M3IndigoAccent,
                                fontFamily = JakartaFontFamily
                            )
                        }
                    }

                    Surface(
                        color = scheme.primary,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onOpenConfig() }
                            .testTag("btn_configure_privacy_${app.packageName}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = scheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Studio",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onPrimary,
                                fontFamily = JakartaFontFamily
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalVpnProxySuiteDialog(
    settings: AppSettings,
    viewModel: IsoVaultViewModel,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val allServers = remember(settings.customProxyServers) {
        settings.customProxyServers + VpnProxyPresets.defaultServers
    }
    var uriText by remember { mutableStateOf("") }
    var importFeedback by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = M3SkyAccent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "V2Ray / Proxy & VPN Suite",
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = JakartaFontFamily
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Global Tunnel Master Switch
                Card(
                    colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Global V2Ray / Proxy Tunnel",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                                fontFamily = JakartaFontFamily
                            )
                            Text(
                                text = "${settings.activeServer.name} (${settings.activeServer.exitIp})",
                                fontSize = 11.sp,
                                color = scheme.onSurfaceVariant,
                                fontFamily = JakartaFontFamily
                            )
                        }
                        Switch(
                            checked = settings.vpnTunnelEnabled,
                            onCheckedChange = { viewModel.setVpnTunnelEnabled(it) }
                        )
                    }
                }

                // Route All Apps Bulk Actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = { viewModel.setAllAppsVpnProxy(true) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Route All Apps", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { viewModel.setAllAppsVpnProxy(false) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Disable All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Exit Node Selector
                Text(
                    text = "Active Exit Node (${allServers.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    fontFamily = JakartaFontFamily
                )

                allServers.forEach { server ->
                    val isSelected = settings.activeProxyServerId == server.id
                    Surface(
                        color = if (isSelected) M3SkyAccent.copy(alpha = 0.14f) else scheme.surfaceContainerLow,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) M3SkyAccent else scheme.outlineVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.selectActiveProxyServer(server.id) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${server.protocol.badge} · ${server.name}",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurface,
                                    fontFamily = JakartaFontFamily
                                )
                                Text(
                                    text = "${server.exitIp} · TZ ${server.timezoneId} · ${server.latencyMs}ms",
                                    fontSize = 11.sp,
                                    color = scheme.onSurfaceVariant,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                            if (server.id.startsWith("custom_")) {
                                IconButton(
                                    onClick = { viewModel.deleteCustomProxyServer(server.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Node",
                                        tint = M3RoseAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = M3SkyAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Import Custom URI
                OutlinedTextField(
                    value = uriText,
                    onValueChange = { uriText = it },
                    label = { Text("Import vless://, vmess://, ss://, socks5://") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val ok = viewModel.importProxyServerFromUri(uriText)
                                if (ok) {
                                    uriText = ""
                                    importFeedback = "Imported node!"
                                } else {
                                    importFeedback = "Invalid URI format"
                                }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Import")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (importFeedback != null) {
                    Text(
                        text = importFeedback!!,
                        fontSize = 11.sp,
                        color = M3SkyAccent,
                        fontFamily = JakartaFontFamily
                    )
                }

                HorizontalDivider()

                Text(
                    text = "Global Proxying & Anti-Leak Options",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    fontFamily = JakartaFontFamily
                )

                DialogSwitchRow(
                    title = "Global VPN Kill Switch",
                    checked = settings.vpnKillSwitchEnabled,
                    onCheckedChange = { viewModel.updateProxyingSuiteOptions(vpnKillSwitch = it) }
                )
                DialogSwitchRow(
                    title = "Auto-Match Timezone with Exit IP",
                    checked = settings.matchTimezoneWithIpGlobal,
                    onCheckedChange = { viewModel.updateProxyingSuiteOptions(matchTzWithIp = it) }
                )
                DialogSwitchRow(
                    title = "SOCKS5 Local Bridge (10808)",
                    checked = settings.socksProxyEnabled,
                    onCheckedChange = { viewModel.updateProxyingSuiteOptions(socksProxy = it) }
                )
                DialogSwitchRow(
                    title = "HTTP CONNECT Proxy Bridge",
                    checked = settings.httpProxyEnabled,
                    onCheckedChange = { viewModel.updateProxyingSuiteOptions(httpProxy = it) }
                )
                DialogSwitchRow(
                    title = "Hook Chromium & WebView Sockets",
                    checked = settings.webViewProxyEnabled,
                    onCheckedChange = { viewModel.updateProxyingSuiteOptions(webViewProxy = it) }
                )
                DialogSwitchRow(
                    title = "Hide VPN & Proxy State From Apps",
                    checked = settings.hideVpnConnection,
                    onCheckedChange = {
                        viewModel.updateProxyingSuiteOptions(hideVpnConn = it, hideProxyConn = it)
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun DialogSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            fontFamily = JakartaFontFamily
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SavedPrivacyProfilesDialog(
    profiles: List<PrivacyProfile>,
    onSaveProfile: (PrivacyProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var newName by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Reusable Privacy Profiles",
                fontWeight = FontWeight.ExtraBold,
                fontFamily = JakartaFontFamily
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Built-in and custom privacy templates can be applied to any app in 1 tap.",
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                    fontFamily = JakartaFontFamily
                )
                profiles.forEach { profile ->
                    Surface(
                        color = scheme.surfaceContainerLow,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = profile.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.onSurface,
                                        fontFamily = JakartaFontFamily
                                    )
                                    if (profile.isBuiltIn) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = M3IndigoAccent.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "Built-in",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = M3IndigoAccent,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontFamily = JakartaFontFamily
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = profile.description,
                                    fontSize = 11.sp,
                                    color = scheme.onSurfaceVariant,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                            if (!profile.isBuiltIn) {
                                IconButton(onClick = { onDeleteProfile(profile.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Profile",
                                        tint = M3RoseAccent
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Create New Custom Profile",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    fontFamily = JakartaFontFamily
                )
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newDesc,
                    onValueChange = { newDesc = it },
                    label = { Text("Description") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onSaveProfile(
                                    PrivacyProfile(
                                        id = "custom_${System.currentTimeMillis()}",
                                        name = newName.trim(),
                                        description = newDesc.trim().ifBlank { "Custom user privacy profile" },
                                        isBuiltIn = false
                                    )
                                )
                                newName = ""
                                newDesc = ""
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Create Profile", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onResetDefaults,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset Defaults", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
