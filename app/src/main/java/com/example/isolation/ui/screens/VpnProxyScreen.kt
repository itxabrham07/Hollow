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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.isolation.model.VpnProtocol
import com.example.isolation.model.VpnProxyPresets
import com.example.isolation.model.VpnProxyServer
import com.example.isolation.pkg.InstalledAppInfo
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.JakartaFontFamily

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
        Spacer(modifier = Modifier.height(6.dp))

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
                    modifier = Modifier
                        .size(38.dp)
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
                        text = "Proxy & V2Ray Suite",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = JakartaFontFamily
                    )
                    Text(
                        text = if (settings.vpnTunnelEnabled) {
                            "Active [${activeServer.countryCode}] ${activeServer.exitIp} • $routedAppsCount app${if (routedAppsCount == 1) "" else "s"} routed"
                        } else {
                            "Configure SOCKS5, HTTP & V2Ray per-app or whole device"
                        },
                        fontSize = 12.sp,
                        color = if (settings.vpnTunnelEnabled) scheme.primary else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = JakartaFontFamily
                    )
                }
            }

            // Quick Connect / Disconnect Pill in Header
            Button(
                onClick = { viewModel.setVpnTunnelEnabled(!settings.vpnTunnelEnabled) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (settings.vpnTunnelEnabled) scheme.primary else scheme.surfaceVariant,
                    contentColor = if (settings.vpnTunnelEnabled) scheme.onPrimary else scheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("vpn_header_connect_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (settings.vpnTunnelEnabled) "Connected" else "Connect",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Segmented 3-Tab Bar: Servers | Per-App Routing | Advanced Proxy
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.outlineVariant, RoundedCornerShape(14.dp))
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
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) scheme.primary else Color.Transparent)
                        .clickable { selectedTab = tab }
                        .padding(vertical = 10.dp)
                        .testTag("vpn_tab_${tab.name.lowercase()}")
                ) {
                    Text(
                        text = labelText,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = JakartaFontFamily
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTab) {
            VpnSuiteTab.SERVERS -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    // 1. Active Connection & Routing Scope Card
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = scheme.surface),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(
                                1.dp,
                                if (settings.vpnTunnelEnabled) scheme.primary.copy(alpha = 0.5f) else scheme.outlineVariant
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
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
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (settings.vpnTunnelEnabled) scheme.primaryContainer
                                                    else scheme.surfaceVariant
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Public,
                                                contentDescription = null,
                                                tint = if (settings.vpnTunnelEnabled) scheme.onPrimaryContainer else scheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = activeServer.name,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = scheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontFamily = JakartaFontFamily
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${activeServer.protocol.badge} • ${activeServer.host}:${activeServer.port} • Exit IP ${activeServer.exitIp}",
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = scheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Timezone: ${activeServer.timezoneId} (${activeServer.localeTag}) • ${activeServer.latencyMs} ms",
                                                fontSize = 11.sp,
                                                color = scheme.primary,
                                                fontFamily = JakartaFontFamily
                                            )
                                        }
                                    }

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

                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "ROUTING SCOPE (CONNECT WHOLE DEVICE OR PER-APP)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurfaceVariant,
                                    fontFamily = JakartaFontFamily
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Whole Device Button
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.setVpnTunnelEnabled(true)
                                            viewModel.setAllAppsVpnProxy(true)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isWholeDeviceRouted && settings.vpnTunnelEnabled) scheme.primary else scheme.outlineVariant
                                        ),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isWholeDeviceRouted && settings.vpnTunnelEnabled)
                                                scheme.primaryContainer.copy(alpha = 0.35f)
                                            else Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("mode_whole_device")
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "Whole Device",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isWholeDeviceRouted && settings.vpnTunnelEnabled) scheme.primary else scheme.onSurface,
                                                fontFamily = JakartaFontFamily
                                            )
                                            Text(
                                                text = "Route all ${allApps.size} apps",
                                                fontSize = 10.sp,
                                                color = scheme.onSurfaceVariant,
                                                fontFamily = JakartaFontFamily
                                            )
                                        }
                                    }

                                    // Per-App Routing Button
                                    OutlinedButton(
                                        onClick = {
                                            selectedTab = VpnSuiteTab.PER_APP_ROUTING
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (!isWholeDeviceRouted && routedAppsCount > 0) scheme.primary else scheme.outlineVariant
                                        ),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (!isWholeDeviceRouted && routedAppsCount > 0)
                                                scheme.primaryContainer.copy(alpha = 0.35f)
                                            else Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("mode_per_app")
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "Per-App Routing",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (!isWholeDeviceRouted && routedAppsCount > 0) scheme.primary else scheme.onSurface,
                                                fontFamily = JakartaFontFamily
                                            )
                                            Text(
                                                text = "$routedAppsCount app${if (routedAppsCount == 1) "" else "s"} selected",
                                                fontSize = 10.sp,
                                                color = scheme.onSurfaceVariant,
                                                fontFamily = JakartaFontFamily
                                            )
                                        }
                                    }
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
                            Button(
                                onClick = { showManualServerDialog = true },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_add_custom_server")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Add Proxy / V2Ray",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = JakartaFontFamily
                                )
                            }

                            OutlinedButton(
                                onClick = { showUriImportDialog = true },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_import_v2ray_uri")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Paste URI Link",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = JakartaFontFamily
                                )
                            }
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
                                            fontSize = 12.sp,
                                            fontFamily = JakartaFontFamily
                                        )
                                    },
                                    shape = RoundedCornerShape(50),
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
                        ProxyServerNodeCard(
                            server = server,
                            isSelected = isSelected,
                            isCustom = isCustom,
                            onSelect = { viewModel.selectActiveProxyServer(server.id) },
                            onDelete = if (isCustom) {
                                { viewModel.deleteCustomProxyServer(server.id) }
                            } else null
                        )
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
                                fontSize = 13.sp,
                                color = scheme.onSurfaceVariant,
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
                                        contentDescription = "Clear",
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
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(VpnAppRoutingFilter.entries) { filter ->
                                FilterChip(
                                    selected = appFilter == filter,
                                    onClick = { appFilter = filter },
                                    label = {
                                        Text(
                                            text = if (filter == VpnAppRoutingFilter.ROUTED) "Routed ($routedAppsCount)" else filter.label,
                                            fontSize = 11.sp,
                                            fontFamily = JakartaFontFamily
                                        )
                                    },
                                    shape = RoundedCornerShape(50),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = scheme.primaryContainer,
                                        selectedLabelColor = scheme.onPrimaryContainer,
                                        containerColor = scheme.surface,
                                        labelColor = scheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    viewModel.setVpnTunnelEnabled(true)
                                    viewModel.setAllAppsVpnProxy(true)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("vpn_route_all_btn")
                            ) {
                                Text("Route All", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = JakartaFontFamily)
                            }
                            if (routedAppsCount > 0) {
                                TextButton(
                                    onClick = { viewModel.setAllAppsVpnProxy(false) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("vpn_clear_all_btn")
                                ) {
                                    Text("Clear", fontSize = 11.sp, color = scheme.error, fontFamily = JakartaFontFamily)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { appInfo ->
                            PerAppVpnRoutingCard(
                                appInfo = appInfo,
                                allServers = allServers,
                                defaultServer = activeServer,
                                onToggleRouting = { enabled ->
                                    viewModel.toggleAppVpnProxy(
                                        appInfo = appInfo,
                                        enabled = enabled,
                                        serverId = appInfo.proxyServerId.ifBlank { activeServer.id }
                                    )
                                },
                                onSelectServer = { serverId ->
                                    viewModel.toggleAppVpnProxy(
                                        appInfo = appInfo,
                                        enabled = true,
                                        serverId = serverId
                                    )
                                },
                                onUpdateOptions = { killSwitch, syncTz ->
                                    viewModel.toggleAppVpnProxy(
                                        appInfo = appInfo,
                                        enabled = appInfo.vpnProxyEnabled,
                                        serverId = appInfo.proxyServerId.ifBlank { activeServer.id },
                                        requireVpnForNetwork = killSwitch,
                                        matchTimezoneWithIp = syncTz
                                    )
                                }
                            )
                        }
                    }
                }
            }

            VpnSuiteTab.ADVANCED_PROXY -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = scheme.surface),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, scheme.outlineVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "GLOBAL PROXY & ANTI-LEAK ENGINE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.primary,
                                    fontFamily = JakartaFontFamily
                                )

                                AdvancedProxyOptionRow(
                                    icon = Icons.Default.Lock,
                                    title = "Strict VPN Kill Switch",
                                    subtitle = "Block all outgoing sockets if the Proxy or V2Ray tunnel drops",
                                    checked = settings.vpnKillSwitchEnabled,
                                    onCheckedChange = {
                                        viewModel.updateProxyingSuiteOptions(vpnKillSwitch = it)
                                    }
                                )

                                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.45f))

                                AdvancedProxyOptionRow(
                                    icon = Icons.Default.Schedule,
                                    title = "Auto-Sync Timezone & Locale with Exit IP",
                                    subtitle = "Automatically align TimeZone.getDefault() and Locale with the active server's country",
                                    checked = settings.matchTimezoneWithIpGlobal,
                                    onCheckedChange = {
                                        viewModel.updateProxyingSuiteOptions(matchTzWithIp = it)
                                    }
                                )

                                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.45f))

                                AdvancedProxyOptionRow(
                                    icon = Icons.Default.Dns,
                                    title = "SOCKS5 Socket Hook (Java & Native)",
                                    subtitle = "Redirect java.net.Socket and OkHttp connections through the SOCKS5/V2Ray endpoint",
                                    checked = settings.socksProxyEnabled,
                                    onCheckedChange = {
                                        viewModel.updateProxyingSuiteOptions(socksProxy = it)
                                    }
                                )

                                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.45f))

                                AdvancedProxyOptionRow(
                                    icon = Icons.Default.Public,
                                    title = "HTTP / HTTPS CONNECT Proxy Hook",
                                    subtitle = "Inject System http.proxyHost / https.proxyHost properties into routed apps",
                                    checked = settings.httpProxyEnabled,
                                    onCheckedChange = {
                                        viewModel.updateProxyingSuiteOptions(httpProxy = it)
                                    }
                                )

                                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.45f))

                                AdvancedProxyOptionRow(
                                    icon = Icons.Default.Tune,
                                    title = "Chromium WebView Traffic Proxy",
                                    subtitle = "Force embedded Android WebViews & custom tabs through the active proxy",
                                    checked = settings.webViewProxyEnabled,
                                    onCheckedChange = {
                                        viewModel.updateProxyingSuiteOptions(webViewProxy = it)
                                    }
                                )

                                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.45f))

                                AdvancedProxyOptionRow(
                                    icon = Icons.Default.Security,
                                    title = "Hide VPN & Proxy Interfaces (tun0 / ppp0)",
                                    subtitle = "Scrub TRANSPORT_VPN, NetworkInterface tun0, and proxy properties from detection SDKs",
                                    checked = settings.hideVpnConnection,
                                    onCheckedChange = {
                                        viewModel.updateProxyingSuiteOptions(
                                            hideVpnConn = it,
                                            hideProxyConn = it
                                        )
                                    }
                                )

                                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.45f))

                                AdvancedProxyOptionRow(
                                    icon = Icons.Default.VpnKey,
                                    title = "Trust Custom SSL/TLS Certificates",
                                    subtitle = "Bypass SSL pinning in routed apps when inspecting traffic with a custom MITM proxy",
                                    checked = settings.trustAllCertificates,
                                    onCheckedChange = {
                                        viewModel.updateProxyingSuiteOptions(trustAllCerts = it)
                                    }
                                )
                            }
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
                        text = "Paste a vless://, vmess://, ss://, trojan://, socks://, or host:port link:",
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                        fontFamily = JakartaFontFamily
                    )
                    OutlinedTextField(
                        value = uriInput,
                        onValueChange = {
                            uriInput = it
                            errorMsg = null
                        },
                        placeholder = {
                            Text(
                                text = "vless://uuid@host:443#US-NewYork",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        singleLine = false,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_v2ray_uri")
                    )
                    errorMsg?.let {
                        Text(
                            text = it,
                            fontSize = 12.sp,
                            color = scheme.error,
                            fontFamily = JakartaFontFamily
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ok = viewModel.importProxyServerFromUri(uriInput)
                        if (ok) {
                            showUriImportDialog = false
                        } else {
                            errorMsg = "Invalid URI format. Enter a valid V2Ray URI or host:port."
                        }
                    },
                    modifier = Modifier.testTag("confirm_import_v2ray_uri")
                ) {
                    Text("Import & Connect", fontFamily = JakartaFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUriImportDialog = false }) {
                    Text("Cancel", fontFamily = JakartaFontFamily)
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
            title = {
                Text(
                    text = "Add Proxy / V2Ray Server",
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily
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
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurfaceVariant,
                        fontFamily = JakartaFontFamily
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VpnProtocol.entries.forEach { proto ->
                            FilterChip(
                                selected = selectedProto == proto,
                                onClick = {
                                    selectedProto = proto
                                    portText = proto.defaultPort.toString()
                                },
                                label = { Text(proto.badge, fontSize = 11.sp, fontFamily = JakartaFontFamily) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Server Label (e.g. Local V2Ray SOCKS5)", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("Host / IP", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Port", fontSize = 12.sp) },
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
                                fontSize = 12.sp
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = passwordOrSec,
                        onValueChange = { passwordOrSec = it },
                        label = { Text("Password / TLS Key (Optional)", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = exitIp,
                            onValueChange = { exitIp = it },
                            label = { Text("Exit IP", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = countryCode,
                            onValueChange = { countryCode = it.uppercase().take(2) },
                            label = { Text("Country", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = timezoneId,
                        onValueChange = { timezoneId = it },
                        label = { Text("IANA Timezone (e.g. America/New_York)", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
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
                ) {
                    Text("Save & Activate", fontFamily = JakartaFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualServerDialog = false }) {
                    Text("Cancel", fontFamily = JakartaFontFamily)
                }
            }
        )
    }
}

@Composable
private fun ProxyServerNodeCard(
    server: VpnProxyServer,
    isSelected: Boolean,
    isCustom: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) scheme.primaryContainer.copy(alpha = 0.22f) else scheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isSelected) scheme.primary else scheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("server_node_${server.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) scheme.primary.copy(alpha = 0.18f)
                            else scheme.surfaceVariant
                        )
                ) {
                    Text(
                        text = server.countryCode,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSelected) scheme.primary else scheme.onSurfaceVariant,
                        fontFamily = JakartaFontFamily
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = server.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = JakartaFontFamily,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(scheme.primary.copy(alpha = 0.14f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = server.protocol.badge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.primary,
                                fontFamily = JakartaFontFamily
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${server.host}:${server.port} • Exit ${server.exitIp} • ${server.latencyMs}ms",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "TZ: ${server.timezoneId} (${server.localeTag})",
                        fontSize = 11.sp,
                        color = scheme.onSurfaceVariant,
                        fontFamily = JakartaFontFamily
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
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
                    onClick = onSelect,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = scheme.primary,
                        unselectedColor = scheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun PerAppVpnRoutingCard(
    appInfo: InstalledAppInfo,
    allServers: List<VpnProxyServer>,
    defaultServer: VpnProxyServer,
    onToggleRouting: (Boolean) -> Unit,
    onSelectServer: (String) -> Unit,
    onUpdateOptions: (killSwitch: Boolean, syncTz: Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isRouted = appInfo.vpnProxyEnabled
    val currentServer = remember(appInfo.proxyServerId, allServers, defaultServer) {
        allServers.firstOrNull { it.id == appInfo.proxyServerId } ?: defaultServer
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isRouted) scheme.primaryContainer.copy(alpha = 0.2f) else scheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isRouted) scheme.primary.copy(alpha = 0.45f) else scheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vpn_app_card_${appInfo.packageName}")
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
                    val iconBitmap = com.example.isolation.ui.components.rememberAppIconBitmap(
                        packageName = appInfo.packageName,
                        drawable = appInfo.icon,
                        sizePx = 88
                    )
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = appInfo.appName,
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
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appInfo.appName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = JakartaFontFamily
                        )
                        Text(
                            text = if (isRouted) {
                                "Routed via [${currentServer.countryCode}] ${currentServer.protocol.badge} (${currentServer.exitIp})"
                            } else {
                                "Direct connection • Tap switch to route via Proxy/V2Ray"
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
                    onCheckedChange = onToggleRouting,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = scheme.onPrimary,
                        checkedTrackColor = scheme.primary
                    ),
                    modifier = Modifier.testTag("switch_vpn_app_${appInfo.packageName}")
                )
            }

            AnimatedVisibility(visible = isRouted) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.45f))
                    Text(
                        text = "Exit Server Override for ${appInfo.appName}:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurfaceVariant,
                        fontFamily = JakartaFontFamily
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(allServers, key = { it.id }) { srv ->
                            val selected = srv.id == currentServer.id
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectServer(srv.id) },
                                label = {
                                    Text(
                                        text = "[${srv.countryCode}] ${srv.protocol.badge} · ${srv.exitIp}",
                                        fontSize = 11.sp,
                                        fontFamily = JakartaFontFamily
                                    )
                                },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = scheme.primary,
                                    selectedLabelColor = scheme.onPrimary,
                                    containerColor = scheme.surfaceVariant.copy(alpha = 0.6f),
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
                                onUpdateOptions(!appInfo.requireVpnForNetwork, appInfo.matchTimezoneWithIp)
                            },
                            label = {
                                Text(
                                    text = "Kill-Switch if VPN Off",
                                    fontSize = 11.sp,
                                    fontFamily = JakartaFontFamily
                                )
                            },
                            shape = RoundedCornerShape(50)
                        )
                        FilterChip(
                            selected = appInfo.matchTimezoneWithIp,
                            onClick = {
                                onUpdateOptions(appInfo.requireVpnForNetwork, !appInfo.matchTimezoneWithIp)
                            },
                            label = {
                                Text(
                                    text = "Sync TZ (${currentServer.timezoneId})",
                                    fontSize = 11.sp,
                                    fontFamily = JakartaFontFamily
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

@Composable
private fun AdvancedProxyOptionRow(
    icon: ImageVector,
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
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (checked) scheme.primaryContainer else scheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
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
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = scheme.onPrimary,
                checkedTrackColor = scheme.primary
            )
        )
    }
}
