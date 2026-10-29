package com.example.isolation.module.sensorfirewall

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
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.isolation.core.module.HollowModule
import com.example.isolation.sdk.HollowModuleContext
import com.example.isolation.ui.components.ContentCard
import com.example.isolation.ui.components.SectionHeader
import com.example.isolation.ui.components.StatusIndicator
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

    override fun onMount(context: HollowModuleContext) {
        context.logger.i("SensorFirewall", "Mounted SensorFirewallPayload v$version")
    }

    override fun onUnmount() {}

    @Composable
    override fun resolveCardBadge(context: HollowModuleContext): String {
        return if (context.isHookActive) "Hooked · v$version" else "v$version"
    }

    @Composable
    override fun resolveCardSubtitle(context: HollowModuleContext): String {
        val activeCount = controls.count {
            context.preferences.getBoolean(it.key, it.defaultEnabled)
        }
        return "$activeCount/3 guards active"
    }

    @Composable
    override fun RenderPrimaryScreen(
        context: HollowModuleContext,
        onBack: () -> Unit,
        onDetailOpenChanged: (Boolean) -> Unit
    ) {
        val scheme = MaterialTheme.colorScheme
        val toggleStates = remember {
            mutableStateMapOf<String, Boolean>().apply {
                controls.forEach { ctrl ->
                    this[ctrl.key] = context.preferences.getBoolean(ctrl.key, ctrl.defaultEnabled)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("sensor_firewall_screen")
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = scheme.onBackground
                    )
                }
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Zero-Touch Dynamic Security Extension",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ContentCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sensor_firewall_status_banner")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Engine Runtime Hook",
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (context.isHookActive) "Zygote & App-Process Interception Active" else "Native Root Guard Standby",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    StatusIndicator(
                        label = if (context.isHookActive) "Hook Active" else "Ready",
                        color = if (context.isHookActive) scheme.secondary else scheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader(title = "Hardware Interceptors", subtitle = "Telemetry blanks applied at libc/driver boundaries")

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(controls) { ctrl ->
                    val isChecked = toggleStates[ctrl.key] ?: ctrl.defaultEnabled
                    ContentCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("control_${ctrl.key}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ctrl.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = scheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = ctrl.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isChecked,
                                onCheckedChange = { next ->
                                    toggleStates[ctrl.key] = next
                                    context.preferences.putBoolean(ctrl.key, next)
                                    context.logger.i("SensorFirewall", "Toggled ${ctrl.key} -> $next")
                                },
                                modifier = Modifier.testTag("switch_${ctrl.key}")
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Module SHA-256 Checksum verified with Ed25519 signature before mounting. Storage scoped to hollow_mod_module_sensor_firewall.",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = scheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
