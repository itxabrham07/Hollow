package com.example.isolation.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.isolation.model.DisguiseIconPreset
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.model.LauncherVisibilityMode
import com.example.isolation.pkg.InstalledAppInfo
import com.example.isolation.ui.components.ContentCard
import com.example.isolation.ui.components.EmptyState
import com.example.isolation.ui.components.ListRow
import com.example.isolation.ui.components.PrimaryButton
import com.example.isolation.ui.components.SecondaryButton
import com.example.isolation.ui.components.SectionHeader
import com.example.isolation.ui.components.rememberAppIconBitmap
import com.example.isolation.ui.viewmodel.IsoVaultViewModel

@Composable
fun AppHiderScreen(
    viewModel: IsoVaultViewModel,
    onBack: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val allApps by viewModel.allInstalledApps.collectAsState()

    var showAllAppsMode by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var includeSystemApps by remember { mutableStateOf(false) }
    var selectedAppForStealth by remember { mutableStateOf<InstalledAppInfo?>(null) }

    val activeStealthApps = remember(allApps) {
        allApps.filter {
            (it.isHiddenOrDisguised && it.launcherVisibilityMode != LauncherVisibilityMode.VISIBLE) ||
                    it.hideFromPlayStore
        }
    }

    val displayedApps = remember(allApps, activeStealthApps, showAllAppsMode, searchQuery, includeSystemApps) {
        val base = if (showAllAppsMode) {
            allApps.filter { includeSystemApps || !it.isSystemApp }
        } else {
            activeStealthApps
        }
        if (searchQuery.isBlank()) {
            base
        } else {
            base.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true) ||
                        it.customLauncherLabel.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("app_hider_screen")
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Top Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
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
                            tint = scheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (showAllAppsMode) "Select Apps" else "Hide App",
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (showAllAppsMode) {
                            "Tap any app to hide, spoof icon/name, or hide from Play Store"
                        } else {
                            "${activeStealthApps.size} app${if (activeStealthApps.size == 1) "" else "s"} hidden or disguised"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = { viewModel.refreshLauncherNow() },
                modifier = Modifier.testTag("btn_refresh_launcher")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Home Screen & Icon Cache",
                    tint = scheme.primary
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            if (!showAllAppsMode) {
                PrimaryButton(
                    text = "Add Apps",
                    icon = Icons.Default.Add,
                    onClick = { showAllAppsMode = true },
                    modifier = Modifier.testTag("btn_add_stealth_apps")
                )
            } else {
                SecondaryButton(
                    text = "Done",
                    icon = Icons.Default.Check,
                    onClick = {
                        showAllAppsMode = false
                        searchQuery = ""
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (showAllAppsMode || activeStealthApps.isNotEmpty() || searchQuery.isNotBlank()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search apps...",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                        IconButton(onClick = { searchQuery = "" }) {
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
                    .testTag("stealth_search_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (showAllAppsMode) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = !includeSystemApps,
                        onClick = { includeSystemApps = false },
                        label = {
                            Text(
                                text = "Downloaded",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        },
                        shape = MaterialTheme.shapes.extraSmall,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = scheme.primaryContainer,
                            selectedLabelColor = scheme.onPrimaryContainer
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = includeSystemApps,
                        onClick = { includeSystemApps = true },
                        label = {
                            Text(
                                text = "Include System",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        },
                        shape = MaterialTheme.shapes.extraSmall,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = scheme.primaryContainer,
                            selectedLabelColor = scheme.onPrimaryContainer
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (displayedApps.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.VisibilityOff,
                        title = if (searchQuery.isNotBlank()) "No matching apps found" else "No hidden or disguised apps yet",
                        message = "Hide apps from launchers & Play Store, or spoof their name & custom icon in Launcher and Settings.",
                        actionLabel = if (!showAllAppsMode) "Select Apps to Hide / Disguise" else null,
                        onAction = if (!showAllAppsMode) { { showAllAppsMode = true } } else null
                    )
                }
            } else {
                items(displayedApps, key = { it.packageName }) { appInfo ->
                    val isActive = (appInfo.isHiddenOrDisguised && appInfo.launcherVisibilityMode != LauncherVisibilityMode.VISIBLE) ||
                            appInfo.hideFromPlayStore

                    val customPreviewBitmap: ImageBitmap? = remember(appInfo.customIconBase64) {
                        decodeBase64ToImageBitmap(appInfo.customIconBase64)
                    }
                    val iconBitmap = rememberAppIconBitmap(
                        packageName = appInfo.packageName,
                        drawable = appInfo.icon,
                        sizePx = 96
                    )
                    val displayBitmap = if (appInfo.launcherVisibilityMode == LauncherVisibilityMode.DISGUISED && customPreviewBitmap != null) {
                        customPreviewBitmap
                    } else {
                        iconBitmap
                    }

                    val statusSummary = buildString {
                        when (appInfo.launcherVisibilityMode) {
                            LauncherVisibilityMode.HIDDEN -> append("Hidden from launchers")
                            LauncherVisibilityMode.DISGUISED -> {
                                val shownName = appInfo.customLauncherLabel.ifBlank {
                                    appInfo.disguiseIconPreset.suggestedName.ifBlank { "Calculator" }
                                }
                                append("As \"$shownName\"")
                                if (appInfo.spoofInSettings) append(" • Settings")
                            }
                            LauncherVisibilityMode.VISIBLE -> {
                                if (appInfo.hideFromPlayStore) {
                                    append("Visible in launcher")
                                } else {
                                    append("Visible • Tap to hide or disguise")
                                }
                            }
                        }
                        if (appInfo.hideFromPlayStore) {
                            append(" • Hidden in Play Store")
                        }
                    }

                    val statusLabel = if (isActive) {
                        if (appInfo.launcherVisibilityMode == LauncherVisibilityMode.VISIBLE && appInfo.hideFromPlayStore) {
                            "Play Store"
                        } else {
                            appInfo.launcherVisibilityMode.badge
                        }
                    } else null

                    ContentCard(
                        onClick = { selectedAppForStealth = appInfo },
                        borderColor = if (isActive) scheme.secondary else scheme.outlineVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListRow(
                            title = appInfo.appName,
                            subtitle = statusSummary,
                            monoSubtitle = appInfo.packageName,
                            leadingBitmap = displayBitmap,
                            leadingIcon = Icons.Default.VisibilityOff,
                            statusLabel = statusLabel,
                            statusColor = scheme.secondary,
                            modifier = Modifier.padding(vertical = 0.dp),
                            trailing = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isActive) {
                                        IconButton(
                                            onClick = { viewModel.launchAppFromHollow(appInfo) },
                                            modifier = Modifier.testTag("btn_launch_${appInfo.packageName}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                contentDescription = "Launch ${appInfo.appName}",
                                                tint = scheme.primary
                                            )
                                        }
                                    }
                                    SecondaryButton(
                                        text = if (isActive) "Edit" else "Set",
                                        icon = Icons.Default.Tune,
                                        onClick = { selectedAppForStealth = appInfo }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    selectedAppForStealth?.let { targetApp ->
        AppStealthConfigDialog(
            appInfo = targetApp,
            allInstalledApps = allApps,
            onDecodeImageUri = { uri, cb -> viewModel.decodeImageUriToBase64(uri, cb) },
            onDismiss = { selectedAppForStealth = null },
            onSave = { mode, customName, iconPreset, hideRecents, hidePlayStore, spoofSettings, customIconB64, donorPkg ->
                viewModel.saveAppStealthConfig(
                    appInfo = targetApp,
                    visibilityMode = mode,
                    customLabel = customName,
                    iconPreset = iconPreset,
                    hideInRecents = hideRecents,
                    hideFromPlayStore = hidePlayStore,
                    spoofInSettings = spoofSettings,
                    customIconBase64 = customIconB64,
                    customIconDonorPkg = donorPkg
                )
                selectedAppForStealth = null
            }
        )
    }
}

@Composable
private fun AppStealthConfigDialog(
    appInfo: InstalledAppInfo,
    allInstalledApps: List<InstalledAppInfo>,
    onDecodeImageUri: (android.net.Uri, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        LauncherVisibilityMode,
        String,
        DisguiseIconPreset,
        Boolean,
        Boolean,
        Boolean,
        String,
        String
    ) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var mode by remember {
        mutableStateOf(
            if (appInfo.launcherVisibilityMode == LauncherVisibilityMode.VISIBLE && !appInfo.hideFromPlayStore) {
                LauncherVisibilityMode.DISGUISED
            } else {
                appInfo.launcherVisibilityMode
            }
        )
    }
    var customName by remember {
        mutableStateOf(
            appInfo.customLauncherLabel.ifBlank { "Calculator" }
        )
    }
    var iconPreset by remember { mutableStateOf(appInfo.disguiseIconPreset) }
    var hideInRecents by remember { mutableStateOf(appInfo.hideInSystemUiRecents) }
    var hideFromPlayStore by remember { mutableStateOf(appInfo.hideFromPlayStore) }
    var spoofInSettings by remember { mutableStateOf(appInfo.spoofInSettings) }
    var customIconBase64 by remember { mutableStateOf(appInfo.customIconBase64) }
    var customIconDonorPkg by remember { mutableStateOf(appInfo.customIconDonorPkg) }
    var showDonorAppPicker by remember { mutableStateOf(false) }
    var donorSearch by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onDecodeImageUri(uri) { b64 ->
                if (!b64.isNullOrBlank()) {
                    customIconBase64 = b64
                    iconPreset = DisguiseIconPreset.CUSTOM_IMAGE
                }
            }
        }
    }

    val quickSuggestions = listOf("Calculator", "Notes", "Calendar", "Clock", "Weather", "System Service")

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Column {
                Text(
                    text = appInfo.appName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Launcher, Play Store & System Settings Stealth",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LauncherVisibilityMode.entries.forEach { option ->
                    ContentCard(
                        onClick = { mode = option },
                        containerColor = if (mode == option) scheme.primaryContainer else scheme.surfaceContainerLow,
                        borderColor = if (mode == option) scheme.primary else scheme.outlineVariant,
                        contentPadding = PaddingValues(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListRow(
                            title = option.title,
                            subtitle = option.description,
                            modifier = Modifier.padding(vertical = 0.dp),
                            trailing = {
                                RadioButton(
                                    selected = mode == option,
                                    onClick = { mode = option }
                                )
                            }
                        )
                    }
                }

                // Play Store Hiding Toggle (Always available)
                HorizontalDivider(color = scheme.outlineVariant)
                ListRow(
                    title = "Hide from Google Play Store",
                    subtitle = "Hides app from Play Store's installed apps list, updates & Play Protect.",
                    onClick = { hideFromPlayStore = !hideFromPlayStore },
                    trailing = {
                        Switch(
                            checked = hideFromPlayStore,
                            onCheckedChange = { hideFromPlayStore = it },
                            modifier = Modifier.testTag("switch_hide_play_store")
                        )
                    }
                )

                if (mode == LauncherVisibilityMode.DISGUISED) {
                    HorizontalDivider(color = scheme.outlineVariant)
                    SectionHeader(title = "Custom App Name")
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Spoofed Name in Launcher & Settings", style = MaterialTheme.typography.bodySmall) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_custom_launcher_name")
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        quickSuggestions.forEach { presetName ->
                            AssistChip(
                                onClick = { customName = presetName },
                                label = { Text(presetName, style = MaterialTheme.typography.labelMedium) },
                                shape = MaterialTheme.shapes.extraSmall
                            )
                        }
                    }

                    SectionHeader(title = "Custom Icon Source")

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SecondaryButton(
                            text = "Custom Image",
                            icon = Icons.Default.Image,
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_pick_custom_icon_image")
                        )

                        SecondaryButton(
                            text = "Clone App Icon",
                            icon = Icons.Default.Apps,
                            onClick = { showDonorAppPicker = !showDonorAppPicker },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_clone_app_icon")
                        )
                    }

                    // Show preview if Custom Image or Cloned App is active
                    val customPreview = remember(customIconBase64) {
                        decodeBase64ToImageBitmap(customIconBase64)
                    }
                    if ((iconPreset == DisguiseIconPreset.CUSTOM_IMAGE || iconPreset == DisguiseIconPreset.CLONED_APP) &&
                        (customPreview != null || customIconDonorPkg.isNotBlank())
                    ) {
                        ContentCard(
                            containerColor = scheme.surfaceContainerLow,
                            borderColor = scheme.primary,
                            contentPadding = PaddingValues(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ListRow(
                                title = if (iconPreset == DisguiseIconPreset.CUSTOM_IMAGE) {
                                    "Custom Gallery Icon Selected"
                                } else {
                                    "Cloned Icon: ${customIconDonorPkg.substringAfterLast('.')}"
                                },
                                subtitle = "Applied to Launcher, System Settings & Recents",
                                leadingBitmap = customPreview,
                                leadingIcon = Icons.Default.Image,
                                modifier = Modifier.padding(vertical = 0.dp)
                            )
                        }
                    }

                    // Expandable Donor App Picker
                    if (showDonorAppPicker) {
                        ContentCard(
                            containerColor = scheme.surfaceContainerLow,
                            contentPadding = PaddingValues(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = donorSearch,
                                onValueChange = { donorSearch = it },
                                placeholder = { Text("Search installed app to copy icon...", style = MaterialTheme.typography.bodySmall) },
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val donorCandidates = remember(allInstalledApps, donorSearch) {
                                allInstalledApps.filter {
                                    it.packageName != appInfo.packageName &&
                                            (donorSearch.isBlank() ||
                                                    it.appName.contains(donorSearch, ignoreCase = true) ||
                                                    it.packageName.contains(donorSearch, ignoreCase = true))
                                }.take(8)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                donorCandidates.forEach { candidate ->
                                    val bmp = remember(candidate.packageName, candidate.icon) {
                                        try {
                                            candidate.icon?.toBitmap(64, 64)?.asImageBitmap()
                                        } catch (_: Exception) {
                                            null
                                        }
                                    }
                                    ListRow(
                                        title = candidate.appName,
                                        monoSubtitle = candidate.packageName,
                                        leadingBitmap = bmp,
                                        leadingIcon = Icons.Default.Apps,
                                        onClick = {
                                            customIconDonorPkg = candidate.packageName
                                            iconPreset = DisguiseIconPreset.CLONED_APP
                                            customName = candidate.appName
                                            customIconBase64 = IsolationRuleManager
                                                .drawableToBase64Png(candidate.icon, 144)
                                            showDonorAppPicker = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    SectionHeader(title = "Or Built-in Icon Presets")

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        DisguiseIconPreset.entries
                            .filter { it != DisguiseIconPreset.CUSTOM_IMAGE && it != DisguiseIconPreset.CLONED_APP }
                            .forEach { preset ->
                                FilterChip(
                                    selected = iconPreset == preset,
                                    onClick = {
                                        iconPreset = preset
                                        if (preset.suggestedName.isNotBlank()) {
                                            customName = preset.suggestedName
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = presetIconVector(preset),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = {
                                        Text(preset.label, style = MaterialTheme.typography.labelMedium)
                                    },
                                    shape = MaterialTheme.shapes.extraSmall
                                )
                            }
                    }

                    HorizontalDivider(color = scheme.outlineVariant)

                    // Spoof in System Settings / App Management List
                    ListRow(
                        title = "Spoof in System Settings (App List)",
                        subtitle = "Replaces name and icon inside Android Settings → Apps / App Management.",
                        onClick = { spoofInSettings = !spoofInSettings },
                        trailing = {
                            Switch(
                                checked = spoofInSettings,
                                onCheckedChange = { spoofInSettings = it },
                                modifier = Modifier.testTag("switch_spoof_settings")
                            )
                        }
                    )

                    // Disguise in SystemUI Recents
                    ListRow(
                        title = "Disguise in SystemUI Recents",
                        subtitle = "Shows the spoofed name and icon in the Recents switcher.",
                        onClick = { hideInRecents = !hideInRecents },
                        trailing = {
                            Switch(
                                checked = hideInRecents,
                                onCheckedChange = { hideInRecents = it }
                            )
                        }
                    )
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "Apply Stealth",
                onClick = {
                    onSave(
                        mode,
                        customName,
                        iconPreset,
                        hideInRecents,
                        hideFromPlayStore,
                        spoofInSettings,
                        customIconBase64,
                        customIconDonorPkg
                    )
                }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Composable
internal fun HollowSelfStealthDialog(
    currentMode: LauncherVisibilityMode,
    currentCustomName: String,
    currentIconPreset: DisguiseIconPreset,
    currentSystemUiTrigger: Boolean,
    currentCustomIconBase64: String = "",
    currentCustomIconDonorPkg: String = "",
    currentSpoofInSettings: Boolean = true,
    onDecodeImageUri: ((android.net.Uri, (String?) -> Unit) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (LauncherVisibilityMode, String, DisguiseIconPreset, Boolean, String, String, Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var mode by remember { mutableStateOf(currentMode) }
    var customName by remember { mutableStateOf(currentCustomName) }
    var iconPreset by remember { mutableStateOf(currentIconPreset) }
    var sysUiTrigger by remember { mutableStateOf(currentSystemUiTrigger) }
    var customIconB64 by remember { mutableStateOf(currentCustomIconBase64) }
    var donorPkg by remember { mutableStateOf(currentCustomIconDonorPkg) }
    var spoofSettings by remember { mutableStateOf(currentSpoofInSettings) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && onDecodeImageUri != null) {
            onDecodeImageUri(uri) { b64 ->
                if (!b64.isNullOrBlank()) {
                    customIconB64 = b64
                    iconPreset = DisguiseIconPreset.CUSTOM_IMAGE
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Column {
                Text(
                    text = "Hollow Stealth & SystemUI Entry",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Hide or disguise Hollow itself and open it directly from SystemUI",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ContentCard(
                    containerColor = scheme.surfaceContainerLow,
                    contentPadding = PaddingValues(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListRow(
                        title = "Direct SystemUI Trigger",
                        subtitle = "Long-press the Date in SystemUI Quick Settings, tap 'Quick Calc' tile, or dial *#*#465569#*#* to open Hollow when hidden.",
                        onClick = { sysUiTrigger = !sysUiTrigger },
                        modifier = Modifier.padding(vertical = 0.dp),
                        trailing = {
                            Switch(
                                checked = sysUiTrigger,
                                onCheckedChange = { sysUiTrigger = it }
                            )
                        }
                    )
                }

                LauncherVisibilityMode.entries.forEach { option ->
                    ContentCard(
                        onClick = { mode = option },
                        containerColor = if (mode == option) scheme.primaryContainer else scheme.surfaceContainerLow,
                        borderColor = if (mode == option) scheme.primary else scheme.outlineVariant,
                        contentPadding = PaddingValues(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListRow(
                            title = option.title,
                            subtitle = option.description,
                            modifier = Modifier.padding(vertical = 0.dp),
                            trailing = {
                                RadioButton(
                                    selected = mode == option,
                                    onClick = { mode = option }
                                )
                            }
                        )
                    }
                }

                if (mode == LauncherVisibilityMode.DISGUISED) {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Custom Name for Hollow in Launchers & Settings", style = MaterialTheme.typography.bodySmall) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (onDecodeImageUri != null) {
                        SecondaryButton(
                            text = "Pick Custom Icon Image from Gallery",
                            icon = Icons.Default.Image,
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        DisguiseIconPreset.entries
                            .filter { it != DisguiseIconPreset.DEFAULT && it != DisguiseIconPreset.CUSTOM_IMAGE && it != DisguiseIconPreset.CLONED_APP }
                            .forEach { preset ->
                                FilterChip(
                                    selected = iconPreset == preset,
                                    onClick = {
                                        iconPreset = preset
                                        if (preset.suggestedName.isNotBlank()) {
                                            customName = preset.suggestedName
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = presetIconVector(preset),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = {
                                        Text(preset.label, style = MaterialTheme.typography.labelMedium)
                                    },
                                    shape = MaterialTheme.shapes.extraSmall
                                )
                            }
                    }

                    ListRow(
                        title = "Spoof Hollow in System Settings",
                        subtitle = "Also disguises Hollow inside Settings → Apps.",
                        onClick = { spoofSettings = !spoofSettings },
                        trailing = {
                            Switch(
                                checked = spoofSettings,
                                onCheckedChange = { spoofSettings = it }
                            )
                        }
                    )
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "Save",
                onClick = { onSave(mode, customName, iconPreset, sysUiTrigger, customIconB64, donorPkg, spoofSettings) }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

private fun decodeBase64ToImageBitmap(b64: String): ImageBitmap? {
    if (b64.isBlank()) return null
    return try {
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}

@Composable
internal fun presetIconVector(preset: DisguiseIconPreset): ImageVector {
    return when (preset) {
        DisguiseIconPreset.CUSTOM_IMAGE -> Icons.Default.Image
        DisguiseIconPreset.CLONED_APP -> Icons.Default.Apps
        DisguiseIconPreset.DEFAULT -> Icons.Default.Shield
        DisguiseIconPreset.CALCULATOR -> Icons.Default.Calculate
        DisguiseIconPreset.NOTES -> Icons.Default.Description
        DisguiseIconPreset.CALENDAR -> Icons.Default.CalendarMonth
        DisguiseIconPreset.CLOCK -> Icons.Default.Schedule
        DisguiseIconPreset.WEATHER -> Icons.Default.Cloud
        DisguiseIconPreset.SYSTEM_UTILITY -> Icons.Default.Settings
    }
}
