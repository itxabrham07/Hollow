package com.example.isolation.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.mount.MountManager
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentLavender
import com.example.ui.theme.AccentMint
import com.example.ui.theme.AccentSky
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkBorderSubtle
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSubtle
import com.example.ui.theme.TextWhite
import java.io.File

@Composable
fun SettingsScreen(
    viewModel: IsoVaultViewModel,
    onTriggerLock: () -> Unit = {}
) {
    val context = LocalContext.current
    val isLsposedActive by viewModel.isLsposedActive.collectAsState()
    val isRootGranted by viewModel.isRootGranted.collectAsState()
    val autoForceStop by viewModel.autoForceStopOnRuleChange.collectAsState()
    val autoRootBindMount by viewModel.autoRootBindMount.collectAsState()
    val primaryPin by viewModel.primaryPin.collectAsState()
    val panicPin by viewModel.panicPin.collectAsState()
    val isPanicModeActive by viewModel.isPanicModeActive.collectAsState()

    var primaryInput by remember(primaryPin) { mutableStateOf(primaryPin) }
    var panicInput by remember(panicPin) { mutableStateOf(panicPin) }
    var masterKeyInput by remember { mutableStateOf(AesVaultCipher.activeMasterPassphrase) }
    var showMagiskScriptDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 20.dp)
            .testTag("settings_screen"),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Column {
                Text(
                    text = "Settings",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily,
                    color = TextWhite
                )
                Text(
                    text = "Root access, kernel bind-mounts, security PINs & storage",
                    fontSize = 12.sp,
                    fontFamily = JakartaFontFamily,
                    color = TextMuted
                )
            }
        }

        // Panic Mode Active Warning Banner
        if (isPanicModeActive) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AccentCoral.copy(alpha = 0.15f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentCoral),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = AccentCoral, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Decoy / Panic Mode Active",
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Real vault albums are currently masked.",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                        Button(
                            onClick = { viewModel.exitPanicMode() },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCoral),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Restore", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 1. Root Access (`su`) & Kernel Namespace Engine Card
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AccentMint.copy(alpha = 0.16f))
                            ) {
                                Icon(Icons.Default.Terminal, null, tint = AccentMint, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Root & Kernel Engine",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = JakartaFontFamily,
                                    color = TextWhite
                                )
                                Text(
                                    text = if (isRootGranted) "Superuser (su) UID=0 Active" else "Root shell not yet granted",
                                    fontSize = 12.sp,
                                    color = if (isRootGranted) AccentMint else AccentAmber
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.requestRootAccess() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRootGranted) DarkSurfaceElevated else AccentSky
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = if (isRootGranted) "Re-Verify su" else "Grant Root",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRootGranted) AccentMint else Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = DarkBorderSubtle)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Toggle 1: Auto Force-Stop App via Root
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto Force-Stop Target App",
                                color = TextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Runs `su -c am force-stop <pkg>` immediately when toggling isolation",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = autoForceStop,
                            onCheckedChange = { viewModel.setAutoForceStop(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentSky,
                                uncheckedTrackColor = DarkSurfaceElevated
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Toggle 2: Kernel Bind Mounts (`nsenter`)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Kernel Namespace Bind-Mount",
                                color = TextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Executes `nsenter -t <pid> -m -- mount --bind` alongside LSPosed hooks",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = autoRootBindMount,
                            onCheckedChange = { viewModel.setAutoRootBindMount(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentSky,
                                uncheckedTrackColor = DarkSurfaceElevated
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Export Magisk / KernelSU Boot Script
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showMagiskScriptDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.Code, null, tint = AccentSky, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Magisk service.sh", fontSize = 12.sp, color = TextWhite, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                MountManager.ensurePersistentVaultRootPermissions()
                                Toast.makeText(
                                    context,
                                    "Applied persistent vault permissions on /.IsoVault",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.FolderSpecial, null, tint = AccentMint, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Fix Vault Perms", fontSize = 12.sp, color = TextWhite, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // 2. Vault Passcode & Panic Duress PIN Card
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AccentSky.copy(alpha = 0.16f))
                            ) {
                                Icon(Icons.Default.Lock, null, tint = AccentSky, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Passcode & Panic Duress PIN",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = JakartaFontFamily,
                                    color = TextWhite
                                )
                                Text(
                                    text = "Panic PIN opens an empty decoy vault under duress",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        Button(
                            onClick = onTriggerLock,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentSky),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("lock_vault_now_button")
                        ) {
                            Text("Lock Now", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Primary PIN (4 digits)", color = TextMuted, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = primaryInput,
                                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) primaryInput = it },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentSky,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Panic PIN (4 digits)", color = AccentCoral, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = panicInput,
                                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) panicInput = it },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentCoral,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (primaryInput.length == 4 && panicInput.length == 4 && primaryInput != panicInput) {
                                viewModel.updatePins(primaryInput, panicInput)
                            } else {
                                Toast.makeText(
                                    context,
                                    "Enter two distinct 4-digit PINs",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .border(1.dp, AccentSky.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    ) {
                        Text("Save Security PINs", color = AccentSky, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // 3. AES-256 Encryption Passphrase Card
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentLavender.copy(alpha = 0.16f))
                        ) {
                            Icon(Icons.Default.Key, null, tint = AccentLavender, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "AES-256-GCM Master Key",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = JakartaFontFamily,
                                color = TextWhite
                            )
                            Text(
                                text = "PBKDF2 key derivation for `.isovault` encrypted files",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = masterKeyInput,
                        onValueChange = { masterKeyInput = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentLavender,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (masterKeyInput.isNotBlank()) {
                                AesVaultCipher.activeMasterPassphrase = masterKeyInput.trim()
                                Toast.makeText(context, "AES-256 Master Key Updated", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentLavender),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) {
                        Text("Update Master Passphrase", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // 4. Persistent Storage & System Status Card
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentAmber.copy(alpha = 0.16f))
                        ) {
                            Icon(Icons.Default.Security, null, tint = AccentAmber, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Uninstall-Safe Vault Storage",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = JakartaFontFamily,
                                color = TextWhite
                            )
                            Text(
                                text = "Data persists across app updates or uninstall",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    SettingStatusRow("Persistent Root Path", MountManager.PERSISTENT_VAULT_ROOT)
                    SettingStatusRow("LSPosed Zygisk Hook", if (isLsposedActive) "Hooked & Active" else "Enable in LSPosed Manager")
                    SettingStatusRow("MediaScanner Shield", ".nomedia enforced")

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = { viewModel.clearAuditLogs() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, tint = AccentCoral, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Interception Audit Logs", color = AccentCoral, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (showMagiskScriptDialog) {
        val script = viewModel.generateMagiskExport()
        AlertDialog(
            onDismissRequest = { showMagiskScriptDialog = false },
            title = {
                Text("Magisk / KernelSU service.sh", color = TextWhite, fontWeight = FontWeight.Bold)
            },
            text = {
                Surface(
                    color = DarkBackground,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    Text(
                        text = script,
                        color = AccentMint,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val out = File("/storage/emulated/0/Download/isovault_service.sh")
                            out.parentFile?.mkdirs()
                            out.writeText(script)
                            Toast.makeText(context, "Saved to ${out.absolutePath}", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        showMagiskScriptDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentSky)
                ) {
                    Text("Export to /Download", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMagiskScriptDialog = false }) {
                    Text("Close", color = TextMuted)
                }
            },
            containerColor = DarkSurfaceElevated,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun SettingStatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextMuted, fontSize = 12.sp)
        Text(
            text = value,
            color = TextWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}
