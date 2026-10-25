package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.isolation.ui.components.PasscodeScreen
import com.example.isolation.ui.screens.ActivityScreen
import com.example.isolation.ui.screens.AppsScreen
import com.example.isolation.ui.screens.FilesScreen
import com.example.isolation.ui.screens.SettingsScreen
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.IsoVaultTheme
import com.example.ui.theme.JakartaFontFamily
import java.io.File

enum class NavDestination(
    val label: String,
    val icon: ImageVector,
    val testTagId: String
) {
    APPS("Apps", Icons.Rounded.Apps, "nav_apps"),
    FILES("Files", Icons.Rounded.Folder, "nav_files"),
    ACTIVITY("Activity", Icons.Rounded.History, "nav_activity"),
    SETTINGS("Settings", Icons.Rounded.Settings, "nav_settings")
}

class MainActivity : ComponentActivity() {
    companion object {
        @JvmStatic
        fun isLsposedModuleActiveReal(): Boolean {
            return false // Hooked by StorageIsolationHook when Vector / LSPosed is active
        }
    }

    private var pendingSharedUris by mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extractSharedUrisFromIntent(intent)

        setContent {
            val viewModel: IsoVaultViewModel = viewModel()
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()
            val blockScreenshots by viewModel.blockScreenshots.collectAsState()

            // Dynamically apply or clear FLAG_SECURE for screenshot protection
            LaunchedEffect(blockScreenshots) {
                if (blockScreenshots) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            IsoVaultTheme(darkTheme = isDarkTheme) {
                MainAppContainer(
                    viewModel = viewModel,
                    incomingSharedUris = pendingSharedUris,
                    onConsumeSharedUris = { pendingSharedUris = emptyList() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractSharedUrisFromIntent(intent)
    }

    private fun extractSharedUrisFromIntent(intent: Intent?) {
        if (intent == null) return
        val extracted = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val single = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (single != null) extracted.add(single)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                if (!list.isNullOrEmpty()) extracted.addAll(list)
            }
        }
        if (extracted.isNotEmpty()) {
            pendingSharedUris = extracted
        }
    }
}

@Composable
fun MainAppContainer(
    viewModel: IsoVaultViewModel = viewModel(),
    incomingSharedUris: List<Uri> = emptyList(),
    onConsumeSharedUris: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colorScheme = MaterialTheme.colorScheme

    var currentScreen by remember { mutableStateOf(NavDestination.APPS) }
    var isPasscodeLocked by remember { mutableStateOf(false) }
    var backgroundedAtMs by remember { mutableLongStateOf(0L) }

    val snackbarHostState = remember { SnackbarHostState() }
    val statusMessage by viewModel.statusMessage.collectAsState()
    val primaryPin by viewModel.primaryPin.collectAsState()
    val panicPin by viewModel.panicPin.collectAsState()
    val decoyPinEnabled by viewModel.decoyPinEnabled.collectAsState()
    val fingerprintEnabled by viewModel.fingerprintEnabled.collectAsState()
    val autoLockMinutes by viewModel.autoLockMinutes.collectAsState()

    // Handle "Share to Vault" incoming URIs from external apps
    LaunchedEffect(incomingSharedUris) {
        if (incomingSharedUris.isNotEmpty()) {
            val sharedFolder = File(viewModel.getAppSandboxDirectory("shared.vault"), "Photos")
            viewModel.importUrisToVault(
                uris = incomingSharedUris,
                targetDir = sharedFolder,
                sourceAppName = "Shared to Vault",
                sourcePackage = "shared.vault"
            )
            viewModel.selectVaultAppPackage("shared.vault")
            currentScreen = NavDestination.FILES
            onConsumeSharedUris()
        }
    }

    // Auto-lock on backgrounding based on configured timeout + wipe ephemeral cache
    DisposableEffect(lifecycleOwner, autoLockMinutes) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    backgroundedAtMs = System.currentTimeMillis()
                    if (autoLockMinutes == 0) {
                        isPasscodeLocked = true
                        viewModel.onVaultLocked()
                    }
                }
                Lifecycle.Event.ON_START -> {
                    if (backgroundedAtMs > 0L && autoLockMinutes > 0) {
                        val elapsedMs = System.currentTimeMillis() - backgroundedAtMs
                        if (elapsedMs >= autoLockMinutes * 60_000L) {
                            isPasscodeLocked = true
                            viewModel.onVaultLocked()
                        }
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    BackHandler(enabled = currentScreen != NavDestination.APPS || isPasscodeLocked) {
        if (!isPasscodeLocked) {
            currentScreen = NavDestination.APPS
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                Column {
                    HorizontalDivider(color = colorScheme.outlineVariant, thickness = 1.dp)
                    NavigationBar(
                        containerColor = colorScheme.surface,
                        contentColor = colorScheme.onSurface,
                        tonalElevation = 0.dp,
                        modifier = Modifier.testTag("bottom_nav_bar")
                    ) {
                        NavDestination.entries.forEach { dest ->
                            val isSelected = currentScreen == dest
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { currentScreen = dest },
                                icon = {
                                    Icon(
                                        imageVector = dest.icon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        fontSize = 12.sp,
                                        fontFamily = JakartaFontFamily,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colorScheme.primary,
                                    selectedTextColor = colorScheme.primary,
                                    unselectedIconColor = colorScheme.onSurfaceVariant,
                                    unselectedTextColor = colorScheme.onSurfaceVariant,
                                    indicatorColor = colorScheme.primary.copy(alpha = 0.14f)
                                ),
                                modifier = Modifier.testTag(dest.testTagId)
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
                when (currentScreen) {
                    NavDestination.APPS -> AppsScreen(
                        viewModel = viewModel,
                        onTriggerLock = {
                            viewModel.onVaultLocked()
                            isPasscodeLocked = true
                        },
                        onOpenAppVaultInFiles = { packageName ->
                            viewModel.selectVaultAppPackage(packageName)
                            currentScreen = NavDestination.FILES
                        }
                    )
                    NavDestination.FILES -> FilesScreen(
                        viewModel = viewModel
                    )
                    NavDestination.ACTIVITY -> ActivityScreen(
                        viewModel = viewModel
                    )
                    NavDestination.SETTINGS -> SettingsScreen(
                        viewModel = viewModel,
                        onTriggerLock = {
                            viewModel.onVaultLocked()
                            isPasscodeLocked = true
                        }
                    )
                }
            }
        }

        // Lock Screen Overlay (PIN pad + Fingerprint + Wrong PIN Cooldown + Decoy PIN)
        AnimatedVisibility(
            visible = isPasscodeLocked,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PasscodeScreen(
                correctPin = primaryPin,
                panicPin = panicPin,
                decoyPinEnabled = decoyPinEnabled,
                fingerprintEnabled = fingerprintEnabled,
                onUnlocked = {
                    viewModel.exitPanicMode()
                    isPasscodeLocked = false
                },
                onPanicUnlocked = {
                    isPasscodeLocked = false
                    viewModel.triggerPanicMode()
                    currentScreen = NavDestination.FILES
                }
            )
        }
    }
}
