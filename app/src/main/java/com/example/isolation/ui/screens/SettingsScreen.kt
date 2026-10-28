package com.example.isolation.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import com.example.isolation.ui.components.SectionHeader
import com.example.isolation.ui.components.StatusIndicator
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.FrauncesFontFamily

@Composable
fun SettingsScreen(
    viewModel: IsoVaultViewModel,
    onOpenDiagnostics: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    val isLsposedActive by viewModel.isLsposedActive.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val rootStatus by viewModel.rootStatus.collectAsState()
    var showSetupGuideDialog by remember { mutableStateOf(false) }
    var showHollowStealthDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Settings",
                style = typography.headlineMedium,
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Hollow stealth, system picker policy, appearance, and volume mounts.",
                style = typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 1. Hollow Self-Stealth & SystemUI Entry
        item {
            ContentCard(
                shape = shapes.large,
                borderColor = scheme.primary,
                onClick = { showHollowStealthDialog = true },
                modifier = Modifier.testTag("card_hollow_self_stealth")
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
                                .size(40.dp)
                                .clip(shapes.small)
                                .background(scheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = presetIconVector(settings.hollowIconPreset),
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hide / Disguise Hollow",
                                style = typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when (settings.hollowVisibilityMode) {
                                    LauncherVisibilityMode.VISIBLE ->
                                        if (settings.systemUiTriggerEnabled) "Visible • SystemUI Clock/Date trigger on"
                                        else "Visible in launcher • Tap to hide or disguise"
                                    LauncherVisibilityMode.DISGUISED ->
                                        "Disguised as \"${settings.hollowCustomName}\" • SystemUI ready"
                                    LauncherVisibilityMode.HIDDEN ->
                                        "Hidden • Open via SystemUI Clock or *#*#465569#*#*"
                                },
                                style = typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    SecondaryButton(
                        text = when (settings.hollowVisibilityMode) {
                            LauncherVisibilityMode.VISIBLE -> "Visible"
                            LauncherVisibilityMode.DISGUISED -> "Disguised"
                            LauncherVisibilityMode.HIDDEN -> "Hidden"
                        },
                        icon = Icons.Default.Edit,
                        onClick = { showHollowStealthDialog = true }
                    )
                }
            }
        }

        // 2. System Photo & File Picker Settings Card
        item {
            ContentCard {
                SectionHeader(
                    title = "System Photo & File Picker",
                    subtitle = if (settings.pickerPolicy == PickerPolicy.ALLOW) {
                        "Picked photos copy safely to app sandbox"
                    } else {
                        "System picker blocked for isolated apps"
                    },
                    trailing = {
                        StatusIndicator(
                            label = if (settings.pickerPolicy == PickerPolicy.ALLOW) "Sandbox Copy" else "Blocked",
                            color = if (settings.pickerPolicy == PickerPolicy.ALLOW) scheme.secondary else scheme.error
                        )
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val allowSelected = settings.pickerPolicy == PickerPolicy.ALLOW
                    FilterChip(
                        selected = allowSelected,
                        onClick = { viewModel.setPickerPolicy(PickerPolicy.ALLOW) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "Allow Picker",
                                style = typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (allowSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        shape = shapes.extraSmall,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = scheme.primaryContainer,
                            selectedLabelColor = scheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("picker_policy_allow")
                    )

                    val blockSelected = settings.pickerPolicy == PickerPolicy.BLOCK
                    FilterChip(
                        selected = blockSelected,
                        onClick = { viewModel.setPickerPolicy(PickerPolicy.BLOCK) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "Block Picker",
                                style = typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (blockSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        shape = shapes.extraSmall,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = scheme.primaryContainer,
                            selectedLabelColor = scheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("picker_policy_block")
                    )
                }
            }
        }

        // 3. Appearance Card
        item {
            ContentCard {
                SectionHeader(
                    title = "Appearance",
                    subtitle = "Theme mode, Material You dynamic colors, and launcher emblem"
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleRow(
                        title = "Dark theme",
                        description = "Default dark appearance",
                        checked = settings.darkTheme,
                        enabled = true,
                        onChange = { viewModel.setDarkTheme(it) }
                    )
                    ToggleRow(
                        title = "Wallpaper colors (Material You)",
                        description = "Match your phone's wallpaper on Android 12+",
                        checked = settings.dynamicColor,
                        enabled = true,
                        onChange = { viewModel.setDynamicColor(it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = scheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Hollow Launcher Icon Style",
                    style = typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Switch between Dynamic Material You (adapts to wallpaper & themed icons) or signature Hollow styles",
                    style = typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HollowIconTheme.entries.forEach { themeOption ->
                        val isSelected = settings.hollowIconTheme == themeOption
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    scheme.primaryContainer.copy(alpha = 0.4f)
                                } else {
                                    scheme.surfaceContainerLow
                                }
                            ),
                            shape = shapes.small,
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) scheme.primary else scheme.outlineVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setHollowIconTheme(themeOption) }
                                .testTag("icon_theme_${themeOption.name.lowercase()}")
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                HollowDynamicEmblem(
                                    iconTheme = themeOption,
                                    size = 32.dp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = themeOption.label,
                                    style = typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) scheme.primary else scheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Volume Isolation & Root Settings
        item {
            ContentCard {
                SectionHeader(
                    title = "Volume Isolation",
                    subtitle = "Root storage mounts and at-rest encryption"
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleRow(
                        title = "Private volumes (Root)",
                        description = "Mount a separate storage folder per app when rooted",
                        checked = settings.rootVolumesEnabled,
                        enabled = true,
                        onChange = { viewModel.setRootVolumesEnabled(it) }
                    )
                    ToggleRow(
                        title = "Lock volumes when unmounted",
                        description = "Automatically encrypt app volumes when closed",
                        checked = settings.encryptVolumesAtRest,
                        enabled = settings.rootVolumesEnabled,
                        onChange = { viewModel.setEncryptVolumesAtRest(it) }
                    )
                }
            }
        }

        // 5. Setup Guide + System Diagnostics
        item {
            ContentCard {
                SectionHeader(
                    title = "System & Diagnostics",
                    subtitle = "LSPosed setup guide, root status, and mount terminal"
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ListRow(
                        title = "Setup & Xposed Guide",
                        subtitle = if (isLsposedActive) "Module active in LSPosed" else "How to activate in 3 steps",
                        leadingIcon = Icons.Default.HelpOutline,
                        trailing = {
                            SecondaryButton(
                                text = "View Guide",
                                onClick = { showSetupGuideDialog = true },
                                modifier = Modifier.testTag("btn_open_setup_guide")
                            )
                        }
                    )

                    ListRow(
                        title = "Root access",
                        subtitle = rootStatus,
                        trailing = {
                            SecondaryButton(
                                text = "Check",
                                onClick = { viewModel.checkRoot() }
                            )
                        }
                    )

                    ListRow(
                        title = "Advanced System Details",
                        subtitle = "Namespace diagnostics, boot script & active mounts",
                        leadingIcon = Icons.Default.Tune,
                        showNavigationChevron = true,
                        onClick = onOpenDiagnostics,
                        modifier = Modifier.testTag("nav_mounts")
                    )
                }
            }
        }
    }

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

    // Setup Guide Modal Dialog
    if (showSetupGuideDialog) {
        AlertDialog(
            onDismissRequest = { showSetupGuideDialog = false },
            shape = shapes.extraLarge,
            containerColor = scheme.surface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(scheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = scheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "How to Enable Protection",
                        style = typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SetupStep(
                        step = "1",
                        title = "Enable Hollow in LSPosed / Vector",
                        desc = "Open your Xposed manager, enable the Hollow module, and select the target apps in its scope."
                    )
                    SetupStep(
                        step = "2",
                        title = "Add apps in Storage Isolate",
                        desc = "Open 'Storage Isolate' on the Home screen, tap '+ Add Apps', and switch on WhatsApp, Snapchat, or any app you want isolated."
                    )
                    SetupStep(
                        step = "3",
                        title = "Force-stop & reopen the app",
                        desc = "The app will now run inside its own private storage sandbox while still allowing you to share single photos via the System Picker."
                    )
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = "Got it",
                    onClick = { showSetupGuideDialog = false }
                )
            },
            dismissButton = {
                TextButton(onClick = { showSetupGuideDialog = false }) {
                    Text("Close")
                }
            }
        )
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
                enabled = enabled
            )
        }
    )
}

@Composable
fun SetupStep(step: String, title: String, desc: String) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(scheme.primaryContainer)
        ) {
            Text(
                text = step,
                color = scheme.onPrimaryContainer,
                style = typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface
            )
            Text(
                text = desc,
                style = typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}
