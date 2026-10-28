package com.example.isolation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JakartaFontFamily
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

    if (mountedModules.isEmpty()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .testTag("dashboard_screen"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp)
                        .testTag("card_zero_modules_shell"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(24.dp))

                    // 3D Isometric Cube Graphic (Screen 07 from storyboard)
                    IsometricCubeGraphic(modifier = Modifier.size(130.dp))

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = "No Modules Yet",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = JakartaFontFamily
                        ),
                        color = scheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Your dashboard is ready. Modules will appear here when approved by admin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = scheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, scheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Awaiting module assignment by admin",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
        return
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

            }
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

/**
 * 3D Isometric Cube Graphic for Screen 07: Home (No Modules Yet)
 * Rendered using Canvas with 3 distinct isometric facets and soft shadow.
 */
@Composable
fun IsometricCubeGraphic(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cubeRadius = (size.minDimension / 2.2f)

        val cx = w / 2f
        val cy = h / 2f - 4.dp.toPx()

        // Elliptical soft shadow underneath the cube
        drawOval(
            color = Color(0x18000000),
            topLeft = Offset(cx - cubeRadius * 0.9f, cy + cubeRadius * 0.7f),
            size = Size(cubeRadius * 1.8f, cubeRadius * 0.45f)
        )

        // Points for isometric cube vertices
        val topVertex = Offset(cx, cy - cubeRadius * 0.75f)
        val centerVertex = Offset(cx, cy)
        val bottomVertex = Offset(cx, cy + cubeRadius * 0.75f)

        val leftVertex = Offset(cx - cubeRadius * 0.866f, cy - cubeRadius * 0.25f)
        val rightVertex = Offset(cx + cubeRadius * 0.866f, cy - cubeRadius * 0.25f)
        val leftBottomVertex = Offset(cx - cubeRadius * 0.866f, cy + cubeRadius * 0.5f)
        val rightBottomVertex = Offset(cx + cubeRadius * 0.866f, cy + cubeRadius * 0.5f)

        // 1. Top Face (Diamond - soft lavender/periwinkle)
        val topFace = Path().apply {
            moveTo(topVertex.x, topVertex.y)
            lineTo(rightVertex.x, rightVertex.y)
            lineTo(centerVertex.x, centerVertex.y)
            lineTo(leftVertex.x, leftVertex.y)
            close()
        }
        drawPath(topFace, color = Color(0xFFC7D2FE))

        // 2. Left Face (Side - medium indigo)
        val leftFace = Path().apply {
            moveTo(leftVertex.x, leftVertex.y)
            lineTo(centerVertex.x, centerVertex.y)
            lineTo(bottomVertex.x, bottomVertex.y)
            lineTo(leftBottomVertex.x, leftBottomVertex.y)
            close()
        }
        drawPath(leftFace, color = Color(0xFF818CF8))

        // 3. Right Face (Side - deep indigo)
        val rightFace = Path().apply {
            moveTo(rightVertex.x, rightVertex.y)
            lineTo(centerVertex.x, centerVertex.y)
            lineTo(bottomVertex.x, bottomVertex.y)
            lineTo(rightBottomVertex.x, rightBottomVertex.y)
            close()
        }
        drawPath(rightFace, color = Color(0xFF6366F1))
    }
}

