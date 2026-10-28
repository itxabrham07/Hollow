package com.example.isolation.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.isolation.core.root.LicenseAccountStatus
import com.example.isolation.ui.components.ContentCard
import com.example.isolation.ui.components.ProSegmentedTabRow
import com.example.isolation.ui.components.SectionHeader
import com.example.isolation.ui.components.StatusIndicator
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.M3AmberAccent
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingAuthScreen(
    viewModel: IsoVaultViewModel,
    onContinueToDashboard: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val vaultRecord by viewModel.vaultLicenseRecord.collectAsStateWithLifecycle()
    val moduleStates by viewModel.moduleStates.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isRootGranted by viewModel.isRootAccessGranted.collectAsStateWithLifecycle()
    val isRequestingRoot by viewModel.isRequestingRoot.collectAsStateWithLifecycle()
    val isAuthLoading by viewModel.isAuthLoading.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val hw = viewModel.hardwareBinding

    // 0 = Create Account (Register), 1 = Sign In
    var authTabIndex by remember { mutableIntStateOf(0) }
    var username by remember(vaultRecord.username) {
        mutableStateOf(vaultRecord.username)
    }
    var email by remember(vaultRecord.email) {
        mutableStateOf(vaultRecord.email)
    }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Auto-poll server every 6 seconds while waiting for approval, and auto-navigate once approved
    LaunchedEffect(vaultRecord.status) {
        if (vaultRecord.status == LicenseAccountStatus.PENDING_APPROVAL) {
            while (true) {
                viewModel.syncLicenseAndModulesFromServer()
                delay(6_000L)
            }
        } else if (vaultRecord.status == LicenseAccountStatus.APPROVED && onBack == null) {
            viewModel.downloadAllAuthorizedModules()
            delay(400L)
            onContinueToDashboard()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("onboarding_auth_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Hollow",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Hollow",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FrauncesFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when (vaultRecord.status) {
                                    LicenseAccountStatus.PENDING_APPROVAL -> "Approval Verification"
                                    LicenseAccountStatus.APPROVED -> "Account & Modules"
                                    LicenseAccountStatus.BANNED -> "Access Restricted"
                                    else -> "Getting Started"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("btn_onboarding_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    if (vaultRecord.status == LicenseAccountStatus.APPROVED || settings.devFullBuildMode) {
                        FilledTonalButton(
                            onClick = onContinueToDashboard,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("btn_continue_dashboard"),
                            shape = MaterialTheme.shapes.small,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Dashboard",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Open Dashboard",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (vaultRecord.status) {
                LicenseAccountStatus.PENDING_APPROVAL -> {
                    WaitingForApprovalView(
                        username = vaultRecord.username,
                        email = vaultRecord.email,
                        hardwareId = hw.fingerprintHex,
                        deviceModel = hw.deviceModel,
                        isRootGranted = isRootGranted,
                        statusMessage = statusMessage,
                        onCheckStatus = { viewModel.syncLicenseAndModulesFromServer() },
                        onRequestRoot = { viewModel.requestRootAccess() },
                        onCopyHardwareId = {
                            copyToClipboard(context, "Hollow Device ID", hw.fingerprintHex)
                            viewModel.postStatusMessage("Device ID copied to clipboard")
                        },
                        onSwitchAccount = { viewModel.simulateAdminResetToUnregistered() }
                    )
                }

                LicenseAccountStatus.BANNED -> {
                    BannedDeviceView(
                        banReason = vaultRecord.banReason,
                        hardwareId = hw.fingerprintHex,
                        onReset = { viewModel.simulateAdminResetToUnregistered() }
                    )
                }

                LicenseAccountStatus.APPROVED -> {
                    ApprovedAccountHubView(
                        viewModel = viewModel,
                        username = vaultRecord.username,
                        email = vaultRecord.email,
                        hardwareId = hw.fingerprintHex,
                        allowedCount = vaultRecord.allowedModules.size,
                        downloadedCount = vaultRecord.downloadedModules.size,
                        moduleStates = moduleStates,
                        onContinueToDashboard = onContinueToDashboard
                    )
                }

                else -> {
                    GettingStartedOnboardingView(
                        authTabIndex = authTabIndex,
                        onTabChange = { authTabIndex = it },
                        username = username,
                        onUsernameChange = { username = it },
                        email = email,
                        onEmailChange = { email = it },
                        password = password,
                        onPasswordChange = { password = it },
                        passwordVisible = passwordVisible,
                        onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                        isRootGranted = isRootGranted,
                        isRequestingRoot = isRequestingRoot,
                        isAuthLoading = isAuthLoading,
                        statusMessage = statusMessage,
                        onRequestRoot = { viewModel.requestRootAccess() },
                        onSubmitRegister = {
                            viewModel.registerAccountWithCloud(
                                username = username.ifBlank { "user_${hw.androidId.take(4)}" },
                                email = email.ifBlank { "user@hollow.app" },
                                password = password.ifBlank { "hollow_pass" }
                            )
                        },
                        onSubmitSignIn = {
                            viewModel.signInWithCloud(
                                identifier = username.ifBlank { email },
                                password = password
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GettingStartedOnboardingView(
    authTabIndex: Int,
    onTabChange: (Int) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    isRootGranted: Boolean,
    isRequestingRoot: Boolean,
    isAuthLoading: Boolean,
    statusMessage: String?,
    onRequestRoot: () -> Unit,
    onSubmitRegister: () -> Unit,
    onSubmitSignIn: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 600.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Expressive Getting Started Hero
        item {
            ContentCard(
                shape = MaterialTheme.shapes.large,
                containerColor = scheme.primaryContainer,
                borderColor = scheme.primary.copy(alpha = 0.25f),
                modifier = Modifier.testTag("card_getting_started_hero")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = scheme.primary.copy(alpha = 0.14f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = "Privacy Layer",
                                tint = scheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        StatusIndicator(
                            label = "Privacy Layer Architecture",
                            color = scheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "The Privacy Layer Between Your Apps & You",
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = FrauncesFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Hollow enforces OS-level storage sandboxing, identity protection, and modular privacy controls. Grant root access and sign in or create an account to activate your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }

        // 2. Step 1: Root Access Permission Gate
        item {
            ContentCard(
                modifier = Modifier.testTag("card_root_permission_step"),
                borderColor = if (isRootGranted) scheme.secondary else scheme.outlineVariant
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isRootGranted) {
                                scheme.secondary.copy(alpha = 0.14f)
                            } else {
                                scheme.primary.copy(alpha = 0.14f)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isRootGranted) Icons.Default.CheckCircle else Icons.Default.Security,
                                    contentDescription = "Root Status",
                                    tint = if (isRootGranted) scheme.secondary else scheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Step 1 · Root Permission",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurface
                            )
                            Text(
                                text = if (isRootGranted) {
                                    "Superuser access granted and verified"
                                } else {
                                    "Required to initialize the isolated privacy engine"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                    StatusIndicator(
                        label = if (isRootGranted) "Granted" else "Required",
                        color = if (isRootGranted) scheme.secondary else M3AmberAccent
                    )
                }

                if (!isRootGranted) {
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = onRequestRoot,
                        enabled = !isRequestingRoot,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("btn_request_root"),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (isRequestingRoot) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Waiting for Superuser Prompt...",
                                style = MaterialTheme.typography.labelLarge
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Grant Root Permission",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 3. Step 2: Sign In or Create Account
        item {
            ContentCard(
                modifier = Modifier.testTag("card_registration_form")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Step 2 · Account Verification",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface
                        )
                        Text(
                            text = if (authTabIndex == 0) {
                                "Register your device to request administrator approval and modular feature access."
                            } else {
                                "Already registered? Sign in to sync your approval status and install your modules."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }

                    ProSegmentedTabRow(
                        options = listOf(
                            "Create Account" to Icons.Default.HowToReg,
                            "Sign In" to Icons.AutoMirrored.Filled.Login
                        ),
                        selectedIndex = authTabIndex,
                        onSelect = onTabChange
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        label = {
                            Text(if (authTabIndex == 0) "Username" else "Username or Email")
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_register_username")
                    )

                    AnimatedVisibility(visible = authTabIndex == 0) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = onEmailChange,
                            label = { Text("Email Address") },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null)
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_register_email")
                        )
                    }

                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = onTogglePasswordVisible) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_register_password")
                    )

                    if (!statusMessage.isNullOrBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = scheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = statusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (authTabIndex == 0) onSubmitRegister() else onSubmitSignIn()
                        },
                        enabled = !isAuthLoading && (username.isNotBlank() || email.isNotBlank()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("btn_submit_registration"),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (isAuthLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = scheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (!isRootGranted) "Requesting Root & Connecting..." else "Connecting...",
                                style = MaterialTheme.typography.labelLarge
                            )
                        } else {
                            Icon(
                                imageVector = if (authTabIndex == 0) {
                                    Icons.Default.HowToReg
                                } else {
                                    Icons.AutoMirrored.Filled.Login
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (authTabIndex == 0) {
                                    if (isRootGranted) "Create Account & Request Approval" else "Grant Root & Create Account"
                                } else {
                                    if (isRootGranted) "Sign In & Sync Modules" else "Grant Root & Sign In"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WaitingForApprovalView(
    username: String,
    email: String,
    hardwareId: String,
    deviceModel: String,
    isRootGranted: Boolean,
    statusMessage: String?,
    onCheckStatus: () -> Unit,
    onRequestRoot: () -> Unit,
    onCopyHardwareId: () -> Unit,
    onSwitchAccount: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val pulseTransition = rememberInfiniteTransition(label = "approval_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 600.dp)
            .testTag("card_waiting_approval"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            ContentCard(
                shape = MaterialTheme.shapes.large,
                containerColor = scheme.primaryContainer,
                borderColor = scheme.primary.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = scheme.primary.copy(alpha = 0.16f),
                        modifier = Modifier
                            .size(72.dp)
                            .scale(pulseScale)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.HourglassTop,
                                contentDescription = "Waiting for Approval",
                                tint = scheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    StatusIndicator(
                        label = "Pending Administrator Approval",
                        color = M3AmberAccent
                    )

                    Text(
                        text = "Waiting for Approval",
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = FrauncesFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Your registration has been sent. Once the administrator approves your device, Hollow will automatically install your authorized modules and unlock your dashboard.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        item {
            ContentCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Registration Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )

                    ApprovalDetailRow(
                        label = "Account",
                        value = username.ifBlank { "Registered User" } +
                            if (email.isNotBlank()) " · $email" else ""
                    )
                    ApprovalDetailRow(
                        label = "Device",
                        value = deviceModel
                    )
                    ApprovalDetailRow(
                        label = "Root Access",
                        value = if (isRootGranted) "Granted" else "Not Granted"
                    )

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = scheme.surfaceVariant,
                        border = BorderStroke(1.dp, scheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Hardware Verification ID",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant
                                )
                                Text(
                                    text = hardwareId,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = onCopyHardwareId) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Device ID",
                                    tint = scheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (!isRootGranted) {
                        FilledTonalButton(
                            onClick = onRequestRoot,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Grant Root Permission",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    if (!statusMessage.isNullOrBlank()) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onCheckStatus,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_poll_approval_status"),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Check Status",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = onSwitchAccount,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("btn_edit_registration"),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                text = "Switch Account",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovedAccountHubView(
    viewModel: IsoVaultViewModel,
    username: String,
    email: String,
    hardwareId: String,
    allowedCount: Int,
    downloadedCount: Int,
    moduleStates: List<com.example.isolation.core.module.DynamicModuleState>,
    onContinueToDashboard: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 600.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ContentCard(
                shape = MaterialTheme.shapes.large,
                containerColor = scheme.primaryContainer,
                borderColor = scheme.primary.copy(alpha = 0.25f),
                modifier = Modifier.testTag("card_approved_license")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = scheme.secondary.copy(alpha = 0.16f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = "Approved",
                                        tint = scheme.secondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = username.ifBlank { "Authorized Operator" },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontFamily = FrauncesFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onPrimaryContainer
                                )
                                Text(
                                    text = "$downloadedCount of $allowedCount authorized modules active",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                        StatusIndicator(
                            label = "Approved",
                            color = scheme.secondary
                        )
                    }

                    Text(
                        text = "Device ID: $hardwareId" + if (email.isNotBlank()) " · $email" else "",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onContinueToDashboard,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                text = "Open Dashboard",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                viewModel.syncLicenseAndModulesFromServer()
                                viewModel.downloadAllAuthorizedModules()
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync Modules",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Sync",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Authorized Modules")
        }

        items(moduleStates, key = { it.module.id }) { modState ->
            val mod = modState.module
            ContentCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("module_vault_row_${mod.id}")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (modState.isReadyToUse) {
                                scheme.primaryContainer
                            } else {
                                scheme.surfaceVariant
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = mod.icon,
                                    contentDescription = mod.cardTitle,
                                    tint = if (modState.isReadyToUse) {
                                        scheme.primary
                                    } else {
                                        scheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mod.cardTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurface
                            )
                            Text(
                                text = mod.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }

                    if (modState.isReadyToUse) {
                        StatusIndicator(label = "Active", color = scheme.secondary)
                    } else if (modState.isAuthorizedByAdmin) {
                        FilledTonalButton(
                            onClick = { viewModel.downloadModuleIntoRootVault(mod.id) },
                            modifier = Modifier.testTag("btn_download_${mod.id}"),
                            shape = MaterialTheme.shapes.small,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Install",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    } else {
                        StatusIndicator(
                            label = "Not Assigned",
                            color = scheme.onSurfaceVariant,
                            showDot = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BannedDeviceView(
    banReason: String,
    hardwareId: String,
    onReset: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 600.dp)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ContentCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_device_banned")
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "Restricted",
                        tint = scheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "Device Access Restricted",
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FrauncesFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = scheme.error
                        )
                        Text(
                            text = hardwareId,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = banReason.ifBlank { "This device's access has been revoked by the administrator." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_unban_reset"),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = "Reset Registration",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun ApprovalDetailRow(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    } catch (_: Throwable) {
    }
}
