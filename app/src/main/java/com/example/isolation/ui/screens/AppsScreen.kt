package com.example.isolation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.isolation.model.IsolationMode
import com.example.isolation.pkg.InstalledAppInfo
import com.example.isolation.ui.components.ContentCard
import com.example.isolation.ui.components.EmptyState
import com.example.isolation.ui.components.ListRow
import com.example.isolation.ui.components.ModeBadge
import com.example.isolation.ui.components.StatusIndicator
import com.example.isolation.ui.components.rememberAppIconBitmap
import com.example.isolation.ui.viewmodel.AppListFilter
import com.example.isolation.ui.viewmodel.IsoVaultViewModel

@Composable
fun AppsScreen(
    viewModel: IsoVaultViewModel,
    onBack: (() -> Unit)? = null,
    onDetailOpenChanged: (Boolean) -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val allApps by viewModel.allInstalledApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val activeFilter by viewModel.filter.collectAsState()
    val isolatedCount by viewModel.isolatedCount.collectAsState()
    val selectedAppForDetail by viewModel.selectedAppForDetail.collectAsState()

    val displayedApps = remember(allApps, searchQuery, activeFilter) {
        allApps.filter { app ->
            val matchesQuery = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (activeFilter) {
                AppListFilter.USER_APPS -> !app.isSystemApp || app.isIsolated
                AppListFilter.ISOLATED_ONLY -> app.isIsolated
                AppListFilter.ALL -> true
                AppListFilter.SYSTEM_APPS -> app.isSystemApp
            }
            matchesQuery && matchesFilter
        }.sortedWith(
            compareByDescending<InstalledAppInfo> { it.isIsolated }
                .thenBy { it.appName.lowercase() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(horizontal = 16.dp)
            .testTag("apps_screen")
    ) {
        // Top Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = scheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column {
                    Text(
                        text = "Storage Isolate",
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onSurface
                    )
                    Text(
                        text = "Keep personal photos & folders private from apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))
            StatusIndicator(
                label = "$isolatedCount Active",
                color = scheme.secondary
            )
        }

        // Flat Summary Hero Card
        ContentCard(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            ListRow(
                title = "Private Per-App Storage",
                subtitle = "Isolated apps get their own clean workspace so they cannot scan your real DCIM photos or downloads.",
                leadingIcon = Icons.Default.FolderSpecial,
                tintLeadingIcon = true,
                modifier = Modifier.padding(vertical = 0.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = {
                Text(
                    text = "Search apps...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = scheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = scheme.onSurfaceVariant
                        )
                    }
                }
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            shape = MaterialTheme.shapes.extraSmall,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = scheme.primary,
                unfocusedBorderColor = scheme.outlineVariant,
                focusedContainerColor = scheme.surface,
                unfocusedContainerColor = scheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_apps_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Chips
        val filterOrder = listOf(
            AppListFilter.USER_APPS to "Installed Apps",
            AppListFilter.ISOLATED_ONLY to "Isolated ($isolatedCount)",
            AppListFilter.ALL to "All Apps (${allApps.size})",
            AppListFilter.SYSTEM_APPS to "System"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filterOrder.forEach { (flt, label) ->
                val selected = activeFilter == flt
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.setFilter(flt) },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    shape = MaterialTheme.shapes.extraSmall,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = scheme.primaryContainer,
                        selectedLabelColor = scheme.onPrimaryContainer,
                        selectedLeadingIconColor = scheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("filter_${flt.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (displayedApps.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Android,
                title = "No apps in this filter",
                message = "Tap below to view all installed apps on your device.",
                actionLabel = "Show All Apps",
                onAction = {
                    viewModel.setSearchQuery("")
                    viewModel.setFilter(AppListFilter.ALL)
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(displayedApps, key = { it.packageName }) { app ->
                    val isIsolated = app.isIsolated
                    val iconBitmap = rememberAppIconBitmap(app.packageName, app.icon)

                    ContentCard(
                        onClick = { viewModel.selectAppForDetail(app) },
                        borderColor = if (isIsolated) scheme.secondary else scheme.outlineVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("app_card_${app.packageName}")
                    ) {
                        ListRow(
                            title = app.appName,
                            monoSubtitle = app.packageName,
                            leadingBitmap = iconBitmap,
                            leadingIcon = Icons.Default.Android,
                            modifier = Modifier.padding(vertical = 0.dp),
                            trailing = {
                                Switch(
                                    checked = isIsolated,
                                    onCheckedChange = { enabled ->
                                        viewModel.toggleAppIsolation(app, enabled)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = scheme.onPrimary,
                                        checkedTrackColor = scheme.primary
                                    ),
                                    modifier = Modifier.testTag("switch_isolate_${app.packageName}")
                                )
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = scheme.outlineVariant)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ModeBadge(mode = app.isolationMode)

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val quickModes = listOf(
                                    IsolationMode.SHARED_WORKSPACE to "Workspace",
                                    IsolationMode.SANDBOX_REDIRECT to "Sandbox",
                                    IsolationMode.ENCRYPTED_VAULT to "Vault"
                                )
                                quickModes.forEach { (mode, label) ->
                                    val selected = isIsolated && app.isolationMode == mode
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            if (!app.isIsolated) {
                                                viewModel.toggleAppIsolation(app.copy(isolationMode = mode), true)
                                            }
                                            viewModel.updateAppIsolationMode(app.packageName, mode)
                                        },
                                        label = {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        },
                                        shape = MaterialTheme.shapes.extraSmall,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = scheme.primary,
                                            selectedLabelColor = scheme.onPrimary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedAppForDetail?.let { detailApp ->
        AppStorageConfigDialog(
            app = detailApp,
            onSelectMode = { mode ->
                viewModel.updateAppIsolationMode(detailApp.packageName, mode)
                viewModel.selectAppForDetail(detailApp.copy(isolationMode = mode))
            },
            onDismiss = { viewModel.selectAppForDetail(null) }
        )
    }
}

@Composable
private fun AppStorageConfigDialog(
    app: InstalledAppInfo,
    onSelectMode: (IsolationMode) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = scheme.surface,
        title = {
            Text(
                text = "${app.appName} Storage Mode",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IsolationMode.entries.forEach { mode ->
                    val selected = app.isolationMode == mode
                    ContentCard(
                        onClick = { onSelectMode(mode) },
                        containerColor = if (selected) scheme.primaryContainer else scheme.surfaceContainerLow,
                        borderColor = if (selected) scheme.primary else scheme.outlineVariant,
                        contentPadding = PaddingValues(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListRow(
                            title = mode.title,
                            subtitle = mode.description,
                            modifier = Modifier.padding(vertical = 0.dp),
                            trailing = {
                                RadioButton(
                                    selected = selected,
                                    onClick = { onSelectMode(mode) }
                                )
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}
