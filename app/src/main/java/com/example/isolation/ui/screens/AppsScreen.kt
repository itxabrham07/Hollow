package com.example.isolation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.model.IsolationMode
import com.example.isolation.pkg.InstalledAppInfo
import com.example.isolation.ui.components.ModeBadge
import com.example.isolation.ui.components.rememberAppIconBitmap
import com.example.isolation.ui.viewmodel.AppListFilter
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.M3AmberAccent
import com.example.ui.theme.M3IndigoAccent
import com.example.ui.theme.M3RoseAccent
import com.example.ui.theme.M3TealAccent
import com.example.ui.theme.M3VioletAccent

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
        // Clean Material 3 Top Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    Surface(
                        color = scheme.surfaceContainerLow,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { onBack() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = scheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column {
                    Text(
                        text = "Storage Isolate",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = scheme.onSurface,
                        fontFamily = JakartaFontFamily
                    )
                    Text(
                        text = "Keep personal photos & folders private from apps",
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                        fontFamily = JakartaFontFamily
                    )
                }
            }

            Surface(
                color = M3TealAccent.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = "$isolatedCount Active",
                    color = M3TealAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Colorful Material 3 Summary Hero Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            M3TealAccent,
                            M3IndigoAccent
                        )
                    )
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.20f))
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Private Per-App Storage",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = JakartaFontFamily
                    )
                    Text(
                        text = "Isolated apps get their own clean workspace so they cannot scan your real DCIM photos or downloads.",
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontFamily = JakartaFontFamily
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Rounded Material 3 Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = {
                Text(
                    text = "Search apps...",
                    fontSize = 13.sp,
                    color = scheme.onSurfaceVariant,
                    fontFamily = JakartaFontFamily
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = M3TealAccent,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = M3TealAccent,
                unfocusedBorderColor = scheme.outlineVariant,
                focusedContainerColor = scheme.surface,
                unfocusedContainerColor = scheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_apps_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Colorful Material 3 Filter Chips
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
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = JakartaFontFamily
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
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = scheme.secondaryContainer,
                        selectedLabelColor = scheme.onSecondaryContainer,
                        selectedLeadingIconColor = scheme.onSecondaryContainer
                    ),
                    modifier = Modifier.testTag("filter_${flt.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (displayedApps.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp)
                ) {
                    Text(
                        text = "No apps in this filter",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = scheme.onSurface,
                        fontFamily = JakartaFontFamily
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap below to view all installed apps on your device.",
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                        fontFamily = JakartaFontFamily
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.setSearchQuery("")
                            viewModel.setFilter(AppListFilter.ALL)
                        },
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Show All Apps", fontFamily = JakartaFontFamily, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(displayedApps, key = { it.packageName }) { app ->
                    ColorfulIsolationAppCard(
                        app = app,
                        onToggleIsolation = { enabled ->
                            viewModel.toggleAppIsolation(app, enabled)
                        },
                        onSelectMode = { mode ->
                            if (!app.isIsolated) {
                                viewModel.toggleAppIsolation(app.copy(isolationMode = mode), true)
                            }
                            viewModel.updateAppIsolationMode(app.packageName, mode)
                        },
                        onOpenDetail = {
                            viewModel.selectAppForDetail(app)
                        }
                    )
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
private fun ColorfulIsolationAppCard(
    app: InstalledAppInfo,
    onToggleIsolation: (Boolean) -> Unit,
    onSelectMode: (IsolationMode) -> Unit,
    onOpenDetail: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isIsolated = app.isIsolated

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isIsolated) {
                scheme.secondaryContainer.copy(alpha = 0.28f)
            } else {
                scheme.surface
            }
        ),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            width = if (isIsolated) 1.5.dp else 1.dp,
            color = if (isIsolated) M3TealAccent.copy(alpha = 0.45f) else scheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable { onOpenDetail() }
            .testTag("app_card_${app.packageName}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val iconBitmap = rememberAppIconBitmap(app.packageName, app.icon)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.surfaceContainerLow)
                ) {
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = app.appName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = M3TealAccent,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = JakartaFontFamily
                    )
                    Text(
                        text = app.packageName,
                        fontSize = 11.sp,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = JakartaFontFamily
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = isIsolated,
                    onCheckedChange = onToggleIsolation,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = M3TealAccent
                    ),
                    modifier = Modifier.testTag("switch_isolate_${app.packageName}")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Mode Selector Pills
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                ModeBadge(mode = app.isolationMode)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val quickModes = listOf(
                        IsolationMode.SHARED_WORKSPACE to ("Workspace" to M3TealAccent),
                        IsolationMode.SANDBOX_REDIRECT to ("Sandbox" to M3IndigoAccent),
                        IsolationMode.ENCRYPTED_VAULT to ("Vault" to M3RoseAccent)
                    )
                    quickModes.forEach { (mode, pair) ->
                        val (label, color) = pair
                        val selected = isIsolated && app.isolationMode == mode
                        Surface(
                            color = if (selected) color else color.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onSelectMode(mode) }
                        ) {
                            Text(
                                text = label,
                                color = if (selected) Color.White else color,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = JakartaFontFamily,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
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
        shape = RoundedCornerShape(24.dp),
        containerColor = scheme.surface,
        title = {
            Text(
                text = "${app.appName} Storage Mode",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                fontFamily = JakartaFontFamily
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IsolationMode.entries.forEach { mode ->
                    val selected = app.isolationMode == mode
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) scheme.secondaryContainer else scheme.surfaceContainerLow
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelectMode(mode) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { onSelectMode(mode) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = mode.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = scheme.onSurface,
                                    fontFamily = JakartaFontFamily
                                )
                                Text(
                                    text = mode.description,
                                    fontSize = 11.sp,
                                    color = scheme.onSurfaceVariant,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", fontWeight = FontWeight.Bold, fontFamily = JakartaFontFamily)
            }
        }
    )
}
