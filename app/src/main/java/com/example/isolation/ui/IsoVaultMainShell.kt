package com.example.isolation.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.isolation.ui.screens.ActivityScreen
import com.example.isolation.ui.screens.AppsScreen
import com.example.isolation.ui.screens.FilesScreen
import com.example.isolation.ui.screens.SettingsScreen
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.IsoVaultTheme
import com.example.ui.theme.JakartaFontFamily
import kotlinx.coroutines.delay

enum class IsoVaultTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String
) {
    APPS("apps", "Apps", Icons.Rounded.Apps, "nav_apps"),
    FILES("files", "Files", Icons.Rounded.Folder, "nav_files"),
    ACTIVITY("activity", "Activity", Icons.Rounded.History, "nav_activity"),
    SETTINGS("settings", "Settings", Icons.Rounded.Settings, "nav_settings")
}

@Composable
fun IsoVaultMainShell(
    viewModel: IsoVaultViewModel = viewModel()
) {
    val securitySettings by viewModel.securitySettings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Enforce screenshot block (FLAG_SECURE) when enabled
    DisposableEffect(securitySettings.blockScreenshots) {
        val window = (context as? Activity)?.window
        if (securitySettings.blockScreenshots) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { }
    }

    // Auto-lock when returning from background or immediately on pause
    var backgroundEnteredAt by remember { mutableLongStateOf(0L) }
    DisposableEffect(lifecycleOwner, securitySettings.isPinEnabled, securitySettings.autoLockMinutes) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                backgroundEnteredAt = System.currentTimeMillis()
                viewModel.wipeTemporaryPreviewCache()
                if (securitySettings.isPinEnabled && securitySettings.autoLockMinutes == 0) {
                    viewModel.lockVaultNow()
                }
            } else if (event == Lifecycle.Event.ON_START) {
                if (securitySettings.isPinEnabled && backgroundEnteredAt > 0L) {
                    val elapsedMs = System.currentTimeMillis() - backgroundEnteredAt
                    val thresholdMs = securitySettings.autoLockMinutes * 60_000L
                    if (elapsedMs >= thresholdMs) {
                        viewModel.lockVaultNow()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var currentTab by rememberSaveable { mutableStateOf(IsoVaultTab.APPS) }

    IsoVaultTheme(darkTheme = securitySettings.isDarkTheme) {
        val colorScheme = MaterialTheme.colorScheme

        if (securitySettings.isVaultLocked) {
            VaultPinLockScreen(
                isBiometricEnabled = securitySettings.isBiometricEnabled,
                isPanicPinEnabled = securitySettings.isPanicPinEnabled,
                wrongAttempts = securitySettings.wrongPinAttempts,
                cooldownUntilMillis = securitySettings.cooldownUntilMillis,
                onVerifyPin = { enteredPin -> viewModel.verifyPinAttempt(enteredPin) },
                onBiometricSuccess = { viewModel.unlockWithBiometricSuccess() }
            )
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.background)
            ) {
                val useNavigationRail = maxWidth >= 600.dp

                if (useNavigationRail) {
                    // Tablet / Foldable / Landscape layout with left NavigationRail
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        NavigationRail(
                            containerColor = colorScheme.surface,
                            contentColor = colorScheme.onSurface,
                            header = {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .padding(vertical = 12.dp)
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colorScheme.primary.copy(alpha = 0.14f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shield,
                                        contentDescription = "IsoVault",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            IsoVaultTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.label,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                            fontFamily = JakartaFontFamily
                                        )
                                    },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = colorScheme.primary,
                                        selectedTextColor = colorScheme.primary,
                                        indicatorColor = colorScheme.primary.copy(alpha = 0.14f),
                                        unselectedIconColor = colorScheme.onSurfaceVariant,
                                        unselectedTextColor = colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier
                                        .padding(vertical = 4.dp)
                                        .testTag(tab.testTag)
                                )
                            }
                        }

                        Box(
                            contentAlignment = Alignment.TopCenter,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .widthIn(max = 980.dp)
                                    .fillMaxWidth()
                            ) {
                                MainTabContent(
                                    currentTab = currentTab,
                                    viewModel = viewModel,
                                    onNavigateToTab = { currentTab = it }
                                )
                            }
                        }
                    }
                } else {
                    // Compact Portrait layout with bottom NavigationBar
                    Scaffold(
                        containerColor = colorScheme.background,
                        contentWindowInsets = WindowInsets.safeDrawing,
                        bottomBar = {
                            Surface(
                                color = colorScheme.surface,
                                tonalElevation = 0.dp,
                                border = BorderStroke(1.dp, colorScheme.outlineVariant)
                            ) {
                                NavigationBar(
                                    containerColor = colorScheme.surface,
                                    tonalElevation = 0.dp,
                                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                                ) {
                                    IsoVaultTab.entries.forEach { tab ->
                                        val selected = currentTab == tab
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { currentTab = tab },
                                            icon = {
                                                Icon(
                                                    imageVector = tab.icon,
                                                    contentDescription = tab.label,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = tab.label,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                                    fontFamily = JakartaFontFamily
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = colorScheme.primary,
                                                selectedTextColor = colorScheme.primary,
                                                indicatorColor = colorScheme.primary.copy(alpha = 0.14f),
                                                unselectedIconColor = colorScheme.onSurfaceVariant,
                                                unselectedTextColor = colorScheme.onSurfaceVariant
                                            ),
                                            modifier = Modifier.testTag(tab.testTag)
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            MainTabContent(
                                currentTab = currentTab,
                                viewModel = viewModel,
                                onNavigateToTab = { currentTab = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainTabContent(
    currentTab: IsoVaultTab,
    viewModel: IsoVaultViewModel,
    onNavigateToTab: (IsoVaultTab) -> Unit
) {
    AnimatedContent(
        targetState = currentTab,
        transitionSpec = {
            fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
        },
        label = "tab_transition"
    ) { tab ->
        when (tab) {
            IsoVaultTab.APPS -> AppsScreen(
                viewModel = viewModel,
                onRequestSetupPin = { onNavigateToTab(IsoVaultTab.SETTINGS) }
            )
            IsoVaultTab.FILES -> FilesScreen(
                viewModel = viewModel
            )
            IsoVaultTab.ACTIVITY -> ActivityScreen(
                viewModel = viewModel
            )
            IsoVaultTab.SETTINGS -> SettingsScreen(
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun VaultPinLockScreen(
    isBiometricEnabled: Boolean,
    isPanicPinEnabled: Boolean,
    wrongAttempts: Int,
    cooldownUntilMillis: Long,
    onVerifyPin: (String) -> Boolean,
    onBiometricSuccess: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var remainingSeconds by remember { mutableStateOf(0) }

    LaunchedEffect(cooldownUntilMillis) {
        while (true) {
            val diff = cooldownUntilMillis - System.currentTimeMillis()
            if (diff <= 0L) {
                remainingSeconds = 0
                break
            } else {
                remainingSeconds = ((diff + 999L) / 1000L).toInt()
                delay(250L)
            }
        }
    }

    val isInCooldown = remainingSeconds > 0

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val isCompactHeight = maxHeight < 640.dp
        val keySize = if (isCompactHeight) 56.dp else 68.dp
        val keySpacing = if (isCompactHeight) 10.dp else 14.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(if (isCompactHeight) 56.dp else 72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colorScheme.primary.copy(alpha = 0.14f))
                    .border(1.dp, colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = "Vault Logo",
                    tint = colorScheme.primary,
                    modifier = Modifier.size(if (isCompactHeight) 28.dp else 36.dp)
                )
            }

            Spacer(modifier = Modifier.height(if (isCompactHeight) 12.dp else 20.dp))

            Text(
                text = "IsoVault Locked",
                fontSize = if (isCompactHeight) 22.sp else 26.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FrauncesFontFamily,
                color = colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = when {
                    isInCooldown -> "Too many attempts. Try again in ${remainingSeconds}s"
                    errorMessage != null -> errorMessage!!
                    isPanicPinEnabled -> "Enter your 4-digit PIN (Decoy PIN active)"
                    else -> "Enter your 4-digit PIN to unlock your vault"
                },
                fontSize = 13.sp,
                color = if (isInCooldown || errorMessage != null) colorScheme.error else colorScheme.onSurfaceVariant,
                fontFamily = JakartaFontFamily,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(if (isCompactHeight) 16.dp else 24.dp))

            // 4 PIN Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { index ->
                    val filled = index < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) colorScheme.primary else colorScheme.surfaceVariant
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (filled) colorScheme.primary else colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isCompactHeight) 20.dp else 32.dp))

            // PIN Keypad
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("BIO", "0", "DEL")
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(keySpacing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                rows.forEach { rowKeys ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowKeys.forEach { key ->
                            when (key) {
                                "BIO" -> {
                                    if (isBiometricEnabled) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(keySize)
                                                .clip(CircleShape)
                                                .background(colorScheme.primary.copy(alpha = 0.14f))
                                                .clickable(enabled = !isInCooldown) {
                                                    onBiometricSuccess()
                                                }
                                                .testTag("biometric_unlock_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Fingerprint,
                                                contentDescription = "Unlock with Fingerprint",
                                                tint = colorScheme.primary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(keySize))
                                    }
                                }

                                "DEL" -> {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(keySize)
                                            .clip(CircleShape)
                                            .background(colorScheme.surfaceVariant)
                                            .clickable(enabled = !isInCooldown) {
                                                if (enteredPin.isNotEmpty()) {
                                                    enteredPin = enteredPin.dropLast(1)
                                                    errorMessage = null
                                                }
                                            }
                                            .testTag("pin_key_del")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                            contentDescription = "Delete digit",
                                            tint = colorScheme.onSurface,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                else -> {
                                    Card(
                                        shape = CircleShape,
                                        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                        modifier = Modifier
                                            .size(keySize)
                                            .clip(CircleShape)
                                            .clickable(enabled = !isInCooldown) {
                                                if (enteredPin.length < 4) {
                                                    val next = enteredPin + key
                                                    enteredPin = next
                                                    errorMessage = null
                                                    if (next.length == 4) {
                                                        val unlocked = onVerifyPin(next)
                                                        if (!unlocked) {
                                                            errorMessage = "Incorrect PIN (${wrongAttempts + 1} failed)"
                                                            enteredPin = ""
                                                        }
                                                    }
                                                }
                                            }
                                            .testTag("pin_key_$key")
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Text(
                                                text = key,
                                                fontSize = if (isCompactHeight) 20.sp else 24.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                fontFamily = FrauncesFontFamily,
                                                color = colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isBiometricEnabled) {
                Spacer(modifier = Modifier.height(if (isCompactHeight) 14.dp else 22.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = !isInCooldown) { onBiometricSuccess() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tap fingerprint icon to unlock with biometrics",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.primary,
                        fontFamily = JakartaFontFamily
                    )
                }
            }
        }
    }
}
