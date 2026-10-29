package com.example.isolation.module.network

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.isolation.sdk.HollowModuleContext
import com.example.isolation.ui.components.ContentCard
import com.example.isolation.ui.components.ListRow
import com.example.isolation.ui.components.SectionHeader
import com.example.isolation.ui.components.StatusIndicator
import com.example.isolation.ui.components.rememberAppIconBitmap
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun VpnProxyScreen(
    context: HollowModuleContext,
    onBack: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val pm = context.context.packageManager
    val prefs = context.preferences

    var isVpnEnabled by remember { mutableStateOf(prefs.getBoolean("vpn_tunnel_active", false)) }
    var killSwitchEnabled by remember { mutableStateOf(prefs.getBoolean("kill_switch_active", true)) }
    var matchTzWithIp by remember { mutableStateOf(prefs.getBoolean("match_tz_with_ip", true)) }
    var selectedServerId by remember { mutableStateOf(prefs.getString("selected_server_id", "us_ny_vless")) }
    var showImportDialog by remember { mutableStateOf(false) }
    var uriInput by remember { mutableStateOf("") }

    val appList = remember { mutableStateListOf<VpnAppRoutingEntry>() }

    fun loadApps() {
        val installed = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (_: Throwable) {
            emptyList<ApplicationInfo>()
        }
        val routedJson = try {
            JSONObject(prefs.getString("vpn_routed_apps", "{}"))
        } catch (_: Throwable) {
            JSONObject()
        }

        val entries = installed.map { appInfo ->
            val pkg = appInfo.packageName
            val label = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Throwable) { pkg }
            val icon = try { pm.getApplicationIcon(appInfo) } catch (_: Throwable) { null }
            val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isRouted = routedJson.optBoolean(pkg, false)
            VpnAppRoutingEntry(
                packageName = pkg,
                appName = label,
                isSystemApp = isSys,
                icon = icon,
                isProxyEnabled = isRouted
            )
        }.sortedWith(compareBy({ !it.isProxyEnabled }, { it.appName.lowercase() }))

        appList.clear()
        appList.addAll(entries)
    }

    LaunchedEffect(Unit) {
        loadApps()
    }

    fun toggleAppRouting(pkg: String, enabled: Boolean) {
        val routedJson = try {
            JSONObject(prefs.getString("vpn_routed_apps", "{}"))
        } catch (_: Throwable) {
            JSONObject()
        }
        routedJson.put(pkg, enabled)
        prefs.putString("vpn_routed_apps", routedJson.toString())
        loadApps()
        context.auditLogger.log("VPN_APP_ROUTING", pkg, if (enabled) "Proxied" else "Direct")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("vpn_proxy_screen")
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = scheme.onBackground
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Proxy & V2Ray Suite",
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Encrypted routing with Exit IP Geo & Timezone sync",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showImportDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Import Proxy", tint = scheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        ContentCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isVpnEnabled) "Tunnel Active" else "Proxy Disabled",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface
                    )
                    Text(
                        text = if (isVpnEnabled) "Routing traffic through encrypted node" else "Direct connection",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isVpnEnabled,
                    onCheckedChange = { next ->
                        isVpnEnabled = next
                        prefs.putBoolean("vpn_tunnel_active", next)
                        context.auditLogger.log("VPN_TOGGLE", "Global", if (next) "Connected" else "Disconnected")
                    },
                    modifier = Modifier.testTag("vpn_master_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader(title = "Proxy Nodes", subtitle = "Select default exit location")

        Spacer(modifier = Modifier.height(8.dp))

        VpnProxyPresets.defaultServers.forEach { server ->
            val isSelected = server.id == selectedServerId
            ContentCard(
                onClick = {
                    selectedServerId = server.id
                    prefs.putString("selected_server_id", server.id)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("server_card_${server.id}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = server.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = scheme.onSurface
                        )
                        Text(
                            text = "${server.protocol.badge} • ${server.exitIp} (${server.city}) • ${server.latencyMs}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    if (isSelected) {
                        StatusIndicator(label = "Selected", color = scheme.primary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader(title = "App Routing (${appList.count { it.isProxyEnabled }} Proxied)", subtitle = "Route selected apps through proxy")

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(appList.take(60), key = { it.packageName }) { app ->
                val iconBitmap = rememberAppIconBitmap(app.packageName, app.icon)
                ContentCard(
                    onClick = { toggleAppRouting(app.packageName, !app.isProxyEnabled) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListRow(
                        title = app.appName,
                        subtitle = app.packageName,
                        leadingAppIcon = iconBitmap,
                        trailing = {
                            Switch(
                                checked = app.isProxyEnabled,
                                onCheckedChange = { toggleAppRouting(app.packageName, it) }
                            )
                        }
                    )
                }
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Proxy Server", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Paste vless://, vmess://, ss://, trojan://, or socks:// URI:")
                    OutlinedTextField(
                        value = uriInput,
                        onValueChange = { uriInput = it },
                        placeholder = { Text("vless://...@...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = VpnProxyPresets.parseUri(uriInput)
                        if (parsed != null) {
                            selectedServerId = parsed.id
                            prefs.putString("selected_server_id", parsed.id)
                        }
                        showImportDialog = false
                    }
                ) {
                    Text("Import & Select", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
