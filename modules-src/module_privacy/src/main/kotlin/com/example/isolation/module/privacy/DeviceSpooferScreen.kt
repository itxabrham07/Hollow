package com.example.isolation.module.privacy

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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
fun DeviceSpooferScreen(
    context: HollowModuleContext,
    onBack: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val pm = context.context.packageManager
    val prefs = context.preferences

    val appList = remember { mutableStateListOf<SpoofedAppEntry>() }
    var searchQuery by remember { mutableStateOf("") }
    var selectedAppForSpoof by remember { mutableStateOf<SpoofedAppEntry?>(null) }
    var filterMode by remember { mutableStateOf("ALL") }

    fun loadApps() {
        val installed = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (_: Throwable) {
            emptyList<ApplicationInfo>()
        }
        val spoofJson = try {
            JSONObject(prefs.getString("spoofed_apps_json", "{}"))
        } catch (_: Throwable) {
            JSONObject()
        }

        val entries = installed.map { appInfo ->
            val pkg = appInfo.packageName
            val label = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Throwable) { pkg }
            val icon = try { pm.getApplicationIcon(appInfo) } catch (_: Throwable) { null }
            val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            val ruleObj = spoofJson.optJSONObject(pkg)
            val isSpoofed = ruleObj != null && ruleObj.optBoolean("enabled", false)
            val presetStr = ruleObj?.optString("preset", DeviceProfilePreset.PIXEL_9_PRO.name)
                ?: DeviceProfilePreset.PIXEL_9_PRO.name
            val preset = try { DeviceProfilePreset.valueOf(presetStr) } catch (_: Throwable) { DeviceProfilePreset.PIXEL_9_PRO }
            val brand = ruleObj?.optString("brand", preset.brand) ?: preset.brand
            val model = ruleObj?.optString("model", preset.model) ?: preset.model
            val aid = ruleObj?.optString("androidId", "") ?: ""

            SpoofedAppEntry(
                packageName = pkg,
                appName = label,
                isSystemApp = isSys,
                icon = icon,
                isSpoofed = isSpoofed,
                preset = preset,
                brand = brand,
                model = model,
                androidId = aid
            )
        }.sortedWith(compareBy({ !it.isSpoofed }, { it.appName.lowercase() }))

        appList.clear()
        appList.addAll(entries)
        prefs.putInt("spoofed_apps_count", entries.count { it.isSpoofed })
        context.notifyStateChanged()
    }

    LaunchedEffect(Unit) {
        loadApps()
    }

    fun saveSpoofConfig(
        target: SpoofedAppEntry,
        enabled: Boolean,
        preset: DeviceProfilePreset,
        brand: String,
        model: String,
        androidId: String
    ) {
        val spoofJson = try {
            JSONObject(prefs.getString("spoofed_apps_json", "{}"))
        } catch (_: Throwable) {
            JSONObject()
        }
        val obj = JSONObject()
            .put("packageName", target.packageName)
            .put("enabled", enabled)
            .put("preset", preset.name)
            .put("brand", brand)
            .put("model", model)
            .put("androidId", androidId)
        spoofJson.put(target.packageName, obj)
        prefs.putString("spoofed_apps_json", spoofJson.toString())

        context.auditLogger.log(
            action = "DEVICE_IDENTITY_SPOOF",
            target = target.packageName,
            details = if (enabled) "Spoofed as ${preset.label} ($model)" else "Restored to Real Hardware"
        )
        loadApps()
    }

    val filteredApps = appList.filter {
        val matchesSearch = searchQuery.isBlank() ||
                it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (filterMode) {
            "SPOOFED" -> it.isSpoofed
            else -> true
        }
        matchesSearch && matchesFilter
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("device_spoofer_screen")
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
                    text = "Hardware Identity Spoofer",
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Virtualize Android ID, Build properties & GPU Vendor",
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
                selected = filterMode == "SPOOFED",
                onClick = { filterMode = "SPOOFED" },
                label = { Text("Spoofed (${appList.count { it.isSpoofed }})") }
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
                    onClick = { selectedAppForSpoof = app },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("spoofer_row_${app.packageName}")
                ) {
                    ListRow(
                        title = app.appName,
                        subtitle = if (app.isSpoofed) "Spoofed: ${app.preset.label} (${app.model})" else app.packageName,
                        leadingAppIcon = iconBitmap,
                        trailing = {
                            if (app.isSpoofed) {
                                StatusIndicator(label = app.preset.label, color = scheme.primary)
                            }
                        }
                    )
                }
            }
        }
    }

    selectedAppForSpoof?.let { target ->
        var enabled by remember { mutableStateOf(target.isSpoofed) }
        var selectedPreset by remember { mutableStateOf(target.preset) }
        var brand by remember { mutableStateOf(target.brand.ifBlank { selectedPreset.brand }) }
        var model by remember { mutableStateOf(target.model.ifBlank { selectedPreset.model }) }
        var androidId by remember { mutableStateOf(target.androidId) }

        AlertDialog(
            onDismissRequest = { selectedAppForSpoof = null },
            title = {
                Text(
                    text = "Spoof Identity: ${target.appName}",
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
                        Text("Enable Hardware Identity Spoofing", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }

                    if (enabled) {
                        Text("Device Profile Preset:", style = MaterialTheme.typography.labelMedium)
                        DeviceProfilePreset.entries.forEach { p ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = selectedPreset == p,
                                    onClick = {
                                        selectedPreset = p
                                        brand = p.brand
                                        model = p.model
                                    }
                                )
                                Text(p.label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            label = { Text("Model Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = androidId,
                            onValueChange = { androidId = it },
                            label = { Text("Custom Android ID (Hex)") },
                            placeholder = { Text("Auto-generated if empty") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        saveSpoofConfig(target, enabled, selectedPreset, brand, model, androidId)
                        selectedAppForSpoof = null
                    }
                ) {
                    Text("Save & Apply", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedAppForSpoof = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
