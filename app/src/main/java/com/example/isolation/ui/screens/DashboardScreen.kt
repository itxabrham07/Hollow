package com.example.isolation.ui.screens

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.isolation.ui.components.AuditActionBadge
import com.example.isolation.ui.components.ContentCard
import com.example.isolation.ui.components.CyberStatCard
import com.example.isolation.ui.components.ListRow
import com.example.isolation.ui.components.ModuleDownloadNotificationBanner
import com.example.isolation.ui.components.PrimaryButton
import com.example.isolation.ui.components.RootVaultStatusCard
import com.example.isolation.ui.components.SecondaryButton
import com.example.isolation.ui.components.SectionHeader
import com.example.isolation.ui.components.StatusIndicator
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.M3AmberAccent

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

    androidx.compose.runtime.LaunchedEffect(pendingDownloadTitles.size) {
        if (pendingDownloadTitles.isNotEmpty()) {
            viewModel.downloadAllAuthorizedModules()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        // 1. Hero Protection Summary Card (single card treatment, large shape, flat surface, 1dp hairline border)
        item {
            ContentCard(
                onClick = { viewModel.refreshDiagnostics() },
                shape = MaterialTheme.shapes.large,
                borderColor = if (isLsposedActive) scheme.secondary else scheme.outlineVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lsposed_status_banner")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(scheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = if (isLsposedActive) Icons.Default.CheckCircle else Icons.Default.Shield,
                                contentDescription = null,
                                tint = scheme.primary
                            )
                        }
                        StatusIndicator(
                            label = if (isLsposedActive) "System Protection Active" else "Hollow Privacy Layer Ready",
                            color = if (isLsposedActive) scheme.secondary else scheme.primary
                        )
                    }

                    StatusIndicator(
                        label = "${mountedIds.size} Active Modules",
                        color = scheme.onSurfaceVariant,
                        showDot = false
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "The Privacy Layer Between Your Apps & You",
                    style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FrauncesFontFamily),
                    color = scheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "OS-level workspace sandboxing, per-app identity protection, and modular privacy controls.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )

                if ("module_privacy" in mountedIds || "module_isolation" in mountedIds || "module_hider" in mountedIds) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = scheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if ("module_privacy" in mountedIds) {
                            ContentCard(
                                onClick = { onOpenModule("module_privacy") },
                                containerColor = scheme.surfaceContainerLow,
                                contentPadding = PaddingValues(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "$spoofedCount Apps",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = scheme.onSurface,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Privacy Shield",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if ("module_isolation" in mountedIds) {
                            ContentCard(
                                onClick = { onOpenModule("module_isolation") },
                                containerColor = scheme.surfaceContainerLow,
                                contentPadding = PaddingValues(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "$isolatedCount Apps",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = scheme.onSurface,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Storage Isolated",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if ("module_hider" in mountedIds) {
                            ContentCard(
                                onClick = { onOpenModule("module_hider") },
                                containerColor = scheme.surfaceContainerLow,
                                contentPadding = PaddingValues(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "$hiddenCount Apps",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = scheme.onSurface,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "App Visibility",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Cloud Account & Module Hub Card
        item {
            RootVaultStatusCard(
                devFullBuild = settings.devFullBuildMode,
                hardwareFingerprint = viewModel.hardwareBinding.fingerprintHex,
                activeModulesCount = mountedIds.size,
                totalModulesCount = totalCatalogSize,
                remainingDays = clockCheck.remainingDays,
                onOpenLicenseHub = onNavigateToLicenseHub
            )
        }

        // 3. Dynamic Module Download Notification Banner
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

        // 4. Sync / Install Modules Prompt (when 0 modules active)
        if (mountedModules.isEmpty()) {
            item {
                ContentCard(
                    borderColor = scheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_zero_modules_shell")
                ) {
                    ListRow(
                        title = "Sync Your Authorized Modules",
                        subtitle = "Fetch and install your approved privacy modules from your account.",
                        leadingIcon = Icons.Default.AutoAwesome,
                        tintLeadingIcon = true,
                        modifier = Modifier.padding(vertical = 0.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PrimaryButton(
                            text = "Sync & Install",
                            icon = Icons.Default.CloudDownload,
                            onClick = {
                                viewModel.syncLicenseAndModulesFromServer()
                                viewModel.downloadAllAuthorizedModules()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_open_module_downloader")
                        )
                        SecondaryButton(
                            text = "Account Status",
                            icon = Icons.Default.VerifiedUser,
                            onClick = onNavigateToLicenseHub,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 5. Privacy & Protection Feature Modules (Single Indigo Accent + Semantic Status)
        val activeOrAuthorizedStates = moduleStates.filter { it.isReadyToUse || it.isAuthorizedByAdmin }
        if (activeOrAuthorizedStates.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = "Privacy & Protection Features",
                        trailing = {
                            Text(
                                text = "Manage Modules",
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.primary,
                                modifier = Modifier
                                    .clickable { onNavigateToLicenseHub() }
                                    .testTag("btn_open_license_hub")
                            )
                        }
                    )

                    val moduleRows = activeOrAuthorizedStates.chunked(2)
                    moduleRows.forEach { rowStates ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowStates.forEach { state ->
                                val mod = state.module
                                val isMounted = state.isReadyToUse
                                val statusText = when {
                                    isMounted -> mod.resolveCardBadge(viewModel)
                                    else -> "Install"
                                }
                                val statusColor = when {
                                    isMounted -> scheme.secondary
                                    else -> M3AmberAccent
                                }

                                ContentCard(
                                    onClick = {
                                        if (isMounted) {
                                            onOpenModule(mod.id)
                                        } else {
                                            viewModel.downloadModuleIntoRootVault(mod.id)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag(mod.cardTestTag)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(MaterialTheme.shapes.small)
                                                .background(scheme.surfaceVariant)
                                        ) {
                                            Icon(
                                                imageVector = mod.icon,
                                                contentDescription = null,
                                                tint = scheme.primary
                                            )
                                        }
                                        StatusIndicator(
                                            label = statusText,
                                            color = statusColor
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = mod.cardTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = scheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isMounted) mod.resolveCardSubtitle(viewModel) else "Tap to install module",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (rowStates.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // 6. Files, Gallery & Safe Folder Section — ONLY shown when Vault Storage module (module_isolation) is mounted
        if ("module_isolation" in mountedIds) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "Files, Gallery & Safe Folder")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CyberStatCard(
                            title = "Browse Files",
                            value = "Workspace",
                            subtitle = "Folders & ZIP",
                            icon = Icons.Default.Folder,
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
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigateToGallery() }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CyberStatCard(
                            title = "Safe Folder",
                            value = formattedEncrypted,
                            subtitle = "Encrypted Vault",
                            icon = Icons.Default.Lock,
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
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigateToAudit() },
                            testTag = "nav_audit"
                        )
                    }
                }
            }
        }

        // 7. Recent Activity Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(
                    title = "Recent Activity",
                    modifier = Modifier.testTag("stat_interceptions"),
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.refreshDiagnostics() },
                                modifier = Modifier.testTag("btn_resync")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = scheme.onSurfaceVariant
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onNavigateToAudit() }
                            ) {
                                Text(
                                    text = "See all",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = scheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = scheme.primary
                                )
                            }
                        }
                    }
                )

                ContentCard(modifier = Modifier.fillMaxWidth()) {
                    if (recentLogs.isEmpty()) {
                        ListRow(
                            title = "Everything looks great",
                            subtitle = "App privacy and storage activity will show up here as you use your phone.",
                            leadingIcon = Icons.Default.CheckCircle,
                            modifier = Modifier.padding(vertical = 0.dp)
                        )
                    } else {
                        val previewLogs = recentLogs.take(4)
                        previewLogs.forEachIndexed { index, log ->
                            ListRow(
                                title = log.appName,
                                subtitle = friendlyPathSummary(log.requestedPath),
                                leadingIcon = Icons.Default.History,
                                onClick = onNavigateToAudit,
                                trailing = {
                                    AuditActionBadge(action = log.action)
                                }
                            )
                            if (index < previewLogs.lastIndex) {
                                HorizontalDivider(color = scheme.outlineVariant)
                            }
                        }
                    }
                }
            }
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
