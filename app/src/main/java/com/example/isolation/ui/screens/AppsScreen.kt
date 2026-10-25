package com.example.isolation.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.data.repository.InstalledAppInfo
import com.example.isolation.ui.components.BrandSquircleIcon
import com.example.isolation.ui.components.formatBytes
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.JakartaFontFamily
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppsScreen(
    viewModel: IsoVaultViewModel,
    onRequestSetupPin: () -> Unit = {}
) {
    val context = LocalContext.current
    val configs by viewModel.allConfigs.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val securitySettings by viewModel.securitySettings.collectAsStateWithLifecycle()
    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val refreshTick by viewModel.vaultRefreshTick.collectAsStateWithLifecycle()

    val isDecoyMode = securitySettings.isPanicDecoyActive
    val visibleConfigs = if (isDecoyMode) emptyList() else configs
    val protectedCount = visibleConfigs.count { it.isIsolated }

    var showAddAppSheet by remember { mutableStateOf(false) }
    var selectedHookDetailsApp by remember { mutableStateOf<AppIsolationConfig?>(null) }
    var showLsposedInstructionsDialog by remember { mutableStateOf(false) }
    var selfTestBannerMessage by remember { mutableStateOf<String?>(null) }

    val sandboxBaseDir = remember { viewModel.getSandboxBaseDir() }
    val sharedSpaceDir = remember { viewModel.getSharedSpaceDir() }

    val storagePerApp = remember(visibleConfigs, refreshTick) {
        visibleConfigs.associate { cfg ->
            val appDir = File(sandboxBaseDir, cfg.packageName)
            val actualBytes = if (appDir.exists()) {
                appDir.walkTopDown()
                    .filter { it.isFile && it.name != ".nomedia" && !it.name.startsWith(".seeded") }
                    .sumOf { it.length() }
            } else {
                0L
            }
            cfg.packageName to actualBytes
        }
    }

    val sharedSpaceBytes = remember(refreshTick) {
        if (sharedSpaceDir.exists()) {
            sharedSpaceDir.walkTopDown()
                .filter { it.isFile && it.name != ".nomedia" && !it.name.startsWith(".seeded") }
                .sumOf { it.length() }
        } else {
            0L
        }
    }

    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        containerColor = colorScheme.background,
        floatingActionButton = {
            if (!isDecoyMode) {
                FloatingActionButton(
                    onClick = {
                        viewModel.refreshInstalledApps()
                        showAddAppSheet = true
                    },
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.testTag("add_app_fab")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add App to Vault",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideScreen = maxWidth >= 600.dp
            val horizontalPad = if (isWideScreen) 24.dp else 16.dp

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPad),
                contentPadding = PaddingValues(top = 14.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column {
                        Text(
                            text = "IsoVault",
                            fontSize = if (isWideScreen) 30.sp else 26.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FrauncesFontFamily,
                            color = colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Per-app encrypted volumes & LSPosed storage isolation",
                            fontSize = 13.sp,
                            fontFamily = JakartaFontFamily,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                // LSPosed Module Status Card (Real status + Self-Test + Open LSPosed Manager)
                item {
                    LsposedModuleStatusBanner(
                        isActive = dashboardState.isLsposedActive,
                        onShowInstructions = { showLsposedInstructionsDialog = true },
                        onRunSelfTest = {
                            viewModel.runDiagnosticsSelfTest { report ->
                                selfTestBannerMessage = report
                                Toast.makeText(context, report, Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }

                if (selfTestBannerMessage != null) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = colorScheme.primary.copy(alpha = 0.12f)
                            ),
                            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VerifiedUser,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = selfTestBannerMessage!!,
                                    fontSize = 12.sp,
                                    fontFamily = JakartaFontFamily,
                                    color = colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { selfTestBannerMessage = null },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Dismiss",
                                        tint = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Top Status Card ("X apps protected" + Shared Space status + Big Lock button)
                item {
                    VaultStatusHeroCard(
                        protectedCount = if (isDecoyMode) 0 else protectedCount,
                        sharedSpaceBytes = if (isDecoyMode) 0L else sharedSpaceBytes,
                        sharedOptInCount = if (isDecoyMode) 0 else visibleConfigs.count { it.isIsolated && it.useSharedSpace },
                        isPinEnabled = securitySettings.isPinEnabled,
                        onLockClick = {
                            if (securitySettings.isPinEnabled) {
                                viewModel.lockVaultNow()
                            } else {
                                onRequestSetupPin()
                            }
                        },
                        onAddAppClick = {
                            viewModel.refreshInstalledApps()
                            showAddAppSheet = true
                        }
                    )
                }

                if (visibleConfigs.isEmpty()) {
                    item {
                        FriendlyAppsEmptyState(
                            isDecoy = isDecoyMode,
                            onAddAppClick = {
                                viewModel.refreshInstalledApps()
                                showAddAppSheet = true
                            }
                        )
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Protected App Volumes (${visibleConfigs.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = JakartaFontFamily,
                                color = colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = {
                                    viewModel.refreshInstalledApps()
                                    showAddAppSheet = true
                                }
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add App", fontSize = 13.sp, fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    items(visibleConfigs, key = { it.packageName }) { config ->
                        val usedBytes = storagePerApp[config.packageName] ?: 0L
                        ProtectedAppCard(
                            config = config,
                            storageUsedBytes = usedBytes,
                            isLsposedModuleReady = dashboardState.isLsposedActive,
                            onToggleIsolation = { enabled ->
                                viewModel.toggleAppIsolation(config, enabled)
                            },
                            onToggleSharedSpace = { useShared ->
                                viewModel.toggleAppSharedSpace(config, useShared)
                            },
                            onShowHookDetails = {
                                selectedHookDetailsApp = config
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddAppSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddAppSheet = false },
            containerColor = colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            AddInstalledAppSheetContent(
                installedApps = installedApps,
                existingPackages = configs.map { it.packageName }.toSet(),
                onRefreshApps = { viewModel.refreshInstalledApps() },
                onSelectApp = { app, useShared ->
                    viewModel.addInstalledAppToVault(app, useSharedSpace = useShared)
                    showAddAppSheet = false
                    Toast.makeText(
                        context,
                        "Created encrypted volume for ${app.appName}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    selectedHookDetailsApp?.let { appConfig ->
        HookStatusDetailsDialog(
            config = appConfig,
            isLsposedModuleReady = dashboardState.isLsposedActive,
            onDismiss = { selectedHookDetailsApp = null }
        )
    }

    if (showLsposedInstructionsDialog) {
        LsposedActivationGuideDialog(
            isActive = dashboardState.isLsposedActive,
            onRunSelfTest = {
                showLsposedInstructionsDialog = false
                viewModel.runDiagnosticsSelfTest { report ->
                    selfTestBannerMessage = report
                    Toast.makeText(context, report, Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = { showLsposedInstructionsDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LsposedModuleStatusBanner(
    isActive: Boolean,
    onShowInstructions: () -> Unit,
    onRunSelfTest: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val statusColor = if (isActive) Color(0xFF10B981) else Color(0xFFF59E0B)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) statusColor.copy(alpha = 0.35f) else statusColor.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onShowInstructions() }
            .testTag("lsposed_status_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.14f))
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isActive) "LSPosed Hook Engine Active" else "Vault Encryption Active • LSPosed Standby",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = JakartaFontFamily,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isActive) {
                            "File, IoBridge, MediaStore & Picker hooks are live for isolated apps"
                        } else {
                            "Encrypted file manager & volumes work natively. Enable in LSPosed Manager to hook external apps."
                        },
                        fontSize = 12.sp,
                        fontFamily = JakartaFontFamily,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onRunSelfTest,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                    modifier = Modifier.testTag("run_selftest_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Run Self-Test",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = JakartaFontFamily,
                        color = colorScheme.primary
                    )
                }

                OutlinedButton(
                    onClick = {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage("org.lsposed.manager")
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        } else {
                            onShowInstructions()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                    modifier = Modifier.testTag("open_lsposed_guide_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LSPosed Setup Guide",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = JakartaFontFamily,
                        color = colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VaultStatusHeroCard(
    protectedCount: Int,
    sharedSpaceBytes: Long,
    sharedOptInCount: Int,
    isPinEnabled: Boolean,
    onLockClick: () -> Unit,
    onAddAppClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("apps_status_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (protectedCount > 0) Color(0xFF10B981) else colorScheme.onSurfaceVariant
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (protectedCount > 0) "VAULT ACTIVE" else "READY TO PROTECT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JakartaFontFamily,
                            color = if (protectedCount > 0) Color(0xFF10B981) else colorScheme.onSurfaceVariant,
                            letterSpacing = 0.8.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "$protectedCount app${if (protectedCount == 1) "" else "s"} protected",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FrauncesFontFamily,
                        color = colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isPinEnabled) {
                            "Per-app AES-256-GCM volumes active"
                        } else {
                            "Tap Lock to set your 4-digit PIN"
                        },
                        fontSize = 13.sp,
                        fontFamily = JakartaFontFamily,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onLockClick,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                    modifier = Modifier.testTag("lock_vault_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Lock Vault",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Lock",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JakartaFontFamily
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.FolderShared,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Shared Space: ${formatBytes(sharedSpaceBytes)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = JakartaFontFamily,
                        color = colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$sharedOptInCount app${if (sharedOptInCount == 1) "" else "s"} linked",
                        fontSize = 12.sp,
                        fontFamily = JakartaFontFamily,
                        color = colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Surface(
                        color = colorScheme.primary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onAddAppClick)
                    ) {
                        Text(
                            text = "+ Protect App",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = JakartaFontFamily,
                            color = colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtectedAppCard(
    config: AppIsolationConfig,
    storageUsedBytes: Long,
    isLsposedModuleReady: Boolean,
    onToggleIsolation: (Boolean) -> Unit,
    onToggleSharedSpace: (Boolean) -> Unit,
    onShowHookDetails: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val hooksActive = config.isIsolated && isLsposedModuleReady
    val dotColor = when {
        hooksActive -> Color(0xFF10B981)
        config.isIsolated -> Color(0xFFF59E0B)
        else -> colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        border = BorderStroke(
            width = 1.dp,
            color = if (config.isIsolated) colorScheme.primary.copy(alpha = 0.28f) else colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_card_${config.packageName}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandSquircleIcon(
                    name = config.appName,
                    size = 48.dp,
                    packageName = config.packageName
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = config.appName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = JakartaFontFamily,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Hook status dot (clickable for hook details)
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                                .clickable { onShowHookDetails() }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val quotaText = if (config.volumeQuotaMb > 0) " / ${config.volumeQuotaMb} MB" else ""
                    Text(
                        text = "Private volume: ${formatBytes(storageUsedBytes)}$quotaText",
                        fontSize = 13.sp,
                        fontFamily = JakartaFontFamily,
                        color = colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onShowHookDetails() }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Hook details",
                            tint = dotColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when {
                                hooksActive -> "5/5 hooks active (File, IoBridge, MediaStore, Picker, Shared)"
                                config.isIsolated -> "Volume encrypted • Tap to view hook status"
                                else -> "Protection paused"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = JakartaFontFamily,
                            color = dotColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Switch(
                    checked = config.isIsolated,
                    onCheckedChange = onToggleIsolation,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colorScheme.onPrimary,
                        checkedTrackColor = colorScheme.primary,
                        uncheckedThumbColor = colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = colorScheme.surface
                    ),
                    modifier = Modifier.testTag("toggle_${config.packageName}")
                )
            }

            if (config.isIsolated) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderShared,
                            contentDescription = null,
                            tint = if (config.useSharedSpace) colorScheme.primary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Use shared space",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = JakartaFontFamily,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = if (config.useSharedSpace) {
                                    "Sees its own volume + Shared Space media"
                                } else {
                                    "Strictly isolated to its own private volume"
                                },
                                fontSize = 11.sp,
                                fontFamily = JakartaFontFamily,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = config.useSharedSpace,
                        onCheckedChange = onToggleSharedSpace,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colorScheme.onPrimary,
                            checkedTrackColor = colorScheme.primary,
                            uncheckedThumbColor = colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = colorScheme.surface
                        ),
                        modifier = Modifier.testTag("shared_space_toggle_${config.packageName}")
                    )
                }
            }
        }
    }
}

@Composable
private fun HookStatusDetailsDialog(
    config: AppIsolationConfig,
    isLsposedModuleReady: Boolean,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val hooks = listOf(
        Triple("File.list / exists / Redirect", "Redirects /storage/emulated/0 paths into private volume", true),
        Triple("Libcore Os & IoBridge.open", "Low-level file descriptor open() redirection + Shared folder link", true),
        Triple("MediaStore Query / Insert / Delete", "Filters gallery cursors to private volume" + if (config.useSharedSpace) " + Shared Space" else " only", true),
        Triple("Photo Picker & Document Intents", "Scopes ACTION_PICK & GET_CONTENT to isolated storage", true),
        Triple("Per-Volume AES256_GCM_HKDF_4KB", "256-bit DEK wrapped by Android Keystore Master Key", true)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandSquircleIcon(
                    name = config.appName,
                    size = 36.dp,
                    packageName = config.packageName
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "${config.appName} Hooks",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FrauncesFontFamily,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = config.packageName,
                        fontSize = 11.sp,
                        fontFamily = JakartaFontFamily,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = if (isLsposedModuleReady && config.isIsolated) {
                        Color(0xFF10B981).copy(alpha = 0.14f)
                    } else {
                        Color(0xFFF59E0B).copy(alpha = 0.14f)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isLsposedModuleReady && config.isIsolated) {
                            "All 5 hook layers active and wrapped in fail-safe try/catch guards."
                        } else if (!config.isIsolated) {
                            "Protection toggle is currently OFF for this app."
                        } else {
                            "Rules are configured and encrypted volume is ready. Enable IsoVault in LSPosed Manager to enforce runtime hooks on external apps."
                        },
                        fontSize = 12.sp,
                        fontFamily = JakartaFontFamily,
                        color = colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                hooks.forEach { (title, desc, enabled) ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = if (enabled && config.isIsolated) colorScheme.primary else colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = JakartaFontFamily,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = desc,
                                fontSize = 11.sp,
                                fontFamily = JakartaFontFamily,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Done", fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun LsposedActivationGuideDialog(
    isActive: Boolean,
    onRunSelfTest: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = colorScheme.surface,
        title = {
            Text(
                text = if (isActive) "LSPosed Module Active" else "Enable LSPosed Module",
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "To isolate external apps (like WhatsApp, Snapchat, or Camera) at runtime:",
                    fontSize = 13.sp,
                    fontFamily = JakartaFontFamily,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    text = "1. Open LSPosed Manager on your device.\n" +
                        "2. Enable the \"IsoVault\" module.\n" +
                        "3. Check the target apps you want to isolate in the module scope.\n" +
                        "4. Force-stop the target app once so hooks attach on launch.",
                    fontSize = 13.sp,
                    fontFamily = JakartaFontFamily,
                    color = colorScheme.onSurface
                )
                Text(
                    text = "Fail-Closed Guarantee: If the Vault provider is unreachable, isolated apps see an empty private directory and never your real storage.",
                    fontSize = 12.sp,
                    fontFamily = JakartaFontFamily,
                    color = colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onRunSelfTest,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Run Live Self-Test", fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun FriendlyAppsEmptyState(
    isDecoy: Boolean,
    onAddAppClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 36.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primary.copy(alpha = 0.12f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isDecoy) "No protected apps" else "Protect your first installed app",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FrauncesFontFamily,
                color = colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isDecoy) {
                    "Your vault has no isolated apps configured yet."
                } else {
                    "Select any installed app on your phone to create its dedicated AES-256-GCM encrypted volume with automatic DCIM, Pictures, and Download folders."
                },
                fontSize = 14.sp,
                fontFamily = JakartaFontFamily,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (!isDecoy) {
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onAddAppClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Choose Installed App", fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AddInstalledAppSheetContent(
    installedApps: List<InstalledAppInfo>,
    existingPackages: Set<String>,
    onRefreshApps: () -> Unit,
    onSelectApp: (InstalledAppInfo, Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var useSharedSpaceDefault by remember { mutableStateOf(false) }
    var includeSystemApps by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (installedApps.isEmpty()) {
            onRefreshApps()
        }
    }

    val filteredApps = remember(installedApps, existingPackages, query, includeSystemApps) {
        val available = installedApps.filter { app ->
            !existingPackages.contains(app.packageName) &&
                (includeSystemApps || !app.isSystemApp || installedApps.none { !it.isSystemApp })
        }
        if (query.isBlank()) {
            available
        } else {
            val q = query.trim().lowercase()
            available.filter {
                it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 380.dp, max = 600.dp)
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Protect an Installed App",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FrauncesFontFamily,
            color = colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Creates an encrypted volume with DCIM, Pictures, Download, Movies, Music & Documents automatically.",
            fontSize = 13.sp,
            fontFamily = JakartaFontFamily,
            color = colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter chips: User Apps vs All Apps (including System)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = !includeSystemApps,
                onClick = { includeSystemApps = false },
                leadingIcon = {
                    Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = {
                    Text("User Apps", fontFamily = JakartaFontFamily, fontSize = 12.sp)
                },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colorScheme.primary.copy(alpha = 0.18f),
                    selectedLabelColor = colorScheme.primary
                )
            )
            FilterChip(
                selected = includeSystemApps,
                onClick = { includeSystemApps = true },
                label = {
                    Text("All / System Apps", fontFamily = JakartaFontFamily, fontSize = 12.sp)
                },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colorScheme.primary.copy(alpha = 0.18f),
                    selectedLabelColor = colorScheme.primary
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Option to link to Shared Space on creation
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderShared,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Use shared space",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = JakartaFontFamily,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "Allow app to also access Shared Space media",
                            fontSize = 11.sp,
                            fontFamily = JakartaFontFamily,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = useSharedSpaceDefault,
                    onCheckedChange = { useSharedSpaceDefault = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colorScheme.onPrimary,
                        checkedTrackColor = colorScheme.primary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search installed apps...", fontFamily = JakartaFontFamily) },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = colorScheme.onSurfaceVariant)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colorScheme.surfaceVariant,
                unfocusedContainerColor = colorScheme.surfaceVariant,
                focusedBorderColor = colorScheme.primary,
                unfocusedBorderColor = colorScheme.outlineVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add_app_search_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredApps.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if ( !includeSystemApps ) "No matching user apps found" else "No matching apps found",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = JakartaFontFamily,
                        color = colorScheme.onSurface
                    )
                    if (!includeSystemApps) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(onClick = { includeSystemApps = true }) {
                            Text("Show All / System Apps", fontFamily = JakartaFontFamily)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onSelectApp(app, useSharedSpaceDefault) }
                            .testTag("select_app_${app.packageName}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BrandSquircleIcon(
                                name = app.appName,
                                size = 42.dp,
                                packageName = app.packageName,
                                appDrawable = app.icon
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = JakartaFontFamily,
                                    color = colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = app.packageName,
                                    fontSize = 12.sp,
                                    fontFamily = JakartaFontFamily,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add",
                                tint = colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
