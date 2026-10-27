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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.isolation.model.VpnProtocol
import com.example.isolation.model.VpnProxyServer
import com.example.isolation.pkg.InstalledAppInfo
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.JakartaFontFamily

private enum class VpnAppFilter {
    ROUTED_ONLY,
    BROWSERS_AND_SOCIAL,
    USER_APPS,
    ALL
}

@Composable
fun VpnProxyScreen(
    viewModel: IsoVaultViewModel,
    onBack: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val settings by viewModel.settings.collectAsState()
    val allApps by viewModel.allInstalledApps.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = V2Ray Nodes, 1 = Per-App VPN, 2 = Proxying Suite
    var showImportUriDialog by remember { mutableStateOf(false) }
    var showAddCustomNodeDialog by remember { mutableStateOf(false) }

    var appSearchQuery by remember { mutableStateOf("") }
    var appFilter by remember { mutableStateOf(VpnAppFilter.ALL) }

    val activeServer = settings.activeServer
    val isVpnActive = settings.vpnTunnelEnabled

    val routedAppsCount = remember(allApps) {
        allApps.count { it.vpnProxyEnabled || it.requireVpnForNetwork || it.matchTimezoneWithIp }
    }

    BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("vpn_proxy_screen")
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
                    onClick = onBack,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("vpn_proxy_back_button")
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
                        text = "VPN & Proxy (V2Ray)",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = JakartaFontFamily
                    )
                    Text(
                        text = if (isVpnActive) {
                            "Active: ${activeServer.exitIp} (${activeServer.timezoneId}) • $routedAppsCount apps"
                        } else {
                            "Tunnel Disconnected • VPN-dependent controls paused"
                        },
                        fontSize = 12.sp,
                        color = if (isVpnActive) scheme.primary else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = JakartaFontFamily
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    onClick = { showImportUriDialog = true },
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("btn_import_v2ray_uri")
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Import URI",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "URI",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JakartaFontFamily
                    )
                }
                Button(
                    onClick = { showAddCustomNodeDialog = true },
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("btn_add_custom_proxy")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Node",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Node",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JakartaFontFamily
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Master V2Ray / Proxy Tunnel Status Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isVpnActive) scheme.primaryContainer.copy(alpha = 0.28f) else scheme.surface
            ),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(
                1.dp,
                if (isVpnActive) scheme.primary else scheme.outlineVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_master_vpn_tunnel")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isVpnActive) scheme.primary else scheme.surfaceContainerHigh)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = if (isVpnActive) scheme.onPrimary else scheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeServer.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = JakartaFontFamily
                            )
                            Text(
                                text = "${activeServer.protocol.label} • ${activeServer.host}:${activeServer.port}",
                                fontSize = 12.sp,
                                color = scheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Switch(
                        checked = isVpnActive,
                        onCheckedChange = { viewModel.setVpnTunnelEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = scheme.onPrimary,
                            checkedTrackColor = scheme.primary
                        ),
                        modifier = Modifier.testTag("switch_master_vpn_tunnel")
                    )
                }

                // Exit IP + Timezone Sync Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    VpnMetaBadge(
                        icon = Icons.Default.Public,
                        text = "Exit IP: ${activeServer.exitIp} (${activeServer.countryCode})",
                        highlight = isVpnActive
                    )
                    VpnMetaBadge(
                        icon = Icons.Default.Schedule,
                        text = if (settings.matchTimezoneWithIpGlobal) {
                            "TZ Matched: ${activeServer.timezoneId}"
                        } else {
                            "Node TZ: ${activeServer.timezoneId}"
                        },
                        highlight = isVpnActive && settings.matchTimezoneWithIpGlobal
                    )
                    if (settings.vpnKillSwitchEnabled) {
                        VpnMetaBadge(
                            icon = Icons.Default.Lock,
                            text = "Kill-Switch Armed",
                            highlight = true
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Section Filter Tabs
        val chipColors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = scheme.primaryContainer,
            selectedLabelColor = scheme.onPrimaryContainer,
            containerColor = scheme.surface,
            labelColor = scheme.onSurfaceVariant
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                label = { Text("V2Ray Nodes (${settings.allProxyServers.size})", fontFamily = JakartaFontFamily) },
                shape = RoundedCornerShape(50),
                colors = chipColors,
                modifier = Modifier.testTag("tab_vpn_nodes")
            )
            FilterChip(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                label = { Text("Per-App Routing ($routedAppsCount)", fontFamily = JakartaFontFamily) },
                shape = RoundedCornerShape(50),
                colors = chipColors,
                modifier = Modifier.testTag("tab_vpn_apps")
            )
            FilterChip(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                label = { Text("Proxying Suite", fontFamily = JakartaFontFamily) },
                shape = RoundedCornerShape(50),
                colors = chipColors,
                modifier = Modifier.testTag("tab_vpn_suite")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (selectedTab) {
            0 -> V2RayNodesTabContent(
                servers = settings.allProxyServers,
                activeServerId = settings.activeProxyServerId,
                customServerIds = settings.customProxyServers.map { it.id }.toSet(),
                onSelectServer = { server ->
                    viewModel.selectActiveProxyServer(server.id)
                },
                onDeleteCustomServer = { serverId ->
                    viewModel.deleteCustomProxyServer(serverId)
                }
            )
            1 -> PerAppVpnRoutingTabContent(
                allApps = allApps,
                isVpnActive = isVpnActive,
                activeServer = activeServer,
                searchQuery = appSearchQuery,
                onSearchChange = { appSearchQuery = it },
                activeFilter = appFilter,
                onFilterChange = { appFilter = it },
                onEnableGlobalVpn = { viewModel.setVpnTunnelEnabled(true) },
                onUpdateAppVpn = { app, enabled, requireVpn, matchTz, webViewProxy ->
                    viewModel.toggleAppVpnProxy(
                        appInfo = app,
                        enabled = enabled,
                        requireVpnForNetwork = requireVpn,
                        matchTimezoneWithIp = matchTz,
                        webViewProxyEnabled = webViewProxy
                    )
                }
            )
            2 -> ProxyingSuiteTabContent(
                isVpnActive = isVpnActive,
                activeServer = activeServer,
                socksProxy = settings.socksProxyEnabled,
                httpProxy = settings.httpProxyEnabled,
                webViewProxy = settings.webViewProxyEnabled,
                trustAllCerts = settings.trustAllCertificates,
                hideProxy = settings.hideProxyConnection,
                hideVpn = settings.hideVpnConnection,
                killSwitch = settings.vpnKillSwitchEnabled,
                matchTimezoneWithIp = settings.matchTimezoneWithIpGlobal,
                showIpInfo = settings.showIpInfoInToolbar,
                onEnableGlobalVpn = { viewModel.setVpnTunnelEnabled(true) },
                onUpdateSuite = { socks, http, webView, trustCerts, hidePrx, hideVpnConn, killSw, matchTz, showIp ->
                    viewModel.updateProxyingSuiteOptions(
                        socksProxy = socks,
                        httpProxy = http,
                        webViewProxy = webView,
                        trustAllCerts = trustCerts,
                        hideProxyConn = hidePrx,
                        hideVpnConn = hideVpnConn,
                        vpnKillSwitch = killSw,
                        matchTzWithIp = matchTz,
                        showIpInfo = showIp
                    )
                }
            )
        }
    }

    if (showImportUriDialog) {
        var uriInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportUriDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Import V2Ray / Proxy URI",
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste a vless://, vmess://, ss://, trojan://, socks://, or http:// link. Hollow automatically extracts the host, port, and matches the exit IP timezone.",
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                        fontFamily = JakartaFontFamily
                    )
                    OutlinedTextField(
                        value = uriInput,
                        onValueChange = { uriInput = it },
                        placeholder = {
                            Text(
                                text = "vless://uuid@us-ny.hollow-node.net:443#US-NewYork",
                                fontSize = 12.sp
                            )
                        },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (uriInput.isNotBlank()) {
                            viewModel.importProxyServerFromUri(uriInput)
                            showImportUriDialog = false
                        }
                    },
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Import & Activate", fontFamily = JakartaFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportUriDialog = false }) {
                    Text("Cancel", fontFamily = JakartaFontFamily)
                }
            }
        )
    }

    if (showAddCustomNodeDialog) {
        var name by remember { mutableStateOf("Custom V2Ray Node") }
        var protocol by remember { mutableStateOf(VpnProtocol.VLESS) }
        var host by remember { mutableStateOf("127.0.0.1") }
        var portInput by remember { mutableStateOf("10808") }
        var exitIp by remember { mutableStateOf("185.159.157.88") }
        var countryCode by remember { mutableStateOf("CH") }
        var city by remember { mutableStateOf("Zurich") }
        var timezone by remember { mutableStateOf("Europe/Zurich") }

        AlertDialog(
            onDismissRequest = { showAddCustomNodeDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Add Custom V2Ray / Proxy Node",
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Node Name", fontSize = 11.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        VpnProtocol.entries.forEach { proto ->
                            FilterChip(
                                selected = protocol == proto,
                                onClick = { protocol = proto },
                                label = { Text(proto.label, fontSize = 11.sp) },
                                shape = RoundedCornerShape(50)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("Host / IP", fontSize = 11.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.4f)
                        )
                        OutlinedTextField(
                            value = portInput,
                            onValueChange = { portInput = it },
                            label = { Text("Port", fontSize = 11.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(0.8f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = exitIp,
                            onValueChange = { exitIp = it },
                            label = { Text("Exit IP", fontSize = 11.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.2f)
                        )
                        OutlinedTextField(
                            value = countryCode,
                            onValueChange = { countryCode = it.uppercase().take(2) },
                            label = { Text("CC", fontSize = 11.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(0.6f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it },
                            label = { Text("City", fontSize = 11.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(0.9f)
                        )
                        OutlinedTextField(
                            value = timezone,
                            onValueChange = { timezone = it },
                            label = { Text("Exit IP Timezone", fontSize = 11.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newServer = VpnProxyServer(
                            id = "custom_${System.currentTimeMillis()}",
                            name = name.ifBlank { "${protocol.badge} Node" },
                            protocol = protocol,
                            host = host.ifBlank { "127.0.0.1" },
                            port = portInput.toIntOrNull() ?: protocol.defaultPort,
                            exitIp = exitIp.ifBlank { "127.0.0.1" },
                            countryCode = countryCode.ifBlank { "US" },
                            city = city.ifBlank { "Custom" },
                            timezoneId = timezone.ifBlank { "UTC" },
                            localeTag = if (countryCode.equals("JP", true)) "ja-JP" else "en-$countryCode",
                            latitude = 40.7128,
                            longitude = -74.0060,
                            latencyMs = (25..65).random()
                        )
                        viewModel.addCustomProxyServer(newServer)
                        showAddCustomNodeDialog = false
                    },
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Save Node", fontFamily = JakartaFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomNodeDialog = false }) {
                    Text("Cancel", fontFamily = JakartaFontFamily)
                }
            }
        )
    }
}

@Composable
private fun VpnMetaBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    highlight: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (highlight) scheme.primary.copy(alpha = 0.14f) else scheme.surfaceContainerHigh,
        shape = RoundedCornerShape(50)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlight) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (highlight) scheme.primary else scheme.onSurfaceVariant,
                fontFamily = JakartaFontFamily
            )
        }
    }
}

@Composable
private fun V2RayNodesTabContent(
    servers: List<VpnProxyServer>,
    activeServerId: String,
    customServerIds: Set<String>,
    onSelectServer: (VpnProxyServer) -> Unit,
    onDeleteCustomServer: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(servers, key = { it.id }) { server ->
            val isSelected = server.id == activeServerId
            val isCustom = server.id in customServerIds

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) scheme.primaryContainer.copy(alpha = 0.25f) else scheme.surface
                ),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) scheme.primary else scheme.outlineVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectServer(server) }
                    .testTag("vpn_node_card_${server.id}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelectServer(server) }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = server.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                                fontFamily = JakartaFontFamily
                            )
                            Surface(
                                color = scheme.secondaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = server.protocol.label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "${server.host}:${server.port} • Exit IP: ${server.exitIp}",
                            fontSize = 11.sp,
                            color = scheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "IP Timezone: ${server.timezoneId} (${server.city}, ${server.countryCode})",
                            fontSize = 11.sp,
                            color = scheme.primary,
                            fontWeight = FontWeight.Medium,
                            fontFamily = JakartaFontFamily
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Surface(
                            color = Color(0xFF188038).copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "${server.latencyMs} ms",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF188038),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        if (isCustom) {
                            IconButton(
                                onClick = { onDeleteCustomServer(server.id) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete node",
                                    tint = scheme.error,
                                    modifier = Modifier.size(16.dp)
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
private fun PerAppVpnRoutingTabContent(
    allApps: List<InstalledAppInfo>,
    isVpnActive: Boolean,
    activeServer: VpnProxyServer,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    activeFilter: VpnAppFilter,
    onFilterChange: (VpnAppFilter) -> Unit,
    onEnableGlobalVpn: () -> Unit,
    onUpdateAppVpn: (
        app: InstalledAppInfo,
        enabled: Boolean,
        requireVpn: Boolean,
        matchTz: Boolean,
        webViewProxy: Boolean
    ) -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    val filteredApps = remember(allApps, searchQuery, activeFilter) {
        allApps.filter { app ->
            val matchesFilter = when (activeFilter) {
                VpnAppFilter.ROUTED_ONLY -> app.vpnProxyEnabled || app.requireVpnForNetwork || app.matchTimezoneWithIp
                VpnAppFilter.BROWSERS_AND_SOCIAL -> {
                    val pkg = app.packageName.lowercase()
                    pkg.contains("chrome") || pkg.contains("browser") || pkg.contains("firefox") ||
                        pkg.contains("brave") || pkg.contains("snapchat") || pkg.contains("facebook") ||
                        pkg.contains("instagram") || pkg.contains("tiktok") || pkg.contains("whatsapp") ||
                        pkg.contains("telegram") || pkg.contains("twitter")
                }
                VpnAppFilter.USER_APPS -> !app.isSystemApp
                VpnAppFilter.ALL -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                app.appName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isVpnActive) {
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.errorContainer.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, scheme.error.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = scheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "VPN Tunnel is OFF. Per-app proxy routing, IP-Timezone sync & Kill-Switch controls require VPN to work.",
                            fontSize = 11.sp,
                            color = scheme.onErrorContainer,
                            fontFamily = JakartaFontFamily
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onEnableGlobalVpn,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Turn On", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = {
                Text(
                    text = "Search Chrome, Snapchat, Meta or any app...",
                    fontSize = 13.sp,
                    fontFamily = JakartaFontFamily
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = activeFilter == VpnAppFilter.ALL,
                    onClick = { onFilterChange(VpnAppFilter.ALL) },
                    label = { Text("All Apps", fontSize = 12.sp, fontFamily = JakartaFontFamily) },
                    shape = RoundedCornerShape(50)
                )
            }
            item {
                FilterChip(
                    selected = activeFilter == VpnAppFilter.ROUTED_ONLY,
                    onClick = { onFilterChange(VpnAppFilter.ROUTED_ONLY) },
                    label = { Text("Routed Only", fontSize = 12.sp, fontFamily = JakartaFontFamily) },
                    shape = RoundedCornerShape(50)
                )
            }
            item {
                FilterChip(
                    selected = activeFilter == VpnAppFilter.BROWSERS_AND_SOCIAL,
                    onClick = { onFilterChange(VpnAppFilter.BROWSERS_AND_SOCIAL) },
                    label = { Text("Browsers & Social", fontSize = 12.sp, fontFamily = JakartaFontFamily) },
                    shape = RoundedCornerShape(50)
                )
            }
            item {
                FilterChip(
                    selected = activeFilter == VpnAppFilter.USER_APPS,
                    onClick = { onFilterChange(VpnAppFilter.USER_APPS) },
                    label = { Text("Downloaded", fontSize = 12.sp, fontFamily = JakartaFontFamily) },
                    shape = RoundedCornerShape(50)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredApps, key = { it.packageName }) { app ->
                val isRouted = app.vpnProxyEnabled
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isRouted) scheme.primaryContainer.copy(alpha = 0.22f) else scheme.surface
                    ),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isRouted) scheme.primary.copy(alpha = 0.45f) else scheme.outlineVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
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
                                val iconBitmap = remember(app.packageName, app.icon) {
                                    try {
                                        app.icon?.toBitmap(88, 88)?.asImageBitmap()
                                    } catch (_: Throwable) {
                                        null
                                    }
                                }
                                if (iconBitmap != null) {
                                    Image(
                                        bitmap = iconBitmap,
                                        contentDescription = app.appName,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                } else {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(scheme.primaryContainer)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhoneAndroid,
                                            contentDescription = null,
                                            tint = scheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontFamily = JakartaFontFamily
                                    )
                                    Text(
                                        text = if (isRouted) {
                                            "Routed via ${activeServer.name} • TZ: ${activeServer.timezoneId}"
                                        } else {
                                            "Direct connection • Tap switch to route via V2Ray"
                                        },
                                        fontSize = 11.sp,
                                        color = if (isRouted) scheme.primary else scheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontFamily = JakartaFontFamily
                                    )
                                }
                            }

                            Switch(
                                checked = isRouted,
                                onCheckedChange = { enable ->
                                    onUpdateAppVpn(
                                        app,
                                        enable,
                                        app.requireVpnForNetwork,
                                        if (enable) true else app.matchTimezoneWithIp,
                                        app.webViewProxyEnabled
                                    )
                                }
                            )
                        }

                        if (isRouted) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                FilterChip(
                                    selected = app.matchTimezoneWithIp,
                                    onClick = {
                                        onUpdateAppVpn(
                                            app,
                                            true,
                                            app.requireVpnForNetwork,
                                            !app.matchTimezoneWithIp,
                                            app.webViewProxyEnabled
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "Match IP Timezone (${activeServer.timezoneId})",
                                            fontSize = 11.sp,
                                            fontFamily = JakartaFontFamily
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    shape = RoundedCornerShape(50)
                                )

                                FilterChip(
                                    selected = app.requireVpnForNetwork,
                                    onClick = {
                                        onUpdateAppVpn(
                                            app,
                                            true,
                                            !app.requireVpnForNetwork,
                                            app.matchTimezoneWithIp,
                                            app.webViewProxyEnabled
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "Block Without VPN (Kill-Switch)",
                                            fontSize = 11.sp,
                                            fontFamily = JakartaFontFamily
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    shape = RoundedCornerShape(50)
                                )

                                FilterChip(
                                    selected = app.webViewProxyEnabled,
                                    onClick = {
                                        onUpdateAppVpn(
                                            app,
                                            true,
                                            app.requireVpnForNetwork,
                                            app.matchTimezoneWithIp,
                                            !app.webViewProxyEnabled
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "WebView / Chrome Proxy",
                                            fontSize = 11.sp,
                                            fontFamily = JakartaFontFamily
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    shape = RoundedCornerShape(50)
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
private fun ProxyingSuiteTabContent(
    isVpnActive: Boolean,
    activeServer: VpnProxyServer,
    socksProxy: Boolean,
    httpProxy: Boolean,
    webViewProxy: Boolean,
    trustAllCerts: Boolean,
    hideProxy: Boolean,
    hideVpn: Boolean,
    killSwitch: Boolean,
    matchTimezoneWithIp: Boolean,
    showIpInfo: Boolean,
    onEnableGlobalVpn: () -> Unit,
    onUpdateSuite: (
        socks: Boolean,
        http: Boolean,
        webView: Boolean,
        trustCerts: Boolean,
        hidePrx: Boolean,
        hideVpnConn: Boolean,
        killSw: Boolean,
        matchTz: Boolean,
        showIp: Boolean
    ) -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (!isVpnActive) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = scheme.errorContainer.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, scheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "VPN Tunnel Required for Proxying Controls",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onErrorContainer,
                                fontFamily = JakartaFontFamily
                            )
                            Text(
                                text = "Controls marked 'Requires VPN' are paused until the V2Ray / Proxy tunnel is enabled.",
                                fontSize = 11.sp,
                                color = scheme.onErrorContainer,
                                fontFamily = JakartaFontFamily
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onEnableGlobalVpn,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Enable VPN", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Proxying Suite & Network Controls",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        fontFamily = JakartaFontFamily
                    )

                    ProxyingSuiteRow(
                        title = "Match Timezone with Exit IP",
                        subtitle = "Automatically sets Java & Chrome Intl TimeZone to ${activeServer.timezoneId} (${activeServer.exitIp})",
                        badge = "Requires VPN",
                        vpnActive = isVpnActive,
                        checked = matchTimezoneWithIp,
                        onCheckedChange = {
                            onUpdateSuite(socksProxy, httpProxy, webViewProxy, trustAllCerts, hideProxy, hideVpn, killSwitch, it, showIpInfo)
                        }
                    )

                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

                    ProxyingSuiteRow(
                        title = "SOCKS5 Proxy Routing",
                        subtitle = "Routes Java Socket & OkHttp traffic via ${activeServer.host}:${activeServer.port}",
                        badge = "Requires VPN",
                        vpnActive = isVpnActive,
                        checked = socksProxy,
                        onCheckedChange = {
                            onUpdateSuite(it, httpProxy, webViewProxy, trustAllCerts, hideProxy, hideVpn, killSwitch, matchTimezoneWithIp, showIpInfo)
                        }
                    )

                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

                    ProxyingSuiteRow(
                        title = "HTTP / HTTPS Proxy",
                        subtitle = "Sets system http.proxyHost & https.proxyHost properties inside hooked apps",
                        badge = "Requires VPN",
                        vpnActive = isVpnActive,
                        checked = httpProxy,
                        onCheckedChange = {
                            onUpdateSuite(socksProxy, it, webViewProxy, trustAllCerts, hideProxy, hideVpn, killSwitch, matchTimezoneWithIp, showIpInfo)
                        }
                    )

                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

                    ProxyingSuiteRow(
                        title = "WebView & Chrome Proxy",
                        subtitle = "Forces Android WebView & browser traffic through active proxy tunnel",
                        badge = "Requires VPN",
                        vpnActive = isVpnActive,
                        checked = webViewProxy,
                        onCheckedChange = {
                            onUpdateSuite(socksProxy, httpProxy, it, trustAllCerts, hideProxy, hideVpn, killSwitch, matchTimezoneWithIp, showIpInfo)
                        }
                    )

                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

                    ProxyingSuiteRow(
                        title = "VPN Kill-Switch (Don't Work Without VPN)",
                        subtitle = "Blocks all network sockets for routed apps if V2Ray / VPN tunnel is disconnected",
                        badge = "Kill-Switch",
                        vpnActive = true,
                        checked = killSwitch,
                        onCheckedChange = {
                            onUpdateSuite(socksProxy, httpProxy, webViewProxy, trustAllCerts, hideProxy, hideVpn, it, matchTimezoneWithIp, showIpInfo)
                        }
                    )

                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

                    ProxyingSuiteRow(
                        title = "Hide VPN Connection",
                        subtitle = "Conceals tun0/wg0/ppp0 interfaces & NetworkCapabilities.TRANSPORT_VPN",
                        badge = "Stealth",
                        vpnActive = true,
                        checked = hideVpn,
                        onCheckedChange = {
                            onUpdateSuite(socksProxy, httpProxy, webViewProxy, trustAllCerts, hideProxy, it, killSwitch, matchTimezoneWithIp, showIpInfo)
                        }
                    )

                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

                    ProxyingSuiteRow(
                        title = "Hide Proxy Connection",
                        subtitle = "Strips System http.proxyHost properties from app inspection while keeping tunnel active",
                        badge = "Stealth",
                        vpnActive = true,
                        checked = hideProxy,
                        onCheckedChange = {
                            onUpdateSuite(socksProxy, httpProxy, webViewProxy, trustAllCerts, it, hideVpn, killSwitch, matchTimezoneWithIp, showIpInfo)
                        }
                    )

                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

                    ProxyingSuiteRow(
                        title = "Trust All SSL Certificates",
                        subtitle = "Bypasses X509TrustManager SSL pinning for custom proxy inspection",
                        badge = "Proxying suite",
                        vpnActive = isVpnActive,
                        checked = trustAllCerts,
                        onCheckedChange = {
                            onUpdateSuite(socksProxy, httpProxy, webViewProxy, it, hideProxy, hideVpn, killSwitch, matchTimezoneWithIp, showIpInfo)
                        }
                    )

                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

                    ProxyingSuiteRow(
                        title = "Show Exit IP Info in Dashboard",
                        subtitle = "Displays active Exit IP and synced Timezone badge on Home & Privacy screens",
                        badge = "Proxying suite",
                        vpnActive = true,
                        checked = showIpInfo,
                        onCheckedChange = {
                            onUpdateSuite(socksProxy, httpProxy, webViewProxy, trustAllCerts, hideProxy, hideVpn, killSwitch, matchTimezoneWithIp, it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProxyingSuiteRow(
    title: String,
    subtitle: String,
    badge: String,
    vpnActive: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (vpnActive) scheme.onSurface else scheme.onSurfaceVariant,
                    fontFamily = JakartaFontFamily
                )
                Surface(
                    color = if (!vpnActive && badge == "Requires VPN") {
                        scheme.errorContainer
                    } else {
                        scheme.secondaryContainer
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (!vpnActive && badge == "Requires VPN") "VPN Off • Paused" else badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (!vpnActive && badge == "Requires VPN") {
                            scheme.onErrorContainer
                        } else {
                            scheme.onSecondaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
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
