package com.example.isolation.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.core.module.DynamicModuleState
import com.example.isolation.core.root.LicenseAccountStatus
import com.example.isolation.core.root.RootLicenseVaultManager
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.JakartaFontFamily

@Composable
fun OnboardingAuthScreen(
    viewModel: IsoVaultViewModel,
    isStandaloneGate: Boolean = false,
    onBack: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val settings by viewModel.settings.collectAsState()
    val vaultRecord by viewModel.vaultLicenseRecord.collectAsState()
    val moduleStates by viewModel.moduleStates.collectAsState()
    val clockCheck by viewModel.clockVerification.collectAsState()
    val hw = viewModel.hardwareBinding

    var usernameInput by remember(vaultRecord.username) {
        mutableStateOf(vaultRecord.username.ifBlank { "operator_01" })
    }
    var emailInput by remember(vaultRecord.email) {
        mutableStateOf(vaultRecord.email.ifBlank { "operator@hollow.sec" })
    }
    var passwordInput by remember { mutableStateOf("Hollow#Root2026") }
    var serverUrlInput by remember(settings.cloudServerUrl) {
        mutableStateOf(settings.cloudServerUrl)
    }

    var adminGrantHider by remember { mutableStateOf(true) }
    var adminGrantIsolation by remember { mutableStateOf(true) }
    var adminGrantPrivacy by remember { mutableStateOf(true) }
    var adminGrantNetwork by remember { mutableStateOf(true) }

    if (!isStandaloneGate) {
        BackHandler { onBack() }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("onboarding_auth_screen"),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header bar
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isStandaloneGate) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("btn_back_from_license_hub")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = scheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Column {
                        Text(
                            text = if (isStandaloneGate) "Hollow Release Shell" else "Root Vault & Module Hub",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JakartaFontFamily,
                            color = scheme.onBackground
                        )
                        Text(
                            text = "AES-256-GCM Root Vault • Dynamic Module Engine",
                            fontSize = 11.sp,
                            color = scheme.onSurfaceVariant,
                            fontFamily = JakartaFontFamily
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            when (vaultRecord.status) {
                                LicenseAccountStatus.APPROVED -> Color(0xFF10B981).copy(alpha = 0.16f)
                                LicenseAccountStatus.PENDING_APPROVAL -> Color(0xFFF59E0B).copy(alpha = 0.16f)
                                LicenseAccountStatus.BANNED -> Color(0xFFEF4444).copy(alpha = 0.18f)
                                else -> scheme.surfaceContainerHigh
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = vaultRecord.status.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (vaultRecord.status) {
                            LicenseAccountStatus.APPROVED -> Color(0xFF10B981)
                            LicenseAccountStatus.PENDING_APPROVAL -> Color(0xFFF59E0B)
                            LicenseAccountStatus.BANNED -> Color(0xFFEF4444)
                            else -> scheme.onSurfaceVariant
                        },
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Build Flavor Switch: Developer Full Build vs Release Shell
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
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
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(scheme.primary.copy(alpha = 0.14f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeveloperMode,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (settings.devFullBuildMode) "Developer Full Build (All Modules)" else "Release Shell Mode (Zero-Module APK)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurface,
                                    fontFamily = JakartaFontFamily
                                )
                                Text(
                                    text = if (settings.devFullBuildMode) {
                                        "Switch OFF to test the Release Shell registration, waiting screen & dynamic server download."
                                    } else {
                                        "Zero modules bundled in APK. Features appear only after Admin approval & Root Vault download."
                                    },
                                    fontSize = 11.sp,
                                    color = scheme.onSurfaceVariant,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                        }
                        Switch(
                            checked = settings.devFullBuildMode,
                            onCheckedChange = { viewModel.setDevFullBuildMode(it) },
                            modifier = Modifier.testTag("switch_dev_full_build")
                        )
                    }
                }
            }
        }

        // Hardware Fingerprint & Root Secret Volume Telemetry Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ROOT SECRET VOLUME & ANTI-TAMPER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.7.sp,
                                color = scheme.primary,
                                fontFamily = JakartaFontFamily
                            )
                        }
                        Text(
                            text = if (clockCheck.isClockRolledBack) "CLOCK TAMPER!" else "MONOTONIC OK",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (clockCheck.isClockRolledBack) Color(0xFFEF4444) else Color(0xFF10B981),
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    VaultTelemetryRow("Secret Volume Path", RootLicenseVaultManager.ROOT_VAULT_DIR)
                    VaultTelemetryRow("Hardware Fingerprint", hw.fingerprintHex)
                    VaultTelemetryRow("SoC / Storage CID", "${hw.socSerial} • ${hw.storageCid.take(12)}")
                    VaultTelemetryRow(
                        "Anti-MITM Channel",
                        "Proxy.NO_PROXY + AES-256-GCM + HMAC-SHA256"
                    )
                    VaultTelemetryRow(
                        "Monotonic Clock Guard",
                        if (vaultRecord.validUntilMs > 0L) {
                            "Remaining: ${clockCheck.remainingDays} days"
                        } else {
                            "SystemClock.elapsedRealtime() watermark active"
                        }
                    )
                }
            }
        }

        // State-specific Panel: BANNED / UNREGISTERED / PENDING_APPROVAL / APPROVED
        when (vaultRecord.status) {
            LicenseAccountStatus.BANNED -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFEF4444).copy(alpha = 0.10f)
                        ),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_device_banned")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "DEVICE PERMANENTLY BANNED",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEF4444),
                                        fontFamily = JakartaFontFamily
                                    )
                                    Text(
                                        text = "Hardware ID ${hw.fingerprintHex} is blacklisted",
                                        fontSize = 11.sp,
                                        color = scheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = vaultRecord.banReason.ifBlank {
                                    "Administrator revoked this device. All encrypted modules in /data/adb/.hollow_vault/modules have been wiped."
                                },
                                fontSize = 12.sp,
                                color = scheme.onSurface,
                                fontFamily = JakartaFontFamily
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.simulateAdminResetToUnregistered() },
                                    modifier = Modifier.testTag("btn_unban_reset")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Restore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Admin Simulator: Unban & Reset", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            LicenseAccountStatus.UNREGISTERED, LicenseAccountStatus.REVOKED -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = scheme.surface),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, scheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_registration_form")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Getting Started • Register Device",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurface,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Register your account to bind this hardware fingerprint (${hw.fingerprintHex}) to /data/adb/.hollow_vault. Once submitted, your device enters Admin Approval Waiting Mode.",
                                fontSize = 11.sp,
                                color = scheme.onSurfaceVariant,
                                fontFamily = JakartaFontFamily
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = usernameInput,
                                onValueChange = { usernameInput = it },
                                label = { Text("Username", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_register_username")
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                label = { Text("Email Address", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_register_email")
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Password", fontSize = 12.sp) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_register_password")
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    viewModel.registerAccountWithCloud(
                                        username = usernameInput,
                                        email = emailInput,
                                        password = passwordInput
                                    )
                                },
                                enabled = usernameInput.isNotBlank() && emailInput.isNotBlank() && passwordInput.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_submit_registration")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Register & Enter Approval Waiting Mode",
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                        }
                    }
                }
            }

            LicenseAccountStatus.PENDING_APPROVAL -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF59E0B).copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_waiting_approval")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF59E0B).copy(alpha = 0.18f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.HourglassTop,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Waiting for Admin Approval",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.onSurface,
                                        fontFamily = JakartaFontFamily
                                    )
                                    Text(
                                        text = "${vaultRecord.username} (${vaultRecord.email})",
                                        fontSize = 12.sp,
                                        color = scheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Your registration and hardware fingerprint (${hw.fingerprintHex}) are stored in /data/adb/.hollow_vault/license.enc. Once the administrator approves your device and assigns modules on the Web Portal, you will receive a notification to download your authorized modules.",
                                fontSize = 12.sp,
                                color = scheme.onSurfaceVariant,
                                fontFamily = JakartaFontFamily
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { viewModel.syncLicenseAndModulesFromServer() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_poll_approval_status")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Check Approval Status", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.simulateAdminResetToUnregistered() },
                                    modifier = Modifier.testTag("btn_edit_registration")
                                ) {
                                    Text("Edit Account", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            LicenseAccountStatus.APPROVED -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = scheme.surface),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_approved_license")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Authorized Operator • ${vaultRecord.username}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = scheme.onSurface,
                                            fontFamily = JakartaFontFamily
                                        )
                                        Text(
                                            text = "Token bound to /data/adb/.hollow_vault/license.enc",
                                            fontSize = 11.sp,
                                            color = scheme.onSurfaceVariant,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.syncLicenseAndModulesFromServer() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Sync Heartbeat",
                                        tint = scheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.simulateClearAppDataAndRecoverFromRootVault() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_simulate_clear_data_recovery")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Restore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Test 'Clear App Data' Survival", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dynamic Modules List (Server -> /data/adb/.hollow_vault/modules/<id>.dex.enc)
        item {
            Text(
                text = "DYNAMIC SERVER MODULES (/data/adb/.hollow_vault/modules)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = scheme.onSurfaceVariant,
                fontFamily = JakartaFontFamily,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp)
            )
        }

        items(moduleStates, key = { it.module.id }) { state ->
            DynamicModuleVaultRow(
                state = state,
                devFullBuild = settings.devFullBuildMode,
                isAccountApproved = vaultRecord.status == LicenseAccountStatus.APPROVED,
                onDownloadClick = {
                    viewModel.downloadModuleIntoRootVault(state.module.id)
                }
            )
        }

        // Built-in Web Admin Portal Simulator & Cloud Endpoint Config
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CLOUD ADMIN PORTAL & INSTANT SIMULATOR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.7.sp,
                            color = scheme.primary,
                            fontFamily = JakartaFontFamily
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Connect to the Dockerized Node.js + MongoDB Atlas server (/web/server.js) or use the quick Admin controls below to approve modules or ban this device:",
                        fontSize = 11.sp,
                        color = scheme.onSurfaceVariant,
                        fontFamily = JakartaFontFamily
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = serverUrlInput,
                            onValueChange = { serverUrlInput = it },
                            label = { Text("Cloud Admin Server URL", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { viewModel.setCloudServerUrl(serverUrlInput) }
                        ) {
                            Text("Save URL", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Admin Module Entitlements to Grant:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        fontFamily = JakartaFontFamily
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = adminGrantHider,
                            onClick = { adminGrantHider = !adminGrantHider },
                            label = { Text("App Hider", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = adminGrantIsolation,
                            onClick = { adminGrantIsolation = !adminGrantIsolation },
                            label = { Text("Storage", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = adminGrantPrivacy,
                            onClick = { adminGrantPrivacy = !adminGrantPrivacy },
                            label = { Text("Privacy ID", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = adminGrantNetwork,
                            onClick = { adminGrantNetwork = !adminGrantNetwork },
                            label = { Text("Proxy/V2Ray", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                val granted = mutableSetOf<String>()
                                if (adminGrantHider) granted.add("module_hider")
                                if (adminGrantIsolation) granted.add("module_isolation")
                                if (adminGrantPrivacy) granted.add("module_privacy")
                                if (adminGrantNetwork) granted.add("module_network")
                                viewModel.simulateAdminApproveAccount(allowedModules = granted)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_admin_approve_device")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Admin: Approve Device", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { viewModel.simulateAdminBanDevice() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEF4444),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.testTag("btn_admin_ban_device")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Ban Device", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultTelemetryRow(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = scheme.onSurfaceVariant,
            fontFamily = JakartaFontFamily
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun DynamicModuleVaultRow(
    state: DynamicModuleState,
    devFullBuild: Boolean,
    isAccountApproved: Boolean,
    onDownloadClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val mod = state.module
    val accent = scheme.primary
    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (state.isReadyToUse) accent.copy(alpha = 0.45f) else scheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("module_vault_row_${mod.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(13.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = mod.icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = mod.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        fontFamily = JakartaFontFamily
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "v${mod.version} • ${mod.sizeKb} KB",
                        fontSize = 10.sp,
                        color = scheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = mod.summary,
                    fontSize = 11.sp,
                    color = scheme.onSurfaceVariant,
                    fontFamily = JakartaFontFamily
                )
                Text(
                    text = when {
                        devFullBuild -> "Unlocked via Developer Full Build"
                        state.isDownloadedInRootVault -> "Bound: /data/adb/.hollow_vault/modules/${mod.id}.dex.enc"
                        state.isAuthorizedByAdmin -> "Authorized by Admin • Ready to download"
                        else -> "Locked • Requires Admin entitlement"
                    },
                    fontSize = 10.sp,
                    color = if (state.isReadyToUse) Color(0xFF10B981) else scheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            when {
                state.isDownloadedInRootVault || devFullBuild -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF10B981).copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (state.isDownloadedInRootVault && !devFullBuild) "MOUNTED" else "DEV ACTIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                state.isAuthorizedByAdmin && isAccountApproved -> {
                    Button(
                        onClick = onDownloadClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_download_${mod.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Download", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(scheme.surfaceContainerHigh)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "LOCKED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
