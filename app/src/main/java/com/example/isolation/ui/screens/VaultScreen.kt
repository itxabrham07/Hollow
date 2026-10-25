package com.example.isolation.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.data.entity.VaultFileItem
import com.example.isolation.ui.components.DecryptedPreviewDialog
import com.example.isolation.ui.components.StatusBadge
import com.example.isolation.ui.components.formatBytes
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.ClaySecondary
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
fun VaultScreen(
    viewModel: IsoVaultViewModel
) {
    val vaultFiles by viewModel.vaultFiles.collectAsState()
    val totalBytes by viewModel.totalEncryptedBytes.collectAsState()
    val previewItem by viewModel.previewDecryptedText.collectAsState()

    var selectedPkgFilter by remember { mutableStateOf("ALL") }
    var showCreateDialog by remember { mutableStateOf(false) }

    val packages = listOf("ALL") + vaultFiles.map { it.packageName }.distinct()

    val filteredFiles = if (selectedPkgFilter == "ALL") {
        vaultFiles
    } else {
        vaultFiles.filter { it.packageName == selectedPkgFilter }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("vault_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Private Locker",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FrauncesFontFamily,
                color = InkPrimary
            )
            Text(
                text = "Files saved by protected apps are kept safely inside their own locker.",
                fontSize = 13.sp,
                color = InkSecondary,
                fontFamily = JakartaFontFamily
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Locker Summary Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SageTint),
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ForestPrimary.copy(alpha = 0.2f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(18.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(SageSoft)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = ForestPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${vaultFiles.size} Saved Items • ${formatBytes(totalBytes)}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            fontFamily = FrauncesFontFamily,
                            color = InkPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Kept separate from your main gallery and other apps.",
                            fontSize = 12.sp,
                            color = InkSecondary,
                            fontFamily = JakartaFontFamily
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // App Filter Chips
            if (packages.size > 2) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(packages) { pkg ->
                        val isSelected = selectedPkgFilter == pkg
                        val label = if (pkg == "ALL") "All Items" else pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedPkgFilter = pkg },
                            label = { Text(label, fontFamily = JakartaFontFamily) },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ForestPrimary,
                                selectedLabelColor = Color.White,
                                containerColor = WarmIvory,
                                labelColor = InkSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = WarmStone,
                                selectedBorderColor = ForestPrimary
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Files list
            if (filteredFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(WarmSand)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = InkSecondary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Your locker is empty",
                            color = InkPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FrauncesFontFamily
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap 'Add Note' below to save a private note or test how files are locked.",
                            color = InkSecondary,
                            fontSize = 13.sp,
                            fontFamily = JakartaFontFamily
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredFiles, key = { it.id }) { fileItem ->
                        VaultFileCard(
                            fileItem = fileItem,
                            onPreview = { viewModel.decryptAndPreviewFile(fileItem) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(84.dp))
                    }
                }
            }
        }

        // FAB to add private file
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = ForestPrimary,
            contentColor = Color.White,
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("fab_create_vault_file")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Note", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, fontFamily = JakartaFontFamily)
            }
        }
    }

    previewItem?.let { (title, content) ->
        DecryptedPreviewDialog(
            title = title,
            content = content,
            onDismiss = { viewModel.clearPreview() }
        )
    }

    if (showCreateDialog) {
        CreateTestFileDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { pkg, path, content ->
                viewModel.testSimulateAppIo(
                    packageName = pkg,
                    appName = pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() },
                    testPath = path,
                    fileContent = content
                )
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun VaultFileCard(
    fileItem: VaultFileItem,
    onPreview: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = WarmIvory),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, WarmStone),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vault_file_${fileItem.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SageSoft)
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = ForestPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileItem.relativePath.substringAfterLast('/'),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = InkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = JakartaFontFamily
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(text = "Private", color = ForestPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatBytes(fileItem.encryptedSizeBytes),
                        fontSize = 12.sp,
                        color = InkSecondary,
                        fontFamily = JakartaFontFamily
                    )
                }
            }

            Button(
                onClick = onPreview,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarmSand,
                    contentColor = InkPrimary
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier.testTag("btn_preview_${fileItem.id}")
            ) {
                Icon(imageVector = Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily)
            }
        }
    }
}

@Composable
fun CreateTestFileDialog(
    onDismiss: () -> Unit,
    onCreate: (packageName: String, path: String, content: String) -> Unit
) {
    var packageName by remember { mutableStateOf("com.snapchat.android") }
    var path by remember { mutableStateOf("/storage/emulated/0/DCIM/PrivateNote.txt") }
    var content by remember { mutableStateOf("Personal note saved inside your private locker.") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = WarmIvory,
        title = {
            Text(
                "New Private Note",
                fontWeight = FontWeight.SemiBold,
                fontFamily = FrauncesFontFamily,
                color = InkPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("File Name / Folder", fontSize = 12.sp, fontFamily = JakartaFontFamily) },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestPrimary,
                        unfocusedBorderColor = WarmStone
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Note Content", fontSize = 12.sp, fontFamily = JakartaFontFamily) },
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestPrimary,
                        unfocusedBorderColor = WarmStone
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(packageName, path, content) },
                colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary, contentColor = Color.White),
                shape = RoundedCornerShape(50)
            ) {
                Text("Save to Locker", fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = InkSecondary, fontFamily = JakartaFontFamily)
            }
        }
    )
}
