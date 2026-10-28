package com.example.isolation.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.isolation.data.entity.VaultFileItem
import com.example.isolation.ui.components.ContentCard
import com.example.isolation.ui.components.DecryptedPreviewDialog
import com.example.isolation.ui.components.EmptyState
import com.example.isolation.ui.components.ListRow
import com.example.isolation.ui.components.PrimaryButton
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.FrauncesFontFamily

@Composable
fun VaultScreen(
    viewModel: IsoVaultViewModel,
    onBack: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    val vaultFiles by viewModel.vaultFiles.collectAsState()
    val totalBytes by viewModel.totalEncryptedBytes.collectAsState()
    val previewItem by viewModel.previewDecryptedText.collectAsState()
    val previewBitmap by viewModel.previewDecryptedBitmap.collectAsState()

    var selectedTypeFilter by remember { mutableStateOf("ALL") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var exportTarget by remember { mutableStateOf<VaultFileItem?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFile(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val item = exportTarget
        if (uri != null && item != null) viewModel.exportFile(item, uri)
        exportTarget = null
    }

    val filteredFiles = vaultFiles.filter { item ->
        when (selectedTypeFilter) {
            "IMAGES" -> item.mimeType.startsWith("image/") ||
                item.relativePath.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "webp")
            "TEXT" -> item.mimeType.startsWith("text/") ||
                item.relativePath.substringAfterLast('.', "").lowercase() in setOf("txt", "md", "json", "log", "csv")
            else -> true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("vault_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Safe Folder Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = scheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(scheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = scheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Safe Folder",
                        style = typography.headlineMedium,
                        fontFamily = FrauncesFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface
                    )
                    Text(
                        text = "${vaultFiles.size} locked items • ${formatBytes(totalBytes)}",
                        style = typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    "ALL" to "All (${vaultFiles.size})",
                    "IMAGES" to "Photos",
                    "TEXT" to "Notes & Docs"
                ).forEach { (key, label) ->
                    val isSelected = selectedTypeFilter == key
                    item {
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedTypeFilter = key },
                            label = { Text(label, style = typography.labelMedium) },
                            shape = shapes.extraSmall,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = scheme.primaryContainer,
                                selectedLabelColor = scheme.onPrimaryContainer,
                                containerColor = scheme.surface,
                                labelColor = scheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Default.Lock,
                        title = "Safe Folder is empty",
                        message = "Keep private photos, documents, and notes locked away from other apps."
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredFiles, key = { it.id }) { fileItem ->
                        VaultFileCard(
                            fileItem = fileItem,
                            onPreview = { viewModel.decryptAndPreviewFile(fileItem) },
                            onDecryptToWorkspace = { viewModel.decryptVaultFileToWorkspace(fileItem) },
                            onExport = {
                                exportTarget = fileItem
                                exportLauncher.launch(fileItem.relativePath.substringAfterLast('/'))
                            },
                            onDelete = { viewModel.deleteVaultFile(fileItem) }
                        )
                    }
                }
            }
        }

        // Floating Action Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = scheme.secondaryContainer,
                contentColor = scheme.onSecondaryContainer,
                shape = shapes.medium,
                modifier = Modifier.testTag("fab_add_vault_file")
            ) {
                Icon(imageVector = Icons.Default.NoteAdd, contentDescription = "New Note")
            }

            ExtendedFloatingActionButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary,
                shape = shapes.medium,
                icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
                text = { Text("Add File", style = typography.labelLarge, fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.testTag("fab_import_vault_file")
            )
        }
    }

    if (showCreateDialog) {
        CreateEncryptedNoteDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { fileName, content ->
                viewModel.addNote(fileName, content)
                showCreateDialog = false
            }
        )
    }

    previewItem?.let { (title, content) ->
        DecryptedPreviewDialog(
            title = title,
            content = content,
            onDismiss = { viewModel.clearPreview() }
        )
    }

    previewBitmap?.let { (title, bmp) ->
        AlertDialog(
            onDismissRequest = { viewModel.clearPreview() },
            shape = shapes.extraLarge,
            containerColor = scheme.surface,
            title = {
                Text(
                    text = title.substringAfterLast('/'),
                    style = typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.25f)
                        .clip(shapes.medium)
                        .background(scheme.surfaceContainerLow)
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearPreview() }) {
                    Text("Close", style = typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

@Composable
fun VaultFileCard(
    fileItem: VaultFileItem,
    onPreview: () -> Unit,
    onDecryptToWorkspace: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var showMenu by remember { mutableStateOf(false) }
    val ext = fileItem.relativePath.substringAfterLast('.', "").lowercase()
    val isImage = fileItem.mimeType.startsWith("image/") || ext in setOf("png", "jpg", "jpeg", "webp")
    val isText = fileItem.mimeType.startsWith("text/") || ext in setOf("txt", "md", "json", "log", "csv")

    val icon = when {
        isImage -> Icons.Default.Image
        isText -> Icons.Default.Description
        else -> Icons.Default.Folder
    }

    val fileName = fileItem.relativePath.substringAfterLast('/')

    ContentCard(
        onClick = onPreview,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vault_item_${fileItem.id}")
    ) {
        ListRow(
            title = fileName,
            subtitle = "${formatBytes(fileItem.originalSizeBytes)} • Locked",
            leadingIcon = icon,
            modifier = Modifier.padding(0.dp),
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPreview,
                        modifier = Modifier.testTag("btn_preview_${fileItem.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "View",
                            tint = scheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("View") },
                                leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onPreview()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Move out of Safe Folder") },
                                leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onDecryptToWorkspace()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save to phone") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onExport()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = scheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = scheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun CreateEncryptedNoteDialog(
    onDismiss: () -> Unit,
    onCreate: (fileName: String, content: String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    var fileName by remember { mutableStateOf("Private_Note.txt") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = shapes.extraLarge,
        containerColor = scheme.surface,
        title = {
            Text(
                text = "New Safe Folder Note",
                style = typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Title") },
                    singleLine = true,
                    shape = shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_vault_filename")
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Note") },
                    placeholder = { Text("Write something private...") },
                    minLines = 4,
                    maxLines = 7,
                    shape = shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_vault_content")
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "Save to Safe Folder",
                onClick = {
                    if (fileName.isNotBlank() && content.isNotBlank()) {
                        onCreate(fileName.trim(), content)
                    }
                },
                modifier = Modifier.testTag("btn_confirm_encrypt")
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = scheme.onSurfaceVariant)
            }
        }
    )
}
