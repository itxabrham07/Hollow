package com.example.isolation.module.isolation

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import org.json.JSONObject

@Composable
fun AppsScreen(
    context: HollowModuleContext,
    onBack: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val pm = context.context.packageManager
    val prefs = context.preferences

    val appList = remember { mutableStateListOf<AppIsolationEntry>() }
    var searchQuery by remember { mutableStateOf("") }
    var selectedAppForConfig by remember { mutableStateOf<AppIsolationEntry?>(null) }
    var filterMode by remember { mutableStateOf("ALL") }

    fun loadApps() {
        val installed = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (_: Throwable) {
            emptyList<ApplicationInfo>()
        }
        val configJson = try {
            JSONObject(prefs.getString("isolation_rules_json", "{}"))
        } catch (_: Throwable) {
            JSONObject()
        }

        val entries = installed.map { appInfo ->
            val pkg = appInfo.packageName
            val label = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Throwable) { pkg }
            val icon = try { pm.getApplicationIcon(appInfo) } catch (_: Throwable) { null }
            val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            val ruleObj = configJson.optJSONObject(pkg)
            val isIsolated = ruleObj?.optBoolean("isolated", false) ?: false
            val modeStr = ruleObj?.optString("mode", IsolationMode.SHARED_WORKSPACE.name)
                ?: IsolationMode.SHARED_WORKSPACE.name
            val mode = try { IsolationMode.valueOf(modeStr) } catch (_: Throwable) { IsolationMode.SHARED_WORKSPACE }
            val blockPollution = ruleObj?.optBoolean("blockRootPollution", true) ?: true

            AppIsolationEntry(
                packageName = pkg,
                appName = label,
                isSystemApp = isSys,
                icon = icon,
                isIsolated = isIsolated,
                isolationMode = mode,
                blockRootPollution = blockPollution
            )
        }.sortedWith(compareBy({ !it.isIsolated }, { it.appName.lowercase() }))

        appList.clear()
        appList.addAll(entries)
        prefs.putInt("isolated_apps_count", entries.count { it.isIsolated })
        context.notifyStateChanged()
    }

    LaunchedEffect(Unit) {
        loadApps()
    }

    fun saveConfig(target: AppIsolationEntry, isolated: Boolean, mode: IsolationMode, blockPollution: Boolean) {
        val configJson = try {
            JSONObject(prefs.getString("isolation_rules_json", "{}"))
        } catch (_: Throwable) {
            JSONObject()
        }
        val obj = JSONObject()
            .put("packageName", target.packageName)
            .put("isolated", isolated)
            .put("mode", mode.name)
            .put("blockRootPollution", blockPollution)
        configJson.put(target.packageName, obj)
        prefs.putString("isolation_rules_json", configJson.toString())

        context.auditLogger.log(
            action = "STORAGE_ISOLATION_CONFIG",
            target = target.packageName,
            details = if (isolated) "Mode: ${mode.name}" else "Un-isolated"
        )
        loadApps()
    }

    val filteredApps = appList.filter {
        val matchesSearch = searchQuery.isBlank() ||
                it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (filterMode) {
            "ISOLATED" -> it.isIsolated
            else -> true
        }
        matchesSearch && matchesFilter
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("apps_isolation_screen")
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
                    text = "Storage Isolation",
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Per-app virtual volumes and media sandboxing",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { loadApps() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = scheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search installed applications...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = filterMode == "ALL",
                onClick = { filterMode = "ALL" },
                label = { Text("All (${appList.size})") }
            )
            FilterChip(
                selected = filterMode == "ISOLATED",
                onClick = { filterMode = "ISOLATED" },
                label = { Text("Isolated (${appList.count { it.isIsolated }})") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredApps, key = { it.packageName }) { app ->
                val iconBitmap = rememberAppIconBitmap(app.packageName, app.icon)
                ContentCard(
                    onClick = { selectedAppForConfig = app },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("isolation_row_${app.packageName}")
                ) {
                    ListRow(
                        title = app.appName,
                        subtitle = if (app.isIsolated) "Isolated: ${app.isolationMode.title}" else app.packageName,
                        leadingAppIcon = iconBitmap,
                        trailing = {
                            if (app.isIsolated) {
                                StatusIndicator(label = app.isolationMode.badge, color = scheme.primary)
                            }
                        }
                    )
                }
            }
        }
    }

    selectedAppForConfig?.let { target ->
        var isIsolated by remember { mutableStateOf(target.isIsolated) }
        var selectedMode by remember { mutableStateOf(target.isolationMode) }
        var blockPollution by remember { mutableStateOf(target.blockRootPollution) }

        AlertDialog(
            onDismissRequest = { selectedAppForConfig = null },
            title = {
                Text(
                    text = "Configure Isolation: ${target.appName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enable Storage Isolation", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = isIsolated, onCheckedChange = { isIsolated = it })
                    }

                    if (isIsolated) {
                        Text("Isolation Mode:", style = MaterialTheme.typography.labelMedium)
                        IsolationMode.entries.forEach { mode ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = selectedMode == mode,
                                    onClick = { selectedMode = mode }
                                )
                                Column(modifier = Modifier.padding(start = 4.dp)) {
                                    Text(mode.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(mode.description, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Block Root / SDCard Pollution", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = blockPollution, onCheckedChange = { blockPollution = it })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        saveConfig(target, isIsolated, selectedMode, blockPollution)
                        selectedAppForConfig = null
                    }
                ) {
                    Text("Save & Apply", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedAppForConfig = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
