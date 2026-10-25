package com.example.isolation.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.model.IsolationMode
import com.example.isolation.pkg.InstalledAppInfo
import com.example.isolation.ui.screens.apps.AppDetailSheet
import com.example.isolation.ui.screens.apps.AppItemCard
import com.example.isolation.ui.viewmodel.AppListFilter
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentMint
import com.example.ui.theme.AccentSky
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
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
    val filteredApps by viewModel.filteredApps.collectAsState()
    val isLsposedActive by viewModel.isLsposedActive.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentFilter by viewModel.filter.collectAsState()

    var selectedConfig by remember { mutableStateOf<AppIsolationConfig?>(null) }
    var showAddAppDialog by remember { mutableStateOf(false) }
    var customPackageInput by remember { mutableStateOf("") }
    var customAppNameInput by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isolatedCount = configs.count { it.isIsolated }
    val configMap = remember(configs) { configs.associateBy { it.packageName } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 20.dp)
            .testTag("apps_screen")
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Header Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "App Isolation",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily,
                    color = TextWhite
                )
                Text(
                    text = "$isolatedCount apps protected in private lockers",
                    fontSize = 12.sp,
                    color = TextMuted,
                    fontFamily = JakartaFontFamily
                )
            }

            IconButton(
                onClick = { showAddAppDialog = true },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AccentSky)
                    .testTag("btn_add_app")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add App",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Search installed apps...", color = TextSubtle, fontSize = 13.sp) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TextMuted) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentSky,
                unfocusedBorderColor = DarkBorder,
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                focusedContainerColor = DarkSurfaceCard,
                unfocusedContainerColor = DarkSurfaceCard
            ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("apps_search_field")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(
                AppListFilter.ALL to "All",
                AppListFilter.ISOLATED_ONLY to "Isolated ($isolatedCount)",
                AppListFilter.USER_APPS to "User Apps"
            ).forEach { (filterType, label) ->
                val isSelected = currentFilter == filterType
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setFilter(filterType) },
                    label = { Text(label, fontSize = 12.sp, fontFamily = JakartaFontFamily) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentSky.copy(alpha = 0.2f),
                        selectedLabelColor = AccentSky,
                        containerColor = DarkSurfaceElevated,
                        labelColor = TextMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) AccentSky else DarkBorder
                    ),
                    shape = RoundedCornerShape(50)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // App List with stable keys
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(
                items = filteredApps,
                key = { it.packageName }
            ) { appInfo ->
                val cfg = configMap[appInfo.packageName] ?: AppIsolationConfig(
                    packageName = appInfo.packageName,
                    appName = appInfo.appName,
                    isIsolated = appInfo.isIsolated,
                    isolationMode = appInfo.isolationMode,
                    useSharedSpace = appInfo.useSharedSpace
                )
                AppItemCard(
                    config = cfg,
                    onToggle = { enable ->
                        viewModel.toggleAppIsolation(appInfo, enable)
                    },
                    onClick = {
                        selectedConfig = cfg
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    // App Detail Bottom Sheet
    selectedConfig?.let { config ->
        AppDetailSheet(
            config = config,
            sheetState = sheetState,
            onDismiss = { selectedConfig = null },
            onModeChanged = { newMode ->
                viewModel.updateAppIsolationMode(config.packageName, newMode)
                selectedConfig = config.copy(isolationMode = newMode)
            },
            onPollutionChanged = { block ->
                viewModel.updateRootPollution(config.packageName, block)
                selectedConfig = config.copy(blockRootPollution = block)
            },
            onWhitelistChanged = { folders ->
                viewModel.updateWhitelistedFolders(config.packageName, folders)
                selectedConfig = config.copy(whitelistedFolders = folders)
            },
            onSharedSpaceChanged = { useShared ->
                viewModel.updateUseSharedSpace(config.packageName, useShared)
                selectedConfig = config.copy(useSharedSpace = useShared)
            },
            onPurgeVault = {
                viewModel.purgeVaultForPackage(config.packageName)
            }
        )
    }

    // Add App Dialog
    if (showAddAppDialog) {
        AlertDialog(
            onDismissRequest = { showAddAppDialog = false },
            title = { Text("Add Custom Package", color = TextWhite, fontFamily = JakartaFontFamily) },
            text = {
                Column {
                    Text("Enter application package to isolate with Hollow:", color = TextMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customPackageInput,
                        onValueChange = { customPackageInput = it },
                        placeholder = { Text("e.g. com.example.app", color = TextSubtle, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = AccentSky,
                            unfocusedBorderColor = DarkBorder
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customAppNameInput,
                        onValueChange = { customAppNameInput = it },
                        placeholder = { Text("Display Name (optional)", color = TextSubtle, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = AccentSky,
                            unfocusedBorderColor = DarkBorder
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pkg = customPackageInput.trim()
                        if (pkg.isNotBlank()) {
                            val name = customAppNameInput.trim().ifBlank { pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() } }
                            val newInfo = InstalledAppInfo(
                                packageName = pkg,
                                appName = name,
                                isSystemApp = false,
                                targetSdkVersion = 35,
                                isIsolated = true,
                                isolationMode = IsolationMode.ENCRYPTED_VAULT
                            )
                            viewModel.toggleAppIsolation(newInfo, true)
                            showAddAppDialog = false
                            customPackageInput = ""
                            customAppNameInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentSky)
                ) {
                    Text("Isolate", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAppDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }
}
