package com.example.isolation.module.hider

import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.isolation.sdk.HollowModuleContext
import com.example.isolation.ui.components.ContentCard
import com.example.isolation.ui.components.ListRow
import com.example.isolation.ui.components.SectionHeader
import com.example.isolation.ui.components.StatusIndicator
import com.example.isolation.ui.components.rememberAppIconBitmap
import org.json.JSONObject

@Composable
fun AppHiderScreen(
    context: HollowModuleContext,
    onBack: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val pm = context.context.packageManager

    val appList = remember { mutableStateListOf<StealthAppItem>() }
    var searchQuery by remember { mutableStateOf("") }
    var includeSystemApps by remember { mutableStateOf(false) }
    var selectedAppForStealth by remember { mutableStateOf<StealthAppItem?>(null) }
    var filterMode by remember { mutableStateOf("ALL") }

    fun reloadApps() {
        val installed = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (_: Throwable) {
            emptyList<ApplicationInfo>()
        }
        val prefs = context.preferences
        val stealthJsonRaw = prefs.getString("stealth_rules_json", "{}")
        val stealthJson = try { JSONObject(stealthJsonRaw) } catch (_: Throwable) { JSONObject() }

        val items = installed.map { appInfo ->
            val pkg = appInfo.packageName
            val label = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Throwable) { pkg }
            val icon = try { pm.getApplicationIcon(appInfo) } catch (_: Throwable) { null }
            val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            val ruleObj = stealthJson.optJSONObject(pkg)
            val visModeStr = ruleObj?.optString("visibility", LauncherVisibilityMode.VISIBLE.name)
                ?: LauncherVisibilityMode.VISIBLE.name
            val visMode = try { LauncherVisibilityMode.valueOf(visModeStr) } catch (_: Throwable) { LauncherVisibilityMode.VISIBLE }
            val customLabel = ruleObj?.optString("customLabel", "") ?: ""
            val presetStr = ruleObj?.optString("preset", DisguiseIconPreset.CALCULATOR.name) ?: DisguiseIconPreset.CALCULATOR.name
            val preset = try { DisguiseIconPreset.valueOf(presetStr) } catch (_: Throwable) { DisguiseIconPreset.CALCULATOR }
            val hideRecents = ruleObj?.optBoolean("hideRecents", true) ?: true
            val hidePlay = ruleObj?.optBoolean("hidePlay", false) ?: false

            StealthAppItem(
                packageName = pkg,
                appName = label,
                isSystemApp = isSys,
                icon = icon,
                launcherVisibilityMode = visMode,
                customLauncherLabel = customLabel,
                disguiseIconPreset = preset,
                hideInSystemUiRecents = hideRecents,
                hideFromPlayStore = hidePlay
            )
        }.sortedWith(compareBy({ !it.isHiddenOrDisguised }, { it.appName.lowercase() }))

        appList.clear()
        appList.addAll(items)

        val hiddenCount = items.count { it.isHiddenOrDisguised }
        prefs.putInt("hidden_disguised_count", hiddenCount)
        context.notifyStateChanged()
    }

    LaunchedEffect(Unit) {
        reloadApps()
    }

    fun saveRule(
        item: StealthAppItem,
        mode: LauncherVisibilityMode,
        customLabel: String,
        preset: DisguiseIconPreset,
        hideRecents: Boolean,
        hidePlay: Boolean
    ) {
        val prefs = context.preferences
        val stealthJsonRaw = prefs.getString("stealth_rules_json", "{}")
        val stealthJson = try { JSONObject(stealthJsonRaw) } catch (_: Throwable) { JSONObject() }

        val obj = JSONObject()
            .put("packageName", item.packageName)
            .put("visibility", mode.name)
            .put("customLabel", customLabel)
            .put("preset", preset.name)
            .put("hideRecents", hideRecents)
            .put("hidePlay", hidePlay)
        stealthJson.put(item.packageName, obj)
        prefs.putString("stealth_rules_json", stealthJson.toString())

        context.auditLogger.log(
            action = "APP_STEALTH_CONFIG",
            target = item.packageName,
            details = "Visibility: ${mode.name}, Preset: ${preset.name}, Recents: $hideRecents"
        )
        reloadApps()
    }

    val filteredApps = appList.filter {
        val matchesSearch = searchQuery.isBlank() ||
                it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        val matchesSystem = includeSystemApps || !it.isSystemApp
        val matchesFilter = when (filterMode) {
            "HIDDEN" -> it.isHiddenOrDisguised
            else -> true
        }
        matchesSearch && matchesSystem && matchesFilter
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("app_hider_screen")
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
                    text = "Hide & Disguise Apps",
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Cloak apps from Launcher, Play Store & SystemUI Recents",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { reloadApps() }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = scheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search installed applications...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_apps_input"),
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
                selected = filterMode == "HIDDEN",
                onClick = { filterMode = "HIDDEN" },
                label = { Text("Disguised (${appList.count { it.isHiddenOrDisguised }})") }
            )
            Spacer(modifier = Modifier.weight(1f))
            AssistChip(
                onClick = { includeSystemApps = !includeSystemApps },
                label = { Text(if (includeSystemApps) "System Apps: On" else "User Apps Only") }
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
                    onClick = { selectedAppForStealth = app },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_row_${app.packageName}")
                ) {
                    ListRow(
                        title = if (app.customLauncherLabel.isNotBlank()) "${app.appName} (${app.customLauncherLabel})" else app.appName,
                        subtitle = app.packageName,
                        leadingAppIcon = iconBitmap,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (app.isHiddenOrDisguised) {
                                    StatusIndicator(
                                        label = app.launcherVisibilityMode.badge,
                                        color = scheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        try {
                                            val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                                            if (launchIntent != null) {
                                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.context.startActivity(launchIntent)
                                            }
                                        } catch (_: Throwable) {}
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Launch",
                                        tint = scheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    selectedAppForStealth?.let { targetApp ->
        var currentMode by remember { mutableStateOf(targetApp.launcherVisibilityMode) }
        var customLabel by remember { mutableStateOf(targetApp.customLauncherLabel) }
        var currentPreset by remember { mutableStateOf(targetApp.disguiseIconPreset) }
        var hideRecents by remember { mutableStateOf(targetApp.hideInSystemUiRecents) }
        var hidePlay by remember { mutableStateOf(targetApp.hideFromPlayStore) }

        AlertDialog(
            onDismissRequest = { selectedAppForStealth = null },
            title = {
                Text(
                    text = "Configure ${targetApp.appName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Launcher Visibility:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    LauncherVisibilityMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = currentMode == mode,
                                onClick = { currentMode = mode }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(mode.title, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    if (currentMode == LauncherVisibilityMode.DISGUISED) {
                        OutlinedTextField(
                            value = customLabel,
                            onValueChange = { customLabel = it },
                            label = { Text("Decoy Launcher Label") },
                            placeholder = { Text(currentPreset.suggestedName) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Icon Disguise Preset:", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = currentPreset == DisguiseIconPreset.CALCULATOR,
                                onClick = { currentPreset = DisguiseIconPreset.CALCULATOR },
                                label = { Text("Calculator") }
                            )
                            FilterChip(
                                selected = currentPreset == DisguiseIconPreset.NOTES,
                                onClick = { currentPreset = DisguiseIconPreset.NOTES },
                                label = { Text("Notes") }
                            )
                            FilterChip(
                                selected = currentPreset == DisguiseIconPreset.CALENDAR,
                                onClick = { currentPreset = DisguiseIconPreset.CALENDAR },
                                label = { Text("Calendar") }
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Hide in SystemUI Recents", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = hideRecents, onCheckedChange = { hideRecents = it })
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cloak from Play Store Queries", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = hidePlay, onCheckedChange = { hidePlay = it })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        saveRule(targetApp, currentMode, customLabel, currentPreset, hideRecents, hidePlay)
                        selectedAppForStealth = null
                    }
                ) {
                    Text("Save & Apply", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedAppForStealth = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
