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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.core.module.DynamicModuleState
import com.example.isolation.ui.components.AuditActionBadge
import com.example.isolation.ui.components.CyberStatCard
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.isolation.uikit.components.ModuleDownloadNotificationBanner
import com.example.isolation.uikit.components.RootVaultStatusCard
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.M3AmberAccent
import com.example.ui.theme.M3IndigoAccent
import com.example.ui.theme.M3RoseAccent
import com.example.ui.theme.M3SkyAccent
import com.example.ui.theme.M3TealAccent
import com.example.ui.theme.M3VioletAccent

@Composable
fun DashboardScreen(
    viewModel: IsoVaultViewModel,
    onNavigateToApps: () -> Unit,
    onNavigateToAppHider: () -> Unit = {},
    onNavigateToDeviceSpoofer: () -> Unit = {},
    onNavigateToVault: () -> Unit,
    onNavigateToFileManager: () -> Unit = onNavigateToVault,
    onNavigateToGallery: () -> Unit = onNavigateToVault,
    onNavigateToTerminal: () -> Unit,
    onNavigateToAudit: () -> Unit,
    onNavigateToVpnProxy: () -> Unit = {},
    onNavigateToLicenseHub: () -> Unit = {},
    onOpenModule: (String) -> Unit = { modId ->
        when (modId) {
            "module_isolation" -> onNavigateToApps()
            "module_privacy" -> onNavigateToDeviceSpoofer()
            "module_hider" -> onNavigateToAppHider()
            "module_network" -> onNavigateToVpnProxy()
            else -> onNavigateToLicenseHub()
        }
    }
) {
    val scheme = MaterialTheme.colorScheme
    val isLsposedActive by viewModel.isLsposedActive.collectAsState()
    val recentLogs by viewModel.recentAuditLogs.collectAsState()
    val totalBytesEncrypted by viewModel.totalEncryptedBytes.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val moduleStates by viewModel.moduleStates.collectAsState()
    val clockCheck by viewModel.clockVerification.collectAsState()
    val isolatedCount by viewModel.isolatedCount.collectAsState()
    val spoofedCount by viewModel.identitySpoofedCount.collectAsState()
    val hiddenCount by viewModel.hiddenOrDisguisedCount.collectAsState()

    val formattedEncrypted = formatBytes(totalBytesEncrypted)

    val mountedModules = moduleStates.filter { it.isReadyToUse }.map { it.module }
    val mountedIds = mountedModules.map { it.id }.toSet()
    val totalCatalogSize = moduleStates.size.coerceAtLeast(4)
    val pendingDownloadTitles = moduleStates
        .filter { it.isAuthorizedByAdmin && !it.isDownloadedInRootVault && !settings.devFullBuildMode }
        .map { it.module.displayName }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Compact Executive Hollow Security Card
        item {
            Spacer(modifier = Modifier.height(2.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lsposed_status_banner")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
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
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(scheme.primaryContainer)
                            ) {
                                Icon(
                                    imageVector = if (isLsposedActive) Icons.Default.VerifiedUser else Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Hollow Security Suite",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurface,
                                    fontFamily = JakartaFontFamily
                                )
                                Text(
                                    text = if (isLsposedActive) "LSPosed Kernel Hooks Active" else "Framework Ready • Standard Sandbox",
                                    fontSize = 11.sp,
                                    color = if (isLsposedActive) Color(0xFF10B981) else scheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.refreshDiagnostics() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Status",
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = scheme.surfaceContainerHigh.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenModule("module_isolation") }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = M3TealAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(text = "$isolatedCount", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                                    Text(text = "Isolated", fontSize = 10.sp, color = scheme.onSurfaceVariant)
                                }
                            }
                        }

                        Surface(
                            color = scheme.surfaceContainerHigh.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenModule("module_privacy") }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = M3IndigoAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(text = "$spoofedCount", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                                    Text(text = "Spoofed", fontSize = 10.sp, color = scheme.onSurfaceVariant)
                                }
                            }
                        }

                        Surface(
                            color = scheme.surfaceContainerHigh.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenModule("module_hider") }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = M3VioletAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(text = "$hiddenCount", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                                    Text(text = "Hidden", fontSize = 10.sp, color = scheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dynamic Module Download Notification Banner (if any pending)
        if (pendingDownloadTitles.isNotEmpty()) {
            item {
                ModuleDownloadNotificationBanner(
                    pendingDownloadCount = pendingDownloadTitles.size,
                    pendingModuleNames = pendingDownloadTitles.joinToString(", "),
                    onDownloadAllClick = { viewModel.downloadAllAuthorizedModules() },
                    onOpenModuleHubClick = onNavigateToLicenseHub
                )
            }
        }

        // Section Header: Feature Modules
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, start = 2.dp)
            ) {
                Text(
                    text = "Privacy & Protection Features",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    fontFamily = JakartaFontFamily
                )
                Text(
                    text = "Manage Modules",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                    fontFamily = JakartaFontFamily,
                    modifier = Modifier
                        .clickable { onNavigateToLicenseHub() }
                        .testTag("btn_open_license_hub")
                )
            }
        }

        // Always show all available modules in a Colorful Material 3 Bento Grid!
        val displayStates = moduleStates
        val moduleRows = displayStates.chunked(2)
        moduleRows.forEachIndexed { rowIdx, rowStates ->
            item(key = rowStates.joinToString("_") { it.module.id }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowStates.forEachIndexed { colIdx, state ->
                        val mod = state.module
                        val paletteColor = modulePaletteColor(mod.id, rowIdx * 2 + colIdx)
                        val isMounted = state.isReadyToUse
                        val badge = when {
                            isMounted -> mod.resolveCardBadge(viewModel)
                            state.isAuthorizedByAdmin -> "Tap to Install"
                            else -> "Tap to Unlock"
                        }
                        ColorfulM3ModuleCard(
                            title = mod.cardTitle,
                            subtitle = mod.resolveCardSubtitle(viewModel),
                            badgeText = badge,
                            icon = mod.icon,
                            accentColor = paletteColor,
                            isReady = isMounted,
                            onClick = {
                                if (isMounted) {
                                    onOpenModule(mod.id)
                                } else if (state.isAuthorizedByAdmin) {
                                    viewModel.downloadModuleIntoRootVault(mod.id)
                                } else {
                                    onNavigateToLicenseHub()
                                }
                            },
                            testTag = mod.cardTestTag,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowStates.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Section Label: Workspace & Files
        item {
            Text(
                text = "Files, Gallery & Safe Folder",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                fontFamily = JakartaFontFamily,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp)
            )
        }

        // Row 1 of Storage: Browse Files & Photos
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CyberStatCard(
                    title = "Browse Files",
                    value = "Workspace",
                    subtitle = "Folders & ZIP",
                    icon = Icons.Default.Folder,
                    accentColor = M3IndigoAccent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToFileManager() },
                    testTag = "stat_selinux"
                )
                CyberStatCard(
                    title = "Photos Gallery",
                    value = "Albums",
                    subtitle = "Private Media",
                    icon = Icons.Default.PhotoLibrary,
                    accentColor = M3TealAccent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToGallery() }
                )
            }
        }

        // Row 2 of Storage: Safe Folder & Activity Log
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CyberStatCard(
                    title = "Safe Folder",
                    value = formattedEncrypted,
                    subtitle = "Encrypted Vault",
                    icon = Icons.Default.Lock,
                    accentColor = M3RoseAccent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToVault() },
                    testTag = "stat_vault_size"
                )
                CyberStatCard(
                    title = "Activity Log",
                    value = "${recentLogs.size}",
                    subtitle = "Recent Events",
                    icon = Icons.Default.History,
                    accentColor = M3AmberAccent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToAudit() },
                    testTag = "nav_audit"
                )
            }
        }

        // Recent Activity Header + Grouped Container
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .testTag("stat_interceptions")
            ) {
                Text(
                    text = "Recent Activity",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily,
                    color = scheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.refreshDiagnostics() },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_resync")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onNavigateToAudit() }
                    ) {
                        Text(
                            text = "See all",
                            fontSize = 12.sp,
                            color = scheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = JakartaFontFamily
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (recentLogs.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(scheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = scheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Everything looks great",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = JakartaFontFamily,
                                color = scheme.onSurface
                            )
                            Text(
                                text = "App privacy and storage activity will show up here as you use your phone.",
                                color = scheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontFamily = JakartaFontFamily
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val previewLogs = recentLogs.take(4)
                        previewLogs.forEachIndexed { index, log ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToAudit() }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(scheme.primaryContainer)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = scheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = log.appName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = scheme.onSurface,
                                            fontFamily = JakartaFontFamily,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        AuditActionBadge(action = log.action)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = friendlyPathSummary(log.requestedPath),
                                        fontSize = 12.sp,
                                        color = scheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontFamily = JakartaFontFamily
                                    )
                                }
                            }
                            if (index < previewLogs.lastIndex) {
                                HorizontalDivider(
                                    color = scheme.outlineVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ColorfulHomeHeroCard(
    isLsposedActive: Boolean,
    isolatedCount: Int,
    spoofedCount: Int,
    hiddenCount: Int,
    mountedCount: Int,
    totalModules: Int,
    onRefresh: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenStorage: () -> Unit
) {
    val heroBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF4F46E5), // Vibrant Indigo
            Color(0xFF7C3AED), // Rich Violet
            Color(0xFF0D9488)  // Electric Teal
        )
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(heroBrush)
            .clickable { onRefresh() }
            .testTag("lsposed_status_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.20f),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (isLsposedActive) Icons.Default.CheckCircle else Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isLsposedActive) "System Protection Active" else "Hollow Privacy Suite Ready",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JakartaFontFamily
                        )
                    }
                }

                Surface(
                    color = Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "$mountedCount/$totalModules Modules",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = JakartaFontFamily,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Your Personal Privacy & App Workspace",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = JakartaFontFamily
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Isolate app folders, customize per-app device identity, and keep private photos safe in one place.",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontFamily = JakartaFontFamily
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3 Colorful Pill Metrics inside the Hero
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeroStatPill(
                    value = "$spoofedCount Apps",
                    label = "Privacy Shield",
                    onClick = onOpenPrivacy,
                    modifier = Modifier.weight(1f)
                )
                HeroStatPill(
                    value = "$isolatedCount Apps",
                    label = "Storage Isolated",
                    onClick = onOpenStorage,
                    modifier = Modifier.weight(1f)
                )
                HeroStatPill(
                    value = "$hiddenCount Apps",
                    label = "Hidden / Disguised",
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HeroStatPill(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = 0.16f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = value,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = JakartaFontFamily,
                maxLines = 1
            )
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = JakartaFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun modulePaletteColor(moduleId: String, index: Int): Color {
    return when (moduleId) {
        "module_isolation" -> M3TealAccent
        "module_privacy" -> M3IndigoAccent
        "module_hider" -> M3VioletAccent
        "module_network" -> M3SkyAccent
        "module_sensor_firewall" -> M3RoseAccent
        else -> when (index % 5) {
            0 -> M3IndigoAccent
            1 -> M3TealAccent
            2 -> M3VioletAccent
            3 -> M3AmberAccent
            else -> M3RoseAccent
        }
    }
}

@Composable
private fun ColorfulM3ModuleCard(
    title: String,
    subtitle: String,
    badgeText: String,
    icon: ImageVector,
    accentColor: Color,
    isReady: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.28f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accentColor.copy(alpha = 0.15f),
                            accentColor.copy(alpha = 0.04f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentColor)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Surface(
                    color = accentColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            fontFamily = JakartaFontFamily,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = JakartaFontFamily
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = if (isReady) subtitle else "Tap to activate module",
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = JakartaFontFamily
            )
        }
    }
}

fun friendlyPathSummary(path: String): String {
    return when {
        path.startsWith("content://media/external/images") -> "Checked your Photo Gallery"
        path.startsWith("content://media/external/video") -> "Checked your Video Gallery"
        path.startsWith("content://media") -> "Checked your Media Library"
        path.contains("/DCIM/Camera", ignoreCase = true) -> "Camera Roll • ${path.substringAfterLast('/')}"
        path.contains("/DCIM", ignoreCase = true) -> "Photos Folder • ${path.substringAfterLast('/')}"
        path.contains("/Pictures", ignoreCase = true) -> "Pictures • ${path.substringAfterLast('/')}"
        path.contains("/Download", ignoreCase = true) -> "Downloads • ${path.substringAfterLast('/')}"
        path.contains("/WhatsApp", ignoreCase = true) -> "WhatsApp Media • ${path.substringAfterLast('/')}"
        else -> path.removePrefix("/storage/emulated/0/").ifBlank { "Phone Storage" }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    if (digitGroups >= units.size) digitGroups = units.size - 1
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return "%.1f %s".format(value, units[digitGroups])
}
