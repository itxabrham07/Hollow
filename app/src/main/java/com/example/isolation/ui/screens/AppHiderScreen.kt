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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.isolation.model.DisguiseIconPreset
import com.example.isolation.model.LauncherVisibilityMode
import com.example.isolation.pkg.InstalledAppInfo
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.JakartaFontFamily

@Composable
fun AppHiderScreen(
    viewModel: IsoVaultViewModel,
    onBack: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val allApps by viewModel.allInstalledApps.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var showAllAppsMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var includeSystemApps by remember { mutableStateOf(false) }
    var selectedAppForStealth by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var showHollowStealthDialog by remember { mutableStateOf(false) }

    val activeStealthApps = remember(allApps) {
        allApps.filter { it.isHiddenOrDisguised && it.launcherVisibilityMode != LauncherVisibilityMode.VISIBLE }
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
        Spacer(modifier = Modifier.height(4.dp))

        // Top Header with Back Button and Single "+ Add Apps" Action
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
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = scheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Column {
                    Text(
                        text = if (showAllAppsMode) "Select Apps to Hide / Disguise" else "Hide & Disguise Apps",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onBackground,
                        fontFamily = JakartaFontFamily
                    )
                    Text(
                        text = if (showAllAppsMode) {
                            "Independent from Storage Protection • Works in any launcher"
                        } else {
                            "${activeStealthApps.size} app${if (activeStealthApps.size == 1) "" else "s"} hidden or disguised via System & SystemUI"
                        },
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                        fontFamily = JakartaFontFamily
                    )
                }
            }

            if (!showAllAppsMode) {
                FilledTonalButton(
                    onClick = { showAllAppsMode = true },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("btn_add_stealth_apps")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Apps", fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold)
                }
            } else {
                OutlinedButton(
                    onClick = {
                        showAllAppsMode = false
                        searchQuery = ""
                    },
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Done", fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Hollow Self-Stealth & SystemUI Direct Injection Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = scheme.surface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.4f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showHollowStealthDialog = true }
                .testTag("card_hollow_self_stealth")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(14.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = presetIconVector(settings.hollowIconPreset),
                        contentDescription = null,
                        tint = scheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Hollow Stealth & SystemUI Entry",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            fontFamily = JakartaFontFamily
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(scheme.primaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = when (settings.hollowVisibilityMode) {
                                    LauncherVisibilityMode.VISIBLE -> "Visible"
                                    LauncherVisibilityMode.DISGUISED -> "As \"${settings.hollowCustomName}\""
                                    LauncherVisibilityMode.HIDDEN -> "Hidden"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onPrimaryContainer,
                                fontFamily = JakartaFontFamily
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (settings.systemUiTriggerEnabled) {
                            "SystemUI Trigger Active: Long-press Quick Settings Clock/Header or use Quick Calc tile to open Hollow"
                        } else {
                            "Tap to hide or disguise Hollow itself and configure SystemUI Quick Settings entry"
                        },
                        fontSize = 11.sp,
                        color = scheme.onSurfaceVariant,
                        fontFamily = JakartaFontFamily
                    )
                }
                IconButton(onClick = { showHollowStealthDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Configure Hollow Stealth",
                        tint = scheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (showAllAppsMode || activeStealthApps.size > 3 || searchQuery.isNotBlank()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search apps by name or custom label...",
                        color = scheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontFamily = JakartaFontFamily
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
                singleLine = true,
                shape = RoundedCornerShape(50),
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = !includeSystemApps,
                    onClick = { includeSystemApps = false },
                    label = { Text("Downloaded Apps", fontFamily = JakartaFontFamily) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = scheme.primaryContainer,
                        selectedLabelColor = scheme.onPrimaryContainer
                    )
                )
                FilterChip(
                    selected = includeSystemApps,
                    onClick = { includeSystemApps = true },
                    label = { Text("Include System Apps", fontFamily = JakartaFontFamily) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = scheme.primaryContainer,
                        selectedLabelColor = scheme.onPrimaryContainer
                    )
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (displayedApps.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = scheme.surface),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(28.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(scheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = scheme.onPrimaryContainer,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                "No matching apps found"
                            } else {
                                "No hidden or disguised apps yet"
                            },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            fontFamily = JakartaFontFamily
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Hide apps completely from every launcher drawer or disguise their launcher name and icon via System Framework & SystemUI hooks.",
                            fontSize = 13.sp,
                            color = scheme.onSurfaceVariant,
                            fontFamily = JakartaFontFamily
                        )
                        if (!showAllAppsMode) {
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = { showAllAppsMode = true },
                                shape = RoundedCornerShape(50)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select Apps to Hide or Disguise", fontFamily = JakartaFontFamily)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(displayedApps, key = { it.packageName }) { appInfo ->
                    StealthAppCard(
                        appInfo = appInfo,
                        onConfigure = { selectedAppForStealth = appInfo },
                        onLaunch = { viewModel.launchAppFromHollow(appInfo) }
                    )
                }
            }
        }
    }

    selectedAppForStealth?.let { targetApp ->
        AppStealthConfigDialog(
            appInfo = targetApp,
            onDismiss = { selectedAppForStealth = null },
            onSave = { mode, customName, iconPreset, hideRecents ->
                viewModel.saveAppStealthConfig(
                    appInfo = targetApp,
                    visibilityMode = mode,
                    customLabel = customName,
                    iconPreset = iconPreset,
                    hideInRecents = hideRecents
                )
                selectedAppForStealth = null
            }
        )
    }

    if (showHollowStealthDialog) {
        HollowSelfStealthDialog(
            currentMode = settings.hollowVisibilityMode,
            currentCustomName = settings.hollowCustomName,
            currentIconPreset = settings.hollowIconPreset,
            currentSystemUiTrigger = settings.systemUiTriggerEnabled,
            onDismiss = { showHollowStealthDialog = false },
            onSave = { mode, customName, iconPreset, sysUiTrigger ->
                viewModel.updateHollowStealthConfig(
                    visibilityMode = mode,
                    customName = customName,
                    iconPreset = iconPreset,
                    systemUiTriggerEnabled = sysUiTrigger
                )
                showHollowStealthDialog = false
            }
        )
    }
}

@Composable
private fun StealthAppCard(
    appInfo: InstalledAppInfo,
    onConfigure: () -> Unit,
    onLaunch: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isActive = appInfo.isHiddenOrDisguised && appInfo.launcherVisibilityMode != LauncherVisibilityMode.VISIBLE

    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) scheme.primary.copy(alpha = 0.45f) else scheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConfigure() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val iconBitmap = remember(appInfo.packageName, appInfo.icon) {
                    try {
                        appInfo.icon?.toBitmap(width = 96, height = 96)?.asImageBitmap()
                    } catch (_: Exception) {
                        null
                    }
                }
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = appInfo.appName,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = scheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appInfo.appName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = JakartaFontFamily
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when (appInfo.launcherVisibilityMode) {
                            LauncherVisibilityMode.HIDDEN ->
                                "Hidden from all launchers & app drawers"
                            LauncherVisibilityMode.DISGUISED -> {
                                val shownName = appInfo.customLauncherLabel.ifBlank {
                                    appInfo.disguiseIconPreset.suggestedName.ifBlank { "Calculator" }
                                }
                                "Disguised as \"$shownName\" (${appInfo.disguiseIconPreset.label} icon)"
                            }
                            LauncherVisibilityMode.VISIBLE ->
                                "Visible normally in launcher • Tap to hide or disguise"
                        },
                        fontSize = 12.sp,
                        color = if (isActive) scheme.primary else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = JakartaFontFamily
                    )
                }

                if (isActive) {
                    IconButton(
                        onClick = onLaunch,
                        modifier = Modifier.testTag("btn_launch_${appInfo.packageName}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Launch ${appInfo.appName}",
                            tint = scheme.primary
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onConfigure,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isActive) appInfo.launcherVisibilityMode.badge else "Configure",
                        fontSize = 12.sp,
                        fontFamily = JakartaFontFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun AppStealthConfigDialog(
    appInfo: InstalledAppInfo,
    onDismiss: () -> Unit,
    onSave: (LauncherVisibilityMode, String, DisguiseIconPreset, Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var mode by remember {
        mutableStateOf(
            if (appInfo.launcherVisibilityMode == LauncherVisibilityMode.VISIBLE) {
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

    val quickSuggestions = listOf("Calculator", "Notes", "Calendar", "Clock", "Weather", "System Service")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = appInfo.appName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    fontFamily = JakartaFontFamily
                )
                Text(
                    text = "System-Wide Launcher & SystemUI Stealth",
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                    fontFamily = JakartaFontFamily
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LauncherVisibilityMode.entries.forEach { option ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (mode == option) scheme.primaryContainer.copy(alpha = 0.35f)
                            else scheme.surfaceContainerLow
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.dp,
                            if (mode == option) scheme.primary else scheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { mode = option }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            RadioButton(
                                selected = mode == option,
                                onClick = { mode = option }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    fontFamily = JakartaFontFamily
                                )
                                Text(
                                    text = option.description,
                                    fontSize = 11.sp,
                                    color = scheme.onSurfaceVariant,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                        }
                    }
                }

                if (mode == LauncherVisibilityMode.DISGUISED) {
                    HorizontalDivider(color = scheme.outlineVariant)
                    Text(
                        text = "Custom App Name in Launcher",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        fontFamily = JakartaFontFamily
                    )
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Custom Name Shown in Launchers") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_custom_launcher_name")
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        quickSuggestions.forEach { presetName ->
                            AssistChip(
                                onClick = { customName = presetName },
                                label = { Text(presetName, fontSize = 11.sp, fontFamily = JakartaFontFamily) }
                            )
                        }
                    }

                    Text(
                        text = "Decoy Launcher Icon",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        fontFamily = JakartaFontFamily
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        DisguiseIconPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = iconPreset == preset,
                                onClick = {
                                    iconPreset = preset
                                    if (preset.suggestedName.isNotBlank() && customName.isBlank()) {
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
                                    Text(preset.label, fontSize = 12.sp, fontFamily = JakartaFontFamily)
                                }
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Disguise in SystemUI Recents",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                fontFamily = JakartaFontFamily
                            )
                            Text(
                                text = "Also shows the spoofed name and icon in the Recents switcher.",
                                fontSize = 11.sp,
                                color = scheme.onSurfaceVariant,
                                fontFamily = JakartaFontFamily
                            )
                        }
                        Switch(
                            checked = hideInRecents,
                            onCheckedChange = { hideInRecents = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(mode, customName, iconPreset, hideInRecents) },
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary)
            ) {
                Text("Apply Stealth", fontFamily = JakartaFontFamily, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = JakartaFontFamily)
            }
        }
    )
}

@Composable
private fun HollowSelfStealthDialog(
    currentMode: LauncherVisibilityMode,
    currentCustomName: String,
    currentIconPreset: DisguiseIconPreset,
    currentSystemUiTrigger: Boolean,
    onDismiss: () -> Unit,
    onSave: (LauncherVisibilityMode, String, DisguiseIconPreset, Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var mode by remember { mutableStateOf(currentMode) }
    var customName by remember { mutableStateOf(currentCustomName) }
    var iconPreset by remember { mutableStateOf(currentIconPreset) }
    var sysUiTrigger by remember { mutableStateOf(currentSystemUiTrigger) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Hollow Stealth & SystemUI Entry",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    fontFamily = JakartaFontFamily
                )
                Text(
                    text = "Hide or disguise Hollow itself and open it directly from SystemUI",
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                    fontFamily = JakartaFontFamily
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Direct SystemUI Trigger",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = JakartaFontFamily
                            )
                            Text(
                                text = "Long-press the Clock or Date in SystemUI Quick Settings (or tap the 'Quick Calc' tile) to open Hollow even when completely hidden.",
                                fontSize = 11.sp,
                                color = scheme.onSurfaceVariant,
                                fontFamily = JakartaFontFamily
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = sysUiTrigger,
                            onCheckedChange = { sysUiTrigger = it }
                        )
                    }
                }

                LauncherVisibilityMode.entries.forEach { option ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (mode == option) scheme.primaryContainer.copy(alpha = 0.35f)
                            else scheme.surfaceContainerLow
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.dp,
                            if (mode == option) scheme.primary else scheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { mode = option }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            RadioButton(
                                selected = mode == option,
                                onClick = { mode = option }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    fontFamily = JakartaFontFamily
                                )
                                Text(
                                    text = option.description,
                                    fontSize = 11.sp,
                                    color = scheme.onSurfaceVariant,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                        }
                    }
                }

                if (mode == LauncherVisibilityMode.DISGUISED) {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Custom Name for Hollow in Launchers") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        DisguiseIconPreset.entries.filter { it != DisguiseIconPreset.DEFAULT }.forEach { preset ->
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
                                    Text(preset.label, fontSize = 12.sp, fontFamily = JakartaFontFamily)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(mode, customName, iconPreset, sysUiTrigger) }
            ) {
                Text("Save", fontFamily = JakartaFontFamily, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = JakartaFontFamily)
            }
        }
    )
}

@Composable
private fun presetIconVector(preset: DisguiseIconPreset): ImageVector {
    return when (preset) {
        DisguiseIconPreset.DEFAULT -> Icons.Default.Shield
        DisguiseIconPreset.CALCULATOR -> Icons.Default.Calculate
        DisguiseIconPreset.NOTES -> Icons.Default.Description
        DisguiseIconPreset.CALENDAR -> Icons.Default.CalendarMonth
        DisguiseIconPreset.CLOCK -> Icons.Default.Schedule
        DisguiseIconPreset.WEATHER -> Icons.Default.Cloud
        DisguiseIconPreset.SYSTEM_UTILITY -> Icons.Default.Settings
    }
}
