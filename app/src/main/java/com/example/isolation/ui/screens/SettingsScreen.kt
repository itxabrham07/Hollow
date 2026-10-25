package com.example.isolation.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.ui.components.formatBytes
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.JakartaFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    viewModel: IsoVaultViewModel,
    onTriggerLock: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    val configs by viewModel.configs.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val primaryPin by viewModel.primaryPin.collectAsState()
    val panicPin by viewModel.panicPin.collectAsState()
    val decoyPinEnabled by viewModel.decoyPinEnabled.collectAsState()
    val fingerprintEnabled by viewModel.fingerprintEnabled.collectAsState()
    val autoLockMinutes by viewModel.autoLockMinutes.collectAsState()
    val blockScreenshots by viewModel.blockScreenshots.collectAsState()
    val isPanicModeActive by viewModel.isPanicModeActive.collectAsState()
    val isLsposedActive by viewModel.isLsposedActive.collectAsState()
    val isRootGranted by viewModel.isRootGranted.collectAsState()
    val vaultRefreshTick by viewModel.vaultRefreshTick.collectAsState()

    var showChangePinDialog by remember { mutableStateOf(false) }
    var primaryInput by remember(primaryPin) { mutableStateOf(primaryPin) }
    var panicInput by remember(panicPin) { mutableStateOf(panicPin) }
    var backupPassphrase by remember { mutableStateOf("") }

    // Encrypted Backup Launcher
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val vaultRoot = viewModel.getVaultRootDirectory()
                    val count = context.contentResolver.openOutputStream(uri)?.use { out ->
                        AesVaultCipher.createEncryptedBackup(vaultRoot, out, backupPassphrase.takeIf { it.isNotBlank() })
                    } ?: 0
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Encrypted backup saved ($count files)",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Encrypted Restore Launcher
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val vaultRoot = viewModel.getVaultRootDirectory()
                    val restored = context.contentResolver.openInputStream(uri)?.use { input ->
                        AesVaultCipher.restoreEncryptedBackup(input, vaultRoot, backupPassphrase.takeIf { it.isNotBlank() })
                    } ?: 0
                    withContext(Dispatchers.Main) {
                        viewModel.notifyVaultFilesChanged()
                        Toast.makeText(
                            context,
                            "Restored $restored file(s) into Vault",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(horizontal = 20.dp)
            .testTag("settings_screen"),
        contentPadding = PaddingValues(top = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Screen Header
        item {
            Column {
                Text(
                    text = "Settings",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FrauncesFontFamily,
                    color = colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Security, volumes, encryption and backups",
                    fontSize = 14.sp,
                    fontFamily = JakartaFontFamily,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }

        // Decoy Vault Active Notice
        if (isPanicModeActive) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, colorScheme.primary),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Decoy Vault Active",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = JakartaFontFamily,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "Real vault items are hidden until you unlock with your primary PIN.",
                                fontSize = 13.sp,
                                fontFamily = JakartaFontFamily,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { viewModel.exitPanicMode() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Exit Decoy")
                        }
                    }
                }
            }
        }

        // 1. PIN, Fingerprint & Decoy Lock
        item {
            SettingsSectionCard(
                icon = Icons.Rounded.Lock,
                title = "PIN & Biometrics",
                subtitle = "Manage your unlock PIN, fingerprint and decoy vault"
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            primaryInput = primaryPin
                            panicInput = panicPin
                            showChangePinDialog = true
                        }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Change Vault & Decoy PINs",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = JakartaFontFamily,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "Primary PIN and optional 4-digit decoy PIN",
                            fontSize = 12.sp,
                            fontFamily = JakartaFontFamily,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Edit",
                        color = colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = JakartaFontFamily
                    )
                }

                HorizontalDivider(color = colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                SettingsToggleRow(
                    icon = Icons.Rounded.Fingerprint,
                    title = "Fingerprint Unlock",
                    subtitle = "Use biometric authentication on the lock screen",
                    checked = fingerprintEnabled,
                    onCheckedChange = { viewModel.setFingerprintEnabled(it) }
                )

                HorizontalDivider(color = colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                SettingsToggleRow(
                    icon = Icons.Rounded.Shield,
                    title = "Decoy PIN Mode",
                    subtitle = "Entering the decoy PIN opens an empty vault",
                    checked = decoyPinEnabled,
                    onCheckedChange = { viewModel.setDecoyPinEnabled(it) }
                )
            }
        }

        // 2. Auto-Lock, Screenshot Block & Theme
        item {
            SettingsSectionCard(
                icon = Icons.Rounded.Timer,
                title = "Privacy & Appearance",
                subtitle = "Auto-lock timer, screenshot protection and theme"
            ) {
                Text(
                    text = "Auto-lock timeout",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = JakartaFontFamily,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                val options = listOf(
                    0 to "Immediately",
                    1 to "1 min",
                    5 to "5 min",
                    15 to "15 min"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { (mins, label) ->
                        val selected = autoLockMinutes == mins
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setAutoLockMinutes(mins) },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontFamily = JakartaFontFamily,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colorScheme.primary.copy(alpha = 0.16f),
                                selectedLabelColor = colorScheme.primary
                            )
                        )
                    }
                }

                HorizontalDivider(color = colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 10.dp))

                SettingsToggleRow(
                    icon = Icons.Rounded.ScreenLockPortrait,
                    title = "Block Screenshots (FLAG_SECURE)",
                    subtitle = "Hide vault content in recents and prevent screen capture",
                    checked = blockScreenshots,
                    onCheckedChange = { viewModel.setBlockScreenshots(it) }
                )

                HorizontalDivider(color = colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 10.dp))

                SettingsToggleRow(
                    icon = Icons.Rounded.DarkMode,
                    title = "Dark Theme",
                    subtitle = "Switch between near-black dark mode and clean light mode",
                    checked = isDarkTheme,
                    onCheckedChange = { viewModel.setDarkTheme(it) }
                )
            }
        }

        // 3. Encrypted Volumes & Per-Volume Key Isolation
        item {
            SettingsSectionCard(
                icon = Icons.Rounded.Storage,
                title = "Encrypted Volumes",
                subtitle = "Each volume uses an independent 256-bit key wrapped by Android Keystore"
            ) {
                val sharedBytes = remember(vaultRefreshTick) {
                    viewModel.getSharedVolumeDirectory().walkTopDown().filter { it.isFile && it.name != ".nomedia" }.sumOf { it.length() }
                }
                VolumeKeyRow(
                    title = "Shared Space Volume",
                    subtitle = "${formatBytes(sharedBytes)} • Visible to opted-in apps",
                    onWipeAndRotate = {
                        viewModel.deleteVolume(IsolationRuleManager.SHARED_VOLUME_ID, "Shared Space")
                    }
                )

                configs.forEach { cfg ->
                    HorizontalDivider(color = colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 6.dp))
                    val bytes = remember(cfg, vaultRefreshTick) {
                        val d = viewModel.getAppSandboxDirectory(cfg.packageName).walkTopDown()
                            .filter { it.isFile && it.name != ".nomedia" }.sumOf { it.length() }
                        maxOf(d, cfg.totalBytesEncrypted)
                    }
                    VolumeKeyRow(
                        title = "${cfg.appName} Volume",
                        subtitle = "${formatBytes(bytes)} • ${if (cfg.useSharedSpace) "Shared Space Linked" else "Private Only"}",
                        onWipeAndRotate = {
                            viewModel.deleteVolume(cfg.packageName, cfg.appName)
                        }
                    )
                }
            }
        }

        // 4. Encrypted Backup & Restore
        item {
            SettingsSectionCard(
                icon = Icons.Rounded.Backup,
                title = "Encrypted Backup & Restore",
                subtitle = "Export or restore your vault with 600,000 PBKDF2-HMAC-SHA256 iterations"
            ) {
                OutlinedTextField(
                    value = backupPassphrase,
                    onValueChange = { backupPassphrase = it },
                    label = { Text("Optional Backup Passphrase (PBKDF2 600k)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            createBackupLauncher.launch("IsoVault_Backup_${System.currentTimeMillis()}.isovaultbackup")
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("backup_vault_button")
                    ) {
                        Icon(Icons.Rounded.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Backup", fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily)
                    }

                    OutlinedButton(
                        onClick = {
                            restoreBackupLauncher.launch(arrayOf("*/*"))
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("restore_vault_button")
                    ) {
                        Icon(Icons.Rounded.Restore, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restore", color = colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily)
                    }
                }
            }
        }

        // 5. Encryption Info & About
        item {
            SettingsSectionCard(
                icon = Icons.Rounded.EnhancedEncryption,
                title = "Encryption & About",
                subtitle = "Cryptographic architecture & module status"
            ) {
                InfoSummaryRow("Streaming Cipher", "AES256_GCM_HKDF_4KB (ISOV v2)")
                InfoSummaryRow("Master Key", "256-bit Android Keystore Wrapped")
                InfoSummaryRow("Volume Keys", "Independent 256-bit DEK per volume")
                InfoSummaryRow("Passphrase KDF", "PBKDF2-HMAC-SHA256 (600,000 rounds)")
                InfoSummaryRow("LSPosed Module", if (isLsposedActive) "Active (API 82)" else "Inactive — Enable in LSPosed")
                InfoSummaryRow(
                    "Kernel Bind-Mount",
                    if (isRootGranted) "Active (Root)" else "Inactive (Experimental • User-space hooks active)"
                )
                InfoSummaryRow("App Version", "3.0.0 (Vault-LSPosed Production)")

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.reopenOnboarding() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Setup Guide", fontSize = 12.sp, fontFamily = JakartaFontFamily)
                    }
                    OutlinedButton(
                        onClick = { viewModel.requestRootAccess() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Check Root Mount", fontSize = 12.sp, fontFamily = JakartaFontFamily)
                    }
                }
            }
        }
    }

    if (showChangePinDialog) {
        AlertDialog(
            onDismissRequest = { showChangePinDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = colorScheme.surface,
            title = {
                Text("Update Security PINs", fontFamily = FrauncesFontFamily, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = primaryInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) primaryInput = it },
                        label = { Text("Primary 4-digit PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = panicInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) panicInput = it },
                        label = { Text("Decoy 4-digit PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (primaryInput.length == 4 && panicInput.length == 4 && primaryInput != panicInput) {
                            viewModel.updatePins(primaryInput, panicInput)
                            showChangePinDialog = false
                        } else {
                            Toast.makeText(context, "Enter two distinct 4-digit PINs", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save PINs")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun VolumeKeyRow(
    title: String,
    subtitle: String,
    onWipeAndRotate: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = JakartaFontFamily,
                color = colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                fontFamily = JakartaFontFamily,
                color = colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onWipeAndRotate) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Wipe Volume & Rotate Key",
                tint = colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary.copy(alpha = 0.14f))
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FrauncesFontFamily,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        fontFamily = JakartaFontFamily,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = JakartaFontFamily,
                color = colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                fontFamily = JakartaFontFamily,
                color = colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colorScheme.primary,
                uncheckedThumbColor = colorScheme.onSurfaceVariant,
                uncheckedTrackColor = colorScheme.surface
            )
        )
    }
}

@Composable
private fun InfoSummaryRow(label: String, value: String) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontFamily = JakartaFontFamily,
            color = colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = JakartaFontFamily,
            color = colorScheme.onSurface
        )
    }
}
