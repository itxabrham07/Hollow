package com.example.isolation.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.isolation.model.DeviceProfilePreset
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.model.PrivacyProfile
import com.example.isolation.pkg.InstalledAppInfo
import com.example.isolation.ui.components.rememberAppIconBitmap
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.M3IndigoAccent
import com.example.ui.theme.M3RoseAccent
import com.example.ui.theme.M3TealAccent
import com.example.ui.theme.M3VioletAccent

private enum class PrivacyAppFilter(val label: String) {
    USER_APPS("Installed Apps"),
    PROTECTED("Protected"),
    ALL("All Apps"),
    SYSTEM("System")
}

@Composable
fun DeviceSpooferScreen(
    viewModel: IsoVaultViewModel,
    onBack: () -> Unit = {},
    onDetailOpenChanged: (Boolean) -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val allApps by viewModel.allInstalledApps.collectAsState()
    val spoofedCount by viewModel.identitySpoofedCount.collectAsState()
    val privacyProfiles by viewModel.privacyProfiles.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(PrivacyAppFilter.USER_APPS) }
    var selectedAppPkg by remember { mutableStateOf<String?>(null) }
    var showProfilesDialog by remember { mutableStateOf(false) }

    val selectedApp = remember(allApps, selectedAppPkg) {
        selectedAppPkg?.let { pkg -> allApps.find { it.packageName == pkg } }
    }

    LaunchedEffect(selectedApp != null) {
        onDetailOpenChanged(selectedApp != null)
    }

    if (selectedApp != null) {
        BackHandler {
            selectedAppPkg = null
        }
        ConfigurePrivacyCategorizedScreen(
            app = selectedApp,
            viewModel = viewModel,
            onBack = { selectedAppPkg = null }
        )
        return
    }

    val filteredApps = remember(allApps, searchQuery, selectedFilter) {
        allApps.filter { app ->
            val matchesQuery = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                PrivacyAppFilter.USER_APPS -> !app.isSystemApp || app.isIdentitySpoofed
                PrivacyAppFilter.PROTECTED -> app.isIdentitySpoofed
                PrivacyAppFilter.ALL -> true
                PrivacyAppFilter.SYSTEM -> app.isSystemApp
            }
            matchesQuery && matchesFilter
        }.sortedWith(
            compareByDescending<InstalledAppInfo> { it.isIdentitySpoofed }
                .thenBy { it.appName.lowercase() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(horizontal = 16.dp)
            .testTag("device_spoofer_screen")
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
                Surface(
                    color = scheme.surfaceContainerLow,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onBack() }
                        .testTag("btn_back_from_spoofer")
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
                Column {
                    Text(
                        text = "App Privacy & Identity",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = scheme.onSurface,
                        fontFamily = JakartaFontFamily
                    )
                    Text(
                        text = "Customize device model, IDs & location per app",
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                        fontFamily = JakartaFontFamily
                    )
                }
            }

            Surface(
                color = scheme.primaryContainer,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { showProfilesDialog = true }
                    .testTag("btn_open_privacy_profiles")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhonelinkSetup,
                        contentDescription = null,
                        tint = scheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Profiles (${privacyProfiles.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer,
                        fontFamily = JakartaFontFamily
                    )
                }
            }
        }

        // Colorful Material 3 Summary Banner
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
                            M3IndigoAccent,
                            M3VioletAccent,
                            M3TealAccent
                        )
                    )
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.20f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = if (spoofedCount > 0) "$spoofedCount Apps Protected" else "Per-App Identity Shield",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = JakartaFontFamily
                        )
                        Text(
                            text = "Flip the switch on any app to spoof its device model & IDs, or tap an app to customize.",
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontFamily = JakartaFontFamily
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Rounded Material 3 Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Search apps by name or package...",
                    fontSize = 13.sp,
                    color = scheme.onSurfaceVariant,
                    fontFamily = JakartaFontFamily
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = scheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
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
                focusedBorderColor = scheme.primary,
                unfocusedBorderColor = scheme.outlineVariant,
                focusedContainerColor = scheme.surface,
                unfocusedContainerColor = scheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("spoofer_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Colorful Material 3 Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrivacyAppFilter.entries.forEach { filterItem ->
                val selected = selectedFilter == filterItem
                val countLabel = when (filterItem) {
                    PrivacyAppFilter.USER_APPS -> allApps.count { !it.isSystemApp || it.isIdentitySpoofed }
                    PrivacyAppFilter.PROTECTED -> spoofedCount
                    PrivacyAppFilter.ALL -> allApps.size
                    PrivacyAppFilter.SYSTEM -> allApps.count { it.isSystemApp }
                }
                FilterChip(
                    selected = selected,
                    onClick = { selectedFilter = filterItem },
                    label = {
                        Text(
                            text = "${filterItem.label} ($countLabel)",
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
                        selectedContainerColor = scheme.primaryContainer,
                        selectedLabelColor = scheme.onPrimaryContainer,
                        selectedLeadingIconColor = scheme.onPrimaryContainer
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredApps.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(scheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = scheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (selectedFilter == PrivacyAppFilter.PROTECTED) {
                            "No protected apps yet"
                        } else {
                            "No matching apps found"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        fontFamily = JakartaFontFamily
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (selectedFilter == PrivacyAppFilter.PROTECTED) {
                            "Switch to Installed Apps to enable privacy protection on any app."
                        } else {
                            "Try clearing your search or switching the filter chip above."
                        },
                        fontSize = 13.sp,
                        color = scheme.onSurfaceVariant,
                        fontFamily = JakartaFontFamily
                    )
                    if (selectedFilter != PrivacyAppFilter.ALL) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                searchQuery = ""
                                selectedFilter = PrivacyAppFilter.ALL
                            },
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Show All Apps", fontFamily = JakartaFontFamily, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    ColorfulPrivacyAppCard(
                        app = app,
                        onOpenConfig = { selectedAppPkg = app.packageName },
                        onToggleSpoof = { enabled ->
                            if (!enabled) {
                                viewModel.removeAppIdentitySpoof(app)
                            } else {
                                val preset = if (app.deviceProfilePreset == DeviceProfilePreset.REAL_HARDWARE) {
                                    DeviceProfilePreset.PIXEL_9_PRO
                                } else {
                                    app.deviceProfilePreset
                                }
                                viewModel.saveAppIdentitySpoofConfig(
                                    appInfo = app,
                                    enabled = true,
                                    preset = preset,
                                    brand = app.spoofBrand.ifBlank { preset.brand },
                                    model = app.spoofModel.ifBlank { preset.model },
                                    device = app.spoofDevice.ifBlank { preset.device },
                                    androidId = app.spoofAndroidId.ifBlank { IsolationRuleManager.generateAndroidId() },
                                    imei = app.spoofImei.ifBlank { IsolationRuleManager.generateLuhnImei() },
                                    advertisingId = app.spoofAdvertisingId.ifBlank { IsolationRuleManager.generateAdvertisingId() },
                                    macAddress = app.spoofMacAddress.ifBlank { IsolationRuleManager.generateMacAddress() },
                                    carrierName = app.spoofCarrierName.ifBlank { "T-Mobile US" },
                                    simOperator = app.spoofSimOperator.ifBlank { "310260" },
                                    serial = app.spoofSerial.ifBlank { IsolationRuleManager.generateSerial() },
                                    hideRootAndXposed = true,
                                    hideAdbAndDevOptions = true,
                                    hideVpnAndProxy = true,
                                    blockClipboardRead = app.blockClipboardRead
                                )
                            }
                        },
                        onQuickRandomize = {
                            val preset = DeviceProfilePreset.PIXEL_9_PRO
                            viewModel.saveAppIdentitySpoofConfig(
                                appInfo = app,
                                enabled = true,
                                preset = preset,
                                brand = preset.brand,
                                model = preset.model,
                                device = preset.device,
                                androidId = IsolationRuleManager.generateAndroidId(),
                                imei = IsolationRuleManager.generateLuhnImei(),
                                advertisingId = IsolationRuleManager.generateAdvertisingId(),
                                macAddress = IsolationRuleManager.generateMacAddress(),
                                carrierName = "T-Mobile US",
                                simOperator = "310260",
                                serial = IsolationRuleManager.generateSerial(),
                                hideRootAndXposed = true,
                                hideAdbAndDevOptions = true,
                                hideVpnAndProxy = true,
                                blockClipboardRead = true
                            )
                        }
                    )
                }
            }
        }
    }

    if (showProfilesDialog) {
        SavedPrivacyProfilesDialog(
            profiles = privacyProfiles,
            onDeleteProfile = { viewModel.deletePrivacyProfile(it) },
            onDismiss = { showProfilesDialog = false }
        )
    }
}

@Composable
private fun ColorfulPrivacyAppCard(
    app: InstalledAppInfo,
    onOpenConfig: () -> Unit,
    onToggleSpoof: (Boolean) -> Unit,
    onQuickRandomize: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isProtected = app.isIdentitySpoofed

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isProtected) {
                scheme.primaryContainer.copy(alpha = 0.28f)
            } else {
                scheme.surface
            }
        ),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            width = if (isProtected) 1.5.dp else 1.dp,
            color = if (isProtected) M3IndigoAccent.copy(alpha = 0.45f) else scheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable { onOpenConfig() }
            .testTag("spoofer_card_${app.packageName}")
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
                            tint = scheme.primary,
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
                    checked = isProtected,
                    onCheckedChange = onToggleSpoof,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = M3IndigoAccent
                    ),
                    modifier = Modifier.testTag("switch_spoof_${app.packageName}")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isProtected) {
                        val modelLabel = app.spoofModel.ifBlank { app.deviceProfilePreset.label }
                        ColorfulTagPill(
                            text = modelLabel,
                            color = M3IndigoAccent
                        )
                        if (app.hideRootAndXposed) {
                            ColorfulTagPill(
                                text = "Root Hidden",
                                color = M3TealAccent
                            )
                        }
                        if (app.spoofLocationEnabled) {
                            ColorfulTagPill(
                                text = "Fake GPS",
                                color = M3RoseAccent
                            )
                        }
                    } else {
                        ColorfulTagPill(
                            text = "Real Device Identity",
                            color = scheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isProtected) {
                        Surface(
                            color = M3VioletAccent.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onQuickRandomize() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = "Randomize",
                                    tint = M3VioletAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "New ID",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = M3VioletAccent,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                        }
                    }

                    Surface(
                        color = scheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onOpenConfig() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Customize",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.primary,
                                fontFamily = JakartaFontFamily
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorfulTagPill(
    text: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = JakartaFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun SavedPrivacyProfilesDialog(
    profiles: List<PrivacyProfile>,
    onDeleteProfile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = scheme.surface,
        title = {
            Text(
                text = "Saved Privacy Profiles",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                fontFamily = JakartaFontFamily
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Reusable device templates that you can apply to any app with one tap.",
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                    fontFamily = JakartaFontFamily
                )
                profiles.forEach { profile ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = scheme.onSurface,
                                    fontFamily = JakartaFontFamily
                                )
                                Text(
                                    text = "${profile.description} • ${profile.gpuRenderer}",
                                    fontSize = 11.sp,
                                    color = scheme.onSurfaceVariant,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                            if (!profile.isBuiltIn) {
                                IconButton(
                                    onClick = { onDeleteProfile(profile.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = scheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
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
