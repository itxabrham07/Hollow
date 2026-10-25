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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentMint
import com.example.ui.theme.AccentPink
import com.example.ui.theme.AccentSky
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkBorderSubtle
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSubtle
import com.example.ui.theme.TextWhite

@Composable
fun SecurityScreen(
    viewModel: IsoVaultViewModel,
    onTriggerLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLsposedActive by viewModel.isLsposedActive.collectAsState()
    val isPanicModeActive by viewModel.isPanicModeActive.collectAsState()
    val primaryPin by viewModel.primaryPin.collectAsState()
    val panicPin by viewModel.panicPin.collectAsState()

    var showPinDialog by remember { mutableStateOf(false) }
    var primaryInput by remember(primaryPin) { mutableStateOf(primaryPin) }
    var panicInput by remember(panicPin) { mutableStateOf(panicPin) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            // Screen Header matching mockup style
            Text(
                text = "Security",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = JakartaFontFamily,
                color = TextWhite
            )
            Text(
                text = "Duress protection, PINs & vault encryption",
                fontSize = 13.sp,
                fontFamily = JakartaFontFamily,
                color = TextMuted
            )
        }

        // Vault Space Hero Card matching bottom-center phone in mockup
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(DarkSurfaceCard)
                    .border(1.dp, DarkBorderSubtle, RoundedCornerShape(26.dp))
                    .padding(22.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // "Vault Space 😎" with neon ring style from mockup
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .border(1.5.dp, AccentPink, RoundedCornerShape(50))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Vault Space",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = JakartaFontFamily,
                                    color = TextWhite
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "😎", fontSize = 20.sp)
                        }

                        // Lock Button
                        IconButton(
                            onClick = onTriggerLock,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceElevated)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock Now",
                                tint = AccentSky,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Encrypted storage engine is active on device. Target apps write to private isolated sandboxes that survive application reinstallation.",
                        fontSize = 12.sp,
                        fontFamily = JakartaFontFamily,
                        color = TextMuted,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Section: Panic PIN & Passcode Protection
        item {
            Text(
                text = "ACCESS & DURESS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = JakartaFontFamily,
                color = TextSubtle,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Panic PIN Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPinDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AccentCoral.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = AccentCoral,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Panic PIN (Decoy Vault)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JakartaFontFamily,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Panic: $panicPin • Primary: $primaryPin",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AccentCoral
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Edit PINs",
                        tint = TextSubtle,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Lock App Now Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTriggerLock)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AccentSky.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = AccentSky,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lock Vault Screen",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JakartaFontFamily,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Test circular keypad & passcode verification",
                            fontSize = 12.sp,
                            fontFamily = JakartaFontFamily,
                            color = TextMuted
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Lock",
                        tint = TextSubtle,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Section: Storage Persistence Location
        item {
            Text(
                text = "PERSISTENT STORAGE LOCATION",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = JakartaFontFamily,
                color = TextSubtle,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentMint.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = AccentMint,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Safe External Vault Root",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = JakartaFontFamily,
                                color = TextWhite
                            )
                            Text(
                                text = "/storage/emulated/0/.IsoVault/",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AccentMint
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "• Protected with hidden .nomedia so system galleries ignore it.\n• Saved media survives if target app or IsoVault is uninstalled.\n• Encrypted with AES-256-GCM hardware cipher.",
                        fontSize = 12.sp,
                        fontFamily = JakartaFontFamily,
                        color = TextMuted,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Configure PIN Dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, null, tint = AccentSky, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Configure PINs", color = TextWhite, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Enter a 4-digit Primary PIN to open your real vault, and a Panic PIN to open a decoy vault under duress.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontFamily = JakartaFontFamily
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Primary PIN", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = primaryInput,
                        onValueChange = { if (it.length <= 4) primaryInput = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentSky,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Panic / Duress PIN", color = AccentCoral, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = panicInput,
                        onValueChange = { if (it.length <= 4) panicInput = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCoral,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updatePins(primaryInput, panicInput)
                        showPinDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentSky)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = DarkSurfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
