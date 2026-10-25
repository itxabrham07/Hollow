package com.example.isolation.ui.screens

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
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
import com.example.ui.theme.ClaySecondary
import com.example.ui.theme.CoralError
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.HoneyTertiary
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.SageSoft
import com.example.ui.theme.SageTint
import com.example.ui.theme.WarmIvory
import com.example.ui.theme.WarmSand
import com.example.ui.theme.WarmStone

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
            .padding(horizontal = 20.dp)
            .testTag("apps_screen")
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Your Apps",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FrauncesFontFamily,
            color = InkPrimary
        )
        Text(
            text = "Turn on privacy for any app so it only sees its own folder.",
            fontSize = 13.sp,
            color = InkSecondary,
            fontFamily = JakartaFontFamily
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Pill Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = {
                Text(
                    "Find an app (e.g. Snapchat, WhatsApp)...",
                    color = InkSecondary,
                    fontSize = 14.sp,
                    fontFamily = JakartaFontFamily
                )
            },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = ForestPrimary)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = InkSecondary)
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ForestPrimary,
                unfocusedBorderColor = WarmStone,
                focusedContainerColor = WarmIvory,
                unfocusedContainerColor = WarmIvory
            ),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("apps_search_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Pills
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = activeFilter == AppListFilter.ALL,
                    onClick = { viewModel.setFilter(AppListFilter.ALL) },
                    label = { Text("All (${filteredApps.size})", fontFamily = JakartaFontFamily) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ForestPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = WarmIvory,
                        labelColor = InkSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = activeFilter == AppListFilter.ALL,
                        borderColor = WarmStone,
                        selectedBorderColor = ForestPrimary
                    )
                )
            }
            item {
                FilterChip(
                    selected = activeFilter == AppListFilter.ISOLATED_ONLY,
                    onClick = { viewModel.setFilter(AppListFilter.ISOLATED_ONLY) },
                    label = { Text("Protected", fontFamily = JakartaFontFamily) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ForestPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = WarmIvory,
                        labelColor = InkSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = activeFilter == AppListFilter.ISOLATED_ONLY,
                        borderColor = WarmStone,
                        selectedBorderColor = ForestPrimary
                    )
                )
            }
            item {
                FilterChip(
                    selected = activeFilter == AppListFilter.USER_APPS,
                    onClick = { viewModel.setFilter(AppListFilter.USER_APPS) },
                    label = { Text("Downloaded", fontFamily = JakartaFontFamily) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ForestPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = WarmIvory,
                        labelColor = InkSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = activeFilter == AppListFilter.USER_APPS,
                        borderColor = WarmStone,
                        selectedBorderColor = ForestPrimary
                    )
                )
            }
            item {
                FilterChip(
                    selected = activeFilter == AppListFilter.SYSTEM_APPS,
                    onClick = { viewModel.setFilter(AppListFilter.SYSTEM_APPS) },
                    label = { Text("Pre-installed", fontFamily = JakartaFontFamily) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ForestPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = WarmIvory,
                        labelColor = InkSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = activeFilter == AppListFilter.SYSTEM_APPS,
                        borderColor = WarmStone,
                        selectedBorderColor = ForestPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

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
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // App Detail Modal Bottom Sheet
    selectedApp?.let { appInfo ->
        val cfg = configs.find { it.packageName == appInfo.packageName }

        ModalBottomSheet(
            onDismissRequest = { viewModel.selectAppForDetail(null) },
            sheetState = sheetState,
            containerColor = WarmIvory,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            AppIsolationDetailContent(
                appInfo = appInfo,
                blockRootPollution = cfg?.blockRootPollution ?: true,
                whitelistedFolders = cfg?.whitelistedFolders ?: "",
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
                onPurgeVault = { viewModel.purgeVaultForPackage(appInfo.packageName) }
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
        colors = CardDefaults.cardColors(
            containerColor = if (appInfo.isIsolated) SageTint else WarmIvory
        ),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (appInfo.isIsolated) ForestPrimary.copy(alpha = 0.28f) else WarmStone
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("app_item_${appInfo.packageName.replace('.', '_')}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            val drawable = appInfo.icon
            if (drawable is BitmapDrawable) {
                Image(
                    bitmap = drawable.bitmap.asImageBitmap(),
                    contentDescription = appInfo.appName,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SageSoft)
                ) {
                    Text(
                        text = appInfo.appName.take(1).uppercase(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FrauncesFontFamily,
                        color = ForestPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appInfo.appName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = InkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = JakartaFontFamily
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (appInfo.isIsolated) {
                        ModeBadge(mode = appInfo.isolationMode)
                    } else {
                        Text(
                            text = "Can see shared folders",
                            fontSize = 12.sp,
                            color = InkSecondary,
                            fontFamily = JakartaFontFamily
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = appInfo.isIsolated,
                onCheckedChange = { onToggleIsolation(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ForestPrimary,
                    uncheckedThumbColor = InkSecondary,
                    uncheckedTrackColor = WarmSand,
                    uncheckedBorderColor = WarmStone
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
    onPurgeVault: () -> Unit
) {
    var whitelistText by remember { mutableStateOf(whitelistedFolders) }
    var testPathText by remember { mutableStateOf("/storage/emulated/0/DCIM/Camera/vacation_photo.jpg") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .padding(bottom = 28.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appInfo.appName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FrauncesFontFamily,
                    color = InkPrimary
                )
                Text(
                    text = if (appInfo.isIsolated) "Personal files & photos are hidden from this app" else "Standard storage access",
                    fontSize = 13.sp,
                    color = InkSecondary,
                    fontFamily = JakartaFontFamily
                )
            }
            StatusBadge(
                text = if (appInfo.isIsolated) "Protected" else "Standard",
                color = if (appInfo.isIsolated) ForestPrimary else HoneyTertiary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = WarmStone)
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "How should ${appInfo.appName} work?",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FrauncesFontFamily,
            color = InkPrimary
        )

        Spacer(modifier = Modifier.height(10.dp))

        IsolationMode.values().forEach { mode ->
            val isSelected = appInfo.isolationMode == mode
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) SageTint else WarmIvory
                ),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSelected) ForestPrimary else WarmStone
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onUpdateMode(mode) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onUpdateMode(mode) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = ForestPrimary,
                            unselectedColor = InkSecondary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mode.title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = InkPrimary,
                            fontFamily = JakartaFontFamily
                        )
                        Text(
                            text = mode.description,
                            fontSize = 12.sp,
                            color = InkSecondary,
                            fontFamily = JakartaFontFamily
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Keep storage tidy toggle
        Card(
            colors = CardDefaults.cardColors(containerColor = WarmSand),
            shape = RoundedCornerShape(18.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Keep main folders tidy",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = InkPrimary,
                        fontFamily = JakartaFontFamily
                    )
                    Text(
                        text = "Prevents hidden clutter or leftover folders on your phone",
                        fontSize = 12.sp,
                        color = InkSecondary,
                        fontFamily = JakartaFontFamily
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = blockRootPollution,
                    onCheckedChange = { onTogglePollution(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ForestPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Allowed folders
        OutlinedTextField(
            value = whitelistText,
            onValueChange = {
                whitelistText = it
                onUpdateWhitelist(it)
            },
            label = { Text("Folders this app is allowed to see", fontSize = 12.sp, fontFamily = JakartaFontFamily) },
            placeholder = { Text("Download, Music", fontSize = 13.sp, fontFamily = JakartaFontFamily) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ForestPrimary,
                unfocusedBorderColor = WarmStone
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { onSimulateIo(testPathText) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForestPrimary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Test Protection", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily)
            }

            OutlinedButton(
                onClick = onPurgeVault,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralError),
                border = androidx.compose.foundation.BorderStroke(1.dp, CoralError.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Empty Locker", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily)
            }
        }
    }
}
