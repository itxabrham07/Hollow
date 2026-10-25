package com.example.isolation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.model.IsolationMode
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.pkg.InstalledAppInfo
import com.example.isolation.ui.components.BrandSquircleIcon
import com.example.isolation.ui.components.formatBytes
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.AccentMint
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.JakartaFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    viewModel: IsoVaultViewModel,
    onTriggerLock: () -> Unit,
    onOpenAppVaultInFiles: (String) -> Unit
) {
    val configs by viewModel.configs.collectAsState()
    val installedApps by viewModel.filteredApps.collectAsState()
    val isLsposedActive by viewModel.isLsposedActive.collectAsState()
    val isPanicModeActive by viewModel.isPanicModeActive.collectAsState()
    val showOnboardingCard by viewModel.showOnboardingCard.collectAsState()
    val vaultRefreshTick by viewModel.vaultRefreshTick.collectAsState()

    var showAddAppsSheet by remember { mutableStateOf(false) }
    var showCreateVolumeDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val displayedConfigs = if (isPanicModeActive) emptyList() else configs
    val protectedCount = displayedConfigs.count { it.isIsolated }
    val sharedOptInCount = displayedConfigs.count { it.isIsolated && it.useSharedSpace }

    // Calculate real storage per app volume + Shared Space volume
    val storageMap = remember(displayedConfigs, vaultRefreshTick) {
        displayedConfigs.associate { cfg ->
            val diskBytes = try {
                val dir = viewModel.getAppSandboxDirectory(cfg.packageName)
                dir.walkTopDown().filter { it.isFile && it.name != ".nomedia" }.sumOf { it.length() }
            } catch (_: Exception) {
                0L
            }
            cfg.packageName to maxOf(diskBytes, cfg.totalBytesEncrypted)
        }
    }
    val sharedVolumeBytes = remember(vaultRefreshTick, isPanicModeActive) {
        if (isPanicModeActive) 0L
        else try {
            viewModel.getSharedVolumeDirectory()
                .walkTopDown()
                .filter { it.isFile && it.name != ".nomedia" }
                .sumOf { it.length() }
        } catch (_: Exception) {
            0L
        }
    }
    val totalPrivateBytes = storageMap.values.sum() + sharedVolumeBytes

    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .testTag("apps_screen")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Screen Title + Create Volume Quick Action
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Protected Apps",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FrauncesFontFamily,
                            color = colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Per-app encrypted volumes & optional shared space",
                            fontSize = 14.sp,
                            fontFamily = JakartaFontFamily,
                            color = colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = { showCreateVolumeDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        modifier = Modifier.testTag("create_volume_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CreateNewFolder,
                            contentDescription = "New Volume",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Volume",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = JakartaFontFamily,
                            color = colorScheme.onSurface
                        )
                    }
                }
            }

            // First-Run Onboarding & LSPosed Live Status Checklist
            if (showOnboardingCard || !isLsposedActive) {
                item {
                    FirstRunOnboardingCard(
                        isLsposedActive = isLsposedActive,
                        hasScopeSelected = protectedCount > 0,
                        hasVolumeCreated = displayedConfigs.isNotEmpty(),
                        onSimulateActiveClick = { viewModel.setSimulatedHookPreview(true) },
                        onCreateFirstVolume = { showCreateVolumeDialog = true },
                        onDismiss = { viewModel.dismissOnboarding() }
                    )
                }
            }

            // Top Status Card ("3 apps protected" + Shared Space summary + Big Lock Button)
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("top_status_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp)
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
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isLsposedActive) AccentMint else colorScheme.error)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isLsposedActive) {
                                            "LSPosed Module Active (API 82)"
                                        } else {
                                            "LSPosed Module Inactive — Enable in LSPosed Manager"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = JakartaFontFamily,
                                        color = if (isLsposedActive) AccentMint else colorScheme.error
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "$protectedCount ${if (protectedCount == 1) "app" else "apps"} protected",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FrauncesFontFamily,
                                    color = colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "Total encrypted volumes: ${formatBytes(totalPrivateBytes)}",
                                    fontSize = 13.sp,
                                    fontFamily = JakartaFontFamily,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Big Lock Button
                            Button(
                                onClick = onTriggerLock,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.primary,
                                    contentColor = colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                                modifier = Modifier
                                    .height(54.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("lock_vault_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = "Lock Vault",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Lock",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Shared Space Volume Quick Banner
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onOpenAppVaultInFiles(IsolationRuleManager.SHARED_VOLUME_ID) }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.FolderShared,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Shared Space Volume • ${formatBytes(sharedVolumeBytes)}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = JakartaFontFamily,
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        text = "$sharedOptInCount ${if (sharedOptInCount == 1) "app linked" else "apps linked"} to Shared Space",
                                        fontSize = 11.sp,
                                        fontFamily = JakartaFontFamily,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = "Open Shared",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = JakartaFontFamily,
                                color = colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Protected App Cards or Smooth Empty State
            if (displayedConfigs.isEmpty()) {
                item {
                    EmptyAppsState(onAddClick = { showAddAppsSheet = true })
                }
            } else {
                items(displayedConfigs, key = { it.packageName }) { config ->
                    val appBytes = storageMap[config.packageName] ?: 0L
                    ProtectedAppCard(
                        config = config,
                        privateStorageBytes = appBytes,
                        hooksActive = isLsposedActive && config.isIsolated,
                        onToggle = { enabled ->
                            viewModel.toggleAppIsolation(
                                InstalledAppInfo(
                                    packageName = config.packageName,
                                    appName = config.appName,
                                    icon = null,
                                    isSystemApp = false,
                                    targetSdkVersion = 34,
                                    isIsolated = enabled,
                                    isolationMode = config.isolationMode
                                ),
                                enable = enabled
                            )
                        },
                        onToggleSharedSpace = { useShared ->
                            viewModel.toggleAppSharedSpace(config.packageName, config.appName, useShared)
                        },
                        onClick = {
                            onOpenAppVaultInFiles(config.packageName)
                        }
                    )
                }
            }
        }

        // Floating "+" Button to add apps with zero path/folder questions
        FloatingActionButton(
            onClick = { showAddAppsSheet = true },
            shape = RoundedCornerShape(20.dp),
            containerColor = colorScheme.primary,
            contentColor = colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(60.dp)
                .testTag("add_app_fab")
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Add App to Vault",
                modifier = Modifier.size(28.dp)
            )
        }

        // Searchable Installed Apps Sheet (No folder or path questions)
        if (showAddAppsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddAppsSheet = false },
                sheetState = sheetState,
                containerColor = colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                AddInstalledAppSheetContent(
                    installedApps = installedApps,
                    configuredPackages = configs.associateBy { it.packageName },
                    onSelectApp = { appInfo ->
                        viewModel.toggleAppIsolation(appInfo, enable = true)
                    },
                    onDismiss = { showAddAppsSheet = false }
                )
            }
        }

        // Simple Create Volume Dialog (Name, Type: Per-App or Shared, Optional Quota MB)
        if (showCreateVolumeDialog) {
            CreateVolumeDialog(
                installedApps = installedApps,
                onDismiss = { showCreateVolumeDialog = false },
                onCreateVolume = { name, pkg, isShared, quotaMb, linkShared ->
                    viewModel.createOrUpdateVolume(
                        appName = name,
                        packageName = pkg,
                        isSharedVolume = isShared,
                        quotaMb = quotaMb,
                        useSharedSpace = linkShared
                    )
                    viewModel.dismissOnboarding()
                    showCreateVolumeDialog = false
                }
            )
        }
    }
}

@Composable
private fun FirstRunOnboardingCard(
    isLsposedActive: Boolean,
    hasScopeSelected: Boolean,
    hasVolumeCreated: Boolean,
    onSimulateActiveClick: () -> Unit,
    onCreateFirstVolume: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, if (isLsposedActive) colorScheme.outlineVariant else colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_checklist_card")
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isLsposedActive) Icons.Rounded.Shield else Icons.Rounded.Info,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "How Vault-LSPosed Works",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FrauncesFontFamily,
                        color = colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss Onboarding",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Each protected app gets its own encrypted volume (AES256_GCM_HKDF_4KB) and cannot see your real phone storage or other apps' files. Enable \"Use shared space\" on any app to let it share a common encrypted folder with other opted-in apps.",
                fontSize = 13.sp,
                fontFamily = JakartaFontFamily,
                color = colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3-step live status checklist
            ChecklistRow(
                checked = isLsposedActive,
                title = "1. LSPosed module active",
                subtitle = if (isLsposedActive) {
                    "Hooks attached (File, Os/IoBridge, MediaStore, Picker)"
                } else {
                    "Open LSPosed Manager → Modules → Enable IsoVault & reboot"
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ChecklistRow(
                checked = hasScopeSelected,
                title = "2. App scope selected",
                subtitle = "Select target apps in LSPosed and toggle them on below"
            )
            Spacer(modifier = Modifier.height(8.dp))
            ChecklistRow(
                checked = hasVolumeCreated,
                title = "3. Encrypted volume initialized",
                subtitle = "Standard folders (DCIM, Pictures, Download, Movies, Music, Documents) ready"
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCreateFirstVolume,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("onboarding_create_volume_button")
                ) {
                    Icon(Icons.Rounded.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Volume", fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily)
                }

                if (!isLsposedActive) {
                    OutlinedButton(
                        onClick = onSimulateActiveClick,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, colorScheme.outline),
                        modifier = Modifier.height(46.dp)
                    ) {
                        Text(
                            text = "Verify Hooks",
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = JakartaFontFamily,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistRow(
    checked: Boolean,
    title: String,
    subtitle: String
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (checked) AccentMint else colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
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
                text = subtitle,
                fontSize = 12.sp,
                fontFamily = JakartaFontFamily,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProtectedAppCard(
    config: AppIsolationConfig,
    privateStorageBytes: Long,
    hooksActive: Boolean,
    onToggle: (Boolean) -> Unit,
    onToggleSharedSpace: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val quotaText = if (config.volumeQuotaMb > 0) {
        " / ${config.volumeQuotaMb} MB quota"
    } else {
        ""
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("app_card_${config.packageName}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandSquircleIcon(
                    name = config.appName,
                    size = 48.dp
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = config.appName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = JakartaFontFamily,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Small status dot showing hooks are active
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (config.isIsolated && hooksActive) AccentMint
                                    else if (config.isIsolated) colorScheme.primary
                                    else colorScheme.outline
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (config.isIsolated) {
                            "Private volume: ${formatBytes(privateStorageBytes)}$quotaText"
                        } else {
                            "Paused • Private volume: ${formatBytes(privateStorageBytes)}$quotaText"
                        },
                        fontSize = 13.sp,
                        fontFamily = JakartaFontFamily,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Switch(
                    checked = config.isIsolated,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colorScheme.primary,
                        uncheckedThumbColor = colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = colorScheme.surface
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_${config.packageName}")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(10.dp))

            // Hook Status Indicator Pills per app (File, Os/IoBridge, MediaStore, Picker, Shared)
            val activeHooks = buildList {
                add("File")
                add("Os/IoBridge")
                add("MediaStore")
                add("Picker")
                if (config.useSharedSpace) add("Shared Space")
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (config.isIsolated) "Active hooks:" else "Hooks paused:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = JakartaFontFamily,
                    color = colorScheme.onSurfaceVariant
                )
                activeHooks.forEach { hookLabel ->
                    Surface(
                        color = if (config.isIsolated) {
                            colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            colorScheme.surface
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = hookLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = JakartaFontFamily,
                            color = if (config.isIsolated) colorScheme.primary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // "Use shared space" toggle per protected app (off by default)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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
                                "Sees its own volume + Shared Space in gallery & files"
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
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colorScheme.primary,
                        uncheckedThumbColor = colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = colorScheme.surface
                    ),
                    modifier = Modifier.testTag("shared_toggle_${config.packageName}")
                )
            }
        }
    }
}

@Composable
private fun CreateVolumeDialog(
    installedApps: List<InstalledAppInfo>,
    onDismiss: () -> Unit,
    onCreateVolume: (name: String, pkg: String, isShared: Boolean, quotaMb: Int, linkShared: Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var isSharedType by remember { mutableStateOf(false) }
    var volumeName by remember { mutableStateOf("") }
    var quotaMbInput by remember { mutableStateOf("500") }
    var linkToShared by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = colorScheme.surface,
        title = {
            Text(
                text = "Create Encrypted Volume",
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Each volume gets its own 256-bit key and default folders (DCIM, Pictures, Download, Movies, Music, Documents).",
                    fontSize = 12.sp,
                    fontFamily = JakartaFontFamily,
                    color = colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isSharedType,
                        onClick = { isSharedType = false },
                        label = { Text("Per-App Volume", fontFamily = JakartaFontFamily) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colorScheme.primary.copy(alpha = 0.16f),
                            selectedLabelColor = colorScheme.primary
                        )
                    )
                    FilterChip(
                        selected = isSharedType,
                        onClick = { isSharedType = true },
                        label = { Text("Shared Space", fontFamily = JakartaFontFamily) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colorScheme.primary.copy(alpha = 0.16f),
                            selectedLabelColor = colorScheme.primary
                        )
                    )
                }

                if (!isSharedType) {
                    OutlinedTextField(
                        value = volumeName,
                        onValueChange = { volumeName = it },
                        label = { Text("App or Volume Name (e.g. Signal)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = quotaMbInput,
                    onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 6) quotaMbInput = it },
                    label = { Text("Optional Size Quota in MB (0 = unlimited)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isSharedType) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Also link to Shared Space",
                            fontSize = 13.sp,
                            fontFamily = JakartaFontFamily,
                            color = colorScheme.onSurface
                        )
                        Switch(
                            checked = linkToShared,
                            onCheckedChange = { linkToShared = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val quota = quotaMbInput.toIntOrNull() ?: 0
                    if (isSharedType) {
                        onCreateVolume(
                            "Shared Space",
                            IsolationRuleManager.SHARED_VOLUME_ID,
                            true,
                            quota,
                            true
                        )
                    } else {
                        val cleanName = volumeName.trim().ifEmpty { "Private App" }
                        val match = installedApps.find {
                            it.appName.equals(cleanName, ignoreCase = true) ||
                                it.packageName.equals(cleanName, ignoreCase = true)
                        }
                        val pkg = match?.packageName
                            ?: "com.vault.volume.${cleanName.lowercase().replace(Regex("[^a-z0-9]"), "")}"
                        onCreateVolume(
                            match?.appName ?: cleanName,
                            pkg,
                            false,
                            quota,
                            linkToShared
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Create Volume")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EmptyAppsState(onAddClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 40.dp)
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
                text = "No apps protected yet",
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FrauncesFontFamily,
                color = colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Tap the + button to choose apps whose photos and files should stay inside your encrypted vault.",
                fontSize = 14.sp,
                fontFamily = JakartaFontFamily,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                ),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Protect an App", fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily)
            }
        }
    }
}

@Composable
private fun AddInstalledAppSheetContent(
    installedApps: List<InstalledAppInfo>,
    configuredPackages: Map<String, AppIsolationConfig>,
    onSelectApp: (InstalledAppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }

    val catalog = remember(installedApps) {
        val defaults = listOf(
            InstalledAppInfo(packageName = "com.snapchat.android", appName = "Snapchat", icon = null, isSystemApp = false, targetSdkVersion = 34, isIsolated = false, isolationMode = IsolationMode.ENCRYPTED_VAULT),
            InstalledAppInfo(packageName = "com.whatsapp", appName = "WhatsApp", icon = null, isSystemApp = false, targetSdkVersion = 34, isIsolated = false, isolationMode = IsolationMode.ENCRYPTED_VAULT),
            InstalledAppInfo(packageName = "org.telegram.messenger", appName = "Telegram", icon = null, isSystemApp = false, targetSdkVersion = 34, isIsolated = false, isolationMode = IsolationMode.ENCRYPTED_VAULT),
            InstalledAppInfo(packageName = "com.instagram.android", appName = "Instagram", icon = null, isSystemApp = false, targetSdkVersion = 34, isIsolated = false, isolationMode = IsolationMode.ENCRYPTED_VAULT),
            InstalledAppInfo(packageName = "com.zhiliaoapp.musically", appName = "TikTok", icon = null, isSystemApp = false, targetSdkVersion = 34, isIsolated = false, isolationMode = IsolationMode.ENCRYPTED_VAULT),
            InstalledAppInfo(packageName = "com.discord", appName = "Discord", icon = null, isSystemApp = false, targetSdkVersion = 34, isIsolated = false, isolationMode = IsolationMode.ENCRYPTED_VAULT),
            InstalledAppInfo(packageName = "com.google.android.apps.photos", appName = "Google Photos", icon = null, isSystemApp = false, targetSdkVersion = 34, isIsolated = false, isolationMode = IsolationMode.ENCRYPTED_VAULT),
            InstalledAppInfo(packageName = "org.thoughtcrime.securesms", appName = "Signal", icon = null, isSystemApp = false, targetSdkVersion = 34, isIsolated = false, isolationMode = IsolationMode.ENCRYPTED_VAULT)
        )
        val merged = (defaults + installedApps).distinctBy { it.packageName }
        merged.sortedBy { it.appName.lowercase() }
    }

    val filtered = catalog.filter {
        query.isBlank() ||
                it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(540.dp)
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Add App to Vault",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FrauncesFontFamily,
                    color = colorScheme.onSurface
                )
                Text(
                    text = "Tap any app to create its encrypted volume automatically",
                    fontSize = 13.sp,
                    fontFamily = JakartaFontFamily,
                    color = colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = {
                Text("Search installed apps...", fontFamily = JakartaFontFamily, fontSize = 14.sp)
            },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = colorScheme.onSurfaceVariant)
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorScheme.primary,
                unfocusedBorderColor = colorScheme.outline,
                focusedContainerColor = colorScheme.surfaceVariant,
                unfocusedContainerColor = colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_installed_apps_input")
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(filtered, key = { it.packageName }) { appItem ->
                val isAlreadyProtected = configuredPackages[appItem.packageName]?.isIsolated == true
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            onSelectApp(appItem)
                            onDismiss()
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BrandSquircleIcon(name = appItem.appName, size = 42.dp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = appItem.appName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = JakartaFontFamily,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = appItem.packageName,
                                fontSize = 12.sp,
                                fontFamily = JakartaFontFamily,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isAlreadyProtected) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(AccentMint.copy(alpha = 0.16f))
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Protected",
                                    tint = AccentMint,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colorScheme.primary.copy(alpha = 0.14f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Add",
                                    color = colorScheme.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
