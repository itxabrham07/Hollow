package com.example.isolation.ui.screens

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.model.IsolationMode
import com.example.isolation.pkg.InstalledAppInfo
import com.example.isolation.ui.components.ModeBadge
import com.example.isolation.ui.components.StatusBadge
import com.example.isolation.ui.viewmodel.AppListFilter
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberRed
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    viewModel: IsoVaultViewModel
) {
    val filteredApps by viewModel.filteredApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val activeFilter by viewModel.filter.collectAsState()
    val selectedApp by viewModel.selectedAppForDetail.collectAsState()
    val configs by viewModel.configs.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("apps_screen")
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Search installed packages or app names...", color = TextSecondary, fontSize = 13.sp) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = CyberCyan)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberCyan,
                unfocusedBorderColor = SurfaceBorder,
                focusedContainerColor = SurfaceCard,
                unfocusedContainerColor = SurfaceCard
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("apps_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filters row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = activeFilter == AppListFilter.ALL,
                    onClick = { viewModel.setFilter(AppListFilter.ALL) },
                    label = { Text("All (${filteredApps.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                        selectedLabelColor = CyberCyan
                    )
                )
            }
            item {
                FilterChip(
                    selected = activeFilter == AppListFilter.ISOLATED_ONLY,
                    onClick = { viewModel.setFilter(AppListFilter.ISOLATED_ONLY) },
                    label = { Text("Isolated Only") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberEmerald.copy(alpha = 0.2f),
                        selectedLabelColor = CyberEmerald
                    )
                )
            }
            item {
                FilterChip(
                    selected = activeFilter == AppListFilter.USER_APPS,
                    onClick = { viewModel.setFilter(AppListFilter.USER_APPS) },
                    label = { Text("User Apps") }
                )
            }
            item {
                FilterChip(
                    selected = activeFilter == AppListFilter.SYSTEM_APPS,
                    onClick = { viewModel.setFilter(AppListFilter.SYSTEM_APPS) },
                    label = { Text("System Apps") }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // App List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredApps, key = { it.packageName }) { appInfo ->
                AppItemCard(
                    appInfo = appInfo,
                    onToggleIsolation = { enable -> viewModel.toggleAppIsolation(appInfo, enable) },
                    onClick = { viewModel.selectAppForDetail(appInfo) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // App Detail Modal Bottom Sheet
    selectedApp?.let { appInfo ->
        val cfg = configs.find { it.packageName == appInfo.packageName }

        ModalBottomSheet(
            onDismissRequest = { viewModel.selectAppForDetail(null) },
            sheetState = sheetState,
            containerColor = SurfaceElevated
        ) {
            AppIsolationDetailContent(
                appInfo = appInfo,
                blockRootPollution = cfg?.blockRootPollution ?: true,
                whitelistedFolders = cfg?.whitelistedFolders ?: "Download",
                onUpdateMode = { mode -> viewModel.updateAppIsolationMode(appInfo.packageName, mode) },
                onTogglePollution = { block -> viewModel.updateRootPollution(appInfo.packageName, block) },
                onUpdateWhitelist = { whitelist -> viewModel.updateWhitelistedFolders(appInfo.packageName, whitelist) },
                onSimulateIo = { path ->
                    viewModel.testSimulateAppIo(
                        packageName = appInfo.packageName,
                        appName = appInfo.appName,
                        testPath = path
                    )
                },
                onPurgeVault = { viewModel.purgeVaultForPackage(appInfo.packageName) },
                onExecuteMount = { viewModel.executeMount(appInfo.packageName, appInfo.isolationMode) },
                onDismiss = { viewModel.selectAppForDetail(null) }
            )
        }
    }
}

@Composable
fun AppItemCard(
    appInfo: InstalledAppInfo,
    onToggleIsolation: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (appInfo.isIsolated) CyberCyan.copy(alpha = 0.4f) else SurfaceBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("app_item_${appInfo.packageName.replace('.', '_')}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            // App Icon
            val drawable = appInfo.icon
            if (drawable is BitmapDrawable) {
                Image(
                    bitmap = drawable.bitmap.asImageBitmap(),
                    contentDescription = appInfo.appName,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyberCyan.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = appInfo.appName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    StatusBadge(
                        text = "API ${appInfo.targetSdkVersion}",
                        color = if (appInfo.targetSdkVersion >= 35) CyberCyan else TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = appInfo.packageName,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (appInfo.isIsolated) {
                        ModeBadge(mode = appInfo.isolationMode)
                    } else {
                        StatusBadge(text = "UNRESTRICTED", color = TextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = appInfo.isIsolated,
                onCheckedChange = { onToggleIsolation(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = CyberEmerald,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = SurfaceElevated
                ),
                modifier = Modifier.testTag("switch_isolate_${appInfo.packageName.replace('.', '_')}")
            )
        }
    }
}

@Composable
fun AppIsolationDetailContent(
    appInfo: InstalledAppInfo,
    blockRootPollution: Boolean,
    whitelistedFolders: String,
    onUpdateMode: (IsolationMode) -> Unit,
    onTogglePollution: (Boolean) -> Unit,
    onUpdateWhitelist: (String) -> Unit,
    onSimulateIo: (String) -> Unit,
    onPurgeVault: () -> Unit,
    onExecuteMount: () -> Unit,
    onDismiss: () -> Unit
) {
    var whitelistText by remember { mutableStateOf(whitelistedFolders) }
    var testPathText by remember { mutableStateOf("/storage/emulated/0/DCIM/Camera/user_photo.jpg") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = appInfo.appName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    text = appInfo.packageName,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
            }
            StatusBadge(
                text = if (appInfo.isIsolated) "ISOLATION ACTIVE" else "NOT ISOLATED",
                color = if (appInfo.isIsolated) CyberEmerald else CyberAmber
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = SurfaceBorder)
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "SELECT ISOLATION STRATEGY",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 0.8.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        IsolationMode.values().forEach { mode ->
            val isSelected = appInfo.isolationMode == mode
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) SurfaceCard else SurfaceElevated
                ),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSelected) CyberCyan else SurfaceBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onUpdateMode(mode) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(10.dp)
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onUpdateMode(mode) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = CyberCyan,
                            unselectedColor = TextSecondary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = mode.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isSelected) CyberCyan else TextPrimary
                            )
                        }
                        Text(
                            text = mode.description,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Privacy protections toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Block Root Directory Pollution",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = TextPrimary
                )
                Text(
                    text = "Blocks hidden dotfolders (e.g. .tencent, .baidu, .pdd) from polluting /sdcard",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            Switch(
                checked = blockRootPollution,
                onCheckedChange = { onTogglePollution(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = CyberEmerald)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Whitelisted subfolders
        OutlinedTextField(
            value = whitelistText,
            onValueChange = {
                whitelistText = it
                onUpdateWhitelist(it)
            },
            label = { Text("Whitelisted Shared Folders (comma separated)", fontSize = 12.sp) },
            placeholder = { Text("Download, Pictures", fontSize = 12.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberCyan,
                unfocusedBorderColor = SurfaceBorder
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Test Simulated I/O Action
        Text(
            text = "LIVE INTERCEPTION TEST",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = testPathText,
                onValueChange = { testPathText = it },
                label = { Text("Target Path to Access", fontSize = 11.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = SurfaceBorder
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { onSimulateIo(testPathText) },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363F)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Test I/O", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onExecuteMount,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberEmerald),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Mount", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = onPurgeVault,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberRed),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Purge Vault", fontSize = 12.sp)
            }
        }
    }
}
