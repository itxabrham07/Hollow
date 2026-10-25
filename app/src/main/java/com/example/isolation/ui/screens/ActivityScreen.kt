package com.example.isolation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.isolation.data.entity.AccessAuditLog
import com.example.isolation.model.AuditAction
import com.example.isolation.ui.components.BrandSquircleIcon
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.JakartaFontFamily

@Composable
fun ActivityScreen(
    viewModel: IsoVaultViewModel
) {
    val logs by viewModel.recentAuditLogs.collectAsStateWithLifecycle()
    val configs by viewModel.allConfigs.collectAsStateWithLifecycle()
    val securitySettings by viewModel.securitySettings.collectAsStateWithLifecycle()
    val isPanicModeActive = securitySettings.isPanicDecoyActive
    val colorScheme = MaterialTheme.colorScheme

    var selectedAppFilter by remember { mutableStateOf<String?>(null) }

    val visibleLogs = remember(logs, selectedAppFilter, isPanicModeActive) {
        if (isPanicModeActive) {
            emptyList()
        } else {
            logs.filter { log ->
                selectedAppFilter == null || log.appName.equals(selectedAppFilter, ignoreCase = true)
            }
        }
    }

    val appNamesForFilter = remember(configs, logs) {
        (configs.map { it.appName } + logs.map { it.appName })
            .filter { it.isNotBlank() }
            .distinct()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .testTag("activity_screen")
    ) {
        val isWideScreen = maxWidth >= 600.dp
        val horizontalPad = if (isWideScreen) 24.dp else 16.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPad)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header + Clear Log Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Activity",
                        fontSize = if (isWideScreen) 28.sp else 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FrauncesFontFamily,
                        color = colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Real-time folders & files encrypted or isolated by your vault",
                        fontSize = 13.sp,
                        fontFamily = JakartaFontFamily,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                if (visibleLogs.isNotEmpty()) {
                    TextButton(
                        onClick = { viewModel.clearAllAuditLogs() },
                        modifier = Modifier.testTag("clear_activity_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = "Clear Activity",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Clear log",
                            color = colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = JakartaFontFamily
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // App Filter Chips
            if (!isPanicModeActive && appNamesForFilter.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(
                        selected = selectedAppFilter == null,
                        onClick = { selectedAppFilter = null },
                        label = {
                            Text(
                                text = "All volumes",
                                fontFamily = JakartaFontFamily,
                                fontWeight = if (selectedAppFilter == null) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colorScheme.primary.copy(alpha = 0.16f),
                            selectedLabelColor = colorScheme.primary
                        )
                    )

                    appNamesForFilter.forEach { appName ->
                        val isSelected = selectedAppFilter == appName
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedAppFilter = if (isSelected) null else appName },
                            label = {
                                Text(
                                    text = appName,
                                    fontFamily = JakartaFontFamily,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colorScheme.primary.copy(alpha = 0.16f),
                                selectedLabelColor = colorScheme.primary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Readable Activity List or Empty State
            if (visibleLogs.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 44.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(colorScheme.primary.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No recent activity",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FrauncesFontFamily,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Whenever you import a folder or a protected app saves photos and files, a clear activity summary appears here.",
                            fontSize = 14.sp,
                            fontFamily = JakartaFontFamily,
                            color = colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(visibleLogs, key = { it.id }) { log ->
                        ReadableActivityCard(log = log)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadableActivityCard(log: AccessAuditLog) {
    val colorScheme = MaterialTheme.colorScheme
    val friendlySentence = remember(log) { formatFriendlyActivitySentence(log) }
    val relativeTime = remember(log.timestamp) { formatRelativeTimeAgo(log.timestamp) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandSquircleIcon(
                name = log.appName,
                size = 44.dp,
                packageName = log.packageName
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friendlySentence,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = JakartaFontFamily,
                    color = colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${log.appName} • $relativeTime",
                    fontSize = 12.sp,
                    fontFamily = JakartaFontFamily,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatFriendlyActivitySentence(log: AccessAuditLog): String {
    if (log.details.isNotBlank() && !log.details.contains("/storage/")) {
        return log.details
    }
    if (log.requestedPath.startsWith("Folder:")) {
        return "Imported & encrypted ${log.requestedPath} into ${log.appName}"
    }
    val itemName = log.requestedPath.substringAfterLast('/').removeSuffix(".isovault")
    return when (log.action) {
        AuditAction.ENCRYPTED -> "${log.appName} encrypted and saved ${itemName.ifBlank { "a file" }}"
        AuditAction.REDIRECTED -> "${log.appName} saved ${itemName.ifBlank { "files" }} to private volume"
        AuditAction.BLOCKED -> "Blocked ${log.appName} clutter folder on phone storage"
        AuditAction.SPOOFED_EMPTY -> "Hid personal gallery from ${log.appName}"
        AuditAction.PERMITTED -> "${log.appName} accessed shared storage"
    }
}

private fun formatRelativeTimeAgo(timestampMs: Long): String {
    val diffSec = ((System.currentTimeMillis() - timestampMs) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60 -> "Just now"
        diffSec < 3600 -> "${diffSec / 60} min ago"
        diffSec < 86400 -> "${diffSec / 3600} hr ago"
        else -> "${diffSec / 86400} d ago"
    }
}
