package com.example.isolation.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.VerifiedUser
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.ui.components.BrandSquircleIcon
import com.example.isolation.ui.components.formatBytes
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.JakartaFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: IsoVaultViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    val configs by viewModel.allConfigs.collectAsStateWithLifecycle()
    val securitySettings by viewModel.securitySettings.collectAsStateWithLifecycle()
    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val vaultRefreshTick by viewModel.vaultRefreshTick.collectAsStateWithLifecycle()

    var showChangePinDialog by remember { mutableStateOf(false) }
    var primaryInput by remember { mutableStateOf("") }
    var panicInput by remember { mutableStateOf("") }
    var backupPassphrase by remember { mutableStateOf("") }

    val sandboxBaseDir = remember { viewModel.getSandboxBaseDir() }
    val sharedSpaceDir = remember { viewModel.getSharedSpaceDir() }

    // Encrypted Backup Launcher (SAF CreateDocument)
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val count = context.contentResolver.openOutputStream(uri)?.use { out ->
                        AesVaultCipher.createEncryptedBackup(
                            vaultRoot = context.filesDir,
                            destinationStream = out,
                            passphrase = backupPassphrase.takeIf { it.isNotBlank() }
                        )
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

    // Encrypted Restore Launcher (SAF OpenDocument)
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val restored = context.contentResolver.openInputStream(uri)?.use { input ->
                        AesVaultCipher.restoreEncryptedBackup(
                            sourceStream = input,
                            vaultRoot = context.filesDir,
                            passphrase = backupPassphrase.takeIf { it.isNotBlank() }
                        )
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        val isWideScreen = maxWidth >= 600.dp
        val horizontalPad = if (isWideScreen) 24.dp else 16.dp

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPad)
                .testTag("settings_screen"),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Screen Header
            item {
                Column {
                    Text(
                        text = "Settings",
                        fontSize = if (isWideScreen) 28.sp else 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FrauncesFontFamily,
                        color = colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Security, volumes, encryption and backups",
                        fontSize = 13.sp,
                        fontFamily = JakartaFontFamily,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            // Decoy Vault Active Notice
            if (securitySettings.isPanicDecoyActive) {
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
                                    text = "Real vault items are hidden until you lock and unlock with your primary PIN.",
                                    fontSize = 13.sp,
                                    fontFamily = JakartaFontFamily,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = { viewModel.lockVaultNow() },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Lock Now")
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
                                primaryInput = ""
                                panicInput = ""
                                showChangePinDialog = true
                            }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (securitySettings.isPinEnabled) "Update Vault & Decoy PINs" else "Set Up 4-Digit Vault PIN",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = JakartaFontFamily,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = if (securitySettings.isPinEnabled) {
                                    "Primary PIN active" + if (securitySettings.isPanicPinEnabled) " • Decoy PIN active" else ""
                                } else {
                                    "Protect your vault with a 4-digit PIN and optional decoy PIN"
                                },
                                fontSize = 12.sp,
                                fontFamily = JakartaFontFamily,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (securitySettings.isPinEnabled) "Edit" else "Set PIN",
                            color = colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = JakartaFontFamily
                        )
                    }

                    if (securitySettings.isPinEnabled) {
                        HorizontalDivider(color = colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Lock Vault Immediately",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = JakartaFontFamily,
                                color = colorScheme.onSurface
                            )
                            OutlinedButton(
                                onClick = { viewModel.lockVaultNow() },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Lock Now", fontSize = 12.sp, fontFamily = JakartaFontFamily)
                            }
                        }
                    }

                    HorizontalDivider(color = colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

                    SettingsToggleRow(
                        icon = Icons.Rounded.Fingerprint,
                        title = "Fingerprint Unlock",
                        subtitle = "Use biometric authentication on the lock screen",
                        checked = securitySettings.isBiometricEnabled,
                        onCheckedChange = { viewModel.setBiometricEnabled(it) }
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
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        options.forEach { (mins, label) ->
                            val selected = securitySettings.autoLockMinutes == mins
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
                        checked = securitySettings.blockScreenshots,
                        onCheckedChange = { viewModel.setBlockScreenshots(it) }
                    )

                    HorizontalDivider(color = colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 10.dp))

                    SettingsToggleRow(
                        icon = Icons.Rounded.DarkMode,
                        title = "Dark Theme",
                        subtitle = "Switch between near-black dark mode and clean light mode",
                        checked = securitySettings.isDarkTheme,
                        onCheckedChange = { viewModel.setDarkTheme(it) }
                    )
                }
            }

            // 3. Encrypted Volumes & Per-Volume Key Isolation (100% Real On-Disk Bytes)
            item {
                SettingsSectionCard(
                    icon = Icons.Rounded.Storage,
                    title = "Encrypted Volumes Manager",
                    subtitle = "Each volume uses an independent 256-bit key wrapped by Android Keystore"
                ) {
                    val sharedBytes = remember(vaultRefreshTick) {
                        if (sharedSpaceDir.exists()) {
                            sharedSpaceDir.walkTopDown()
                                .filter { it.isFile && it.name != ".nomedia" && !it.name.startsWith(".seeded") }
                                .sumOf { it.length() }
                        } else {
                            0L
                        }
                    }
                    VolumeKeyRow(
                        title = "Shared Space Volume",
                        packageName = "shared.vault",
                        subtitle = "${formatBytes(sharedBytes)} on disk • Linked across opted-in apps",
                        onWipeAndRotate = {
                            viewModel.deleteVolume("shared.vault", wipeDiskFiles = true)
                            Toast.makeText(context, "Wiped Shared Space & rotated 256-bit key", Toast.LENGTH_SHORT).show()
                        }
                    )

                    configs.forEach { cfg ->
                        HorizontalDivider(color = colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 6.dp))
                        val bytes = remember(cfg, vaultRefreshTick) {
                            val appDir = File(sandboxBaseDir, cfg.packageName)
                            if (appDir.exists()) {
                                appDir.walkTopDown()
                                    .filter { it.isFile && it.name != ".nomedia" && !it.name.startsWith(".seeded") }
                                    .sumOf { it.length() }
                            } else {
                                0L
                            }
                        }
                        VolumeKeyRow(
                            title = "${cfg.appName} Volume",
                            packageName = cfg.packageName,
                            subtitle = "${formatBytes(bytes)} on disk • ${if (cfg.useSharedSpace) "Shared Space Linked" else "Private Only"}",
                            onWipeAndRotate = {
                                viewModel.deleteVolume(cfg.packageName, wipeDiskFiles = true)
                                Toast.makeText(context, "Removed ${cfg.appName} volume & wiped encrypted files", Toast.LENGTH_SHORT).show()
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

            // 5. Encryption Info & Live Diagnostics
            item {
                SettingsSectionCard(
                    icon = Icons.Rounded.EnhancedEncryption,
                    title = "Encryption & Diagnostics",
                    subtitle = "Cryptographic architecture & live verification"
                ) {
                    InfoSummaryRow("Streaming Cipher", "AES256_GCM_HKDF_4KB (ISOV v2)")
                    InfoSummaryRow("Master Key", "256-bit Android Keystore Wrapped")
                    InfoSummaryRow("Volume Keys", "Independent 256-bit DEK per volume")
                    InfoSummaryRow("Passphrase KDF", "PBKDF2-HMAC-SHA256 (600,000 rounds)")
                    InfoSummaryRow(
                        "LSPosed Module",
                        if (dashboardState.isLsposedActive) "Active (API 82)" else "Standby — Enable in LSPosed Manager"
                    )
                    InfoSummaryRow(
                        "Root Bind-Mount",
                        if (dashboardState.isRootAvailable) "Root Available" else "User-space hooks & encrypted volumes active"
                    )
                    InfoSummaryRow("App Version", "3.1.0 (Production)")

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.runDiagnosticsSelfTest { report ->
                                Toast.makeText(context, report, Toast.LENGTH_LONG).show()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary.copy(alpha = 0.16f),
                            contentColor = colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Run Cryptographic & Rule Sync Self-Test",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = JakartaFontFamily
                        )
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
                Text("Configure Security PINs", fontFamily = FrauncesFontFamily, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = primaryInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) primaryInput = it },
                        label = { Text("Primary 4-digit PIN (Required)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = panicInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) panicInput = it },
                        label = { Text("Optional Decoy 4-digit PIN") },
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
                        if (primaryInput.length == 4) {
                            if (panicInput.isNotBlank() && (panicInput.length != 4 || panicInput == primaryInput)) {
                                Toast.makeText(context, "Decoy PIN must be 4 digits and different from Primary PIN", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.setMasterPin(primaryInput)
                            if (panicInput.length == 4) {
                                viewModel.setPanicDecoyPin(panicInput)
                            }
                            showChangePinDialog = false
                            Toast.makeText(context, "Vault PIN saved (PBKDF2 600k)", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Enter a 4-digit Primary PIN", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save PINs")
                }
            },
            dismissButton = {
                Row {
                    if (securitySettings.isPinEnabled) {
                        TextButton(
                            onClick = {
                                viewModel.disableMasterPin()
                                showChangePinDialog = false
                                Toast.makeText(context, "PIN lock disabled", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Disable PIN", color = colorScheme.error)
                        }
                    }
                    TextButton(onClick = { showChangePinDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
private fun VolumeKeyRow(
    title: String,
    packageName: String,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            BrandSquircleIcon(
                name = title.removeSuffix(" Volume"),
                size = 38.dp,
                packageName = packageName
            )
            Spacer(modifier = Modifier.width(12.dp))
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
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = JakartaFontFamily,
            color = colorScheme.onSurface,
            modifier = Modifier.weight(0.58f)
        )
    }
}
