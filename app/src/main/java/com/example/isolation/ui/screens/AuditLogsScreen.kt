package com.example.isolation.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.example.isolation.model.AuditAction
import com.example.isolation.ui.components.AuditActionBadge
import com.example.isolation.ui.components.ContentCard
import com.example.isolation.ui.components.EmptyState
import com.example.isolation.ui.components.ListRow
import com.example.isolation.ui.viewmodel.IsoVaultViewModel

@Composable
fun AuditLogsScreen(
    viewModel: IsoVaultViewModel,
    onBack: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val auditLogs by viewModel.recentAuditLogs.collectAsState()
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredLogs = when (selectedFilter) {
        "BLOCKED" -> auditLogs.filter { it.action == AuditAction.BLOCKED }
        "ENCRYPTED" -> auditLogs.filter { it.action == AuditAction.ENCRYPTED }
        "REDIRECTED" -> auditLogs.filter { it.action == AuditAction.REDIRECTED }
        "SPOOFED" -> auditLogs.filter { it.action == AuditAction.SPOOFED_EMPTY }
        else -> auditLogs
    }

    val filterLabels = listOf(
        "ALL" to "All",
        "REDIRECTED" to "Kept Private",
        "SPOOFED" to "Hidden Gallery",
        "BLOCKED" to "Blocked Clutter"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("audit_logs_screen")
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = scheme.onBackground
                        )
                    }
                }
                Column {
                    Text(
                        text = "Activity",
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onBackground
                    )
                    Text(
                        text = "See when apps check your photos or save files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
            if (auditLogs.isNotEmpty()) {
                IconButton(onClick = { viewModel.clearAuditLogs() }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear History",
                        tint = scheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(filterLabels) { (key, label) ->
                val isSelected = selectedFilter == key
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = key },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    shape = MaterialTheme.shapes.extraSmall,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = scheme.primaryContainer,
                        selectedLabelColor = scheme.onPrimaryContainer,
                        containerColor = scheme.surface,
                        labelColor = scheme.onSurfaceVariant
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Default.History,
                    title = "No activity yet",
                    message = "When protected apps access storage, you'll see it here."
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredLogs, key = { it.id }) { log ->
                    val timeStr = android.text.format.DateFormat.format("h:mm a", log.timestamp).toString()
                    ContentCard(modifier = Modifier.fillMaxWidth()) {
                        ListRow(
                            title = log.appName,
                            subtitle = friendlyPathSummary(log.requestedPath),
                            monoSubtitle = "$timeStr • ${log.requestedPath}",
                            leadingIcon = Icons.Default.Folder,
                            modifier = Modifier.padding(vertical = 0.dp),
                            trailing = {
                                AuditActionBadge(action = log.action)
                            }
                        )
                    }
                }
            }
        }
    }
}
