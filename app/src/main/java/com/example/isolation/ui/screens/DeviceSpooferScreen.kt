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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.isolation.model.DeviceProfilePreset
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.pkg.InstalledAppInfo
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.JakartaFontFamily

@Composable
fun DeviceSpooferScreen(
    viewModel: IsoVaultViewModel,
    onBack: () -> Unit = {}
) {
    BackHandler { onBack() }

    val scheme = MaterialTheme.colorScheme
    val allApps by viewModel.allInstalledApps.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showAddAppsDialog by remember { mutableStateOf(false) }
    var editingApp by remember { mutableStateOf<InstalledAppInfo?>(null) }

    val enrolledApps = remember(allApps, searchQuery) {
        allApps.filter { app ->
            val isEnrolled = app.isIdentitySpoofed
            val matchesSearch = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true) ||
                    app.spoofModel.contains(searchQuery, ignoreCase = true)
            isEnrolled && matchesSearch
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("device_spoofer_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
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
                        onClick = onBack,
                        modifier = Modifier.testTag("device_spoofer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = scheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Privacy",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onBackground,
                            fontFamily = JakartaFontFamily
                        )
                        Text(
                            text = "Device identity spoofing, IMEI, DRM & privacy shields",
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariant,
                            fontFamily = JakartaFontFamily
                        )
                    }
                }
                Button(
                    onClick = { showAddAppsDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("btn_add_spoof_app")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add App",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add App",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JakartaFontFamily
                    )
                }
            }
        }

        // Info Banner Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = scheme.tertiaryContainer.copy(alpha = 0.38f)
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, scheme.tertiary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(scheme.tertiaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = scheme.onTertiaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hardware & Telemetry Isolation",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            fontFamily = JakartaFontFamily
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Spoofs Android ID, IMEI, IMSI, Advertising ID, GSF ID, Widevine DRM, Wi-Fi MAC, Build props, and hides Root/LSPosed, ADB & VPN per app.",
                            fontSize = 11.sp,
                            color = scheme.onSurfaceVariant,
                            fontFamily = JakartaFontFamily,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        if (enrolledApps.isNotEmpty() || searchQuery.isNotBlank()) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search spoofed apps...",
                            fontSize = 13.sp,
                            fontFamily = JakartaFontFamily
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("device_spoofer_search_input")
                )
            }
        }

        if (enrolledApps.isEmpty()) {
            item {
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
                            .padding(horizontal = 24.dp, vertical = 32.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(scheme.primaryContainer.copy(alpha = 0.55f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No Apps Spoofed Yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            fontFamily = JakartaFontFamily
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Select any installed app to assign it a virtual hardware profile (Pixel 9 Pro, Galaxy S24 Ultra, etc.), randomized IMEI/Android ID/DRM ID, and Root/VPN/ADB privacy shields.",
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariant,
                            fontFamily = JakartaFontFamily,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = { showAddAppsDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("empty_state_add_spoof_app")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Select App to Spoof Identity",
                                fontWeight = FontWeight.Bold,
                                fontFamily = JakartaFontFamily
                            )
                        }
                    }
                }
            }
        } else {
            items(enrolledApps, key = { it.packageName }) { appInfo ->
                SpoofedAppCard(
                    appInfo = appInfo,
                    onConfigure = { editingApp = appInfo },
                    onRemove = { viewModel.removeAppIdentitySpoof(appInfo) }
                )
            }
        }
    }

    if (showAddAppsDialog) {
        SelectAppForIdentitySpoofDialog(
            allApps = allApps,
            onDismiss = { showAddAppsDialog = false },
            onSelectApp = { selected ->
                showAddAppsDialog = false
                editingApp = selected
            }
        )
    }

    editingApp?.let { target ->
        ConfigureDeviceIdentityDialog(
            appInfo = target,
            onDismiss = { editingApp = null },
            onSave = { preset, brand, model, device, androidId, imei, adId, mac, carrier, simOp, serial, hideRoot, hideAdb, hideVpn, blockClip ->
                viewModel.saveAppIdentitySpoofConfig(
                    appInfo = target,
                    enabled = true,
                    preset = preset,
                    brand = brand,
                    model = model,
                    device = device,
                    androidId = androidId,
                    imei = imei,
                    advertisingId = adId,
                    macAddress = mac,
                    carrierName = carrier,
                    simOperator = simOp,
                    serial = serial,
                    hideRootAndXposed = hideRoot,
                    hideAdbAndDevOptions = hideAdb,
                    hideVpnAndProxy = hideVpn,
                    blockClipboardRead = blockClip
                )
                editingApp = null
            },
            onDisable = {
                viewModel.removeAppIdentitySpoof(target)
                editingApp = null
            }
        )
    }
}

@Composable
private fun SpoofedAppCard(
    appInfo: InstalledAppInfo,
    onConfigure: () -> Unit,
    onRemove: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val displayBrand = appInfo.spoofBrand.ifBlank { appInfo.deviceProfilePreset.brand }
    val displayModel = appInfo.spoofModel.ifBlank { appInfo.deviceProfilePreset.model }

    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConfigure() }
            .testTag("spoofed_app_card_${appInfo.packageName}")
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
                    val iconBitmap = remember(appInfo.packageName, appInfo.icon) {
                        try {
                            appInfo.icon?.toBitmap(96, 96)?.asImageBitmap()
                        } catch (_: Throwable) {
                            null
                        }
                    }
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
                    Column {
                        Text(
                            text = appInfo.appName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            fontFamily = JakartaFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = appInfo.packageName,
                            fontSize = 11.sp,
                            color = scheme.onSurfaceVariant,
                            fontFamily = JakartaFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(scheme.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "$displayBrand $displayModel",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer,
                        fontFamily = JakartaFontFamily
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Identifier summary pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                IdentityMetaChip("AID: ${appInfo.spoofAndroidId.take(8)}…")
                IdentityMetaChip("IMEI: ${appInfo.spoofImei.take(6)}…${appInfo.spoofImei.takeLast(3)}")
                IdentityMetaChip("MAC: ${appInfo.spoofMacAddress}")
                if (appInfo.hideRootAndXposed) IdentityMetaChip("Root/LSPosed Hidden")
                if (appInfo.hideAdbAndDevOptions) IdentityMetaChip("ADB Hidden")
                if (appInfo.hideVpnAndProxy) IdentityMetaChip("VPN Hidden")
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag("btn_restore_identity_${appInfo.packageName}")
                ) {
                    Text(
                        text = "Restore Real ID",
                        fontSize = 12.sp,
                        fontFamily = JakartaFontFamily
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onConfigure,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("btn_edit_identity_${appInfo.packageName}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Edit Identity",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JakartaFontFamily
                    )
                }
            }
        }
    }
}

@Composable
private fun IdentityMetaChip(label: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.65f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = scheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SelectAppForIdentitySpoofDialog(
    allApps: List<InstalledAppInfo>,
    onDismiss: () -> Unit,
    onSelectApp: (InstalledAppInfo) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var includeSystem by remember { mutableStateOf(false) }

    val filtered = remember(allApps, query, includeSystem) {
        allApps.filter { app ->
            val sysOk = includeSystem || !app.isSystemApp
            val qOk = query.isBlank() ||
                    app.appName.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
            sysOk && qOk
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Select App to Spoof",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily
                )
                Text(
                    text = "Assign a virtual hardware fingerprint & privacy shields",
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                    fontFamily = JakartaFontFamily
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search app name or package...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Show system apps",
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                        fontFamily = JakartaFontFamily
                    )
                    Switch(
                        checked = includeSystem,
                        onCheckedChange = { includeSystem = it }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectApp(app) }
                                .padding(vertical = 8.dp, horizontal = 6.dp)
                        ) {
                            val bmp = remember(app.packageName, app.icon) {
                                try {
                                    app.icon?.toBitmap(72, 72)?.asImageBitmap()
                                } catch (_: Throwable) { null }
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = app.appName,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = JakartaFontFamily,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = app.packageName,
                                    fontSize = 11.sp,
                                    color = scheme.onSurfaceVariant,
                                    fontFamily = JakartaFontFamily,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontFamily = JakartaFontFamily)
            }
        }
    )
}

@Composable
private fun ConfigureDeviceIdentityDialog(
    appInfo: InstalledAppInfo,
    onDismiss: () -> Unit,
    onSave: (
        preset: DeviceProfilePreset,
        brand: String,
        model: String,
        device: String,
        androidId: String,
        imei: String,
        adId: String,
        mac: String,
        carrier: String,
        simOp: String,
        serial: String,
        hideRoot: Boolean,
        hideAdb: Boolean,
        hideVpn: Boolean,
        blockClip: Boolean
    ) -> Unit,
    onDisable: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var preset by remember { mutableStateOf(appInfo.deviceProfilePreset) }
    var brand by remember { mutableStateOf(appInfo.spoofBrand.ifBlank { preset.brand }) }
    var model by remember { mutableStateOf(appInfo.spoofModel.ifBlank { preset.model }) }
    var device by remember { mutableStateOf(appInfo.spoofDevice.ifBlank { preset.device }) }
    var androidId by remember { mutableStateOf(appInfo.spoofAndroidId.ifBlank { IsolationRuleManager.generateAndroidId() }) }
    var imei by remember { mutableStateOf(appInfo.spoofImei.ifBlank { IsolationRuleManager.generateLuhnImei() }) }
    var adId by remember { mutableStateOf(appInfo.spoofAdvertisingId.ifBlank { IsolationRuleManager.generateAdvertisingId() }) }
    var mac by remember { mutableStateOf(appInfo.spoofMacAddress.ifBlank { IsolationRuleManager.generateMacAddress() }) }
    var carrier by remember { mutableStateOf(appInfo.spoofCarrierName.ifBlank { "T-Mobile" }) }
    var simOp by remember { mutableStateOf(appInfo.spoofSimOperator.ifBlank { "310260" }) }
    var serial by remember { mutableStateOf(appInfo.spoofSerial.ifBlank { IsolationRuleManager.generateSerial() }) }

    var hideRoot by remember { mutableStateOf(appInfo.hideRootAndXposed) }
    var hideAdb by remember { mutableStateOf(appInfo.hideAdbAndDevOptions) }
    var hideVpn by remember { mutableStateOf(appInfo.hideVpnAndProxy) }
    var blockClip by remember { mutableStateOf(appInfo.blockClipboardRead) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = appInfo.appName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily
                )
                Text(
                    text = "Device Identity & Privacy Spoofing",
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
                    .heightIn(max = 470.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1-Tap Randomize All Identifiers Button
                FilledTonalButton(
                    onClick = {
                        val presets = DeviceProfilePreset.entries.filter { it != DeviceProfilePreset.CUSTOM }
                        val randomPreset = presets.random()
                        preset = randomPreset
                        brand = randomPreset.brand
                        model = randomPreset.model
                        device = randomPreset.device
                        androidId = IsolationRuleManager.generateAndroidId()
                        imei = IsolationRuleManager.generateLuhnImei()
                        adId = IsolationRuleManager.generateAdvertisingId()
                        mac = IsolationRuleManager.generateMacAddress()
                        serial = IsolationRuleManager.generateSerial()
                        val (cName, cCode) = IsolationRuleManager.generateRandomCarrier()
                        carrier = cName
                        simOp = cCode
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_randomize_all_identity")
                ) {
                    Icon(
                        imageVector = Icons.Default.Casino,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Randomize Full Device Identity",
                        fontWeight = FontWeight.Bold,
                        fontFamily = JakartaFontFamily
                    )
                }

                // Hardware Profile Preset Selector
                Text(
                    text = "Hardware Model Preset",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    fontFamily = JakartaFontFamily
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    DeviceProfilePreset.entries.forEach { item ->
                        FilterChip(
                            selected = preset == item,
                            onClick = {
                                preset = item
                                if (item != DeviceProfilePreset.CUSTOM) {
                                    brand = item.brand
                                    model = item.model
                                    device = item.device
                                }
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    fontSize = 12.sp,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = brand,
                        onValueChange = {
                            brand = it
                            preset = DeviceProfilePreset.CUSTOM
                        },
                        label = { Text("Brand", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = {
                            model = it
                            preset = DeviceProfilePreset.CUSTOM
                        },
                        label = { Text("Model", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1.3f)
                    )
                }

                HorizontalDivider(color = scheme.outlineVariant)

                Text(
                    text = "Unique Hardware & Ad Identifiers",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    fontFamily = JakartaFontFamily
                )

                IdentifierFieldWithRandomize(
                    label = "Android ID (16-hex) & Widevine DRM Seed",
                    value = androidId,
                    onValueChange = { androidId = it },
                    onRandomize = { androidId = IsolationRuleManager.generateAndroidId() }
                )

                IdentifierFieldWithRandomize(
                    label = "IMEI / MEID (15-digit Luhn Valid)",
                    value = imei,
                    onValueChange = { imei = it },
                    onRandomize = { imei = IsolationRuleManager.generateLuhnImei() }
                )

                IdentifierFieldWithRandomize(
                    label = "Google Advertising ID (AAID)",
                    value = adId,
                    onValueChange = { adId = it },
                    onRandomize = { adId = IsolationRuleManager.generateAdvertisingId() }
                )

                IdentifierFieldWithRandomize(
                    label = "Wi-Fi & Bluetooth MAC Address",
                    value = mac,
                    onValueChange = { mac = it },
                    onRandomize = { mac = IsolationRuleManager.generateMacAddress() }
                )

                IdentifierFieldWithRandomize(
                    label = "Hardware Serial (Build.SERIAL)",
                    value = serial,
                    onValueChange = { serial = it },
                    onRandomize = { serial = IsolationRuleManager.generateSerial() }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = carrier,
                        onValueChange = { carrier = it },
                        label = { Text("SIM Carrier", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = simOp,
                        onValueChange = { simOp = it },
                        label = { Text("MCC/MNC", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }

                HorizontalDivider(color = scheme.outlineVariant)

                Text(
                    text = "Environment & Privacy Shields",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    fontFamily = JakartaFontFamily
                )

                PrivacyShieldToggleRow(
                    title = "Hide Root, Magisk, KernelSU & LSPosed",
                    subtitle = "Blocks root/Xposed package queries, stacktraces & spoofs release-keys",
                    checked = hideRoot,
                    onCheckedChange = { hideRoot = it }
                )

                PrivacyShieldToggleRow(
                    title = "Hide Developer Options & USB Debugging",
                    subtitle = "Spoofs adb_enabled=0 & development_settings_enabled=0",
                    checked = hideAdb,
                    onCheckedChange = { hideAdb = it }
                )

                PrivacyShieldToggleRow(
                    title = "Hide VPN & Proxy Status",
                    subtitle = "Hides tun0/wg0 interfaces & TRANSPORT_VPN capability",
                    checked = hideVpn,
                    onCheckedChange = { hideVpn = it }
                )

                PrivacyShieldToggleRow(
                    title = "Block Background Clipboard Snooping",
                    subtitle = "Prevents this app from silently reading copied text",
                    checked = blockClip,
                    onCheckedChange = { blockClip = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        preset,
                        brand,
                        model,
                        device,
                        androidId,
                        imei,
                        adId,
                        mac,
                        carrier,
                        simOp,
                        serial,
                        hideRoot,
                        hideAdb,
                        hideVpn,
                        blockClip
                    )
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("btn_save_identity_spoof")
            ) {
                Text("Save & Apply", fontWeight = FontWeight.Bold, fontFamily = JakartaFontFamily)
            }
        },
        dismissButton = {
            Row {
                if (appInfo.isIdentitySpoofed) {
                    TextButton(onClick = onDisable) {
                        Text("Disable", color = scheme.error, fontFamily = JakartaFontFamily)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", fontFamily = JakartaFontFamily)
                }
            }
        }
    )
}

@Composable
private fun IdentifierFieldWithRandomize(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onRandomize: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        trailingIcon = {
            IconButton(onClick = onRandomize) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Randomize $label",
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PrivacyShieldToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                fontFamily = JakartaFontFamily
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = scheme.onSurfaceVariant,
                fontFamily = JakartaFontFamily
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
