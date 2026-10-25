package com.example.isolation.ui.screens.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.model.IsolationMode
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentMint
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailSheet(
    config: AppIsolationConfig,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onModeChanged: (IsolationMode) -> Unit,
    onPollutionChanged: (Boolean) -> Unit,
    onWhitelistChanged: (String) -> Unit,
    onSharedSpaceChanged: (Boolean) -> Unit,
    onPurgeVault: () -> Unit
) {
    var whitelistInput by remember(config.packageName) { mutableStateOf(config.whitelistedFolders) }
    var showPurgeConfirmDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkBackground,
        dragHandle = null,
        modifier = Modifier.testTag("app_detail_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentSky.copy(alpha = 0.15f))
                            .border(1.dp, AccentSky.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = AccentSky,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = config.appName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            fontFamily = JakartaFontFamily
                        )
                        Text(
                            text = config.packageName,
                            fontSize = 12.sp,
                            color = TextMuted,
                            fontFamily = JakartaFontFamily
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = DarkBorderSubtle)
            Spacer(modifier = Modifier.height(18.dp))

            // Shared Space Toggle
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = AccentSky, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Use Shared Space",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextWhite,
                                fontFamily = JakartaFontFamily
                            )
                            Text(
                                text = "Allow this app to access files in the shared vault",
                                fontSize = 11.sp,
                                color = TextMuted,
                                fontFamily = JakartaFontFamily
                            )
                        }
                    }
                    Switch(
                        checked = config.useSharedSpace,
                        onCheckedChange = onSharedSpaceChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentSky,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurfaceElevated
                        ),
                        modifier = Modifier.testTag("switch_shared_space")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Isolation Mode Header
            Text(
                text = "Isolation Strategy",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite,
                fontFamily = JakartaFontFamily
            )
            Spacer(modifier = Modifier.height(10.dp))

            IsolationMode.values().forEach { mode ->
                val isSelected = config.isolationMode == mode
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) AccentSky.copy(alpha = 0.12f) else DarkSurfaceCard
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) AccentSky else DarkBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onModeChanged(mode) }
                        .testTag("mode_${mode.name}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .border(2.dp, if (isSelected) AccentSky else TextSubtle, CircleShape)
                                .padding(3.dp)
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(AccentSky)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = mode.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) AccentSky else TextWhite,
                                fontFamily = JakartaFontFamily
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = mode.description,
                                fontSize = 11.sp,
                                color = TextMuted,
                                fontFamily = JakartaFontFamily
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Whitelist Folders
            Text(
                text = "Whitelisted Folders (Comma-separated)",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextWhite,
                fontFamily = JakartaFontFamily
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = whitelistInput,
                onValueChange = {
                    whitelistInput = it
                    onWhitelistChanged(it)
                },
                placeholder = { Text("e.g. Download, Documents", color = TextSubtle, fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentSky,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedContainerColor = DarkSurfaceCard,
                    unfocusedContainerColor = DarkSurfaceCard
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("whitelist_input")
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Purge Volume Button
            OutlinedButton(
                onClick = { showPurgeConfirmDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCoral),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentCoral.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_purge_vault")
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Purge Isolated Vault Storage", fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showPurgeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirmDialog = false },
            title = { Text("Purge Isolated Vault?", color = TextWhite, fontFamily = JakartaFontFamily) },
            text = {
                Text(
                    "This permanently deletes all encrypted files stored inside the private volume for ${config.appName}. This cannot be undone.",
                    color = TextMuted,
                    fontFamily = JakartaFontFamily
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPurgeConfirmDialog = false
                        onPurgeVault()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCoral)
                ) {
                    Text("Purge", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirmDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }
}
