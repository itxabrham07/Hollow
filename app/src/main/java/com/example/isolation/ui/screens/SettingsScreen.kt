package com.example.isolation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.model.PickerPolicy
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                fontFamily = JakartaFontFamily,
                color = scheme.onBackground
            )
            Text(
                text = "Configure system photo picker behavior, appearance, and volume isolation.",
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant,
                fontFamily = JakartaFontFamily
            )
        }

        // 1. Dedicated System Photo & File Picker Settings Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(scheme.primaryContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    tint = scheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "System Photo & File Picker",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    fontFamily = JakartaFontFamily,
                                    color = scheme.onSurface
                                )
                                Text(
                                    text = "Control what happens when isolated apps request a photo or file",
                                    fontSize = 11.sp,
                                    color = scheme.onSurfaceVariant,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    for (policy in PickerPolicy.values()) {
                        val selected = settings.pickerPolicy == policy
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) scheme.primaryContainer.copy(alpha = 0.35f) else scheme.surfaceContainerLow
                            ),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                1.dp,
                                if (selected) scheme.primary else scheme.outlineVariant
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { viewModel.setPickerPolicy(policy) }
                                .testTag("picker_policy_${policy.name.lowercase()}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { viewModel.setPickerPolicy(policy) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (policy == PickerPolicy.ALLOW) {
                                                "Allow System Picker (Recommended)"
                                            } else {
                                                "Block System Picker"
                                            },
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = scheme.onSurface,
                                            fontFamily = JakartaFontFamily
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = policy.description,
                                        fontSize = 12.sp,
                                        color = scheme.onSurfaceVariant,
                                        fontFamily = JakartaFontFamily
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = scheme.outlineVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Atomic Sandbox Copy for Picked Photos",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurface,
                                fontFamily = JakartaFontFamily
                            )
                            Text(
                                text = "Selected photos are safely copied into the app's private Pictures sandbox so your full gallery remains hidden.",
                                fontSize = 11.sp,
                                color = scheme.onSurfaceVariant,
                                fontFamily = JakartaFontFamily
                            )
                        }
                        StatusBadge(text = "Active", color = scheme.primary)
                    }
                }
            }
        }

        // 2. Appearance Card (Dark theme default)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(scheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = scheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Appearance",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            fontFamily = JakartaFontFamily,
                            color = scheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    ToggleRow(
                        title = "Dark theme",
                        description = "Default dark appearance for comfortable viewing",
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
                }
            }
        }

        // 3. Volume Isolation & Root Settings
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Volume Isolation",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        fontFamily = JakartaFontFamily,
                        color = scheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))

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

        // 4. Setup Guide Hidden Behind a Button + System Diagnostics
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Setup Guide Row (Hidden behind button)
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
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(scheme.secondaryContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = null,
                                    tint = scheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Setup & Xposed Guide",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.onSurface,
                                    fontFamily = JakartaFontFamily
                                )
                                Text(
                                    text = if (isLsposedActive) "Module active in LSPosed" else "How to activate Hollow in 3 steps",
                                    fontSize = 12.sp,
                                    color = scheme.onSurfaceVariant,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { showSetupGuideDialog = true },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.testTag("btn_open_setup_guide")
                        ) {
                            Text("View Guide", fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = scheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Root Access Check
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Root access",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurface,
                                fontFamily = JakartaFontFamily
                            )
                            Text(
                                text = rootStatus,
                                fontSize = 12.sp,
                                color = scheme.onSurfaceVariant,
                                fontFamily = JakartaFontFamily
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.checkRoot() },
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Check", fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = scheme.outlineVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Advanced System Details
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDiagnostics() }
                            .padding(vertical = 4.dp)
                            .testTag("nav_mounts")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Advanced System Details",
                                fontSize = 14.sp,
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
            Spacer(modifier = Modifier.height(20.dp))
        }
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
                        title = "Add apps in Protected Apps",
                        desc = "Open 'Protected Apps' on the Home screen, tap '+ Add Apps', and switch on WhatsApp, Snapchat, or any app you want isolated."
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
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant,
                fontFamily = JakartaFontFamily
            )
            Text(text = description, fontSize = 12.sp, color = scheme.onSurfaceVariant, fontFamily = JakartaFontFamily)
        }
        Spacer(modifier = Modifier.width(12.dp))
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
