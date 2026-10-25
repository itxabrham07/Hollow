package com.example.isolation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.data.entity.AccessAuditLog
import com.example.isolation.model.AuditAction
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentLavender
import com.example.ui.theme.AccentMint
import com.example.ui.theme.AccentPink
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: IsoVaultViewModel,
    onNavigateToApps: () -> Unit,
    onNavigateToVault: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onNavigateToAudit: () -> Unit,
    onNavigateToSettings: (() -> Unit)? = null
) {
    val isLsposedActive by viewModel.isLsposedActive.collectAsState()
    val isolatedCount by viewModel.isolatedCount.collectAsState()
    val recentLogs by viewModel.recentAuditLogs.collectAsState()
    val totalBytesEncrypted by viewModel.totalEncryptedBytes.collectAsState()

    val formattedEncrypted = if (totalBytesEncrypted > 1024 * 1024) {
        String.format(Locale.US, "%.1f MB", totalBytesEncrypted / (1024.0 * 1024.0))
    } else {
        "${totalBytesEncrypted / 1024} KB"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 20.dp)
            .testTag("dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))

            // Vault Space Hero Card matching bottom-center phone in mockup
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(DarkSurfaceCard)
                    .border(1.dp, DarkBorderSubtle, RoundedCornerShape(26.dp))
                    .padding(22.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // "Vault Space 😎" with neon ring style
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .border(1.5.dp, AccentPink, RoundedCornerShape(50))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Vault Space",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = JakartaFontFamily,
                                    color = TextWhite
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "😎", fontSize = 20.sp)
                        }

                        // Gear / Settings Icon
                        IconButton(
                            onClick = { onNavigateToSettings?.invoke() ?: onNavigateToTerminal() },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceElevated)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // LSPosed Protection Banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isLsposedActive) AccentMint.copy(alpha = 0.12f) else AccentAmber.copy(alpha = 0.12f))
                            .clickable { viewModel.toggleLsposedStatus() }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isLsposedActive) AccentMint else AccentAmber)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = DarkBackground,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isLsposedActive) "LSPosed Engine Active" else "LSPosed Hook Standby",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = JakartaFontFamily,
                                color = TextWhite
                            )
                            Text(
                                text = if (isLsposedActive) "Zygote & MediaStore intercepting storage" else "Tap to toggle test interception",
                                fontSize = 11.sp,
                                fontFamily = JakartaFontFamily,
                                color = if (isLsposedActive) AccentMint else AccentAmber
                            )
                        }
                    }
                }
            }
        }

        // 2x2 Metric Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Metric 1: Isolated Apps
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onNavigateToApps)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentSky.copy(alpha = 0.15f))
                        ) {
                            Icon(Icons.Default.Apps, null, tint = AccentSky, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "$isolatedCount Apps",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JakartaFontFamily,
                            color = TextWhite
                        )
                        Text(
                            text = "Isolated Targets",
                            fontSize = 12.sp,
                            fontFamily = JakartaFontFamily,
                            color = TextMuted
                        )
                    }
                }

                // Metric 2: Vault Encrypted Storage
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onNavigateToVault)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentLavender.copy(alpha = 0.15f))
                        ) {
                            Icon(Icons.Default.Lock, null, tint = AccentLavender, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = formattedEncrypted,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JakartaFontFamily,
                            color = TextWhite
                        )
                        Text(
                            text = "AES-256 Vault Data",
                            fontSize = 12.sp,
                            fontFamily = JakartaFontFamily,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        // Quick Navigation Launchers
        item {
            Text(
                text = "Privacy Tools",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = JakartaFontFamily,
                color = TextWhite,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Albums Launcher
                ToolLauncherCard(
                    title = "Albums",
                    subtitle = "Private Gallery",
                    icon = Icons.Default.Image,
                    accentColor = AccentSky,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToVault
                )

                // MT Manager File Explorer Launcher
                ToolLauncherCard(
                    title = "File Explorer",
                    subtitle = "Import & Export",
                    icon = Icons.Default.Folder,
                    accentColor = AccentMint,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToTerminal
                )
            }
        }

        // Recent Interception Activity Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Interceptions",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily,
                    color = TextWhite
                )

                Text(
                    text = "See All",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = JakartaFontFamily,
                    color = AccentSky,
                    modifier = Modifier.clickable(onClick = onNavigateToAudit)
                )
            }
        }

        // Recent Activity Rows
        if (recentLogs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurfaceCard)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Hook engine is ready. Interceptions appear here in real time.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontFamily = JakartaFontFamily
                    )
                }
            }
        } else {
            items(recentLogs.take(5)) { log ->
                DashboardLogItem(log = log)
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ToolLauncherCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(DarkSurfaceCard)
            .border(1.dp, DarkBorderSubtle, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.15f))
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily,
                    color = TextWhite
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    fontFamily = JakartaFontFamily,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun DashboardLogItem(log: AccessAuditLog) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceCard)
            .border(1.dp, DarkBorderSubtle, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (badgeColor, label) = when (log.action) {
            AuditAction.ENCRYPTED -> AccentLavender to "ENCRYPTED"
            AuditAction.REDIRECTED -> AccentMint to "SANDBOX"
            AuditAction.BLOCKED -> AccentCoral to "BLOCKED"
            AuditAction.SPOOFED_EMPTY -> AccentAmber to "EMPTY"
            AuditAction.PERMITTED -> AccentSky to "PERMITTED"
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(badgeColor.copy(alpha = 0.15f))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${log.appName} • ${log.operation}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = JakartaFontFamily,
                color = TextWhite
            )
            Text(
                text = log.requestedPath,
                fontSize = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = TextSubtle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(log.timestamp))
        Text(
            text = time,
            fontSize = 11.sp,
            fontFamily = JakartaFontFamily,
            color = TextSubtle
        )
    }
}
