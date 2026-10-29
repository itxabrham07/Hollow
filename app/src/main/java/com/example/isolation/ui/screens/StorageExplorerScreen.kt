package com.example.isolation.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.isolation.data.entity.VaultFileItem
import com.example.isolation.model.IsolationMode
import com.example.isolation.ui.components.ContentCard
import com.example.isolation.ui.viewmodel.FileCategory
import com.example.isolation.ui.viewmodel.FileEntryItem
import com.example.isolation.ui.viewmodel.FileManagerViewModel
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.isolation.ui.viewmodel.StorageRootType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StorageExplorerScreen(
    viewModel: IsoVaultViewModel,
    fileManagerViewModel: FileManagerViewModel = viewModel()
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val uiState by fileManagerViewModel.uiState.collectAsState()
    val notice by fileManagerViewModel.notice.collectAsState()
    val vaultFiles by viewModel.vaultFiles.collectAsState()
    val configs by viewModel.configs.collectAsState()

    val separateAppDrives = remember(configs) {
        configs.filter {
            it.isIsolated && (it.isolationMode == IsolationMode.SANDBOX_REDIRECT || it.isolationMode == IsolationMode.ENCRYPTED_VAULT)
        }
    }

    var activeSection by remember { mutableStateOf(0) } // 0 = Hollow Drives, 1 = Private Locker
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewNoteDialog by remember { mutableStateOf(false) }
    var folderNameInput by remember { mutableStateOf("") }
    var noteTitleInput by remember { mutableStateOf("") }
    var noteBodyInput by remember { mutableStateOf("") }

    var previewItem by remember { mutableStateOf<FileEntryItem?>(null) }
    var previewText by remember { mutableStateOf("") }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            fileManagerViewModel.importExternalUrisHere(listOf(uri))
        }
    }

    LaunchedEffect(Unit) {
        fileManagerViewModel.refreshDirectory()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("files_explorer_screen")
    ) {
        // Section Mode Switcher (Hollow Drive vs Private Locker)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = activeSection == 0,
                onClick = { activeSection = 0 },
                label = { Text("Hollow Drives") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.testTag("tab_hollow_drives")
            )
            FilterChip(
                selected = activeSection == 1,
                onClick = { activeSection = 1 },
                label = { Text("Private Locker (${vaultFiles.size})") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.testTag("tab_private_locker")
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { fileManagerViewModel.refreshDirectory() },
                modifier = Modifier.testTag("btn_refresh_files")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Files"
                )
            }
        }

        if (activeSection == 0) {
            // Drive selector bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.activeRoot == StorageRootType.WORKSPACE && uiState.activeSeparateDrivePkg == null,
                    onClick = { fileManagerViewModel.switchStorageRoot(StorageRootType.WORKSPACE) },
                    label = { Text("Hollow Drive") },
                    modifier = Modifier.testTag("chip_drive_workspace")
                )
                FilterChip(
                    selected = uiState.activeRoot == StorageRootType.SHARED_STORAGE,
                    onClick = { fileManagerViewModel.switchStorageRoot(StorageRootType.SHARED_STORAGE) },
                    label = { Text("Phone Storage") },
                    modifier = Modifier.testTag("chip_drive_shared")
                )
                separateAppDrives.forEach { cfg ->
                    val isSelected = uiState.activeSeparateDrivePkg == cfg.packageName
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            fileManagerViewModel.openSeparateAppDrive(cfg.packageName, cfg.appName)
                        },
                        label = {
                            Text(
                                text = "${cfg.appName} Drive",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag("chip_drive_${cfg.packageName}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search + Quick Actions Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { fileManagerViewModel.setSearchQuery(it) },
                placeholder = { Text("Search files or folders...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_search_files")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        folderNameInput = ""
                        showNewFolderDialog = true
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_new_folder")
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Folder", maxLines = 1)
                }

                OutlinedButton(
                    onClick = {
                        noteTitleInput = ""
                        noteBodyInput = ""
                        showNewNoteDialog = true
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_new_note")
                ) {
                    Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Note", maxLines = 1)
                }

                Button(
                    onClick = { importFileLauncher.launch("*/*") },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_import_file")
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Import", maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Breadcrumb bar
            val canGoUp = uiState.currentPath != uiState.rootPath && uiState.currentPath.isNotEmpty()
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = scheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (canGoUp) {
                            IconButton(
                                onClick = { fileManagerViewModel.navigateUp() },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("btn_navigate_up")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Navigate Up",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        val breadcrumbText = if (uiState.breadcrumbs.isNotEmpty()) {
                            uiState.breadcrumbs.joinToString(" / ") { it.label }
                        } else {
                            uiState.activeDriveTitle
                        }
                        Text(
                            text = breadcrumbText,
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${uiState.entries.size} items",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            if (notice != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = notice.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (uiState.entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "This folder is empty",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = scheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Create a folder, write a note, or import files into ${uiState.activeDriveTitle}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(uiState.entries, key = { it.path }) { item ->
                        DriveFileRow(
                            item = item,
                            onOpen = {
                                if (item.isDirectory) {
                                    fileManagerViewModel.navigateToPath(item.path)
                                } else {
                                    scope.launch {
                                        previewText = fileManagerViewModel.readTextFileContent(item.path)
                                        previewItem = item
                                    }
                                }
                            },
                            onLockToVault = {
                                fileManagerViewModel.encryptEntriesToLocker(
                                    paths = listOf(item.path),
                                    shredOriginals = true
                                )
                            },
                            onDelete = {
                                fileManagerViewModel.deleteEntries(listOf(item.path))
                            }
                        )
                    }
                }
            }
        } else {
            // Private Encrypted Locker Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AES-256-GCM Encrypted Locker",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onSurface
                    )
                    Text(
                        text = "Files locked here are encrypted inside the private vault.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (vaultFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = scheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Locked Files Yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = scheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap the lock icon on any file in Hollow Drives to encrypt and move it here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(vaultFiles, key = { it.id }) { entry ->
                        LockedVaultEntryRow(
                            entry = entry,
                            onDecrypt = { viewModel.decryptAndPreviewFile(entry) },
                            onDelete = { viewModel.deleteVaultFile(entry) }
                        )
                    }
                }
            }
        }
    }

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Create New Folder") },
            text = {
                OutlinedTextField(
                    value = folderNameInput,
                    onValueChange = { folderNameInput = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderNameInput.isNotBlank()) {
                            fileManagerViewModel.createNewFolder(folderNameInput.trim())
                            showNewFolderDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showNewNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNewNoteDialog = false },
            title = { Text("Create Text Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = noteTitleInput,
                        onValueChange = { noteTitleInput = it },
                        label = { Text("File name (e.g. notes.txt)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteBodyInput,
                        onValueChange = { noteBodyInput = it },
                        label = { Text("Content") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (noteTitleInput.isNotBlank()) {
                            fileManagerViewModel.createNewTextFile(noteTitleInput.trim(), noteBodyInput)
                            showNewNoteDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewNoteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val openPreview = previewItem
    if (openPreview != null) {
        var editableContent by remember(openPreview, previewText) {
            mutableStateOf(previewText)
        }
        val isTextEditable = openPreview.category == FileCategory.CODE_OR_TEXT ||
            openPreview.category == FileCategory.PDF_OR_DOC ||
            openPreview.extension in setOf("txt", "md", "json", "sh", "csv", "log", "conf", "xml")
        AlertDialog(
            onDismissRequest = { previewItem = null },
            title = { Text(openPreview.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                if (isTextEditable) {
                    OutlinedTextField(
                        value = editableContent,
                        onValueChange = { editableContent = it },
                        minLines = 6,
                        maxLines = 14,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "File (${formatStorageBytes(openPreview.sizeBytes)})\nPath: ${openPreview.path}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                if (isTextEditable) {
                    Button(
                        onClick = {
                            fileManagerViewModel.saveTextFileContent(openPreview.path, editableContent)
                            previewItem = null
                        }
                    ) {
                        Text("Save Changes")
                    }
                } else {
                    Button(onClick = { previewItem = null }) {
                        Text("Close")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { previewItem = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DriveFileRow(
    item: FileEntryItem,
    onOpen: () -> Unit,
    onLockToVault: () -> Unit,
    onDelete: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val icon = when {
        item.isDirectory -> Icons.Default.Folder
        item.category == FileCategory.IMAGE -> Icons.Default.Image
        item.category == FileCategory.VIDEO -> Icons.Default.VideoFile
        item.category == FileCategory.AUDIO -> Icons.Default.AudioFile
        item.category == FileCategory.CODE_OR_TEXT || item.category == FileCategory.PDF_OR_DOC -> Icons.Default.Description
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }

    ContentCard(
        onClick = onOpen,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("file_row_${item.name}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (item.isDirectory) scheme.primaryContainer
                            else scheme.surfaceVariant
                        )
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (item.isDirectory) scheme.primary else scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val dateStr = remember(item.lastModified) {
                        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(item.lastModified))
                    }
                    Text(
                        text = if (item.isDirectory) "Folder (${item.childCount}) • $dateStr" else "${formatStorageBytes(item.sizeBytes)} • $dateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!item.isDirectory) {
                    IconButton(
                        onClick = onLockToVault,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock to Private Vault",
                            tint = scheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
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

@Composable
private fun LockedVaultEntryRow(
    entry: VaultFileItem,
    onDecrypt: () -> Unit,
    onDelete: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    ContentCard(
        onClick = onDecrypt,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("locked_row_${entry.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.relativePath.substringAfterLast('/'),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatStorageBytes(entry.originalSizeBytes)} • ${entry.algorithm}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecrypt, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = "Decrypt & Preview",
                        tint = scheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
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

private fun formatStorageBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}
