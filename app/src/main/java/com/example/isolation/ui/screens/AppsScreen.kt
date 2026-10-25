package com.example.isolation.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.model.IsolationMode
import com.example.isolation.pkg.InstalledAppInfo
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentLavender
import com.example.ui.theme.AccentMint
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    viewModel: IsoVaultViewModel,
    onOpenExplorerForPackage: ((String) -> Unit)? = null
) {
    val configs by viewModel.configs.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedConfig by remember { mutableStateOf<AppIsolationConfig?>(null) }
    var showAddAppDialog by remember { mutableStateOf(false) }
    var customPackageInput by remember { mutableStateOf("") }
    var customAppNameInput by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val filteredConfigs = configs.filter {
        searchQuery.isBlank() ||
                it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 20.dp)
            .testTag("apps_screen")
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Isolated Apps",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily,
                    color = TextWhite
                )
                Text(
                    text = "LSPosed active targets & storage permissions",
                    fontSize = 12.sp,
                    fontFamily = JakartaFontFamily,
                    color = TextMuted
                )
            }

            // Quick Status Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(AccentMint.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(AccentMint)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${configs.count { it.isIsolated }} Isolated",
                        color = AccentMint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = JakartaFontFamily
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Pill + Sky Blue '+' Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(DarkSurfaceCard)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search apps...",
                                color = TextMuted,
                                fontSize = 14.sp,
                                fontFamily = JakartaFontFamily
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            cursorColor = AccentSky
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("apps_search_input")
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // '+' Add Custom Target App Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AccentSky)
                    .clickable { showAddAppDialog = true }
                    .testTag("add_custom_app_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Target App",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Configured Apps Cards List
        if (filteredConfigs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = TextSubtle,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No apps found",
                        color = TextMuted,
                        fontSize = 15.sp,
                        fontFamily = JakartaFontFamily
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(filteredConfigs, key = { it.packageName }) { config ->
                    ConfiguredAppCard(
                        config = config,
                        onToggleStorage = { isIsolated ->
                            viewModel.toggleAppIsolation(
                                InstalledAppInfo(
                                    packageName = config.packageName,
                                    appName = config.appName,
                                    icon = null,
                                    isSystemApp = false,
                                    targetSdkVersion = 34,
                                    isIsolated = isIsolated,
                                    isolationMode = config.isolationMode
                                ),
                                enable = isIsolated
                            )
                        },
                        onClick = { selectedConfig = config }
                    )
                }
            }
        }

        // App Detail & Permission Sheet
        selectedConfig?.let { config ->
            ModalBottomSheet(
                onDismissRequest = { selectedConfig = null },
                sheetState = sheetState,
                containerColor = DarkSurfaceElevated,
                dragHandle = null,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                AppDetailPermissionSheet(
                    config = config,
                    viewModel = viewModel,
                    onClose = { selectedConfig = null },
                    onOpenExplorer = {
                        selectedConfig = null
                        onOpenExplorerForPackage?.invoke(config.packageName)
                    }
                )
            }
        }

        // Add Custom Target App Dialog
        if (showAddAppDialog) {
            AlertDialog(
                onDismissRequest = { showAddAppDialog = false },
                title = {
                    Text(
                        text = "Add Isolated Target App",
                        fontWeight = FontWeight.Bold,
                        fontFamily = JakartaFontFamily,
                        color = TextWhite
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Add any app package enabled in LSPosed scope to configure isolated storage rules.",
                            color = TextMuted,
                            fontSize = 13.sp,
                            fontFamily = JakartaFontFamily
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = customAppNameInput,
                            onValueChange = { customAppNameInput = it },
                            placeholder = { Text("App Name (e.g. TikTok, Gallery)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentSky,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                cursorColor = AccentSky
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = customPackageInput,
                            onValueChange = { customPackageInput = it },
                            placeholder = { Text("Package Name (e.g. com.zhiliaoapp.musically)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentSky,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                cursorColor = AccentSky
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (customPackageInput.isNotBlank()) {
                                val pkg = customPackageInput.trim()
                                val name = customAppNameInput.ifBlank { pkg.substringAfterLast('.') }
                                viewModel.toggleAppIsolation(
                                    InstalledAppInfo(
                                        packageName = pkg,
                                        appName = name,
                                        icon = null,
                                        isSystemApp = false,
                                        targetSdkVersion = 34,
                                        isIsolated = true,
                                        isolationMode = IsolationMode.ENCRYPTED_VAULT
                                    ),
                                    enable = true
                                )
                                showAddAppDialog = false
                                customPackageInput = ""
                                customAppNameInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentSky)
                    ) {
                        Text("Add Target", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddAppDialog = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                },
                containerColor = DarkSurfaceElevated,
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

@Composable
private fun ConfiguredAppCard(
    config: AppIsolationConfig,
    onToggleStorage: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("app_card_${config.packageName}")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Squircle App Icon tile
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            when {
                                config.packageName.contains("snapchat") -> Color(0xFFFFFC00).copy(alpha = 0.2f)
                                config.packageName.contains("whatsapp") -> Color(0xFF25D366).copy(alpha = 0.2f)
                                config.packageName.contains("telegram") -> Color(0xFF0088CC).copy(alpha = 0.2f)
                                config.packageName.contains("instagram") -> Color(0xFFE1306C).copy(alpha = 0.2f)
                                else -> AccentSky.copy(alpha = 0.15f)
                            }
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = when {
                            config.packageName.contains("snapchat") -> Color(0xFFFACC15)
                            config.packageName.contains("whatsapp") -> Color(0xFF4ADE80)
                            config.packageName.contains("telegram") -> Color(0xFF38BDF8)
                            config.packageName.contains("instagram") -> Color(0xFFF472B6)
                            else -> AccentSky
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = config.appName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JakartaFontFamily,
                        color = TextWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = config.packageName,
                        fontSize = 12.sp,
                        fontFamily = JakartaFontFamily,
                        color = TextSubtle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Details",
                    tint = TextSubtle,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Toggle: [Isolated Storage] vs [Shared Storage]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceElevated)
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Isolated Storage Option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            if (config.isIsolated) AccentSky else Color.Transparent
                        )
                        .clickable { onToggleStorage(true) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (config.isIsolated) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = "Isolated Storage",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JakartaFontFamily,
                            color = if (config.isIsolated) Color.White else TextMuted
                        )
                    }
                }

                // Shared Storage Option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            if (!config.isIsolated) AccentAmber else Color.Transparent
                        )
                        .clickable { onToggleStorage(false) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!config.isIsolated) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = "Shared Storage",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JakartaFontFamily,
                            color = if (!config.isIsolated) Color.White else TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sub-status row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (config.isIsolated) "Mode: ${config.isolationMode.title}" else "Unrestricted phone storage access",
                    fontSize = 12.sp,
                    fontFamily = JakartaFontFamily,
                    color = if (config.isIsolated) AccentMint else TextMuted
                )

                if (config.isIsolated) {
                    Text(
                        text = "${config.totalFilesRedirected} items protected",
                        fontSize = 11.sp,
                        fontFamily = JakartaFontFamily,
                        color = TextSubtle
                    )
                }
            }
        }
    }
}

@Composable
private fun AppDetailPermissionSheet(
    config: AppIsolationConfig,
    viewModel: IsoVaultViewModel,
    onClose: () -> Unit,
    onOpenExplorer: () -> Unit
) {
    var selectedMode by remember { mutableStateOf(config.isolationMode) }
    var blockPollution by remember { mutableStateOf(config.blockRootPollution) }
    var whitelistInput by remember { mutableStateOf(config.whitelistedFolders) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        // Sheet Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = config.appName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily,
                    color = TextWhite
                )
                Text(
                    text = "Storage & Privacy Settings",
                    fontSize = 12.sp,
                    fontFamily = JakartaFontFamily,
                    color = TextMuted
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceCard)
            ) {
                Icon(Icons.Default.Close, null, tint = TextMuted)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Mode Selector Pills
        Text(
            text = "Isolation Mode",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = JakartaFontFamily,
            color = TextWhite
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                IsolationMode.ENCRYPTED_VAULT to "AES-256 Vault",
                IsolationMode.SANDBOX_REDIRECT to "Sandbox",
                IsolationMode.READ_ONLY to "Read Only"
            ).forEach { (mode, label) ->
                val isSelected = selectedMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) AccentSky else DarkSurfaceCard)
                        .border(1.dp, if (isSelected) AccentSky else DarkBorder, RoundedCornerShape(12.dp))
                        .clickable {
                            selectedMode = mode
                            viewModel.updateAppIsolationMode(config.packageName, mode)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Privacy Features Toggles
        Text(
            text = "Privacy & File System Shields",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = JakartaFontFamily,
            color = TextWhite
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Block Root Pollution Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurfaceCard)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Block Root Storage Pollution",
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Prevents creation of dirty root folders (.tencent, .baidu, etc.)",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Switch(
                checked = blockPollution,
                onCheckedChange = {
                    blockPollution = it
                    viewModel.updateRootPollution(config.packageName, it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentSky,
                    uncheckedTrackColor = DarkSurfaceElevated
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Whitelisted Shared Folders
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurfaceCard)
                .padding(14.dp)
        ) {
            Text(
                text = "Whitelisted Folders (Shared with Phone)",
                color = TextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Folders this app can access directly on phone (e.g. Download)",
                color = TextMuted,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = whitelistInput,
                onValueChange = {
                    whitelistInput = it
                    viewModel.updateWhitelistedFolders(config.packageName, it)
                },
                placeholder = { Text("e.g. Download, Documents") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentSky,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    cursorColor = AccentSky
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Action: Open in MT Manager
        Button(
            onClick = onOpenExplorer,
            colors = ButtonDefaults.buttonColors(containerColor = AccentSky),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            Icon(Icons.Default.FolderOpen, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Browse Sandbox in MT Explorer", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Purge Sandbox Button
        TextButton(
            onClick = {
                viewModel.purgeVaultForPackage(config.packageName)
                onClose()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Delete, null, tint = AccentCoral, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Reset & Purge Sandbox Data", color = AccentCoral, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
