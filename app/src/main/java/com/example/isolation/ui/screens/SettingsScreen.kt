package com.example.isolation.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import com.example.isolation.model.PickerPolicy
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.SageSoft
import com.example.ui.theme.SageTint
import com.example.ui.theme.WarmIvory
import com.example.ui.theme.WarmSand
import com.example.ui.theme.WarmStone

@Composable
fun SettingsScreen(
    viewModel: IsoVaultViewModel,
    onOpenDiagnostics: () -> Unit = {}
) {
    val isLsposedActive by viewModel.isLsposedActive.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val rootStatus by viewModel.rootStatus.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Settings & Setup",
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                fontFamily = FrauncesFontFamily,
                color = InkPrimary
            )
            Text(
                text = "How to turn on gallery protection and manage your locker key.",
                fontSize = 13.sp,
                color = InkSecondary,
                fontFamily = JakartaFontFamily
            )
        }

        // Friendly Setup Guide Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = WarmIvory),
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, WarmStone),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SageSoft)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = ForestPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Quick Setup Guide",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp,
                                fontFamily = FrauncesFontFamily,
                                color = InkPrimary
                            )
                            Text(
                                text = if (isLsposedActive) "Protection is currently active" else "3 simple steps to activate",
                                fontSize = 12.sp,
                                color = InkSecondary,
                                fontFamily = JakartaFontFamily
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    SetupStep(
                        step = "1",
                        title = "Enable IsoVault in Vector or LSPosed",
                        desc = "Open your module manager app and switch on IsoVault."
                    )
                    SetupStep(
                        step = "2",
                        title = "Check the apps you want to isolate",
                        desc = "Tick Snapchat, WhatsApp, or any app you want to keep separate."
                    )
                    SetupStep(
                        step = "3",
                        title = "Force-stop and reopen the app",
                        desc = "When you reopen Snapchat or WhatsApp, its gallery will be clean and private."
                    )
                }
            }
        }

        // Isolation controls
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = WarmIvory),
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, WarmStone),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Isolation Controls",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        fontFamily = FrauncesFontFamily,
                        color = InkPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    ToggleRow(
                        title = "Root volumes",
                        description = "Lets you attach a private volume to an app (Apps > app > Attach Volume). Needs root.",
                        checked = settings.rootVolumesEnabled,
                        enabled = true,
                        onChange = { viewModel.setRootVolumesEnabled(it) }
                    )
                    HorizontalDivider(color = WarmStone)
                    ToggleRow(
                        title = "Encrypt volumes when detached",
                        description = "Detach stops the app, encrypts its volume (AES-256) and deletes the plain files. " +
                            "Attach decrypts it again. While an app is using its volume the files are plain.",
                        checked = settings.encryptVolumesAtRest,
                        enabled = settings.rootVolumesEnabled,
                        onChange = { viewModel.setEncryptVolumesAtRest(it) }
                    )
                    HorizontalDivider(color = WarmStone)

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "System photo / file picker",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = InkPrimary,
                        fontFamily = JakartaFontFamily
                    )
                    Text(
                        text = settings.pickerPolicy.description,
                        fontSize = 12.sp,
                        color = InkSecondary,
                        fontFamily = JakartaFontFamily
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        for (policy in PickerPolicy.values()) {
                            val selected = settings.pickerPolicy == policy
                            if (selected) {
                                Button(
                                    onClick = { viewModel.setPickerPolicy(policy) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary, contentColor = Color.White),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(policy.title, fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { viewModel.setPickerPolicy(policy) },
                                    border = androidx.compose.foundation.BorderStroke(1.dp, WarmStone),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(policy.title, color = InkPrimary, fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = WarmStone)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Root status",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = InkPrimary,
                                fontFamily = JakartaFontFamily
                            )
                            Text(text = rootStatus, fontSize = 12.sp, color = InkSecondary, fontFamily = JakartaFontFamily)
                        }
                        OutlinedButton(
                            onClick = { viewModel.checkRoot() },
                            border = androidx.compose.foundation.BorderStroke(1.dp, WarmStone),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Check root", color = InkPrimary, fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Locker key card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = WarmIvory),
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, WarmStone),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(WarmSand)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = ForestPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Locker Encryption",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp,
                                fontFamily = FrauncesFontFamily,
                                color = InkPrimary
                            )
                            Text(
                                text = "AES-256-GCM, key held in the Android Keystore",
                                fontSize = 12.sp,
                                color = InkSecondary,
                                fontFamily = JakartaFontFamily
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Every locker file gets its own random key, wrapped by a key that never leaves the Keystore. " +
                            "There is no passphrase to leak, and locker files cannot be opened on another device or after " +
                            "clearing the app's data. Export anything you need to keep.",
                        fontSize = 13.sp,
                        color = InkSecondary,
                        fontFamily = JakartaFontFamily
                    )
                }
            }
        }

        // About & Advanced Diagnostics Link
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = WarmIvory),
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, WarmStone),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "How Protection Works",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FrauncesFontFamily,
                        color = InkPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FeatureRow(title = "Photo & Video Gallery", value = "Hidden from selected apps")
                    FeatureRow(title = "App Downloads & Saves", value = "Kept in private app folder")
                    FeatureRow(title = "Phone Storage Clutter", value = "Automatically blocked")

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = WarmStone)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDiagnostics() }
                            .padding(vertical = 4.dp)
                            .testTag("nav_mounts")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = InkSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Advanced System Details",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = InkPrimary,
                                fontFamily = JakartaFontFamily
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = InkSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) InkPrimary else InkSecondary,
                fontFamily = JakartaFontFamily
            )
            Text(text = description, fontSize = 12.sp, color = InkSecondary, fontFamily = JakartaFontFamily)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ForestPrimary)
        )
    }
}

@Composable
fun SetupStep(step: String, title: String, desc: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(SageSoft)
        ) {
            Text(
                text = step,
                color = ForestPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = JakartaFontFamily
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = InkPrimary,
                fontFamily = JakartaFontFamily
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                color = InkSecondary,
                fontFamily = JakartaFontFamily
            )
        }
    }
}

@Composable
fun FeatureRow(title: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Text(text = title, fontSize = 13.sp, color = InkSecondary, fontFamily = JakartaFontFamily)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = InkPrimary, fontFamily = JakartaFontFamily)
    }
}
