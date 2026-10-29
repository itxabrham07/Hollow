package com.example.isolation.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.isolation.model.DeviceProfilePreset
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.model.PrivacyProfile
import com.example.isolation.pkg.InstalledAppInfo
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.JakartaFontFamily

private enum class PrivacyHubTab(val title: String) {
    APPS("Apps"),
    PROFILES("Profiles")
}

private enum class PrivacyAppFilter {
    SPOOFED_ONLY,
    USER_APPS,
    SYSTEM_APPS,
    ALL
}

@Composable
fun DeviceSpooferScreen(
    viewModel: IsoVaultViewModel,
    onBack: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val allApps by viewModel.allInstalledApps.collectAsState()
    val privacyProfiles by viewModel.privacyProfiles.collectAsState()

    var selectedTab by remember { mutableStateOf(PrivacyHubTab.APPS) }
    var searchQuery by remember { mutableStateOf("") }
    var showAllAppsMode by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf(PrivacyAppFilter.SPOOFED_ONLY) }
    var editingApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var editingProfile by remember { mutableStateOf<PrivacyProfile?>(null) }

    BackHandler {
        when {
            editingApp != null -> editingApp = null
            showAllAppsMode -> {
                showAllAppsMode = false
                activeFilter = PrivacyAppFilter.SPOOFED_ONLY
                searchQuery = ""
            }
            selectedTab == PrivacyHubTab.PROFILES -> selectedTab = PrivacyHubTab.APPS
            else -> onBack()
        }
    }

    // Full-screen per-app configuration page when an app is clicked
    editingApp?.let { target ->
        ConfigurePrivacyCategorizedScreen(
            appInfo = target,
            viewModel = viewModel,
            onBack = { editingApp = null }
        )
        return
    }

    val spoofedAppsCount = remember(allApps) {
        allApps.count {
            it.isIdentitySpoofed || it.spoofLocationEnabled || it.preventScreenshots ||
                    it.hideOtherApps || it.disableLogcatLogging || it.fakeBatteryEnabled || it.disableAllNetworking ||
                    it.spoofGpuFingerprint || it.spoofBrowserUserAgent || it.anonymizeSensors || it.blockCellTowerTracking || it.autoSyncTimezone
        }
    }

    val displayedApps = remember(allApps, searchQuery, showAllAppsMode, activeFilter) {
        allApps.filter { app ->
            val hasShields = app.isIdentitySpoofed || app.spoofLocationEnabled || app.preventScreenshots ||
                    app.hideOtherApps || app.disableLogcatLogging || app.fakeBatteryEnabled || app.disableAllNetworking ||
                    app.spoofGpuFingerprint || app.spoofBrowserUserAgent || app.anonymizeSensors || app.blockCellTowerTracking || app.autoSyncTimezone
            val matchesFilter = when (activeFilter) {
                PrivacyAppFilter.SPOOFED_ONLY -> hasShields
                PrivacyAppFilter.USER_APPS -> !app.isSystemApp
                PrivacyAppFilter.SYSTEM_APPS -> app.isSystemApp
                PrivacyAppFilter.ALL -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true) ||
                    app.spoofModel.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("device_spoofer_screen")
    ) {
        Spacer(modifier = Modifier.height(4.dp))

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
                IconButton(
                    onClick = {
                        if (showAllAppsMode) {
                            showAllAppsMode = false
                            activeFilter = PrivacyAppFilter.SPOOFED_ONLY
                            searchQuery = ""
                        } else {
                            onBack()
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("device_spoofer_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = scheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (showAllAppsMode) "Select Apps" else "Privacy",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = JakartaFontFamily
                    )
                    Text(
                        text = if (selectedTab == PrivacyHubTab.PROFILES) {
                            "${privacyProfiles.size} reusable privacy profiles (optional per-app)"
                        } else if (showAllAppsMode) {
                            "Tap any app to configure full hardware identity & shields"
                        } else {
                            "$spoofedAppsCount app${if (spoofedAppsCount == 1) "" else "s"} using virtual hardware identity"
                        },
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = JakartaFontFamily
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (selectedTab == PrivacyHubTab.APPS) {
                if (!showAllAppsMode) {
                    Button(
                        onClick = {
                            showAllAppsMode = true
                            activeFilter = PrivacyAppFilter.USER_APPS
                        },
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("btn_add_spoof_app")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Apps",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add Apps",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = JakartaFontFamily
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            showAllAppsMode = false
                            activeFilter = PrivacyAppFilter.SPOOFED_ONLY
                            searchQuery = ""
                        },
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("btn_done_spoof_select")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Done",
                            fontSize = 13.sp,
                            fontFamily = JakartaFontFamily,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Button(
                    onClick = {
                        editingProfile = PrivacyProfile(
                            id = "custom_${System.currentTimeMillis()}",
                            name = "Custom Shield",
                            description = "Custom reusable privacy & anti-fingerprinting preset",
                            isBuiltIn = false
                        )
                    },
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("btn_create_privacy_profile")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Profile",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "New Profile",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = JakartaFontFamily
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Segmented Tabs: Apps | Profiles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.outlineVariant, RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PrivacyHubTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                val label = when (tab) {
                    PrivacyHubTab.APPS -> "Apps ($spoofedAppsCount)"
                    PrivacyHubTab.PROFILES -> "Profiles (${privacyProfiles.size})"
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) scheme.primary else Color.Transparent)
                        .clickable { selectedTab = tab }
                        .padding(vertical = 9.dp)
                        .testTag("privacy_tab_${tab.name.lowercase()}")
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant,
                        fontFamily = JakartaFontFamily
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (selectedTab == PrivacyHubTab.APPS) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = if (showAllAppsMode) "Search installed apps..." else "Search spoofed apps...",
                        color = scheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                                contentDescription = "Clear search",
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
                    .testTag("device_spoofer_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            val filterChipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = scheme.primaryContainer,
                selectedLabelColor = scheme.onPrimaryContainer,
                containerColor = scheme.surface,
                labelColor = scheme.onSurfaceVariant
            )

            // Filter Chips row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = activeFilter == PrivacyAppFilter.SPOOFED_ONLY,
                        onClick = {
                            activeFilter = PrivacyAppFilter.SPOOFED_ONLY
                            showAllAppsMode = false
                        },
                        label = { Text("Spoofed ($spoofedAppsCount)", fontFamily = JakartaFontFamily) },
                        shape = RoundedCornerShape(50),
                        colors = filterChipColors
                    )
                }
                item {
                    FilterChip(
                        selected = activeFilter == PrivacyAppFilter.USER_APPS,
                        onClick = {
                            activeFilter = PrivacyAppFilter.USER_APPS
                            showAllAppsMode = true
                        },
                        label = { Text("Downloaded", fontFamily = JakartaFontFamily) },
                        shape = RoundedCornerShape(50),
                        colors = filterChipColors
                    )
                }
                item {
                    FilterChip(
                        selected = activeFilter == PrivacyAppFilter.SYSTEM_APPS,
                        onClick = {
                            activeFilter = PrivacyAppFilter.SYSTEM_APPS
                            showAllAppsMode = true
                        },
                        label = { Text("System", fontFamily = JakartaFontFamily) },
                        shape = RoundedCornerShape(50),
                        colors = filterChipColors
                    )
                }
                item {
                    FilterChip(
                        selected = activeFilter == PrivacyAppFilter.ALL,
                        onClick = {
                            activeFilter = PrivacyAppFilter.ALL
                            showAllAppsMode = true
                        },
                        label = { Text("All (${allApps.size})", fontFamily = JakartaFontFamily) },
                        shape = RoundedCornerShape(50),
                        colors = filterChipColors
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (displayedApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 28.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(scheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = scheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (activeFilter == PrivacyAppFilter.SPOOFED_ONLY && searchQuery.isBlank()) {
                                "No spoofed apps yet"
                            } else {
                                "No matching apps found"
                            },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            fontFamily = JakartaFontFamily
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap '+ Add Apps' to choose apps and configure virtual hardware models, IMEI/Android ID/DRM, Virtual GPS, and Root/VPN shields.",
                            fontSize = 13.sp,
                            color = scheme.onSurfaceVariant,
                            fontFamily = JakartaFontFamily
                        )
                        if (!showAllAppsMode) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    showAllAppsMode = true
                                    activeFilter = PrivacyAppFilter.USER_APPS
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.testTag("empty_state_add_spoof_app")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Select Apps to Spoof Identity",
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    items(displayedApps, key = { it.packageName }) { appInfo ->
                        val profileName = remember(appInfo.privacyProfileId, privacyProfiles) {
                            if (appInfo.privacyProfileId.isBlank()) null
                            else privacyProfiles.firstOrNull { it.id == appInfo.privacyProfileId }?.name
                        }
                        SpoofedAppCard(
                            appInfo = appInfo,
                            assignedProfileName = profileName,
                            onConfigure = { editingApp = appInfo },
                            onToggleSpoof = { enabled ->
                                if (enabled) {
                                    val preset = if (appInfo.deviceProfilePreset == DeviceProfilePreset.CUSTOM) {
                                        DeviceProfilePreset.PIXEL_9_PRO
                                    } else {
                                        appInfo.deviceProfilePreset
                                    }
                                    viewModel.saveAppIdentitySpoofConfig(
                                        appInfo = appInfo,
                                        enabled = true,
                                        preset = preset,
                                        brand = appInfo.spoofBrand.ifBlank { preset.brand },
                                        model = appInfo.spoofModel.ifBlank { preset.model },
                                        device = appInfo.spoofDevice.ifBlank { preset.device },
                                        androidId = appInfo.spoofAndroidId.ifBlank { IsolationRuleManager.generateAndroidId() },
                                        imei = appInfo.spoofImei.ifBlank { IsolationRuleManager.generateLuhnImei() },
                                        advertisingId = appInfo.spoofAdvertisingId.ifBlank { IsolationRuleManager.generateAdvertisingId() },
                                        macAddress = appInfo.spoofMacAddress.ifBlank { IsolationRuleManager.generateMacAddress() },
                                        carrierName = appInfo.spoofCarrierName.ifBlank { "T-Mobile" },
                                        simOperator = appInfo.spoofSimOperator.ifBlank { "310260" },
                                        serial = appInfo.spoofSerial.ifBlank { IsolationRuleManager.generateSerial() },
                                        hideRootAndXposed = appInfo.hideRootAndXposed,
                                        hideAdbAndDevOptions = appInfo.hideAdbAndDevOptions,
                                        hideVpnAndProxy = appInfo.hideVpnAndProxy,
                                        blockClipboardRead = appInfo.blockClipboardRead,
                                        privacyProfileId = appInfo.privacyProfileId
                                    )
                                } else {
                                    viewModel.removeAppIdentitySpoof(appInfo)
                                }
                            }
                        )
                    }
                }
            }
        } else {
            // PROFILES TAB: Manage built-in & custom Privacy Profiles
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = scheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, scheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Reusable Privacy Profiles",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurface,
                                    fontFamily = JakartaFontFamily
                                )
                                Text(
                                    text = "Profiles are optional. By default, apps use manual configuration unless you select a profile inside the app page.",
                                    fontSize = 11.sp,
                                    color = scheme.onSurfaceVariant,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { viewModel.resetPrivacyProfilesToDefaults() },
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("btn_reset_builtin_profiles")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset Built-ins", fontSize = 11.sp, fontFamily = JakartaFontFamily)
                            }
                        }
                    }
                }

                items(privacyProfiles, key = { it.id }) { profile ->
                    val appsUsingCount = remember(allApps, profile.id) {
                        allApps.count { it.privacyProfileId == profile.id && it.isIdentitySpoofed }
                    }
                    PrivacyProfileCard(
                        profile = profile,
                        appsUsingCount = appsUsingCount,
                        onEdit = { editingProfile = profile },
                        onDuplicate = {
                            val copy = profile.copy(
                                id = "custom_${System.currentTimeMillis()}",
                                name = "${profile.name} Copy",
                                isBuiltIn = false
                            )
                            viewModel.savePrivacyProfile(copy)
                        },
                        onDelete = if (!profile.isBuiltIn) {
                            { viewModel.deletePrivacyProfile(profile.id) }
                        } else null
                    )
                }
            }
        }
    }

    // Profile Editor Dialog (Built-in & Custom Profiles)
    editingProfile?.let { profile ->
        var name by remember(profile.id) { mutableStateOf(profile.name) }
        var description by remember(profile.id) { mutableStateOf(profile.description) }
        var gpuVendor by remember(profile.id) { mutableStateOf(profile.gpuVendor) }
        var gpuRenderer by remember(profile.id) { mutableStateOf(profile.gpuRenderer) }
        var screenRes by remember(profile.id) { mutableStateOf(profile.spoofScreenResolution) }
        var spoofAndroidId by remember(profile.id) { mutableStateOf(profile.spoofAndroidId) }
        var spoofImei by remember(profile.id) { mutableStateOf(profile.spoofImei) }
        var spoofDrm by remember(profile.id) { mutableStateOf(profile.spoofDrmWidevine) }
        var spoofGpu by remember(profile.id) { mutableStateOf(profile.spoofGpuFingerprint) }
        var canvasNoise by remember(profile.id) { mutableStateOf(profile.spoofCanvasNoise) }
        var anonymizeSensors by remember(profile.id) { mutableStateOf(profile.anonymizeSensors) }
        var spoofWebRtc by remember(profile.id) { mutableStateOf(profile.spoofWebRtc) }
        var hideRoot by remember(profile.id) { mutableStateOf(profile.hideRootAndXposed) }
        var hideVpn by remember(profile.id) { mutableStateOf(profile.hideVpnAndProxy) }
        var deadman by remember(profile.id) { mutableStateOf(profile.deadmanSwitchEnabled) }

        AlertDialog(
            onDismissRequest = { editingProfile = null },
            title = {
                Text(
                    text = if (profile.isBuiltIn) "Edit Built-in Profile" else "Configure Privacy Profile",
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Profile Name", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description", fontSize = 12.sp) },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = gpuVendor,
                            onValueChange = { gpuVendor = it },
                            label = { Text("GPU Vendor", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = gpuRenderer,
                            onValueChange = { gpuRenderer = it },
                            label = { Text("GPU Renderer", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f)
                        )
                    }
                    OutlinedTextField(
                        value = screenRes,
                        onValueChange = { screenRes = it },
                        label = { Text("Screen Resolution (e.g. 1080x2400)", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    ProfileToggleItem("Spoof Android ID & Serial", spoofAndroidId) { spoofAndroidId = it }
                    ProfileToggleItem("Spoof IMEI & Advertising ID", spoofImei) { spoofImei = it }
                    ProfileToggleItem("Spoof Widevine DRM ID", spoofDrm) { spoofDrm = it }
                    ProfileToggleItem("Spoof GPU & Display Metrics", spoofGpu) { spoofGpu = it }
                    ProfileToggleItem("Canvas & AudioContext Noise", canvasNoise) { canvasNoise = it }
                    ProfileToggleItem("Anonymize Hardware Sensors", anonymizeSensors) { anonymizeSensors = it }
                    ProfileToggleItem("Block WebRTC IP Leak", spoofWebRtc) { spoofWebRtc = it }
                    ProfileToggleItem("Hide Root, Magisk & LSPosed", hideRoot) { hideRoot = it }
                    ProfileToggleItem("Hide VPN & Proxy Status", hideVpn) { hideVpn = it }
                    ProfileToggleItem("Total Identity Leak Deadman Switch", deadman) { deadman = it }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.savePrivacyProfile(
                            profile.copy(
                                name = name.trim().ifBlank { "Custom Profile" },
                                description = description.trim(),
                                gpuVendor = gpuVendor.trim().ifBlank { "Qualcomm" },
                                gpuRenderer = gpuRenderer.trim().ifBlank { "Adreno (TM) 750" },
                                spoofScreenResolution = screenRes.trim().ifBlank { "1080x2400" },
                                spoofAndroidId = spoofAndroidId,
                                spoofSerial = spoofAndroidId,
                                spoofImei = spoofImei,
                                spoofAdvertisingId = spoofImei,
                                spoofDrmWidevine = spoofDrm,
                                spoofGpuFingerprint = spoofGpu,
                                spoofCanvasNoise = canvasNoise,
                                spoofAudioContext = canvasNoise,
                                anonymizeSensors = anonymizeSensors,
                                sensorBiasNeutralizer = anonymizeSensors,
                                spoofWebRtc = spoofWebRtc,
                                hideRootAndXposed = hideRoot,
                                hideVpnAndProxy = hideVpn,
                                deadmanSwitchEnabled = deadman
                            )
                        )
                        editingProfile = null
                    }
                ) {
                    Text("Save Profile", fontFamily = JakartaFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingProfile = null }) {
                    Text("Cancel", fontFamily = JakartaFontFamily)
                }
            }
        )
    }
}

@Composable
private fun ProfileToggleItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontFamily = JakartaFontFamily,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun PrivacyProfileCard(
    profile: PrivacyProfile,
    appsUsingCount: Int,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .testTag("privacy_profile_card_${profile.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = scheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = profile.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                                fontFamily = JakartaFontFamily
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (profile.isBuiltIn) scheme.primary.copy(alpha = 0.14f)
                                        else scheme.surfaceVariant
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (profile.isBuiltIn) "Built-in" else "Custom",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (profile.isBuiltIn) scheme.primary else scheme.onSurfaceVariant,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = profile.description,
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = JakartaFontFamily
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "GPU: ${profile.gpuRenderer} • Screen: ${profile.spoofScreenResolution} • Used by $appsUsingCount app${if (appsUsingCount == 1) "" else "s"}",
                            fontSize = 11.sp,
                            color = scheme.primary,
                            fontWeight = FontWeight.Medium,
                            fontFamily = JakartaFontFamily
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Duplicate Profile",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Profile",
                                tint = scheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpoofedAppCard(
    appInfo: InstalledAppInfo,
    assignedProfileName: String?,
    onConfigure: () -> Unit,
    onToggleSpoof: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isSpoofed = appInfo.isIdentitySpoofed
    val hasActiveShields = isSpoofed ||
            appInfo.spoofLocationEnabled ||
            appInfo.preventScreenshots ||
            appInfo.hideOtherApps ||
            appInfo.disableLogcatLogging ||
            appInfo.fakeBatteryEnabled ||
            appInfo.disableAllNetworking ||
            appInfo.spoofGpuFingerprint ||
            appInfo.spoofBrowserUserAgent ||
            appInfo.anonymizeSensors ||
            appInfo.blockCellTowerTracking ||
            appInfo.autoSyncTimezone

    val displayBrand = appInfo.spoofBrand.ifBlank { appInfo.deviceProfilePreset.brand }
    val displayModel = appInfo.spoofModel.ifBlank { appInfo.deviceProfilePreset.model }

    val subtitleText = when {
        isSpoofed && !assignedProfileName.isNullOrBlank() ->
            "$displayBrand $displayModel • Profile: $assignedProfileName"
        isSpoofed ->
            "$displayBrand $displayModel • Custom Hardware & Shields"
        hasActiveShields ->
            "Privacy Shields Active • Tap to configure"
        else ->
            "Not spoofed • Tap to configure privacy & identity"
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (hasActiveShields) scheme.primaryContainer.copy(alpha = 0.2f) else scheme.surface
        ),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (hasActiveShields) scheme.primary.copy(alpha = 0.45f) else scheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConfigure() }
            .testTag("spoofed_app_card_${appInfo.packageName}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val iconBitmap = com.example.isolation.ui.components.rememberAppIconBitmap(
                    packageName = appInfo.packageName,
                    drawable = appInfo.icon,
                    sizePx = 96
                )
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = appInfo.appName,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
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
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        fontFamily = JakartaFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitleText,
                        fontSize = 12.sp,
                        color = if (hasActiveShields) scheme.primary else scheme.onSurfaceVariant,
                        fontWeight = if (hasActiveShields) FontWeight.Medium else FontWeight.Normal,
                        fontFamily = JakartaFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = hasActiveShields,
                onCheckedChange = { onToggleSpoof(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = scheme.onPrimary,
                    checkedTrackColor = scheme.primary
                ),
                modifier = Modifier.testTag("switch_spoof_${appInfo.packageName}")
            )
        }
    }
}
