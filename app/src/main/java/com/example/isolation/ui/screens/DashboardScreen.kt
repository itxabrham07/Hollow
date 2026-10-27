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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.ui.components.AuditActionBadge
import com.example.isolation.ui.components.CyberStatCard
import com.example.isolation.ui.components.LsposedStatusBanner
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.isolation.uikit.components.ModuleDownloadNotificationBanner
import com.example.isolation.uikit.components.RootVaultStatusCard
import com.example.ui.theme.JakartaFontFamily

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
    onNavigateToLicenseHub: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val isLsposedActive by viewModel.isLsposedActive.collectAsState()
    val isolatedCount by viewModel.isolatedCount.collectAsState()
    val hiddenOrDisguisedCount by viewModel.hiddenOrDisguisedCount.collectAsState()
    val identitySpoofedCount by viewModel.identitySpoofedCount.collectAsState()
    val recentLogs by viewModel.recentAuditLogs.collectAsState()
    val totalBytesEncrypted by viewModel.totalEncryptedBytes.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val allApps by viewModel.allInstalledApps.collectAsState()
    val moduleStates by viewModel.moduleStates.collectAsState()
    val clockCheck by viewModel.clockVerification.collectAsState()

    val routedAppsCount = allApps.count { it.vpnProxyEnabled }
    val activeServer = settings.activeServer
    val isVpnOn = settings.vpnTunnelEnabled
    val formattedEncrypted = formatBytes(totalBytesEncrypted)

    val mountedIds = moduleStates.filter { it.isReadyToUse }.map { it.module.id }.toSet()
    val pendingDownloadTitles = moduleStates
        .filter { it.isAuthorizedByAdmin && !it.isDownloadedInRootVault && !settings.devFullBuildMode }
        .map { it.module.displayName }

    val isIsolationMounted = "module_isolation" in mountedIds
    val isPrivacyMounted = "module_privacy" in mountedIds
    val isHiderMounted = "module_hider" in mountedIds
    val isNetworkMounted = "module_network" in mountedIds

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(2.dp))
            LsposedStatusBanner(
                isActive = isLsposedActive,
                onToggle = { viewModel.refreshDiagnostics() }
            )
        }

        // Root Secret Volume & Cloud License Hub Banner
        item {
            RootVaultStatusCard(
                devFullBuild = settings.devFullBuildMode,
                hardwareFingerprint = viewModel.hardwareBinding.fingerprintHex,
                activeModulesCount = mountedIds.size,
                totalModulesCount = moduleStates.size.coerceAtLeast(4),
                remainingDays = clockCheck.remainingDays,
                onOpenLicenseHub = onNavigateToLicenseHub
            )
        }

        // Dynamic Module Download Notification Banner (appears when Admin grants modules that aren't downloaded yet)
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

        // Section Label: Core Protection Modules (Dynamic Bento Grid)
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, start = 2.dp)
            ) {
                Text(
                    text = "PROTECTION MODULES (${mountedIds.size}/4 MOUNTED)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = scheme.onSurfaceVariant,
                    fontFamily = JakartaFontFamily
                )
                Text(
                    text = if (settings.devFullBuildMode) "Dev Full Build • Manage" else "Release Shell • Module Hub",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                    fontFamily = JakartaFontFamily,
                    modifier = Modifier
                        .clickable { onNavigateToLicenseHub() }
                        .testTag("btn_open_license_hub")
                )
            }
        }

        if (mountedIds.isEmpty()) {
            // Zero-Module Release Shell Empty State
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = scheme.surface),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_zero_modules_shell")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Hollow Release Shell • No Modules Mounted",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            fontFamily = JakartaFontFamily
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "This release build ships with 0 bundled feature modules. Authorized modules are downloaded from the Cloud Admin Server and bound to /data/adb/.hollow_vault/modules so they survive 'Clear App Data'.",
                            fontSize = 11.sp,
                            color = scheme.onSurfaceVariant,
                            fontFamily = JakartaFontFamily
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onNavigateToLicenseHub,
                            modifier = Modifier.testTag("btn_open_module_downloader")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Cloud License & Module Hub", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            // Bento Row 1: Storage Isolate + Privacy & ID (rendered dynamically if mounted)
            if (isIsolationMounted || isPrivacyMounted) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isIsolationMounted) {
                            BentoModuleTile(
                                title = "Storage Isolate",
                                subtitle = if (isolatedCount == 0) "Sandbox app files & media" else "$isolatedCount app${if (isolatedCount == 1) "" else "s"} isolated",
                                badgeText = "$isolatedCount",
                                icon = Icons.Default.Apps,
                                accentColor = scheme.primary,
                                onClick = onNavigateToApps,
                                testTag = "stat_isolated_apps",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (isPrivacyMounted) {
                            BentoModuleTile(
                                title = "Privacy & ID",
                                subtitle = if (identitySpoofedCount == 0) "IMEI, Build, GPS & Shields" else "$identitySpoofedCount app${if (identitySpoofedCount == 1) "" else "s"} cloaked",
                                badgeText = "$identitySpoofedCount",
                                icon = Icons.Default.Fingerprint,
                                accentColor = scheme.tertiary,
                                onClick = onNavigateToDeviceSpoofer,
                                testTag = "card_device_identity_spoofer",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Bento Row 2: Hide App + Proxy & V2Ray (rendered dynamically if mounted)
            if (isHiderMounted || isNetworkMounted) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isHiderMounted) {
                            BentoModuleTile(
                                title = "Hide App",
                                subtitle = if (hiddenOrDisguisedCount == 0) "Launcher & SystemUI stealth" else "$hiddenOrDisguisedCount app${if (hiddenOrDisguisedCount == 1) "" else "s"} disguised",
                                badgeText = "$hiddenOrDisguisedCount",
                                icon = Icons.Default.VisibilityOff,
                                accentColor = scheme.secondary,
                                onClick = onNavigateToAppHider,
                                testTag = "card_hide_disguise_apps",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (isNetworkMounted) {
                            BentoModuleTile(
                                title = "Proxy & V2Ray",
                                subtitle = when {
                                    isVpnOn && routedAppsCount > 0 -> "[${activeServer.countryCode}] ${activeServer.exitIp} • $routedAppsCount apps"
                                    isVpnOn -> "[${activeServer.countryCode}] ${activeServer.exitIp} • Ready"
                                    else -> "Per-app & global routing"
                                },
                                badgeText = activeServer.protocol.badge,
                                icon = Icons.Default.Public,
                                accentColor = Color(0xFF0EA5E9),
                                onClick = onNavigateToVpnProxy,
                                testTag = "dashboard_vpn_card",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Section Label: Vault & Storage
        item {
            Text(
                text = "STORAGE & TELEMETRY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = scheme.onSurfaceVariant,
                fontFamily = JakartaFontFamily,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp)
            )
        }

        // Row 1 of Storage: Browse Files & Photos
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CyberStatCard(
                    title = "Browse Files",
                    value = "Workspace",
                    subtitle = "Folders & ZIP",
                    icon = Icons.Default.Folder,
                    accentColor = scheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToFileManager() },
                    testTag = "stat_selinux"
                )
                CyberStatCard(
                    title = "Photos",
                    value = "Albums",
                    subtitle = "Gallery",
                    icon = Icons.Default.PhotoLibrary,
                    accentColor = scheme.secondary,
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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CyberStatCard(
                    title = "Safe Folder",
                    value = formattedEncrypted,
                    subtitle = "AES-256 Locked",
                    icon = Icons.Default.Lock,
                    accentColor = scheme.tertiary,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToVault() },
                    testTag = "stat_vault_size"
                )
                CyberStatCard(
                    title = "Activity Log",
                    value = "${recentLogs.size}",
                    subtitle = "Events",
                    icon = Icons.Default.History,
                    accentColor = scheme.primary,
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
                    text = "RECENT ACTIVITY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    fontFamily = JakartaFontFamily,
                    color = scheme.onSurfaceVariant
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
                shape = RoundedCornerShape(18.dp),
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
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(scheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = scheme.onPrimaryContainer,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "All quiet right now",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = JakartaFontFamily,
                                color = scheme.onSurface
                            )
                            Text(
                                text = "Protected storage and identity events will appear here in real time.",
                                color = scheme.onSurfaceVariant,
                                fontSize = 11.sp,
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
                                    .padding(horizontal = 14.dp, vertical = 11.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(scheme.surfaceContainerLow)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = scheme.primary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(11.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = log.appName,
                                            fontWeight = FontWeight.SemiBold,
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
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = friendlyPathSummary(log.requestedPath),
                                        fontSize = 11.sp,
                                        color = scheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            if (index < previewLogs.lastIndex) {
                                HorizontalDivider(
                                    color = scheme.outlineVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BentoModuleTile(
    title: String,
    subtitle: String,
    badgeText: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accentColor.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
                .padding(14.dp)
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
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.16f))
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(21.dp)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accentColor.copy(alpha = 0.14f))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            fontFamily = JakartaFontFamily
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

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = JakartaFontFamily
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
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
