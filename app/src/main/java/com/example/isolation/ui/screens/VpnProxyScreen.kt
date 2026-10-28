package com.example.isolation.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.isolation.model.VpnProtocol
import com.example.isolation.model.VpnProxyServer
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
import com.example.ui.theme.JetBrainsMonoFontFamily

private enum class VpnSuiteTab(val title: String) {
    SERVERS("Servers"),
    PER_APP_ROUTING("Per-App Routing"),
    ADVANCED_PROXY("Advanced Proxy")
}

private enum class ServerProtoFilter(val label: String) {
    ALL("All Nodes"),
    V2RAY("V2Ray (VLESS / VMess / Trojan / SS)"),
    PROXY("Proxy (SOCKS5 / HTTP)")
}

private enum class VpnAppRoutingFilter(val label: String) {
    ALL("All"),
    ROUTED("Routed Only"),
    BROWSERS_SOCIAL("Browsers & Social"),
    USER_APPS("Downloaded")
}

@Composable
fun VpnProxyScreen(
    viewModel: IsoVaultViewModel,
    onBack: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val settings by viewModel.settings.collectAsState()
    val allApps by viewModel.allInstalledApps.collectAsState()

    var selectedTab by remember { mutableStateOf(VpnSuiteTab.SERVERS) }
    var protoFilter by remember { mutableStateOf(ServerProtoFilter.ALL) }
    var appFilter by remember { mutableStateOf(VpnAppRoutingFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    var showUriImportDialog by remember { mutableStateOf(false) }
    var showManualServerDialog by remember { mutableStateOf(false) }

    val activeServer = settings.activeServer
    val allServers = settings.allProxyServers
    val routedAppsCount = remember(allApps) { allApps.count { it.vpnProxyEnabled } }
    val isWholeDeviceRouted = remember(allApps, routedAppsCount) {
        allApps.isNotEmpty() && routedAppsCount >= allApps.size
    }

    BackHandler { onBack() }

    val filteredServers = remember(allServers, protoFilter) {
        allServers.filter { s ->
            when (protoFilter) {
                ServerProtoFilter.ALL -> true
                ServerProtoFilter.V2RAY -> s.protocol in listOf(
                    VpnProtocol.VLESS,
                    VpnProtocol.VMESS,
                    VpnProtocol.TROJAN,
                    VpnProtocol.SHADOWSOCKS
                )
                ServerProtoFilter.PROXY -> s.protocol in listOf(
                    VpnProtocol.SOCKS5,
                    VpnProtocol.HTTP
                )
            }
        }
    }

    val filteredApps = remember(allApps, appFilter, searchQuery) {
        allApps.filter { app ->
            val pkg = app.packageName.lowercase()
            val isBrowserOrSocial = pkg.contains("chrome") || pkg.contains("browser") ||
                    pkg.contains("firefox") || pkg.contains("brave") || pkg.contains("opera") ||
                    pkg.contains("instagram") || pkg.contains("whatsapp") || pkg.contains("telegram") ||
                    pkg.contains("tiktok") || pkg.contains("twitter") || pkg.contains("facebook") ||
                    pkg.contains("snapchat") || pkg.contains("discord") || pkg.contains("reddit")

            val matchesFilter = when (appFilter) {
                VpnAppRoutingFilter.ALL -> true
                VpnAppRoutingFilter.ROUTED -> app.vpnProxyEnabled
                VpnAppRoutingFilter.BROWSERS_SOCIAL -> isBrowserOrSocial
                VpnAppRoutingFilter.USER_APPS -> !app.isSystemApp
            }
            val matchesSearch = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(horizontal = 16.dp)
            .testTag("vpn_proxy_screen")
    ) {
        Spacer(modifier = Modifier.height(8.dp))

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
                    modifier = Modifier.testTag("vpn_proxy_back_button")
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
                        text = "Proxy & V2Ray Suite",
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (settings.vpnTunnelEnabled) {
                            "Active [${activeServer.countryCode}] ${activeServer.exitIp} • $routedAppsCount app${if (routedAppsCount == 1) "" else "s"} routed"
                        } else {
                            "Configure SOCKS5, HTTP & V2Ray per-app or whole device"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (settings.vpnTunnelEnabled) scheme.secondary else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (settings.vpnTunnelEnabled) {
                PrimaryButton(
                    text = "Connected",
                    icon = Icons.Default.PowerSettingsNew,
                    onClick = { viewModel.setVpnTunnelEnabled(false) },
                    modifier = Modifier.testTag("vpn_header_connect_btn")
                )
            } else {
                SecondaryButton(
                    text = "Connect",
                    icon = Icons.Default.PowerSettingsNew,
                    onClick = { viewModel.setVpnTunnelEnabled(true) },
                    modifier = Modifier.testTag("vpn_header_connect_btn")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Segmented 3-Tab Bar: Servers | Per-App Routing | Advanced Proxy
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(scheme.surface)
                .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.small)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            VpnSuiteTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                val labelText = if (tab == VpnSuiteTab.PER_APP_ROUTING && routedAppsCount > 0) {
                    "Per-App ($routedAppsCount)"
                } else {
                    tab.title
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(if (isSelected) scheme.primary else Color.Transparent)
                        .clickable { selectedTab = tab }
                        .padding(vertical = 8.dp, horizontal = 8.dp)
                        .testTag("vpn_tab_${tab.name.lowercase()}")
                ) {
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            VpnSuiteTab.SERVERS -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // 1. Active Connection & Routing Scope Hero Card
                    item {
                        ContentCard(
                            borderColor = if (settings.vpnTunnelEnabled) scheme.secondary else scheme.outlineVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ListRow(
                                title = activeServer.name,
                                subtitle = "Timezone: ${activeServer.timezoneId} (${activeServer.localeTag}) • ${activeServer.latencyMs} ms",
                                monoSubtitle = "${activeServer.protocol.badge} • ${activeServer.host}:${activeServer.port} • Exit IP ${activeServer.exitIp}",
                                leadingIcon = Icons.Default.Public,
                                tintLeadingIcon = true,
                                statusLabel = if (settings.vpnTunnelEnabled) "Active" else "Standby",
                                statusColor = if (settings.vpnTunnelEnabled) scheme.secondary else scheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 0.dp),
                                trailing = {
                                    Switch(
                                        checked = settings.vpnTunnelEnabled,
                                        onCheckedChange = { viewModel.setVpnTunnelEnabled(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = scheme.onPrimary,
                                            checkedTrackColor = scheme.primary
                                        ),
                                        modifier = Modifier.testTag("vpn_master_switch")
                                    )
                                }
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = scheme.outlineVariant)
                            Spacer(modifier = Modifier.height(12.dp))

                            SectionHeader(
                                title = "Routing scope",
                                subtitle = "Route whole device or selected apps"
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isWholeDeviceRouted && settings.vpnTunnelEnabled) {
                                    PrimaryButton(
                                        text = "Whole Device (${allApps.size})",
                                        onClick = {
                                            viewModel.setVpnTunnelEnabled(true)
                                            viewModel.setAllAppsVpnProxy(true)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("mode_whole_device")
                                    )
                                } else {
                                    SecondaryButton(
                                        text = "Whole Device (${allApps.size})",
                                        onClick = {
                                            viewModel.setVpnTunnelEnabled(true)
                                            viewModel.setAllAppsVpnProxy(true)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("mode_whole_device")
                                    )
                                }

                                if (!isWholeDeviceRouted && routedAppsCount > 0) {
                                    PrimaryButton(
                                        text = "Per-App ($routedAppsCount)",
                                        onClick = { selectedTab = VpnSuiteTab.PER_APP_ROUTING },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("mode_per_app")
                                    )
                                } else {
                                    SecondaryButton(
                                        text = "Per-App ($routedAppsCount)",
                                        onClick = { selectedTab = VpnSuiteTab.PER_APP_ROUTING },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("mode_per_app")
                                    )
                                }
                            }
                        }
                    }

                    // 2. Add Custom Proxy / V2Ray Server Buttons
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PrimaryButton(
                                text = "Add Proxy / V2Ray",
                                icon = Icons.Default.Add,
                                onClick = { showManualServerDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_add_custom_server")
                            )

                            SecondaryButton(
                                text = "Paste URI Link",
                                icon = Icons.Default.ContentPaste,
                                onClick = { showUriImportDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_import_v2ray_uri")
                            )
                        }
                    }

                    // 3. Protocol Filter Chips
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(ServerProtoFilter.entries) { filter ->
                                FilterChip(
                                    selected = protoFilter == filter,
                                    onClick = { protoFilter = filter },
                                    label = {
                                        Text(
                                            text = filter.label,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    shape = MaterialTheme.shapes.extraSmall,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = scheme.primaryContainer,
                                        selectedLabelColor = scheme.onPrimaryContainer,
                                        containerColor = scheme.surface,
                                        labelColor = scheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }

                    // 4. Server Node Cards
                    items(filteredServers, key = { it.id }) { server ->
                        val isSelected = server.id == activeServer.id
                        val isCustom = server.id.startsWith("custom_")

                        ContentCard(
                            onClick = { viewModel.selectActiveProxyServer(server.id) },
                            borderColor = if (isSelected) scheme.primary else scheme.outlineVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("server_node_${server.id}")
                        ) {
                            ListRow(
                                title = "[${server.countryCode}] ${server.name}",
                                subtitle = "TZ: ${server.timezoneId} (${server.localeTag})",
                                monoSubtitle = "${server.host}:${server.port} • Exit ${server.exitIp} • ${server.latencyMs}ms",
                                statusLabel = server.protocol.badge,
                                statusColor = if (isSelected) scheme.secondary else scheme.primary,
                                modifier = Modifier.padding(vertical = 0.dp),
                                trailing = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isCustom) {
                                            IconButton(
                                                onClick = { viewModel.deleteCustomProxyServer(server.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Custom Server",
                                                    tint = scheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { viewModel.selectActiveProxyServer(server.id) },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = scheme.primary,
                                                unselectedColor = scheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            VpnSuiteTab.PER_APP_ROUTING -> {
                Column(modifier = Modifier.weight(1f)) {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search apps to route via Proxy / V2Ray...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant
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
                                        contentDescription = "Clear",
                                        tint = scheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraSmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = scheme.primary,
                            unfocusedBorderColor = scheme.outlineVariant,
                            focusedContainerColor = scheme.surface,
                            unfocusedContainerColor = scheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vpn_app_search_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Filter Chips + Batch Actions
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(VpnAppRoutingFilter.entries) { filter ->
                                FilterChip(
                                    selected = appFilter == filter,
                                    onClick = { appFilter = filter },
                                    label = {
                                        Text(
                                            text = if (filter == VpnAppRoutingFilter.ROUTED) "Routed ($routedAppsCount)" else filter.label,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    shape = MaterialTheme.shapes.extraSmall,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = scheme.primaryContainer,
                                        selectedLabelColor = scheme.onPrimaryContainer,
                                        containerColor = scheme.surface,
                                        labelColor = scheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    viewModel.setVpnTunnelEnabled(true)
                                    viewModel.setAllAppsVpnProxy(true)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("vpn_route_all_btn")
                            ) {
                                Text("Route All", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                            }
                            if (routedAppsCount > 0) {
                                TextButton(
                                    onClick = { viewModel.setAllAppsVpnProxy(false) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("vpn_clear_all_btn")
                                ) {
                                    Text("Clear", style = MaterialTheme.typography.labelMedium, color = scheme.error)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (filteredApps.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.PhoneAndroid,
                            title = "No matching apps",
                            message = "Try adjusting your search or filter selection."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { appInfo ->
                                val isRouted = appInfo.vpnProxyEnabled
                                val currentServer = remember(appInfo.proxyServerId, allServers, activeServer) {
                                    allServers.firstOrNull { it.id == appInfo.proxyServerId } ?: activeServer
                                }
                                val iconBitmap = rememberAppIconBitmap(
                                    packageName = appInfo.packageName,
                                    drawable = appInfo.icon,
                                    sizePx = 88
                                )

                                ContentCard(
                                    borderColor = if (isRouted) scheme.secondary else scheme.outlineVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("vpn_app_card_${appInfo.packageName}")
                                ) {
                                    ListRow(
                                        title = appInfo.appName,
                                        subtitle = if (isRouted) {
                                            "Routed via [${currentServer.countryCode}] ${currentServer.protocol.badge} (${currentServer.exitIp})"
                                        } else {
                                            "Direct connection • Toggle switch to route via Proxy/V2Ray"
                                        },
                                        monoSubtitle = appInfo.packageName,
                                        leadingBitmap = iconBitmap,
                                        leadingIcon = Icons.Default.PhoneAndroid,
                                        statusLabel = if (isRouted) "Routed" else null,
                                        statusColor = scheme.secondary,
                                        modifier = Modifier.padding(vertical = 0.dp),
                                        trailing = {
                                            Switch(
                                                checked = isRouted,
                                                onCheckedChange = { enabled ->
                                                    viewModel.toggleAppVpnProxy(
                                                        appInfo = appInfo,
                                                        enabled = enabled,
                                                        serverId = appInfo.proxyServerId.ifBlank { activeServer.id }
                                                    )
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = scheme.onPrimary,
                                                    checkedTrackColor = scheme.primary
                                                ),
                                                modifier = Modifier.testTag("switch_vpn_app_${appInfo.packageName}")
                                            )
                                        }
                                    )

                                    AnimatedVisibility(visible = isRouted) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            HorizontalDivider(color = scheme.outlineVariant)
                                            Text(
                                                text = "Exit Server Override for ${appInfo.appName}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = scheme.onSurfaceVariant
                                            )
                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                items(allServers, key = { it.id }) { srv ->
                                                    val selected = srv.id == currentServer.id
                                                    FilterChip(
                                                        selected = selected,
                                                        onClick = {
                                                            viewModel.toggleAppVpnProxy(
                                                                appInfo = appInfo,
                                                                enabled = true,
                                                                serverId = srv.id
                                                            )
                                                        },
                                                        label = {
                                                            Text(
                                                                text = "[${srv.countryCode}] ${srv.protocol.badge} · ${srv.exitIp}",
                                                                style = MaterialTheme.typography.labelMedium
                                                            )
                                                        },
                                                        shape = MaterialTheme.shapes.extraSmall,
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            selectedContainerColor = scheme.primary,
                                                            selectedLabelColor = scheme.onPrimary,
                                                            containerColor = scheme.surfaceVariant,
                                                            labelColor = scheme.onSurfaceVariant
                                                        )
                                                    )
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                FilterChip(
                                                    selected = appInfo.requireVpnForNetwork,
                                                    onClick = {
                                                        viewModel.toggleAppVpnProxy(
                                                            appInfo = appInfo,
                                                            enabled = appInfo.vpnProxyEnabled,
                                                            serverId = appInfo.proxyServerId.ifBlank { activeServer.id },
                                                            requireVpnForNetwork = !appInfo.requireVpnForNetwork,
                                                            matchTimezoneWithIp = appInfo.matchTimezoneWithIp
                                                        )
                                                    },
                                                    label = {
                                                        Text(
                                                            text = "Kill-Switch if VPN Off",
                                                            style = MaterialTheme.typography.labelMedium
                                                        )
                                                    },
                                                    shape = MaterialTheme.shapes.extraSmall
                                                )
                                                FilterChip(
                                                    selected = appInfo.matchTimezoneWithIp,
                                                    onClick = {
                                                        viewModel.toggleAppVpnProxy(
                                                            appInfo = appInfo,
                                                            enabled = appInfo.vpnProxyEnabled,
                                                            serverId = appInfo.proxyServerId.ifBlank { activeServer.id },
                                                            requireVpnForNetwork = appInfo.requireVpnForNetwork,
                                                            matchTimezoneWithIp = !appInfo.matchTimezoneWithIp
                                                        )
                                                    },
                                                    label = {
                                                        Text(
                                                            text = "Sync TZ (${currentServer.timezoneId})",
                                                            style = MaterialTheme.typography.labelMedium
                                                        )
                                                    },
                                                    shape = MaterialTheme.shapes.extraSmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            VpnSuiteTab.ADVANCED_PROXY -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    item {
                        ContentCard(modifier = Modifier.fillMaxWidth()) {
                            SectionHeader(
                                title = "Global Proxy & Anti-Leak Engine",
                                subtitle = "Socket redirection, WebView proxying, and interface stealth"
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            ListRow(
                                title = "Strict VPN Kill Switch",
                                subtitle = "Block all outgoing sockets if the Proxy or V2Ray tunnel drops",
                                leadingIcon = Icons.Default.Lock,
                                onClick = { viewModel.updateProxyingSuiteOptions(vpnKillSwitch = !settings.vpnKillSwitchEnabled) },
                                trailing = {
                                    Switch(
                                        checked = settings.vpnKillSwitchEnabled,
                                        onCheckedChange = { viewModel.updateProxyingSuiteOptions(vpnKillSwitch = it) }
                                    )
                                }
                            )
                            HorizontalDivider(color = scheme.outlineVariant)

                            ListRow(
                                title = "Auto-Sync Timezone & Locale with Exit IP",
                                subtitle = "Automatically align TimeZone.getDefault() and Locale with the active server's country",
                                leadingIcon = Icons.Default.Schedule,
                                onClick = { viewModel.updateProxyingSuiteOptions(matchTzWithIp = !settings.matchTimezoneWithIpGlobal) },
                                trailing = {
                                    Switch(
                                        checked = settings.matchTimezoneWithIpGlobal,
                                        onCheckedChange = { viewModel.updateProxyingSuiteOptions(matchTzWithIp = it) }
                                    )
                                }
                            )
                            HorizontalDivider(color = scheme.outlineVariant)

                            ListRow(
                                title = "SOCKS5 Socket Hook (Java & Native)",
                                subtitle = "Redirect java.net.Socket and OkHttp connections through the SOCKS5/V2Ray endpoint",
                                leadingIcon = Icons.Default.Dns,
                                onClick = { viewModel.updateProxyingSuiteOptions(socksProxy = !settings.socksProxyEnabled) },
                                trailing = {
                                    Switch(
                                        checked = settings.socksProxyEnabled,
                                        onCheckedChange = { viewModel.updateProxyingSuiteOptions(socksProxy = it) }
                                    )
                                }
                            )
                            HorizontalDivider(color = scheme.outlineVariant)

                            ListRow(
                                title = "HTTP / HTTPS CONNECT Proxy Hook",
                                subtitle = "Inject System http.proxyHost / https.proxyHost properties into routed apps",
                                leadingIcon = Icons.Default.Public,
                                onClick = { viewModel.updateProxyingSuiteOptions(httpProxy = !settings.httpProxyEnabled) },
                                trailing = {
                                    Switch(
                                        checked = settings.httpProxyEnabled,
                                        onCheckedChange = { viewModel.updateProxyingSuiteOptions(httpProxy = it) }
                                    )
                                }
                            )
                            HorizontalDivider(color = scheme.outlineVariant)

                            ListRow(
                                title = "Chromium WebView Traffic Proxy",
                                subtitle = "Force embedded Android WebViews & custom tabs through the active proxy",
                                leadingIcon = Icons.Default.Tune,
                                onClick = { viewModel.updateProxyingSuiteOptions(webViewProxy = !settings.webViewProxyEnabled) },
                                trailing = {
                                    Switch(
                                        checked = settings.webViewProxyEnabled,
                                        onCheckedChange = { viewModel.updateProxyingSuiteOptions(webViewProxy = it) }
                                    )
                                }
                            )
                            HorizontalDivider(color = scheme.outlineVariant)

                            ListRow(
                                title = "Hide VPN & Proxy Interfaces (tun0 / ppp0)",
                                subtitle = "Scrub TRANSPORT_VPN, NetworkInterface tun0, and proxy properties from detection SDKs",
                                leadingIcon = Icons.Default.Security,
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
                            HorizontalDivider(color = scheme.outlineVariant)

                            ListRow(
                                title = "Trust Custom SSL/TLS Certificates",
                                subtitle = "Bypass SSL pinning in routed apps when inspecting traffic with a custom MITM proxy",
                                leadingIcon = Icons.Default.VpnKey,
                                onClick = { viewModel.updateProxyingSuiteOptions(trustAllCerts = !settings.trustAllCertificates) },
                                trailing = {
                                    Switch(
                                        checked = settings.trustAllCertificates,
                                        onCheckedChange = { viewModel.updateProxyingSuiteOptions(trustAllCerts = it) }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog 1: Paste V2Ray / Proxy URI
    if (showUriImportDialog) {
        var uriInput by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showUriImportDialog = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = {
                Text(
                    text = "Import V2Ray / Proxy URI",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste a vless://, vmess://, ss://, trojan://, socks://, or host:port link:",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = uriInput,
                        onValueChange = {
                            uriInput = it
                            errorMsg = null
                        },
                        placeholder = {
                            MonoDataText(
                                text = "vless://uuid@host:443#US-NewYork",
                                color = scheme.onSurfaceVariant
                            )
                        },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        shape = MaterialTheme.shapes.extraSmall,
                        singleLine = false,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_v2ray_uri")
                    )
                    errorMsg?.let {
                        StatusIndicator(
                            label = it,
                            color = scheme.error
                        )
                    }
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = "Import & Connect",
                    onClick = {
                        val ok = viewModel.importProxyServerFromUri(uriInput)
                        if (ok) {
                            showUriImportDialog = false
                        } else {
                            errorMsg = "Invalid URI format. Enter a valid V2Ray URI or host:port."
                        }
                    },
                    modifier = Modifier.testTag("confirm_import_v2ray_uri")
                )
            },
            dismissButton = {
                TextButton(onClick = { showUriImportDialog = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    // Dialog 2: Manual Proxy / V2Ray Server Configuration
    if (showManualServerDialog) {
        var name by remember { mutableStateOf("") }
        var selectedProto by remember { mutableStateOf(VpnProtocol.SOCKS5) }
        var host by remember { mutableStateOf("127.0.0.1") }
        var portText by remember { mutableStateOf("10808") }
        var usernameOrUuid by remember { mutableStateOf("") }
        var passwordOrSec by remember { mutableStateOf("") }
        var exitIp by remember { mutableStateOf("104.244.72.119") }
        var countryCode by remember { mutableStateOf("US") }
        var timezoneId by remember { mutableStateOf("America/New_York") }

        AlertDialog(
            onDismissRequest = { showManualServerDialog = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = {
                Text(
                    text = "Add Proxy / V2Ray Server",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Protocol",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VpnProtocol.entries.forEach { proto ->
                            FilterChip(
                                selected = selectedProto == proto,
                                onClick = {
                                    selectedProto = proto
                                    portText = proto.defaultPort.toString()
                                },
                                label = { Text(proto.badge, style = MaterialTheme.typography.labelMedium) },
                                shape = MaterialTheme.shapes.extraSmall
                            )
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Server Label (e.g. Local V2Ray SOCKS5)", style = MaterialTheme.typography.bodySmall) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shape = MaterialTheme.shapes.extraSmall,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("Host / IP", style = MaterialTheme.typography.bodySmall) },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            shape = MaterialTheme.shapes.extraSmall,
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Port", style = MaterialTheme.typography.bodySmall) },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            shape = MaterialTheme.shapes.extraSmall,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = usernameOrUuid,
                        onValueChange = { usernameOrUuid = it },
                        label = {
                            Text(
                                if (selectedProto in listOf(VpnProtocol.VLESS, VpnProtocol.VMESS))
                                    "UUID / User ID"
                                else "Username (Optional)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        shape = MaterialTheme.shapes.extraSmall,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = passwordOrSec,
                        onValueChange = { passwordOrSec = it },
                        label = { Text("Password / TLS Key (Optional)", style = MaterialTheme.typography.bodySmall) },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        shape = MaterialTheme.shapes.extraSmall,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = exitIp,
                            onValueChange = { exitIp = it },
                            label = { Text("Exit IP", style = MaterialTheme.typography.bodySmall) },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            shape = MaterialTheme.shapes.extraSmall,
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = countryCode,
                            onValueChange = { countryCode = it.uppercase().take(2) },
                            label = { Text("Country", style = MaterialTheme.typography.bodySmall) },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            shape = MaterialTheme.shapes.extraSmall,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = timezoneId,
                        onValueChange = { timezoneId = it },
                        label = { Text("IANA Timezone (e.g. America/New_York)", style = MaterialTheme.typography.bodySmall) },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        shape = MaterialTheme.shapes.extraSmall,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = "Save & Activate",
                    onClick = {
                        val portNum = portText.toIntOrNull() ?: selectedProto.defaultPort
                        val cleanHost = host.trim().ifBlank { "127.0.0.1" }
                        val cc = countryCode.trim().ifBlank { "US" }
                        val server = VpnProxyServer(
                            id = "custom_${System.currentTimeMillis()}",
                            name = name.trim().ifBlank { "${selectedProto.badge} · $cleanHost:$portNum" },
                            protocol = selectedProto,
                            host = cleanHost,
                            port = portNum,
                            exitIp = exitIp.trim().ifBlank { cleanHost },
                            countryCode = cc,
                            city = "$cc Custom Node",
                            timezoneId = timezoneId.trim().ifBlank { "America/New_York" },
                            localeTag = "en-$cc",
                            latitude = 40.7128,
                            longitude = -74.0060,
                            latencyMs = 24,
                            usernameOrUuid = usernameOrUuid.trim(),
                            passwordOrSecurity = passwordOrSec.trim().ifBlank { "auto" }
                        )
                        viewModel.addCustomProxyServer(server)
                        showManualServerDialog = false
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showManualServerDialog = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }
}
