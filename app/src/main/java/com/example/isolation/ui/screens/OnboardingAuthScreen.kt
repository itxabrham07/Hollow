package com.example.isolation.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.isolation.core.root.LicenseAccountStatus
import com.example.isolation.core.root.RootLicenseVaultManager
import com.example.isolation.ui.components.ContentCard
import com.example.isolation.ui.components.ListRow
import com.example.isolation.ui.components.MonoDataText
import com.example.isolation.ui.components.PrimaryButton
import com.example.isolation.ui.components.SecondaryButton
import com.example.isolation.ui.components.SectionHeader
import com.example.isolation.ui.components.StatusIndicator
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.M3AmberAccent

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
    var adminGrantSensorFirewall by remember { mutableStateOf(false) }

    if (!isStandaloneGate) {
        BackHandler { onBack() }
    }

    val statusColor = when (vaultRecord.status) {
        LicenseAccountStatus.APPROVED -> scheme.secondary
        LicenseAccountStatus.PENDING_APPROVAL -> M3AmberAccent
        LicenseAccountStatus.BANNED -> scheme.error
        else -> scheme.onSurfaceVariant
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("onboarding_auth_screen"),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header bar
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (!isStandaloneGate) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("btn_back_from_license_hub")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = scheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Column {
                        Text(
                            text = if (isStandaloneGate) "Hollow Release Shell" else "Root Vault & Module Hub",
                            style = MaterialTheme.typography.titleLarge,
                            color = scheme.onBackground
                        )
                        Text(
                            text = "AES-256-GCM Root Vault • Dynamic Module Engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))
                StatusIndicator(
                    label = vaultRecord.status.name,
                    color = statusColor
                )
            }
        }

        // Build Flavor Switch: Developer Full Build vs Release Shell (Interactive only in Dev APK)
        if (BuildConfig.DEBUG) {
            item {
                ContentCard(modifier = Modifier.fillMaxWidth()) {
                    ListRow(
                        title = if (settings.devFullBuildMode) "Developer Full Build (All Modules)" else "Release Shell Mode (Zero-Module APK)",
                        subtitle = if (settings.devFullBuildMode) {
                            "Switch OFF to test the Release Shell registration, waiting screen & dynamic server download."
                        } else {
                            "Zero modules bundled in APK. Features appear only after Admin approval & Root Vault download."
                        },
                        leadingIcon = Icons.Default.DeveloperMode,
                        tintLeadingIcon = true,
                        modifier = Modifier.padding(vertical = 0.dp),
                        trailing = {
                            Switch(
                                checked = settings.devFullBuildMode,
                                onCheckedChange = { viewModel.setDevFullBuildMode(it) },
                                modifier = Modifier.testTag("switch_dev_full_build")
                            )
                        }
                    )
                }
            }
        }

        // Hardware Fingerprint & Root Secret Volume Telemetry Card
        item {
            ContentCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader(
                    title = "Root Secret Volume & Anti-Tamper",
                    subtitle = "Hardware-bound storage & monotonic clock verification",
                    trailing = {
                        StatusIndicator(
                            label = if (clockCheck.isClockRolledBack) "Clock Tamper" else "Monotonic OK",
                            color = if (clockCheck.isClockRolledBack) scheme.error else scheme.secondary
                        )
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = scheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                ListRow(
                    title = "Secret Volume Path",
                    monoSubtitle = RootLicenseVaultManager.ROOT_VAULT_DIR,
                    leadingIcon = Icons.Default.Security
                )
                HorizontalDivider(color = scheme.outlineVariant)

                ListRow(
                    title = "Hardware Fingerprint",
                    monoSubtitle = hw.fingerprintHex
                )
                HorizontalDivider(color = scheme.outlineVariant)

                ListRow(
                    title = "SoC / Storage CID",
                    monoSubtitle = "${hw.socSerial} • ${hw.storageCid.take(12)}"
                )
                HorizontalDivider(color = scheme.outlineVariant)

                ListRow(
                    title = "Anti-MITM Channel",
                    monoSubtitle = "Proxy.NO_PROXY + AES-256-GCM + HMAC-SHA256"
                )
                HorizontalDivider(color = scheme.outlineVariant)

                ListRow(
                    title = "Monotonic Clock Guard",
                    monoSubtitle = if (vaultRecord.validUntilMs > 0L) {
                        "Remaining: ${clockCheck.remainingDays} days"
                    } else {
                        "SystemClock.elapsedRealtime() watermark active"
                    }
                )
            }
        }

        // State-specific Panel: BANNED / UNREGISTERED / PENDING_APPROVAL / APPROVED
        when (vaultRecord.status) {
            LicenseAccountStatus.BANNED -> {
                item {
                    ContentCard(
                        borderColor = scheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_device_banned")
                    ) {
                        ListRow(
                            title = "Device Permanently Banned",
                            subtitle = vaultRecord.banReason.ifBlank {
                                "Administrator revoked this device. All encrypted modules in /data/adb/.hollow_vault/modules have been wiped."
                            },
                            monoSubtitle = "Hardware ID ${hw.fingerprintHex} is blacklisted",
                            leadingIcon = Icons.Default.Block,
                            statusLabel = "Banned",
                            statusColor = scheme.error,
                            modifier = Modifier.padding(vertical = 0.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SecondaryButton(
                            text = "Admin Simulator: Unban & Reset",
                            icon = Icons.Default.Restore,
                            onClick = { viewModel.simulateAdminResetToUnregistered() },
                            modifier = Modifier.testTag("btn_unban_reset")
                        )
                    }
                }
            }

            LicenseAccountStatus.UNREGISTERED, LicenseAccountStatus.REVOKED -> {
                item {
                    ContentCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_registration_form")
                    ) {
                        ListRow(
                            title = "Getting Started • Register Device",
                            subtitle = "Register your account to bind this hardware fingerprint to /data/adb/.hollow_vault. Once submitted, your device enters Admin Approval Waiting Mode.",
                            monoSubtitle = hw.fingerprintHex,
                            leadingIcon = Icons.Default.PersonAdd,
                            tintLeadingIcon = true,
                            modifier = Modifier.padding(vertical = 0.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = { usernameInput = it },
                            label = { Text("Username", style = MaterialTheme.typography.bodySmall) },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            shape = MaterialTheme.shapes.extraSmall,
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_register_username")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("Email Address", style = MaterialTheme.typography.bodySmall) },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            shape = MaterialTheme.shapes.extraSmall,
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_register_email")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Password", style = MaterialTheme.typography.bodySmall) },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            shape = MaterialTheme.shapes.extraSmall,
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_register_password")
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        PrimaryButton(
                            text = "Register & Enter Approval Waiting Mode",
                            icon = Icons.Default.Lock,
                            enabled = usernameInput.isNotBlank() && emailInput.isNotBlank() && passwordInput.isNotBlank(),
                            onClick = {
                                viewModel.registerAccountWithCloud(
                                    username = usernameInput,
                                    email = emailInput,
                                    password = passwordInput
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_submit_registration")
                        )
                    }
                }
            }

            LicenseAccountStatus.PENDING_APPROVAL -> {
                item {
                    ContentCard(
                        borderColor = M3AmberAccent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_waiting_approval")
                    ) {
                        ListRow(
                            title = "Waiting for Admin Approval",
                            subtitle = "Your registration and hardware fingerprint (${hw.fingerprintHex}) are stored in /data/adb/.hollow_vault/license.enc.",
                            monoSubtitle = "${vaultRecord.username} (${vaultRecord.email})",
                            leadingIcon = Icons.Default.HourglassTop,
                            statusLabel = "Pending",
                            statusColor = M3AmberAccent,
                            modifier = Modifier.padding(vertical = 0.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PrimaryButton(
                                text = "Check Approval Status",
                                icon = Icons.Default.CloudSync,
                                onClick = { viewModel.syncLicenseAndModulesFromServer() },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_poll_approval_status")
                            )
                            SecondaryButton(
                                text = "Edit Account",
                                onClick = { viewModel.simulateAdminResetToUnregistered() },
                                modifier = Modifier.testTag("btn_edit_registration")
                            )
                        }
                    }
                }
            }

            LicenseAccountStatus.APPROVED -> {
                item {
                    ContentCard(
                        borderColor = scheme.secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_approved_license")
                    ) {
                        ListRow(
                            title = "Authorized Operator • ${vaultRecord.username}",
                            subtitle = "Active hardware-bound operator license",
                            monoSubtitle = "/data/adb/.hollow_vault/license.enc",
                            leadingIcon = Icons.Default.VerifiedUser,
                            statusLabel = "Approved",
                            statusColor = scheme.secondary,
                            modifier = Modifier.padding(vertical = 0.dp),
                            trailing = {
                                IconButton(
                                    onClick = { viewModel.syncLicenseAndModulesFromServer() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Sync Heartbeat",
                                        tint = scheme.primary
                                    )
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        SecondaryButton(
                            text = "Test 'Clear App Data' Survival",
                            icon = Icons.Default.Restore,
                            onClick = { viewModel.simulateClearAppDataAndRecoverFromRootVault() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_simulate_clear_data_recovery")
                        )
                    }
                }
            }
        }

        // Dynamic Modules List (Server -> /data/adb/.hollow_vault/modules/<id>.dex.enc)
        item {
            SectionHeader(
                title = "Dynamic Server Modules",
                subtitle = "/data/adb/.hollow_vault/modules"
            )
        }

        items(moduleStates, key = { it.module.id }) { state ->
            val mod = state.module
            val isAccountApproved = vaultRecord.status == LicenseAccountStatus.APPROVED
            val devFullBuild = settings.devFullBuildMode

            val monoDetail = when {
                devFullBuild -> "v${mod.version} • ${mod.sizeKb} KB • Unlocked via Developer Full Build"
                state.isDownloadedInRootVault -> "v${mod.version} • /data/adb/.hollow_vault/modules/${mod.id}.dex.enc"
                state.isAuthorizedByAdmin -> "v${mod.version} • ${mod.sizeKb} KB • Authorized by Admin"
                else -> "v${mod.version} • ${mod.sizeKb} KB • Requires Admin entitlement"
            }

            ContentCard(
                borderColor = if (state.isReadyToUse) scheme.secondary else scheme.outlineVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("module_vault_row_${mod.id}")
            ) {
                ListRow(
                    title = mod.displayName,
                    subtitle = mod.summary,
                    monoSubtitle = monoDetail,
                    leadingIcon = mod.icon,
                    modifier = Modifier.padding(vertical = 0.dp),
                    trailing = {
                        when {
                            state.isDownloadedInRootVault || devFullBuild -> {
                                StatusIndicator(
                                    label = if (state.isDownloadedInRootVault && !devFullBuild) "Mounted" else "Dev Active",
                                    color = scheme.secondary
                                )
                            }
                            state.isAuthorizedByAdmin && isAccountApproved -> {
                                PrimaryButton(
                                    text = "Download",
                                    icon = Icons.Default.CloudDownload,
                                    onClick = { viewModel.downloadModuleIntoRootVault(mod.id) },
                                    modifier = Modifier.testTag("btn_download_${mod.id}")
                                )
                            }
                            else -> {
                                StatusIndicator(
                                    label = "Locked",
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
            }
        }

        // Built-in Web Admin Portal Simulator & Cloud Endpoint Config
        item {
            ContentCard(modifier = Modifier.fillMaxWidth()) {
                ListRow(
                    title = "Cloud Admin Portal & Instant Simulator",
                    subtitle = "Connect to the Node.js + MongoDB Atlas server (/web/server.js) or use the quick Admin controls below:",
                    leadingIcon = Icons.Default.Key,
                    modifier = Modifier.padding(vertical = 0.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        label = { Text("Cloud Admin Server URL", style = MaterialTheme.typography.bodySmall) },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        shape = MaterialTheme.shapes.extraSmall,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "Save URL",
                        onClick = { viewModel.setCloudServerUrl(serverUrlInput) }
                    )
                }

                if (BuildConfig.DEBUG) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = scheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Admin Module Entitlements to Grant (Dev Simulator):",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = adminGrantHider,
                            onClick = { adminGrantHider = !adminGrantHider },
                            label = { Text("App Hider", style = MaterialTheme.typography.labelMedium) },
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = adminGrantIsolation,
                            onClick = { adminGrantIsolation = !adminGrantIsolation },
                            label = { Text("Storage", style = MaterialTheme.typography.labelMedium) },
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = adminGrantPrivacy,
                            onClick = { adminGrantPrivacy = !adminGrantPrivacy },
                            label = { Text("Privacy ID", style = MaterialTheme.typography.labelMedium) },
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = adminGrantNetwork,
                            onClick = { adminGrantNetwork = !adminGrantNetwork },
                            label = { Text("Proxy/V2Ray", style = MaterialTheme.typography.labelMedium) },
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = adminGrantSensorFirewall,
                            onClick = { adminGrantSensorFirewall = !adminGrantSensorFirewall },
                            label = {
                                Text(
                                    text = "Sensor Firewall (Zero-Touch /modules/ Plugin)",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chip_grant_sensor_firewall")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PrimaryButton(
                            text = "Admin: Approve Device",
                            icon = Icons.Default.CheckCircle,
                            onClick = {
                                val granted = mutableSetOf<String>()
                                if (adminGrantHider) granted.add("module_hider")
                                if (adminGrantIsolation) granted.add("module_isolation")
                                if (adminGrantPrivacy) granted.add("module_privacy")
                                if (adminGrantNetwork) granted.add("module_network")
                                if (adminGrantSensorFirewall) granted.add("module_sensor_firewall")
                                viewModel.simulateAdminApproveAccount(allowedModules = granted)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_admin_approve_device")
                        )

                        PrimaryButton(
                            text = "Ban Device",
                            icon = Icons.Default.Block,
                            isDestructive = true,
                            onClick = { viewModel.simulateAdminBanDevice() },
                            modifier = Modifier.testTag("btn_admin_ban_device")
                        )
                    }
                }
            }
        }
    }
}
