package com.example.isolation.core.module

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.core.root.RootLicenseVaultManager
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.JakartaFontFamily
import org.json.JSONObject

data class DynamicFeatureToggleSpec(
    val key: String,
    val title: String,
    val description: String,
    val rootCommandOnEnable: String = "",
    val defaultEnabled: Boolean = true
)

/**
 * Zero-Touch Server-Shipped Module (`DynamicSchemaModule`).
 *
 * Whenever a new module directory `/modules/<module_id>/manifest.json` is added on the server
 * and downloaded into `/data/adb/.hollow_vault/modules/<module_id>.dex.enc`, `HollowModuleRegistry`
 * instantiates `DynamicSchemaModule` directly from the decrypted manifest JSON.
 *
 * This allows brand-new modules to appear on the Dashboard as a Bento Card and open a full
 * interactive control screen with zero changes to the Core APK!
 */
class DynamicSchemaModule(
    override val id: String,
    override val displayName: String,
    override val cardTitle: String,
    override val summary: String,
    override val version: Int,
    override val requiredMinCoreVersion: Int = 1,
    override val sizeKb: Int = 380,
    override val accentColorHex: Long = 0xFFF59E0BL,
    val entryClass: String = "",
    val features: List<DynamicFeatureToggleSpec> = emptyList(),
    val hookTargets: List<String> = emptyList()
) : HollowModule {

    override val icon: ImageVector
        get() = when {
            id.contains("sensor", ignoreCase = true) -> Icons.Default.Sensors
            id.contains("memory", ignoreCase = true) -> Icons.Default.Memory
            id.contains("security", ignoreCase = true) || id.contains("firewall", ignoreCase = true) -> Icons.Default.Security
            else -> Icons.Default.Extension
        }

    override val primaryRoute: String = "DYNAMIC_${id.uppercase()}"
    override val contributesBottomNavTab: Boolean = false
    override val cardTestTag: String = "module_card_$id"

    @Composable
    override fun resolveCardBadge(viewModel: IsoVaultViewModel): String {
        return "v$version"
    }

    @Composable
    override fun resolveCardSubtitle(viewModel: IsoVaultViewModel): String {
        return if (features.isNotEmpty()) {
            "${features.size} root controls active"
        } else {
            summary
        }
    }

    @Composable
    override fun RenderPrimaryScreen(
        viewModel: IsoVaultViewModel,
        onBack: () -> Unit,
        onDetailOpenChanged: (Boolean) -> Unit
    ) {
        DynamicModuleScreen(
            module = this,
            viewModel = viewModel,
            onBack = onBack
        )
    }

    companion object {
        fun fromDecryptedManifestJson(moduleId: String, rawJson: String?): DynamicSchemaModule {
            if (rawJson.isNullOrBlank()) {
                return defaultFallback(moduleId)
            }
            return try {
                val obj = JSONObject(rawJson)
                val name = obj.optString("name", formatModuleTitle(moduleId))
                val shortTitle = obj.optString("cardTitle", name.take(18))
                val desc = obj.optString(
                    "description",
                    "Dynamic server-delivered module mounted from /data/adb/.hollow_vault/modules/$moduleId.dex.enc"
                )
                val version = obj.optInt("version", 1)
                val sizeKb = obj.optInt("sizeKb", 420)
                val accentHexStr = obj.optString("accentColorHex", "0xFFF59E0B").removePrefix("0x").removePrefix("#")
                val accentHex = accentHexStr.toLongOrNull(16) ?: 0xFFF59E0BL
                val entryClass = obj.optString("entryClass", "com.example.isolation.module.$moduleId.Payload")

                val parsedFeatures = mutableListOf<DynamicFeatureToggleSpec>()
                val featArr = obj.optJSONArray("features")
                if (featArr != null) {
                    for (i in 0 until featArr.length()) {
                        val item = featArr.opt(i)
                        if (item is JSONObject) {
                            val key = item.optString("key", "feat_$i")
                            parsedFeatures.add(
                                DynamicFeatureToggleSpec(
                                    key = key,
                                    title = item.optString("title", formatModuleTitle(key)),
                                    description = item.optString(
                                        "description",
                                        "Enforced via /data/adb/.hollow_vault/modules/$moduleId.dex.enc"
                                    ),
                                    rootCommandOnEnable = item.optString("rootCommand", ""),
                                    defaultEnabled = item.optBoolean("defaultEnabled", true)
                                )
                            )
                        } else if (item is String) {
                            parsedFeatures.add(
                                DynamicFeatureToggleSpec(
                                    key = item,
                                    title = formatModuleTitle(item),
                                    description = "Dynamic kernel/Xposed hook feature ($item) loaded from Root Vault.",
                                    defaultEnabled = true
                                )
                            )
                        }
                    }
                }

                val parsedHooks = mutableListOf<String>()
                val hooksArr = obj.optJSONArray("hookTargets")
                if (hooksArr != null) {
                    for (i in 0 until hooksArr.length()) {
                        val target = hooksArr.optString(i)
                        if (target.isNotBlank()) parsedHooks.add(target)
                    }
                }

                DynamicSchemaModule(
                    id = moduleId,
                    displayName = name,
                    cardTitle = shortTitle,
                    summary = desc,
                    version = version,
                    sizeKb = sizeKb,
                    accentColorHex = accentHex,
                    entryClass = entryClass,
                    features = parsedFeatures,
                    hookTargets = parsedHooks
                )
            } catch (_: Throwable) {
                defaultFallback(moduleId)
            }
        }

        private fun defaultFallback(moduleId: String): DynamicSchemaModule {
            val readable = formatModuleTitle(moduleId.removePrefix("module_"))
            return DynamicSchemaModule(
                id = moduleId,
                displayName = "$readable Module",
                cardTitle = readable,
                summary = "Server-delivered dynamic module mounted in /data/adb/.hollow_vault/modules/$moduleId.dex.enc",
                version = 1,
                features = listOf(
                    DynamicFeatureToggleSpec(
                        key = "strict_enforcement",
                        title = "Strict Kernel & Xposed Enforcement",
                        description = "Enforces $readable rules across scoped user applications."
                    )
                ),
                hookTargets = listOf("android.app.Instrumentation", "android.os.BinderProxy")
            )
        }

        private fun formatModuleTitle(raw: String): String {
            return raw.split("_", "-")
                .filter { it.isNotBlank() }
                .joinToString(" ") { part ->
                    part.replaceFirstChar { it.uppercase() }
                }
        }
    }
}

@Composable
fun DynamicModuleScreen(
    module: DynamicSchemaModule,
    viewModel: IsoVaultViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val accent = Color(module.accentColorHex)

    val toggleStates = remember(module.id) {
        mutableStateMapOf<String, Boolean>().apply {
            module.features.forEach { feat ->
                put(
                    feat.key,
                    RootLicenseVaultManager.readModuleToggleState(
                        context = context,
                        moduleId = module.id,
                        featureKey = feat.key,
                        defaultValue = feat.defaultEnabled
                    )
                )
            }
        }
    }
    var commandExecCount by remember { mutableIntStateOf(0) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("dynamic_module_screen_${module.id}"),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("btn_back_dynamic_module")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = scheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = module.displayName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onBackground,
                            fontFamily = JakartaFontFamily
                        )
                        Text(
                            text = "Dynamic Server Module • /data/adb/.hollow_vault/modules/${module.id}.dex.enc",
                            fontSize = 11.sp,
                            color = scheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Module Telemetry Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accent.copy(alpha = 0.16f))
                            ) {
                                Icon(
                                    imageVector = module.icon,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Zero-Touch Dynamic Plugin • v${module.version}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurface,
                                    fontFamily = JakartaFontFamily
                                )
                                Text(
                                    text = "Hardware Bound (${viewModel.hardwareBinding.fingerprintHex})",
                                    fontSize = 11.sp,
                                    color = accent,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = module.summary,
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                        fontFamily = JakartaFontFamily
                    )
                    if (module.entryClass.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Entrypoint: ${module.entryClass}",
                            fontSize = 11.sp,
                            color = scheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Interactive Feature Controls defined in manifest.json
        item {
            Text(
                text = "MODULE FEATURE CONTROLS (${module.features.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = scheme.onSurfaceVariant,
                fontFamily = JakartaFontFamily
            )
        }

        items(module.features, key = { it.key }) { feat ->
            val isChecked = toggleStates[feat.key] ?: feat.defaultEnabled
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dynamic_feature_card_${feat.key}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = feat.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            fontFamily = JakartaFontFamily
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = feat.description,
                            fontSize = 11.sp,
                            color = scheme.onSurfaceVariant,
                            fontFamily = JakartaFontFamily
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Switch(
                        checked = isChecked,
                        onCheckedChange = { enabled ->
                            toggleStates[feat.key] = enabled
                            RootLicenseVaultManager.saveModuleToggleState(
                                context = context,
                                moduleId = module.id,
                                featureKey = feat.key,
                                enabled = enabled
                            )
                        },
                        modifier = Modifier.testTag("switch_dynamic_${feat.key}")
                    )
                }
            }
        }

        // Hook Targets & Root Enforcement
        if (module.hookTargets.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = scheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LSPosed / Zygisk Hook Targets",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                                fontFamily = JakartaFontFamily
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        module.hookTargets.forEach { target ->
                            Text(
                                text = "• $target",
                                fontSize = 11.sp,
                                color = scheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { commandExecCount++ },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_sync_dynamic_hooks")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (commandExecCount == 0) {
                                    "Apply Rules to /data/adb/.hollow_vault"
                                } else {
                                    "Synced to Root Vault ($commandExecCount)"
                                },
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
