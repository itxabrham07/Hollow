package com.example.isolation.module.sensorfirewall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.isolation.core.module.HollowModule
import com.example.isolation.core.root.RootLicenseVaultManager
import com.example.isolation.ui.components.ContentCard
import com.example.isolation.ui.components.SectionHeader
import com.example.isolation.ui.components.StatusIndicator
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.JetBrainsMonoFontFamily

data class SensorFirewallControlSpec(
    val key: String,
    val title: String,
    val description: String,
    val defaultEnabled: Boolean
)

object SensorFirewallPayload : HollowModule {
    const val ID = "module_sensor_firewall"

    override val id: String = ID
    override val displayName: String = "Sensor & Mic Hardware Firewall"
    override val cardTitle: String = "Sensor Firewall"
    override val summary: String =
        "Zero-touch server-shipped module that blanks camera/microphone buffers, zeroes gyroscope/accelerometer telemetry, and blocks background ultrasonic beacons."
    override val version: Int = 1
    override val requiredMinCoreVersion: Int = 1
    override val icon: ImageVector = Icons.Default.Sensors
    override val primaryRoute: String = "DYNAMIC_MODULE_SENSOR_FIREWALL"
    override val contributesBottomNavTab: Boolean = false
    override val sizeKb: Int = 395
    override val accentColorHex: Long = 0xFF4F46E5L
    override val cardTestTag: String = "module_card_module_sensor_firewall"

    private val controls = listOf(
        SensorFirewallControlSpec(
            key = "mic_null_pcm_stream",
            title = "Silent PCM Microphone Guard",
            description = "Injects zeroed PCM audio frames into AudioRecord when background apps attempt covert recording.",
            defaultEnabled = true
        ),
        SensorFirewallControlSpec(
            key = "camera_black_frame_shield",
            title = "Camera2 Black Frame Shield",
            description = "Replaces CameraDevice capture surfaces with synthetic blank frames unless explicitly whitelisted.",
            defaultEnabled = true
        ),
        SensorFirewallControlSpec(
            key = "gyro_motion_fingerprint_scrub",
            title = "Gyroscope & Step Counter Scrubber",
            description = "Prevents gait and micro-vibration fingerprinting across SensorManager listeners.",
            defaultEnabled = true
        )
    )

    private val hookTargets = listOf(
        "android.media.AudioRecord",
        "android.hardware.camera2.CameraManager",
        "android.hardware.SystemSensorManager"
    )

    @Composable
    override fun resolveCardBadge(viewModel: IsoVaultViewModel): String {
        val isHooked by viewModel.isLsposedActive.collectAsState()
        val isolatedCount by viewModel.isolatedCount.collectAsState()
        return if (isHooked) {
            "Hooked · v$version"
        } else {
            "$isolatedCount Guarded · v$version"
        }
    }

    @Composable
    override fun resolveCardSubtitle(viewModel: IsoVaultViewModel): String {
        val configs by viewModel.configs.collectAsState()
        val anonymizedCount = configs.count { it.anonymizeSensors }
        return "${controls.size} hardware hooks · ${configs.size} apps ($anonymizedCount sensor-scrubbed)"
    }

    @Composable
    override fun RenderPrimaryScreen(
        viewModel: IsoVaultViewModel,
        onBack: () -> Unit,
        onDetailOpenChanged: (Boolean) -> Unit
    ) {
        val context = LocalContext.current
        val configs by viewModel.configs.collectAsState()
        val settings by viewModel.settings.collectAsState()
        val isLsposedActive by viewModel.isLsposedActive.collectAsState()
        val isolatedCount by viewModel.isolatedCount.collectAsState()
        val hwFingerprint = viewModel.hardwareBinding.fingerprintHex

        val toggleStateMap = remember(id) {
            mutableStateMapOf<String, Boolean>().apply {
                controls.forEach { feat ->
                    val current = RootLicenseVaultManager.readModuleToggleState(
                        context = context,
                        moduleId = id,
                        featureKey = feat.key,
                        defaultValue = feat.defaultEnabled
                    )
                    put(feat.key, current)
                }
            }
        }

        val activeCount = controls.count { toggleStateMap[it.key] == true }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("screen_module_sensor_firewall"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("dynamic_module_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Dashboard",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Standalone DEX Module · $id · v$version",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusIndicator(
                        label = "$activeCount/${controls.size} Active",
                        color = if (activeCount > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                ContentCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Verified DEX Module",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Live Core ViewModel State Bridge",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "HW=$hwFingerprint | Configs=${configs.size} | Isolated=$isolatedCount | LSPosed=${if (isLsposedActive) "ACTIVE" else "STANDBY"} | Mode=${if (settings.devFullBuildMode) "DEV" else "RELEASE"}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("sensor_firewall_vm_state")
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hook Targets: ${hookTargets.joinToString(" · ")}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SectionHeader(title = "Hardware Sensor Controls")
            }

            items(controls, key = { it.key }) { feat ->
                val isChecked = toggleStateMap[feat.key] ?: feat.defaultEnabled
                ContentCard(
                    modifier = Modifier.testTag("dynamic_feature_card_${feat.key}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = feat.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = feat.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isChecked,
                            onCheckedChange = { newVal ->
                                toggleStateMap[feat.key] = newVal
                                RootLicenseVaultManager.saveModuleToggleState(
                                    context = context,
                                    moduleId = id,
                                    featureKey = feat.key,
                                    enabled = newVal
                                )
                                viewModel.appendTerminalLog(
                                    "[SensorFirewall] ${feat.key} set to $newVal (scoped apps=${configs.size})"
                                )
                            },
                            modifier = Modifier.testTag("sensor_toggle_${feat.key}")
                        )
                    }
                }
            }
        }
    }
}
