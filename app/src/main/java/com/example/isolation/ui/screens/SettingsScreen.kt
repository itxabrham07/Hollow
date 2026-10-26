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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.sp
import com.example.isolation.model.HollowIconTheme
import com.example.isolation.model.LauncherVisibilityMode
import com.example.isolation.model.PickerPolicy
import com.example.isolation.ui.components.HollowDynamicEmblem
import com.example.isolation.ui.components.StatusBadge
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.JakartaFontFamily

@Composable
fun SettingsScreen(
    viewModel: IsoVaultViewModel,
    onOpenDiagnostics: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                fontFamily = JakartaFontFamily,
                color = scheme.onBackground
            )
            Text(
                text = "Hollow stealth, system picker policy, appearance, and volume mounts.",
                fontSize = 11.sp,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = JakartaFontFamily
            )
        }

        // 1. Hollow Self-Stealth & SystemUI Entry (Moved from AppHiderScreen to Settings)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showHollowStealthDialog = true }
                    .testTag("card_hollow_self_stealth")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = presetIconVector(settings.hollowIconPreset),
                            contentDescription = null,
                            tint = scheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hide / Disguise Hollow",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = JakartaFontFamily
                        )
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
                            fontSize = 11.sp,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = JakartaFontFamily
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = { showHollowStealthDialog = true },
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (settings.hollowVisibilityMode) {
                                LauncherVisibilityMode.VISIBLE -> "Visible"
                                LauncherVisibilityMode.DISGUISED -> "Disguised"
                                LauncherVisibilityMode.HIDDEN -> "Hidden"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = JakartaFontFamily
                        )
                    }
                }
            }
        }

        // 2. Compact System Photo & File Picker Settings Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
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
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(scheme.primaryContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    tint = scheme.onPrimaryContainer,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "System Photo & File Picker",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = JakartaFontFamily,
                                    color = scheme.onSurface
                                )
                                Text(
                                    text = if (settings.pickerPolicy == PickerPolicy.ALLOW) {
                                        "Picked photos copy safely to app sandbox"
                                    } else {
                                        "System picker blocked for isolated apps"
                                    },
                                    fontSize = 11.sp,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusBadge(
                            text = if (settings.pickerPolicy == PickerPolicy.ALLOW) "Sandbox Copy" else "Blocked",
                            color = scheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

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
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "Allow Picker",
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = JakartaFontFamily,
                                    fontWeight = if (allowSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            shape = RoundedCornerShape(50),
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
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "Block Picker",
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = JakartaFontFamily,
                                    fontWeight = if (blockSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            shape = RoundedCornerShape(50),
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
        }

        // 3. Appearance Card (Dark theme default)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(scheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = scheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Appearance",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            fontFamily = JakartaFontFamily,
                            color = scheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    ToggleRow(
                        title = "Dark theme",
                        description = "Default dark appearance",
                        checked = settings.darkTheme,
                        enabled = true,
                        onChange = { viewModel.setDarkTheme(it) }
                    )
                    HorizontalDivider(color = scheme.outlineVariant)
                    ToggleRow(
                        title = "Wallpaper colors (Material You)",
                        description = "Match your phone's wallpaper on Android 12+",
                        checked = settings.dynamicColor,
                        enabled = true,
                        onChange = { viewModel.setDynamicColor(it) }
                    )
                    HorizontalDivider(color = scheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Hollow Launcher Icon Style",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        fontFamily = JakartaFontFamily
                    )
                    Text(
                        text = "Switch between Dynamic Material You (adapts to wallpaper & themed icons) or signature Hollow styles",
                        fontSize = 11.sp,
                        color = scheme.onSurfaceVariant,
                        fontFamily = JakartaFontFamily
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
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
                                        size = 36.dp
                                    )
                                    Spacer(modifier = Modifier.height(5.dp))
                                    Text(
                                        text = themeOption.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) scheme.primary else scheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontFamily = JakartaFontFamily
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Volume Isolation & Root Settings
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        text = "Volume Isolation",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        fontFamily = JakartaFontFamily,
                        color = scheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    ToggleRow(
                        title = "Private volumes (Root)",
                        description = "Mount a separate storage folder per app when rooted",
                        checked = settings.rootVolumesEnabled,
                        enabled = true,
                        onChange = { viewModel.setRootVolumesEnabled(it) }
                    )
                    HorizontalDivider(color = scheme.outlineVariant)
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

        // 5. Setup Guide Hidden Behind a Button + System Diagnostics
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
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
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(scheme.secondaryContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = null,
                                    tint = scheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Setup & Xposed Guide",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = JakartaFontFamily
                                )
                                Text(
                                    text = if (isLsposedActive) "Module active in LSPosed" else "How to activate in 3 steps",
                                    fontSize = 11.sp,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { showSetupGuideDialog = true },
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_open_setup_guide")
                        ) {
                            Text(
                                text = "View Guide",
                                fontSize = 12.sp,
                                fontFamily = JakartaFontFamily,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = scheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Root Access Check
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Root access",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurface,
                                fontFamily = JakartaFontFamily
                            )
                            Text(
                                text = rootStatus,
                                fontSize = 11.sp,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = JakartaFontFamily
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.checkRoot() },
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Check",
                                fontSize = 12.sp,
                                fontFamily = JakartaFontFamily,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = scheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Advanced System Details
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDiagnostics() }
                            .padding(vertical = 2.dp)
                            .testTag("nav_mounts")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Advanced System Details",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurface,
                                fontFamily = JakartaFontFamily
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
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

    // Setup Guide Modal Dialog (opened only when clicking "View Guide")
    if (showSetupGuideDialog) {
        AlertDialog(
            onDismissRequest = { showSetupGuideDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = scheme.surface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(scheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = scheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "How to Enable Protection",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        fontFamily = JakartaFontFamily,
                        color = scheme.onSurface
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Button(
                    onClick = { showSetupGuideDialog = false },
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Got it", fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetupGuideDialog = false }) {
                    Text("Close", fontFamily = JakartaFontFamily)
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
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant,
                fontFamily = JakartaFontFamily
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = JakartaFontFamily
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled
        )
    }
}

@Composable
fun SetupStep(step: String, title: String, desc: String) {
    val scheme = MaterialTheme.colorScheme
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
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = JakartaFontFamily
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                fontFamily = JakartaFontFamily
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant,
                fontFamily = JakartaFontFamily
            )
        }
    }
}
