package com.example.isolation.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.isolation.model.HollowIconTheme
import com.example.isolation.model.LauncherVisibilityMode
import com.example.isolation.model.PickerPolicy
import com.example.isolation.ui.components.ContentCard
import com.example.isolation.ui.components.HollowDynamicEmblem
import com.example.isolation.ui.components.ListRow
import com.example.isolation.ui.components.PrimaryButton
import com.example.isolation.ui.components.SecondaryButton
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.JakartaFontFamily

// Onboarding-matched color tokens
private val SettingsDarkOnyx = Color(0xFF111827)
private val SettingsMuted = Color(0xFF64748B)
private val SettingsCardBorder = Color(0xFFE2E8F0)
private val SettingsLightGray = Color(0xFFF1F5F9)

@Composable
fun SettingsScreen(
    viewModel: IsoVaultViewModel,
    onOpenDiagnostics: () -> Unit = {},
    onNavigateToLicenseHub: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    val isLsposedActive by viewModel.isLsposedActive.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val rootStatus by viewModel.rootStatus.collectAsState()
    val vaultRecord by viewModel.vaultLicenseRecord.collectAsState()
    var showSetupGuideDialog by remember { mutableStateOf(false) }
    var showHollowStealthDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp)
    ) {

        // ── Section 0: Account Card ──────────────────────────────────────────
        item {
            SettingsSectionLabel("Account")
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SettingsDarkOnyx,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Avatar circle
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = vaultRecord.username.ifBlank { "Hollow User" },
                            style = typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = JakartaFontFamily
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = vaultRecord.email.ifBlank { "Approved · Device Bound" },
                            style = typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Status pill
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF22C55E).copy(alpha = 0.18f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = Color(0xFF4ADE80),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Active",
                                style = typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4ADE80)
                            )
                        }
                    }
                }
            }
        }

        // ── Section 1: Appearance ───────────────────────────────────────────
        item {
            SettingsSectionLabel("Appearance")
            Spacer(modifier = Modifier.height(8.dp))
            SettingsGroupCard {
                // Dark theme toggle
                SettingsToggleRow(
                    icon = Icons.Default.Palette,
                    title = "Dark Theme",
                    subtitle = "Switch between light and dark appearance",
                    checked = settings.darkTheme,
                    onToggle = { viewModel.setDarkTheme(it) }
                )
                SettingsDivider()
                SettingsToggleRow(
                    icon = Icons.Default.Palette,
                    title = "Wallpaper Colors (Material You)",
                    subtitle = "Match your wallpaper palette on Android 12+",
                    checked = settings.dynamicColor,
                    onToggle = { viewModel.setDynamicColor(it) }
                )
                SettingsDivider()

                // Icon theme picker
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        SettingsIconBox(Icons.Default.Palette)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Launcher Icon Style",
                                style = typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SettingsDarkOnyx
                            )
                            Text(
                                text = "Choose signature Hollow or dynamic icon",
                                style = typography.bodySmall,
                                color = SettingsMuted
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HollowIconTheme.entries.forEach { themeOption ->
                            val isSelected = settings.hollowIconTheme == themeOption
                            val borderColor by animateColorAsState(
                                targetValue = if (isSelected) SettingsDarkOnyx else SettingsCardBorder,
                                animationSpec = tween(200),
                                label = "icon_border"
                            )
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) SettingsDarkOnyx.copy(alpha = 0.06f) else Color.Transparent,
                                border = BorderStroke(1.5.dp, borderColor),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.setHollowIconTheme(themeOption) }
                                    .testTag("icon_theme_${themeOption.name.lowercase()}")
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                                ) {
                                    HollowDynamicEmblem(
                                        iconTheme = themeOption,
                                        size = 30.dp
                                    )
                                    Spacer(modifier = Modifier.height(5.dp))
                                    Text(
                                        text = themeOption.label,
                                        style = typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) SettingsDarkOnyx else SettingsMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Section 2: Security & Disguise ──────────────────────────────────
        item {
            SettingsSectionLabel("Security & Disguise")
            Spacer(modifier = Modifier.height(8.dp))
            SettingsGroupCard {
                // Hide / Disguise row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .clickable { showHollowStealthDialog = true }
                        .padding(vertical = 6.dp)
                        .testTag("card_hollow_self_stealth")
                ) {
                    SettingsIconBox(presetIconVector(settings.hollowIconPreset))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hide / Disguise Hollow",
                            style = typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = SettingsDarkOnyx
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (settings.hollowVisibilityMode) {
                                LauncherVisibilityMode.VISIBLE ->
                                    if (settings.systemUiTriggerEnabled) "Visible · SystemUI trigger on"
                                    else "Visible in launcher"
                                LauncherVisibilityMode.DISGUISED ->
                                    "Disguised as \"${settings.hollowCustomName}\""
                                LauncherVisibilityMode.HIDDEN ->
                                    "Hidden · Open via clock trigger"
                            },
                            style = typography.bodySmall,
                            color = SettingsMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = SettingsDarkOnyx,
                        modifier = Modifier
                            .clickable { showHollowStealthDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = when (settings.hollowVisibilityMode) {
                                    LauncherVisibilityMode.VISIBLE -> "Visible"
                                    LauncherVisibilityMode.DISGUISED -> "Disguised"
                                    LauncherVisibilityMode.HIDDEN -> "Hidden"
                                },
                                style = typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // ── Section 3: Storage ──────────────────────────────────────────────
        item {
            SettingsSectionLabel("Storage")
            Spacer(modifier = Modifier.height(8.dp))
            SettingsGroupCard {
                SettingsToggleRow(
                    icon = Icons.Default.Storage,
                    title = "Private Volumes (Root)",
                    subtitle = "Mount a separate storage folder per app",
                    checked = settings.rootVolumesEnabled,
                    onToggle = { viewModel.setRootVolumesEnabled(it) }
                )
                SettingsDivider()
                SettingsToggleRow(
                    icon = Icons.Default.Lock,
                    title = "Lock Volumes When Unmounted",
                    subtitle = "Encrypt app volumes when closed",
                    checked = settings.encryptVolumesAtRest,
                    enabled = settings.rootVolumesEnabled,
                    onToggle = { viewModel.setEncryptVolumesAtRest(it) }
                )
            }
        }

        // ── Section 4: System ───────────────────────────────────────────────
        item {
            SettingsSectionLabel("System")
            Spacer(modifier = Modifier.height(8.dp))
            SettingsGroupCard {
                SettingsNavRow(
                    icon = Icons.Default.Layers,
                    title = "Manage Modules",
                    subtitle = "Enable, disable & download privacy modules",
                    onClick = onNavigateToLicenseHub,
                    modifier = Modifier.testTag("nav_manage_modules")
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Default.HelpOutline,
                    title = "Setup & Xposed Guide",
                    subtitle = if (isLsposedActive) "Module active in LSPosed" else "How to activate in 3 steps",
                    onClick = { showSetupGuideDialog = true },
                    modifier = Modifier.testTag("btn_open_setup_guide")
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Default.Shield,
                    title = "Root Access",
                    subtitle = rootStatus,
                    trailingLabel = "Check",
                    onClick = { viewModel.checkRoot() }
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Default.Tune,
                    title = "Advanced System Details",
                    subtitle = "Namespace diagnostics, boot script & mounts",
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.testTag("nav_mounts")
                )
            }
        }
    }

    // ── Dialogs ─────────────────────────────────────────────────────────────

    if (showHollowStealthDialog) {
        HollowSelfStealthDialog(
            currentMode = settings.hollowVisibilityMode,
            currentCustomName = settings.hollowCustomName,
            currentIconPreset = settings.hollowIconPreset,
            currentSystemUiTrigger = settings.systemUiTriggerEnabled,
            currentCustomIconBase64 = settings.hollowCustomIconBase64,
            currentCustomIconDonorPkg = settings.hollowCustomIconDonorPkg,
            currentSpoofInSettings = settings.hollowSpoofInSettings,
            onDecodeImageUri = { uri, cb -> viewModel.decodeImageUriToBase64(uri, cb) },
            onDismiss = { showHollowStealthDialog = false },
            onSave = { mode, customName, iconPreset, sysUiTrigger, customIconB64, donorPkg, spoofSettings ->
                viewModel.updateHollowStealthConfig(
                    visibilityMode = mode,
                    customName = customName,
                    iconPreset = iconPreset,
                    systemUiTriggerEnabled = sysUiTrigger,
                    customIconBase64 = customIconB64,
                    customIconDonorPkg = donorPkg,
                    spoofInSettings = spoofSettings
                )
                showHollowStealthDialog = false
            }
        )
    }

    if (showSetupGuideDialog) {
        AlertDialog(
            onDismissRequest = { showSetupGuideDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = SettingsDarkOnyx,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "How to Enable Protection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SettingsDarkOnyx
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SetupStep("1", "Enable in LSPosed / Vector",
                        "Open your Xposed manager, enable Hollow, and select target apps in its scope.")
                    SetupStep("2", "Add apps in Storage Isolate",
                        "Open Storage Isolate, tap + Add Apps, and switch on apps you want isolated.")
                    SetupStep("3", "Force-stop & reopen the app",
                        "The app will now run inside its own private sandbox.")
                }
            },
            confirmButton = {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = SettingsDarkOnyx
                ) {
                    Text(
                        text = "Got it",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier
                            .clickable { showSetupGuideDialog = false }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetupGuideDialog = false }) {
                    Text("Close", color = SettingsMuted)
                }
            }
        )
    }
}

// ── Reusable setting sub-components ──────────────────────────────────────────

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = SettingsMuted,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun SettingsGroupCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, SettingsCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = SettingsCardBorder,
        thickness = 1.dp,
        modifier = Modifier.padding(start = 52.dp)
    )
}

@Composable
private fun SettingsIconBox(icon: ImageVector) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SettingsLightGray,
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SettingsDarkOnyx,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.4f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onToggle(!checked) }
            .padding(vertical = 12.dp)
    ) {
        SettingsIconBox(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = SettingsDarkOnyx.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SettingsMuted.copy(alpha = contentAlpha),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Animated switch using Material3's Switch with onyx thumb/track when ON
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SettingsDarkOnyx,
                uncheckedThumbColor = SettingsMuted,
                uncheckedTrackColor = SettingsLightGray,
                uncheckedBorderColor = SettingsCardBorder
            )
        )
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailingLabel: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        SettingsIconBox(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = SettingsDarkOnyx,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SettingsMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailingLabel != null) {
            Surface(
                shape = RoundedCornerShape(50),
                color = SettingsLightGray
            ) {
                Text(
                    text = trailingLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SettingsDarkOnyx,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = SettingsMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    ListRow(
        title = title,
        subtitle = description,
        onClick = if (enabled) ({ onChange(!checked) }) else null,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SettingsDarkOnyx,
                    uncheckedThumbColor = SettingsMuted,
                    uncheckedTrackColor = SettingsLightGray
                )
            )
        }
    )
}

@Composable
fun SetupStep(step: String, title: String, desc: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = SettingsDarkOnyx,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = step,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = SettingsDarkOnyx
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = SettingsMuted
            )
        }
    }
}
