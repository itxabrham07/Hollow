package com.example.isolation.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.isolation.model.AppSettings
import com.example.isolation.model.DeviceProfilePreset
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.model.PrivacyProfile
import com.example.isolation.model.VpnProxyPresets
import com.example.isolation.pkg.InstalledAppInfo
import com.example.isolation.ui.components.ContentCard
import com.example.isolation.ui.components.EmptyState
import com.example.isolation.ui.components.ListRow
import com.example.isolation.ui.components.MonoDataText
import com.example.isolation.ui.components.PrimaryButton
import com.example.isolation.ui.components.SecondaryButton
import com.example.isolation.ui.components.SectionHeader
import com.example.isolation.ui.components.StatusIndicator
import com.example.isolation.ui.components.rememberAppIconBitmap
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.M3AmberAccent

private enum class PrivacyAppFilter(val label: String) {
    USER_APPS("Installed Apps"),
    PROTECTED("Protected"),
    VPN_ROUTED("VPN Routed"),
    LOCATION_SPOOFED("GPS Spoofed"),
    ALL_APPS("All & System")
}

/**
 * App Privacy, Hardware Identity, Per-App VPN & Anti-Tracking Screen.
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
                .padding(top = 8.dp, bottom = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "App Privacy & Identity",
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Device IDs, fingerprint, sensors, GPS & VPN",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    text = if (settings.vpnTunnelEnabled) activeExitNode.protocol.badge else "VPN",
                    icon = Icons.Default.VpnKey,
                    onClick = { showVpnSuiteDialog = true },
                    modifier = Modifier.testTag("btn_open_vpn_suite")
                )
                SecondaryButton(
                    text = "Profiles (${privacyProfiles.size})",
                    icon = Icons.Default.Shield,
                    onClick = { showProfilesDialog = true },
                    modifier = Modifier.testTag("btn_manage_privacy_profiles")
                )
            }
        }

        // Summary Hero Card
        ContentCard(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
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
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(scheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhonelinkSetup,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$protectedCount Protected · $vpnRoutedCount VPN Routed",
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (settings.vpnTunnelEnabled) {
                            MonoDataText(
                                text = "Exit Node: ${activeExitNode.name} (${activeExitNode.exitIp})",
                                maxLines = 1
                            )
                        } else {
                            Text(
                                text = "Tap any app for 50+ hardware, browser, GPS, sensor & VPN controls",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                StatusIndicator(
                    label = if (settings.vpnTunnelEnabled) "Tunnel ON" else "Standby",
                    color = if (settings.vpnTunnelEnabled) scheme.secondary else scheme.onSurfaceVariant,
                    modifier = Modifier.clickable { showVpnSuiteDialog = true }
                )
            }

            if (deadmanIncidents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = scheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GppBad,
                            contentDescription = null,
                            tint = scheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${deadmanIncidents.size} app(s) severed by Deadman Switch",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.error
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    SecondaryButton(
                        text = "Restore All",
                        isDestructive = true,
                        onClick = { viewModel.restoreAllNetworks() }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Search apps, package names, or spoofed models...",
                    style = MaterialTheme.typography.bodyMedium
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
            shape = MaterialTheme.shapes.extraSmall,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = scheme.surface,
                unfocusedContainerColor = scheme.surface,
                focusedBorderColor = scheme.primary,
                unfocusedBorderColor = scheme.outlineVariant
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
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
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
                    shape = MaterialTheme.shapes.extraSmall,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = scheme.primaryContainer,
                        selectedLabelColor = scheme.onPrimaryContainer
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Unified App List
        if (filteredApps.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                EmptyState(
                    icon = Icons.Default.PhonelinkSetup,
                    title = "No matching apps found",
                    message = "Try adjusting your search or filter to view installed packages.",
                    actionLabel = "Show All Installed Apps",
                    onAction = {
                        activeFilter = PrivacyAppFilter.ALL_APPS
                        searchQuery = ""
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isProtected = app.isIdentitySpoofed || app.spoofLocationEnabled || app.vpnProxyEnabled
                    val activeModelLabel = if (app.isIdentitySpoofed) {
                        app.spoofModel.ifBlank { app.deviceProfilePreset.label }
                    } else {
                        "Real Device"
                    }
                    val matchedProfile = remember(app.privacyProfileId, privacyProfiles) {
                        privacyProfiles.firstOrNull { it.id == app.privacyProfileId }
                    }
                    val iconBitmap = rememberAppIconBitmap(app.packageName, app.icon)

                    ContentCard(
                        onClick = { selectedAppPkg = app.packageName },
                        borderColor = if (isProtected) scheme.secondary else scheme.outlineVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("privacy_app_card_${app.packageName}")
                    ) {
                        ListRow(
                            title = app.appName,
                            monoSubtitle = app.packageName,
                            leadingBitmap = iconBitmap,
                            leadingIcon = if (iconBitmap == null) Icons.Default.Android else null,
                            statusLabel = if (isProtected) "Protected" else null,
                            statusColor = scheme.secondary,
                            modifier = Modifier.padding(0.dp),
                            trailing = {
                                Switch(
                                    checked = app.isIdentitySpoofed,
                                    onCheckedChange = { enabled ->
                                        if (!enabled) {
                                            viewModel.removeAppIdentitySpoof(app)
                                        } else {
                                            val preset = DeviceProfilePreset.REAL_HARDWARE
                                            viewModel.saveAppIdentitySpoofConfig(
                                                appInfo = app,
                                                enabled = true,
                                                preset = preset,
                                                brand = "",
                                                model = "",
                                                device = "",
                                                androidId = app.spoofAndroidId.takeIf { it.isNotBlank() && it != viewModel.realHardware.androidId }
                                                    ?: IsolationRuleManager.generateAndroidId(),
                                                imei = app.spoofImei.ifBlank { IsolationRuleManager.generateLuhnImei() },
                                                advertisingId = app.spoofAdvertisingId.ifBlank { IsolationRuleManager.generateAdvertisingId() },
                                                macAddress = app.spoofMacAddress.ifBlank { IsolationRuleManager.generateMacAddress() },
                                                carrierName = app.spoofCarrierName.ifBlank { "T-Mobile US" },
                                                simOperator = app.spoofSimOperator.ifBlank { "310260" },
                                                serial = "unknown",
                                                hideRootAndXposed = true,
                                                hideAdbAndDevOptions = true,
                                                hideVpnAndProxy = true,
                                                blockClipboardRead = app.blockClipboardRead,
                                                spoofGpuFingerprint = false,
                                                spoofBrowserUserAgent = false,
                                                spoofWebRtc = false,
                                                spoofCanvasNoise = false,
                                                deadmanSwitchEnabled = false,
                                                spoofDrmWidevine = false
                                            )
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = scheme.onPrimary,
                                        checkedTrackColor = scheme.primary
                                    ),
                                    modifier = Modifier.testTag("switch_spoof_${app.packageName}")
                                )
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Active Status Indicators Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusIndicator(
                                label = activeModelLabel,
                                color = if (app.isIdentitySpoofed) scheme.secondary else scheme.onSurfaceVariant
                            )
                            if (matchedProfile != null) {
                                StatusIndicator(
                                    label = matchedProfile.name,
                                    color = scheme.primary
                                )
                            }
                            if (app.spoofLocationEnabled) {
                                StatusIndicator(
                                    label = "GPS · ${app.spoofTimezoneId.substringAfterLast('/')}",
                                    color = M3AmberAccent
                                )
                            }
                            if (app.disableAllNetworking) {
                                StatusIndicator(
                                    label = "Offline Firewall",
                                    color = scheme.error
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick Action Controls Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                FilterChip(
                                    selected = app.vpnProxyEnabled,
                                    onClick = { viewModel.toggleAppVpnProxy(app, !app.vpnProxyEnabled) },
                                    label = {
                                        Text(
                                            text = if (app.vpnProxyEnabled) "VPN ON" else "VPN Off",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.VpnKey,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    shape = MaterialTheme.shapes.extraSmall
                                )

                                privacyProfiles.take(2).forEach { profile ->
                                    val active = app.privacyProfileId == profile.id
                                    FilterChip(
                                        selected = active,
                                        onClick = { viewModel.applyPrivacyProfileToApp(app, profile) },
                                        label = {
                                            Text(
                                                text = profile.name,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        },
                                        shape = MaterialTheme.shapes.extraSmall
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SecondaryButton(
                                    text = "Random",
                                    icon = Icons.Default.AutoFixHigh,
                                    onClick = {
                                        viewModel.saveAppIdentitySpoofConfig(
                                            appInfo = app,
                                            enabled = true,
                                            preset = DeviceProfilePreset.REAL_HARDWARE,
                                            brand = "",
                                            model = "",
                                            device = "",
                                            androidId = IsolationRuleManager.generateAndroidId(),
                                            imei = IsolationRuleManager.generateLuhnImei(),
                                            advertisingId = IsolationRuleManager.generateAdvertisingId(),
                                            macAddress = IsolationRuleManager.generateMacAddress(),
                                            carrierName = "T-Mobile US",
                                            simOperator = "310260",
                                            serial = "unknown",
                                            hideRootAndXposed = true,
                                            hideAdbAndDevOptions = true,
                                            hideVpnAndProxy = true,
                                            blockClipboardRead = true,
                                            spoofGpuFingerprint = false,
                                            spoofBrowserUserAgent = false,
                                            spoofWebRtc = false,
                                            spoofCanvasNoise = false,
                                            deadmanSwitchEnabled = false,
                                            spoofDrmWidevine = false
                                        )
                                    },
                                    modifier = Modifier.testTag("btn_quick_randomize_${app.packageName}")
                                )
                                PrimaryButton(
                                    text = "Studio",
                                    icon = Icons.Default.Tune,
                                    onClick = { selectedAppPkg = app.packageName },
                                    modifier = Modifier.testTag("btn_configure_privacy_${app.packageName}")
                                )
                            }
                        }
                    }
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
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = scheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = scheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "V2Ray / Proxy & VPN Suite",
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ContentCard {
                    ListRow(
                        title = "Global V2Ray / Proxy Tunnel",
                        monoSubtitle = "${settings.activeServer.name} (${settings.activeServer.exitIp})",
                        trailing = {
                            Switch(
                                checked = settings.vpnTunnelEnabled,
                                onCheckedChange = { viewModel.setVpnTunnelEnabled(it) }
                            )
                        }
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PrimaryButton(
                        text = "Route All Apps",
                        onClick = { viewModel.setAllAppsVpnProxy(true) },
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "Disable All",
                        onClick = { viewModel.setAllAppsVpnProxy(false) },
                        modifier = Modifier.weight(1f)
                    )
                }

                SectionHeader(title = "Active Exit Node (${allServers.size})")

                allServers.forEach { server ->
                    val isSelected = settings.activeProxyServerId == server.id
                    ContentCard(
                        onClick = { viewModel.selectActiveProxyServer(server.id) },
                        borderColor = if (isSelected) scheme.primary else scheme.outlineVariant
                    ) {
                        ListRow(
                            title = "${server.protocol.badge} · ${server.name}",
                            monoSubtitle = "${server.exitIp} · TZ ${server.timezoneId} · ${server.latencyMs}ms",
                            statusLabel = if (isSelected) "Active" else null,
                            statusColor = scheme.secondary,
                            trailing = if (server.id.startsWith("custom_")) {
                                {
                                    IconButton(
                                        onClick = { viewModel.deleteCustomProxyServer(server.id) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Node",
                                            tint = scheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            } else null
                        )
                    }
                }

                OutlinedTextField(
                    value = uriText,
                    onValueChange = { uriText = it },
                    label = {
                        Text(
                            text = "Import vless://, vmess://, ss://, socks5://",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraSmall,
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
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.primary
                    )
                }

                HorizontalDivider(color = scheme.outlineVariant)

                SectionHeader(title = "Global Proxying & Anti-Leak Options")

                ListRow(
                    title = "Global VPN Kill Switch",
                    onClick = { viewModel.updateProxyingSuiteOptions(vpnKillSwitch = !settings.vpnKillSwitchEnabled) },
                    trailing = {
                        Switch(
                            checked = settings.vpnKillSwitchEnabled,
                            onCheckedChange = { viewModel.updateProxyingSuiteOptions(vpnKillSwitch = it) }
                        )
                    }
                )
                ListRow(
                    title = "Auto-Match Timezone with Exit IP",
                    onClick = { viewModel.updateProxyingSuiteOptions(matchTzWithIp = !settings.matchTimezoneWithIpGlobal) },
                    trailing = {
                        Switch(
                            checked = settings.matchTimezoneWithIpGlobal,
                            onCheckedChange = { viewModel.updateProxyingSuiteOptions(matchTzWithIp = it) }
                        )
                    }
                )
                ListRow(
                    title = "SOCKS5 Local Bridge (10808)",
                    onClick = { viewModel.updateProxyingSuiteOptions(socksProxy = !settings.socksProxyEnabled) },
                    trailing = {
                        Switch(
                            checked = settings.socksProxyEnabled,
                            onCheckedChange = { viewModel.updateProxyingSuiteOptions(socksProxy = it) }
                        )
                    }
                )
                ListRow(
                    title = "HTTP CONNECT Proxy Bridge",
                    onClick = { viewModel.updateProxyingSuiteOptions(httpProxy = !settings.httpProxyEnabled) },
                    trailing = {
                        Switch(
                            checked = settings.httpProxyEnabled,
                            onCheckedChange = { viewModel.updateProxyingSuiteOptions(httpProxy = it) }
                        )
                    }
                )
                ListRow(
                    title = "Hook Chromium & WebView Sockets",
                    onClick = { viewModel.updateProxyingSuiteOptions(webViewProxy = !settings.webViewProxyEnabled) },
                    trailing = {
                        Switch(
                            checked = settings.webViewProxyEnabled,
                            onCheckedChange = { viewModel.updateProxyingSuiteOptions(webViewProxy = it) }
                        )
                    }
                )
                ListRow(
                    title = "Hide VPN & Proxy State From Apps",
                    onClick = {
                        val next = !settings.hideVpnConnection
                        viewModel.updateProxyingSuiteOptions(hideVpnConn = next, hideProxyConn = next)
                    },
                    trailing = {
                        Switch(
                            checked = settings.hideVpnConnection,
                            onCheckedChange = {
                                viewModel.updateProxyingSuiteOptions(hideVpnConn = it, hideProxyConn = it)
                            }
                        )
                    }
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "Done",
                onClick = onDismiss
            )
        }
    )
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
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = scheme.surface,
        title = {
            Text(
                text = "Reusable Privacy Profiles",
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Built-in and custom privacy templates can be applied to any app in 1 tap.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
                profiles.forEach { profile ->
                    ContentCard {
                        ListRow(
                            title = profile.name,
                            subtitle = profile.description,
                            statusLabel = if (profile.isBuiltIn) "Built-in" else null,
                            statusColor = scheme.primary,
                            trailing = if (!profile.isBuiltIn) {
                                {
                                    IconButton(onClick = { onDeleteProfile(profile.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Profile",
                                            tint = scheme.error
                                        )
                                    }
                                }
                            } else null
                        )
                    }
                }

                HorizontalDivider(color = scheme.outlineVariant)

                SectionHeader(title = "Create New Custom Profile")
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Profile Name", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newDesc,
                    onValueChange = { newDesc = it },
                    label = { Text("Description", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PrimaryButton(
                        text = "Create Profile",
                        icon = Icons.Default.Add,
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
                    )
                    SecondaryButton(
                        text = "Reset Defaults",
                        icon = Icons.Default.RestartAlt,
                        onClick = onResetDefaults,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Close",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    )
}
