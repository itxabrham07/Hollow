package com.example.isolation.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.isolation.model.IsolationMode
import com.example.isolation.ui.components.ContentCard
import com.example.isolation.ui.components.EmptyState
import com.example.isolation.ui.components.ListRow
import com.example.isolation.ui.components.PrimaryButton
import com.example.isolation.ui.components.SecondaryButton
import com.example.isolation.ui.components.SectionHeader
import com.example.isolation.ui.viewmodel.ClipboardMode
import com.example.isolation.ui.viewmodel.FileCategory
import com.example.isolation.ui.viewmodel.FileEntryItem
import com.example.isolation.ui.viewmodel.FileManagerViewModel
import com.example.isolation.ui.viewmodel.FileSortField
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.isolation.ui.viewmodel.StorageRootType
import java.io.File
import java.text.DateFormat
import java.util.Date

private enum class GoogleFilesSubView {
    BROWSE_HOME,
    FOLDER_EXPLORER,
    GALLERY,
    SAFE_FOLDER
}

/**
 * Unified Browse & Storage Hub.
 */
@Composable
fun FilesScreen(
    viewModel: IsoVaultViewModel,
    initialTab: Int = 0,
    onNavigateToApps: () -> Unit = {}
) {
    val fmViewModel: FileManagerViewModel = viewModel()
    var subView by remember {
        mutableStateOf(
            when (initialTab) {
                1 -> GoogleFilesSubView.GALLERY
                2 -> GoogleFilesSubView.SAFE_FOLDER
                else -> GoogleFilesSubView.BROWSE_HOME
            }
        )
    }
    var galleryInitialMode by remember { mutableStateOf("PHOTOS") }

    LaunchedEffect(initialTab) {
        subView = when (initialTab) {
            1 -> {
                galleryInitialMode = "PHOTOS"
                GoogleFilesSubView.GALLERY
            }
            2 -> GoogleFilesSubView.SAFE_FOLDER
            else -> GoogleFilesSubView.BROWSE_HOME
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("files_screen")
    ) {
        when (subView) {
            GoogleFilesSubView.BROWSE_HOME -> {
                GoogleFilesBrowseHome(
                    isoViewModel = viewModel,
                    fmViewModel = fmViewModel,
                    onOpenCategoryGallery = { mode ->
                        galleryInitialMode = mode
                        subView = GoogleFilesSubView.GALLERY
                    },
                    onOpenSafeFolder = {
                        subView = GoogleFilesSubView.SAFE_FOLDER
                    },
                    onOpenFolder = { rootType, subDirName ->
                        fmViewModel.switchStorageRoot(rootType)
                        if (subDirName != null) {
                            val base = File(fmViewModel.uiState.value.rootPath)
                            val target = File(base, subDirName).apply { mkdirs() }
                            fmViewModel.navigateToPath(target.absolutePath)
                        }
                        subView = GoogleFilesSubView.FOLDER_EXPLORER
                    },
                    onOpenSeparateDrive = { pkg, name ->
                        fmViewModel.openSeparateAppDrive(pkg, name)
                        subView = GoogleFilesSubView.FOLDER_EXPLORER
                    },
                    onOpenApps = onNavigateToApps
                )
            }
            GoogleFilesSubView.FOLDER_EXPLORER -> {
                CleanFolderExplorerView(
                    fmViewModel = fmViewModel,
                    onBackToBrowseHome = {
                        fmViewModel.setSearchQuery("")
                        subView = GoogleFilesSubView.BROWSE_HOME
                    }
                )
            }
            GoogleFilesSubView.GALLERY -> {
                GalleryScreen(
                    viewModel = viewModel,
                    initialMode = galleryInitialMode,
                    onBack = { subView = GoogleFilesSubView.BROWSE_HOME }
                )
            }
            GoogleFilesSubView.SAFE_FOLDER -> {
                VaultScreen(
                    viewModel = viewModel,
                    onBack = { subView = GoogleFilesSubView.BROWSE_HOME }
                )
            }
        }
    }
}

@Composable
private fun GoogleFilesBrowseHome(
    isoViewModel: IsoVaultViewModel,
    fmViewModel: FileManagerViewModel,
    onOpenCategoryGallery: (String) -> Unit,
    onOpenSafeFolder: () -> Unit,
    onOpenFolder: (StorageRootType, String?) -> Unit,
    onOpenSeparateDrive: (packageName: String, appName: String) -> Unit = { _, _ -> },
    onOpenApps: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val uiState by fmViewModel.uiState.collectAsState()
    val vaultFiles by isoViewModel.vaultFiles.collectAsState()
    val totalEncryptedBytes by isoViewModel.totalEncryptedBytes.collectAsState()
    val isolatedCount by isoViewModel.isolatedCount.collectAsState()
    val notice by fmViewModel.notice.collectAsState()
    val breakdown = uiState.storageBreakdown

    var searchQuery by remember { mutableStateOf("") }
    var showHomeNewMenu by remember { mutableStateOf(false) }

    val homeImportFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            fmViewModel.switchStorageRoot(StorageRootType.WORKSPACE)
            fmViewModel.importExternalUrisHere(uris)
        }
    }

    val homeImportFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            fmViewModel.switchStorageRoot(StorageRootType.WORKSPACE)
            fmViewModel.importExternalFolderHere(treeUri)
            onOpenFolder(StorageRootType.WORKSPACE, null)
        }
    }

    val categoryRows = remember(breakdown, isolatedCount) {
        listOf(
            listOf(
                Triple("Downloads", "Saved files", Icons.Default.Download) to { onOpenFolder(StorageRootType.WORKSPACE, "Downloads") },
                Triple("Images", formatBytes(breakdown.imagesBytes), Icons.Default.Image) to { onOpenCategoryGallery("PHOTOS") }
            ),
            listOf(
                Triple("Videos", formatBytes(breakdown.videosBytes), Icons.Default.VideoFile) to { onOpenCategoryGallery("VIDEOS") },
                Triple("Audio", formatBytes(breakdown.audioBytes), Icons.Default.AudioFile) to { onOpenCategoryGallery("AUDIO") }
            ),
            listOf(
                Triple("Documents", formatBytes(breakdown.docsAndCodeBytes), Icons.Default.Description) to { onOpenFolder(StorageRootType.WORKSPACE, "Documents") },
                Triple("Apps", "$isolatedCount protected", Icons.Default.Apps) to onOpenApps
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            if (notice != null) {
                item {
                    ContentCard(
                        onClick = { fmViewModel.clearNotice() },
                        containerColor = scheme.primaryContainer,
                        borderColor = scheme.primary
                    ) {
                        Text(
                            text = notice ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onPrimaryContainer
                        )
                    }
                }
            }

            // 1. Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { query ->
                        searchQuery = query
                        if (query.isNotBlank()) {
                            fmViewModel.switchStorageRoot(StorageRootType.WORKSPACE)
                            fmViewModel.setSearchQuery(query)
                            onOpenFolder(StorageRootType.WORKSPACE, null)
                        }
                    },
                    placeholder = {
                        Text(
                            text = "Search files and folders",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = scheme.onSurfaceVariant
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 2. Categories Section (2x3 Grid using ContentCard + ListRow)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "Categories")

                    categoryRows.forEach { rowItems ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            rowItems.forEach { (meta, action) ->
                                ContentCard(
                                    onClick = action,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = meta.third,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Text(
                                            text = meta.first,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = meta.second,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Collections Section (Safe Folder & Starred)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "Collections")

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ContentCard(
                            onClick = onOpenSafeFolder,
                            modifier = Modifier.weight(1f)
                        ) {
                            ListRow(
                                title = "Safe folder",
                                subtitle = "${vaultFiles.size} items • ${formatBytes(totalEncryptedBytes)}",
                                leadingIcon = Icons.Default.Lock,
                                tintLeadingIcon = true,
                                modifier = Modifier.padding(vertical = 0.dp)
                            )
                        }
                        ContentCard(
                            onClick = { onOpenCategoryGallery("STARRED") },
                            modifier = Modifier.weight(1f)
                        ) {
                            ListRow(
                                title = "Starred",
                                subtitle = "Favorites",
                                leadingIcon = Icons.Default.Star,
                                modifier = Modifier.padding(vertical = 0.dp)
                            )
                        }
                    }
                }
            }

            // 4. Storage Devices: Internal Storage (Hollow Drive) & External Storage (Assigned App Drives)
            item {
                val allApps by isoViewModel.allInstalledApps.collectAsState()
                val separateDriveApps = allApps.filter {
                    it.isIsolated && it.isolationMode != IsolationMode.SHARED_WORKSPACE
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "Storage devices")

                    val workspaceUsedBytes = breakdown.imagesBytes +
                            breakdown.videosBytes +
                            breakdown.audioBytes +
                            breakdown.docsAndCodeBytes +
                            breakdown.archivesBytes

                    // Main Internal Storage: Hollow Drive
                    ContentCard(
                        onClick = { onOpenFolder(StorageRootType.WORKSPACE, null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListRow(
                            title = "Hollow Drive (Internal Storage)",
                            subtitle = "${formatBytes(workspaceUsedBytes)} • Zero-exposure private storage",
                            leadingIcon = Icons.Default.Folder,
                            tintLeadingIcon = true,
                            showNavigationChevron = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(scheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.28f)
                                    .clip(CircleShape)
                                    .background(scheme.primary)
                            )
                        }
                    }

                    // Assigned App Drives
                    if (separateDriveApps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SectionHeader(
                            title = "Assigned drives",
                            subtitle = "Dedicated per-app isolated volumes"
                        )
                        separateDriveApps.forEach { app ->
                            ContentCard(
                                onClick = { onOpenSeparateDrive(app.packageName, app.appName) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ListRow(
                                    title = "${app.appName} Drive",
                                    subtitle = "Dedicated isolated storage volume • Tap to explore",
                                    leadingIcon = Icons.AutoMirrored.Filled.InsertDriveFile,
                                    showNavigationChevron = true
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(scheme.surfaceVariant)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(0.15f)
                                            .clip(CircleShape)
                                            .background(scheme.primary)
                                    )
                                }
                            }
                        }
                    } else {
                        ContentCard(
                            containerColor = scheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "All apps use unified Hollow Drive by default. Per-app assigned drives appear here as dedicated storage volumes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // "+ New" button on Browse Home for quick file/folder import into Hollow Workspace
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            ExtendedFloatingActionButton(
                onClick = { showHomeNewMenu = true },
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary,
                shape = MaterialTheme.shapes.medium,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New", style = MaterialTheme.typography.labelLarge) }
            )

            DropdownMenu(
                expanded = showHomeNewMenu,
                onDismissRequest = { showHomeNewMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Import folder", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                    onClick = {
                        showHomeNewMenu = false
                        homeImportFolderLauncher.launch(null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Import files", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                    onClick = {
                        showHomeNewMenu = false
                        homeImportFilesLauncher.launch(arrayOf("*/*"))
                    }
                )
                DropdownMenuItem(
                    text = { Text("Open Hollow workspace", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    onClick = {
                        showHomeNewMenu = false
                        onOpenFolder(StorageRootType.WORKSPACE, null)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CleanFolderExplorerView(
    fmViewModel: FileManagerViewModel,
    onBackToBrowseHome: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val uiState by fmViewModel.uiState.collectAsState()
    val notice by fmViewModel.notice.collectAsState()

    var showSearchBar by remember { mutableStateOf(uiState.searchQuery.isNotBlank()) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCreateMenu by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewNoteDialog by remember { mutableStateOf(false) }
    var showZipDialogForPaths by remember { mutableStateOf<List<String>?>(null) }
    var renameTarget by remember { mutableStateOf<FileEntryItem?>(null) }
    var deleteTargets by remember { mutableStateOf<List<String>?>(null) }
    var textEditorTarget by remember { mutableStateOf<FileEntryItem?>(null) }
    var imagePreviewTarget by remember { mutableStateOf<FileEntryItem?>(null) }
    var exportTargetPath by remember { mutableStateOf<String?>(null) }

    val importFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            fmViewModel.importExternalUrisHere(uris)
        }
    }

    val importFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            fmViewModel.importExternalFolderHere(treeUri)
        }
    }

    val exportFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val path = exportTargetPath
        if (uri != null && path != null) {
            fmViewModel.exportFileToUri(path, uri)
        }
        exportTargetPath = null
    }

    val isInSubfolder = uiState.currentPath != uiState.rootPath
    BackHandler {
        when {
            uiState.selectedPaths.isNotEmpty() -> fmViewModel.clearSelection()
            isInSubfolder -> fmViewModel.navigateUp()
            else -> onBackToBrowseHome()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Top Header or Multi-Select Action Bar
            if (uiState.selectedPaths.isNotEmpty()) {
                ContentCard(
                    containerColor = scheme.primaryContainer,
                    borderColor = scheme.primary,
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { fmViewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                            Text(
                                text = "${uiState.selectedPaths.size} selected",
                                style = MaterialTheme.typography.titleSmall,
                                color = scheme.onPrimaryContainer
                            )
                        }
                        Row {
                            IconButton(onClick = { fmViewModel.copySelectedToClipboard() }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                            }
                            IconButton(onClick = { fmViewModel.cutSelectedToClipboard() }) {
                                Icon(Icons.Default.ContentCut, contentDescription = "Move")
                            }
                            IconButton(onClick = {
                                fmViewModel.encryptEntriesToLocker(uiState.selectedPaths.toList(), shredOriginals = false)
                            }) {
                                Icon(Icons.Default.Lock, contentDescription = "Move to Safe Folder")
                            }
                            IconButton(onClick = {
                                showZipDialogForPaths = uiState.selectedPaths.toList()
                            }) {
                                Icon(Icons.Default.FolderZip, contentDescription = "Compress to ZIP")
                            }
                            IconButton(onClick = {
                                deleteTargets = uiState.selectedPaths.toList()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = scheme.error)
                            }
                        }
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = {
                                if (isInSubfolder) fmViewModel.navigateUp() else onBackToBrowseHome()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                        Column {
                            val folderTitle = uiState.breadcrumbs.lastOrNull()?.label ?: uiState.activeRoot.title
                            Text(
                                text = folderTitle,
                                style = MaterialTheme.typography.titleLarge,
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${uiState.entries.size} items",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showSearchBar = !showSearchBar }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { fmViewModel.toggleGridView() }) {
                            Icon(
                                imageVector = if (uiState.isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = "View mode"
                            )
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                FileSortField.values().forEach { field ->
                                    DropdownMenuItem(
                                        text = { Text("Sort by ${field.label}", style = MaterialTheme.typography.bodyMedium) },
                                        onClick = {
                                            fmViewModel.setSort(field)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Breadcrumb Row
            if (uiState.breadcrumbs.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    items(uiState.breadcrumbs.size) { index ->
                        val crumb = uiState.breadcrumbs[index]
                        val isLast = index == uiState.breadcrumbs.lastIndex
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = crumb.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isLast) scheme.primary else scheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .clickable { fmViewModel.navigateToPath(crumb.absolutePath) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            if (!isLast) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = scheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (showSearchBar) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { fmViewModel.setSearchQuery(it) },
                    placeholder = { Text("Search in folder...", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { fmViewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }

            // Clipboard Paste Banner
            if (uiState.clipboardPaths.isNotEmpty()) {
                ContentCard(
                    containerColor = scheme.surfaceContainerLow,
                    borderColor = scheme.primary,
                    contentPadding = PaddingValues(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${uiState.clipboardPaths.size} item(s) ready to ${if (uiState.clipboardMode == ClipboardMode.COPY) "copy" else "move"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SecondaryButton(
                                text = "Cancel",
                                onClick = { fmViewModel.clearClipboard() }
                            )
                            PrimaryButton(
                                text = "Paste here",
                                icon = Icons.Default.ContentPaste,
                                onClick = { fmViewModel.pasteClipboardHere() }
                            )
                        }
                    }
                }
            }

            notice?.let { msg ->
                ContentCard(
                    onClick = { fmViewModel.clearNotice() },
                    containerColor = scheme.primaryContainer,
                    borderColor = scheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onPrimaryContainer
                    )
                }
            }

            // File List or Grid
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Default.Folder,
                        title = "This folder is empty",
                        message = "Tap '+ New' below to add a folder, note, or file."
                    )
                }
            } else if (uiState.isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(uiState.entries, key = { it.path }) { entry ->
                        val isSelected = entry.path in uiState.selectedPaths
                        FileEntryCardItem(
                            entry = entry,
                            isSelected = isSelected,
                            onClick = {
                                if (uiState.selectedPaths.isNotEmpty()) {
                                    fmViewModel.toggleSelection(entry.path)
                                } else {
                                    handleFileOpen(
                                        entry = entry,
                                        fmViewModel = fmViewModel,
                                        onOpenTextEditor = { textEditorTarget = it },
                                        onOpenImagePreview = { imagePreviewTarget = it }
                                    )
                                }
                            },
                            onLongClick = { fmViewModel.toggleSelection(entry.path) },
                            onCopy = {
                                fmViewModel.clearSelection()
                                fmViewModel.toggleSelection(entry.path)
                                fmViewModel.copySelectedToClipboard()
                            },
                            onMove = {
                                fmViewModel.clearSelection()
                                fmViewModel.toggleSelection(entry.path)
                                fmViewModel.cutSelectedToClipboard()
                            },
                            onRename = { renameTarget = entry },
                            onCompressZip = { showZipDialogForPaths = listOf(entry.path) },
                            onExtractZip = { fmViewModel.extractZipArchive(entry) },
                            onMoveToSafeFolder = {
                                fmViewModel.encryptEntriesToLocker(listOf(entry.path), shredOriginals = false)
                            },
                            onExport = {
                                exportTargetPath = entry.path
                                exportFileLauncher.launch(entry.name)
                            },
                            onDelete = { deleteTargets = listOf(entry.path) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(uiState.entries, key = { it.path }) { entry ->
                        val isSelected = entry.path in uiState.selectedPaths
                        FileEntryCardItem(
                            entry = entry,
                            isSelected = isSelected,
                            onClick = {
                                if (uiState.selectedPaths.isNotEmpty()) {
                                    fmViewModel.toggleSelection(entry.path)
                                } else {
                                    handleFileOpen(
                                        entry = entry,
                                        fmViewModel = fmViewModel,
                                        onOpenTextEditor = { textEditorTarget = it },
                                        onOpenImagePreview = { imagePreviewTarget = it }
                                    )
                                }
                            },
                            onLongClick = { fmViewModel.toggleSelection(entry.path) },
                            onCopy = {
                                fmViewModel.clearSelection()
                                fmViewModel.toggleSelection(entry.path)
                                fmViewModel.copySelectedToClipboard()
                            },
                            onMove = {
                                fmViewModel.clearSelection()
                                fmViewModel.toggleSelection(entry.path)
                                fmViewModel.cutSelectedToClipboard()
                            },
                            onRename = { renameTarget = entry },
                            onCompressZip = { showZipDialogForPaths = listOf(entry.path) },
                            onExtractZip = { fmViewModel.extractZipArchive(entry) },
                            onMoveToSafeFolder = {
                                fmViewModel.encryptEntriesToLocker(listOf(entry.path), shredOriginals = false)
                            },
                            onExport = {
                                exportTargetPath = entry.path
                                exportFileLauncher.launch(entry.name)
                            },
                            onDelete = { deleteTargets = listOf(entry.path) }
                        )
                    }
                }
            }
        }

        // "+ New" Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            ExtendedFloatingActionButton(
                onClick = { showCreateMenu = true },
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary,
                shape = MaterialTheme.shapes.medium,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New", style = MaterialTheme.typography.labelLarge) }
            )

            DropdownMenu(
                expanded = showCreateMenu,
                onDismissRequest = { showCreateMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("New folder", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                    onClick = {
                        showCreateMenu = false
                        showNewFolderDialog = true
                    }
                )
                DropdownMenuItem(
                    text = { Text("New text note", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.NoteAdd, contentDescription = null) },
                    onClick = {
                        showCreateMenu = false
                        showNewNoteDialog = true
                    }
                )
                DropdownMenuItem(
                    text = { Text("Import files", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                    onClick = {
                        showCreateMenu = false
                        importFilesLauncher.launch(arrayOf("*/*"))
                    }
                )
                DropdownMenuItem(
                    text = { Text("Import folder", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    onClick = {
                        showCreateMenu = false
                        importFolderLauncher.launch(null)
                    }
                )
            }
        }
    }

    // Dialogs
    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("New folder", style = MaterialTheme.typography.titleMedium) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder name", style = MaterialTheme.typography.bodySmall) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                PrimaryButton(
                    text = "Create",
                    onClick = {
                        if (folderName.isNotBlank()) {
                            fmViewModel.createNewFolder(folderName)
                            showNewFolderDialog = false
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    if (showNewNoteDialog) {
        var fileName by remember { mutableStateOf("Note.txt") }
        var content by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewNoteDialog = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("New text note", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("File name", style = MaterialTheme.typography.bodySmall) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Content", style = MaterialTheme.typography.bodySmall) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        minLines = 4,
                        maxLines = 8,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = "Save",
                    onClick = {
                        if (fileName.isNotBlank()) {
                            fmViewModel.createNewTextFile(fileName, content)
                            showNewNoteDialog = false
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showNewNoteDialog = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    renameTarget?.let { target ->
        var newName by remember(target.path) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Rename", style = MaterialTheme.typography.titleMedium) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                PrimaryButton(
                    text = "Rename",
                    onClick = {
                        if (newName.isNotBlank()) {
                            fmViewModel.renameEntry(target, newName)
                            renameTarget = null
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    showZipDialogForPaths?.let { paths ->
        var zipName by remember { mutableStateOf("Archive.zip") }
        AlertDialog(
            onDismissRequest = { showZipDialogForPaths = null },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Compress to ZIP", style = MaterialTheme.typography.titleMedium) },
            text = {
                OutlinedTextField(
                    value = zipName,
                    onValueChange = { zipName = it },
                    label = { Text("Archive name", style = MaterialTheme.typography.bodySmall) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                PrimaryButton(
                    text = "Compress",
                    onClick = {
                        fmViewModel.compressToZip(paths, zipName)
                        showZipDialogForPaths = null
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showZipDialogForPaths = null }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    deleteTargets?.let { paths ->
        AlertDialog(
            onDismissRequest = { deleteTargets = null },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Delete ${paths.size} item(s)?", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text("This will permanently remove the selected items.", style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                PrimaryButton(
                    text = "Delete",
                    isDestructive = true,
                    onClick = {
                        fmViewModel.deleteEntries(paths)
                        deleteTargets = null
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { deleteTargets = null }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    textEditorTarget?.let { target ->
        val loadedText by produceState<String?>(initialValue = null, target.path) {
            value = fmViewModel.readTextFileContent(target.path)
        }
        var editedText by remember(loadedText) { mutableStateOf(loadedText ?: "") }
        AlertDialog(
            onDismissRequest = { textEditorTarget = null },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(target.name, style = MaterialTheme.typography.titleMedium) },
            text = {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    minLines = 6,
                    maxLines = 12,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                PrimaryButton(
                    text = "Save",
                    onClick = {
                        fmViewModel.saveTextFileContent(target.path, editedText)
                        textEditorTarget = null
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { textEditorTarget = null }) {
                    Text("Close", style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    imagePreviewTarget?.let { target ->
        val bmp by produceState<ImageBitmap?>(initialValue = null, target.path) {
            value = try {
                BitmapFactory.decodeFile(target.path)?.asImageBitmap()
            } catch (_: Throwable) { null }
        }
        AlertDialog(
            onDismissRequest = { imagePreviewTarget = null },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(target.name, style = MaterialTheme.typography.titleMedium) },
            text = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(scheme.surfaceContainerLow)
                        .padding(16.dp)
                ) {
                    val image = bmp
                    if (image != null) {
                        Image(
                            bitmap = image,
                            contentDescription = target.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = "Move to Safe Folder",
                    icon = Icons.Default.Lock,
                    onClick = {
                        fmViewModel.encryptEntriesToLocker(listOf(target.path), shredOriginals = false)
                        imagePreviewTarget = null
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { imagePreviewTarget = null }) {
                    Text("Close", style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }
}

private fun handleFileOpen(
    entry: FileEntryItem,
    fmViewModel: FileManagerViewModel,
    onOpenTextEditor: (FileEntryItem) -> Unit,
    onOpenImagePreview: (FileEntryItem) -> Unit
) {
    when {
        entry.isDirectory -> fmViewModel.navigateToPath(entry.path)
        entry.category == FileCategory.IMAGE -> onOpenImagePreview(entry)
        entry.category == FileCategory.ARCHIVE && entry.extension == "zip" -> fmViewModel.extractZipArchive(entry)
        else -> onOpenTextEditor(entry)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileEntryCardItem(
    entry: FileEntryItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onCompressZip: () -> Unit,
    onExtractZip: () -> Unit,
    onMoveToSafeFolder: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var showMenu by remember { mutableStateOf(false) }

    val icon = when {
        entry.isDirectory -> Icons.Default.Folder
        entry.category == FileCategory.IMAGE -> Icons.Default.Image
        entry.category == FileCategory.VIDEO -> Icons.Default.VideoFile
        entry.category == FileCategory.AUDIO -> Icons.Default.AudioFile
        entry.category == FileCategory.ARCHIVE -> Icons.Default.FolderZip
        entry.category == FileCategory.CODE_OR_TEXT || entry.category == FileCategory.PDF_OR_DOC ->
            Icons.Default.Description
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }

    val dateStr = remember(entry.lastModified) {
        if (entry.lastModified > 0L) {
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.lastModified))
        } else ""
    }
    val metaText = if (entry.isDirectory) {
        "${entry.childCount} items • $dateStr"
    } else {
        "${formatBytes(entry.sizeBytes)} • $dateStr"
    }

    ContentCard(
        containerColor = if (isSelected) scheme.primaryContainer else scheme.surface,
        borderColor = if (isSelected) scheme.primary else scheme.outlineVariant,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        ListRow(
            title = entry.name,
            subtitle = metaText,
            leadingIcon = if (isSelected) Icons.Default.CheckCircle else icon,
            tintLeadingIcon = entry.isDirectory || isSelected,
            modifier = Modifier.padding(vertical = 0.dp),
            trailing = {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (!entry.isDirectory) {
                            DropdownMenuItem(
                                text = { Text("Move to Safe Folder", style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onMoveToSafeFolder()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Copy", style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onCopy()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move", style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onMove()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename", style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onRename()
                            }
                        )
                        if (entry.extension == "zip") {
                            DropdownMenuItem(
                                text = { Text("Extract ZIP here", style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = { Icon(Icons.Default.Unarchive, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onExtractZip()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Compress to ZIP", style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = { Icon(Icons.Default.FolderZip, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onCompressZip()
                                }
                            )
                        }
                        if (!entry.isDirectory) {
                            DropdownMenuItem(
                                text = { Text("Save / Share", style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onExport()
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = scheme.error, style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = scheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        )
    }
}
