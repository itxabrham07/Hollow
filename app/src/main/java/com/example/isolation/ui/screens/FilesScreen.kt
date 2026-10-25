package com.example.isolation.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.ui.components.BrandSquircleIcon
import com.example.isolation.ui.components.StatusBadge
import com.example.isolation.ui.components.formatBytes
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.JakartaFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun FilesScreen(
    viewModel: IsoVaultViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    val configs by viewModel.allConfigs.collectAsStateWithLifecycle()
    val securitySettings by viewModel.securitySettings.collectAsStateWithLifecycle()
    val refreshTick by viewModel.vaultRefreshTick.collectAsStateWithLifecycle()
    val transferProgress by viewModel.transferProgress.collectAsStateWithLifecycle()

    val isDecoyMode = securitySettings.isPanicDecoyActive
    val folderPins = securitySettings.folderPins
    val unlockedFolders = securitySettings.unlockedFolderPaths
    val favoritePaths = securitySettings.favoritePaths

    var localTick by remember { mutableIntStateOf(0) }

    // Top-level mode: Files vs Gallery
    var browserMode by remember { mutableStateOf(VaultBrowserMode.FILES) }
    var gallerySubMode by remember { mutableStateOf(GallerySubMode.ALBUMS) }
    var selectedAlbum by remember { mutableStateOf<GalleryAlbumItem?>(null) }

    // Scope filter: "ALL", "shared.vault", or a specific packageName
    var selectedScope by remember { mutableStateOf("ALL") }

    // Navigation state for Files mode: null = Volume list, non-null = inside a volume
    var selectedBucket by remember { mutableStateOf<VaultAppBucket?>(null) }
    var currentDir by remember { mutableStateOf<File?>(null) }
    var showRecycleBin by remember { mutableStateOf(false) }

    // Browser controls
    var isGridView by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(VaultFileCategory.ALL) }
    var sortOption by remember { mutableStateOf(VaultSortOption.DATE) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Multi-select mode
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectionMode = selectedPaths.isNotEmpty()

    // Dialogs & sheets
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var showCreateVolumeDialog by remember { mutableStateOf(false) }
    var newVolumeName by remember { mutableStateOf("") }
    var newVolumeUseShared by remember { mutableStateOf(true) }
    var newVolumeQuotaMb by remember { mutableStateOf("") }

    var showImportOptionsSheet by remember { mutableStateOf(false) }
    var deleteOriginalsOnImport by remember { mutableStateOf(false) }

    var renameTarget by remember { mutableStateOf<VaultEntry?>(null) }
    var renameInput by remember { mutableStateOf("") }

    var detailsTarget by remember { mutableStateOf<VaultEntry?>(null) }

    var showTransferSheet by remember { mutableStateOf(false) }
    var isMoveOperation by remember { mutableStateOf(true) }

    // Secondary PIN for folders
    var folderPinSetupTarget by remember { mutableStateOf<File?>(null) }
    var folderPinSetupInput by remember { mutableStateOf("") }
    var lockedFolderPromptTarget by remember { mutableStateOf<VaultEntry?>(null) }
    var lockedFolderPinInput by remember { mutableStateOf("") }
    var lockedFolderPinError by remember { mutableStateOf(false) }

    // Built-in viewers
    var photoViewerList by remember { mutableStateOf<List<VaultEntry>>(emptyList()) }
    var photoViewerInitialIndex by remember { mutableIntStateOf(0) }
    var showPhotoViewer by remember { mutableStateOf(false) }
    var videoPlayerEntry by remember { mutableStateOf<VaultEntry?>(null) }
    var audioPlayerEntry by remember { mutableStateOf<VaultEntry?>(null) }
    pdfViewerEntry@ var pdfViewerEntry by remember { mutableStateOf<VaultEntry?>(null) }
    var textViewerEntry by remember { mutableStateOf<VaultEntry?>(null) }

    val sandboxBaseDir = remember(isDecoyMode) {
        if (isDecoyMode) viewModel.getDecoySandboxDir() else viewModel.getSandboxBaseDir()
    }
    val sharedSpaceDir = remember(isDecoyMode) {
        if (isDecoyMode) File(viewModel.getDecoySandboxDir(), "shared") else viewModel.getSharedSpaceDir()
    }
    val recycleBinDir = remember { viewModel.getRecycleBinDir() }

    // Build volume buckets (Shared Space + Per-App Volumes) with 100% real on-disk byte counts
    val appBuckets = remember(configs, isDecoyMode, refreshTick, localTick) {
        if (isDecoyMode) {
            emptyList()
        } else {
            val sharedFiles = if (sharedSpaceDir.exists()) {
                sharedSpaceDir.walkTopDown().filter { it.isFile && it.name != ".nomedia" && !it.name.startsWith(".seeded") }.toList()
            } else {
                emptyList()
            }
            val sharedBucket = VaultAppBucket(
                packageName = "shared.vault",
                appName = "Shared Space",
                rootDir = sharedSpaceDir,
                fileCount = sharedFiles.size,
                totalBytes = sharedFiles.sumOf { it.length() },
                isHookActive = true,
                isSharedVolume = true,
                useSharedSpace = true,
                quotaMb = 0
            )

            val perAppBuckets = configs.map { cfg ->
                val dir = File(sandboxBaseDir, cfg.packageName).apply { if (!exists()) mkdirs() }
                val files = dir.walkTopDown().filter { it.isFile && it.name != ".nomedia" && !it.name.startsWith(".seeded") }.toList()
                val actualBytes = files.sumOf { it.length() }
                VaultAppBucket(
                    packageName = cfg.packageName,
                    appName = cfg.appName,
                    rootDir = dir,
                    fileCount = files.size,
                    totalBytes = actualBytes,
                    isHookActive = cfg.isIsolated,
                    isSharedVolume = false,
                    useSharedSpace = cfg.useSharedSpace,
                    quotaMb = cfg.volumeQuotaMb
                )
            }
            listOf(sharedBucket) + perAppBuckets
        }
    }

    val totalVaultBytes = remember(appBuckets) {
        appBuckets.sumOf { it.totalBytes }
    }

    val appNameLookup = remember(configs) {
        configs.associate { it.packageName to it.appName } +
            ("shared.vault" to "Shared Space")
    }

    // Active target directory & volume metadata for imports
    val activeImportDestinationDir: File = currentDir
        ?: selectedBucket?.rootDir
        ?: File(sharedSpaceDir, "Pictures").apply { if (!exists()) mkdirs() }
    val activeImportPackage: String = selectedBucket?.packageName
        ?: if (selectedScope != "ALL") selectedScope else "shared.vault"
    val activeImportAppName: String = selectedBucket?.appName
        ?: appNameLookup[activeImportPackage]
        ?: "Shared Space"

    // 1. Multi-File Import Launcher
    val importFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importUrisToVault(
                uris = uris,
                destinationDir = activeImportDestinationDir,
                packageName = activeImportPackage,
                appName = activeImportAppName,
                deleteOriginalAfterImport = deleteOriginalsOnImport
            ) { count ->
                localTick += 1
                Toast.makeText(
                    context,
                    "Encrypted & imported $count file(s) into $activeImportAppName",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // 2. Photo & Video Picker Import Launcher
    val importVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importUrisToVault(
                uris = uris,
                destinationDir = activeImportDestinationDir,
                packageName = activeImportPackage,
                appName = activeImportAppName,
                deleteOriginalAfterImport = deleteOriginalsOnImport
            ) { count ->
                localTick += 1
                Toast.makeText(
                    context,
                    "Encrypted & imported $count photo(s)/video(s) into $activeImportAppName",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // 3. Recursive Folder Tree Import Launcher (OpenDocumentTree)
    val importFolderTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            val folderDest = currentDir ?: selectedBucket?.rootDir ?: sharedSpaceDir
            viewModel.importFolderTreeToVault(
                treeUri = treeUri,
                destinationDir = folderDest,
                packageName = activeImportPackage,
                appName = activeImportAppName,
                deleteOriginalAfterImport = deleteOriginalsOnImport
            ) { filesCount, foldersCount, bytesEncrypted ->
                localTick += 1
                Toast.makeText(
                    context,
                    "Imported $foldersCount folder(s) & $filesCount file(s) (${formatBytes(bytesEncrypted)}) into $activeImportAppName",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Load entries for current directory or scoped combined view
    val scopedBuckets = remember(appBuckets, selectedScope) {
        when (selectedScope) {
            "ALL" -> appBuckets
            else -> appBuckets.filter { it.packageName == selectedScope }
        }
    }

    val currentEntries = remember(
        currentDir,
        selectedBucket,
        scopedBuckets,
        browserMode,
        selectedScope,
        searchQuery,
        selectedCategory,
        sortOption,
        folderPins,
        unlockedFolders,
        favoritePaths,
        refreshTick,
        localTick
    ) {
        val raw = if (currentDir != null && selectedBucket != null) {
            VaultFileScanner.listEntriesInDirectory(
                directory = currentDir!!,
                sourceAppName = selectedBucket!!.appName,
                sourcePackage = selectedBucket!!.packageName,
                folderPins = folderPins,
                favorites = favoritePaths
            )
        } else if (browserMode == VaultBrowserMode.GALLERY || selectedScope != "ALL" || searchQuery.isNotBlank() || selectedCategory != VaultFileCategory.ALL) {
            VaultFileScanner.listAllFilesAcrossBuckets(scopedBuckets, favoritePaths)
        } else {
            emptyList()
        }

        val filtered = raw.filter { entry ->
            val matchesQuery = searchQuery.isBlank() || entry.cleanName.contains(searchQuery.trim(), ignoreCase = true)
            val matchesCat = selectedCategory == VaultFileCategory.ALL || entry.isDirectory || entry.category == selectedCategory
            matchesQuery && matchesCat
        }

        when (sortOption) {
            VaultSortOption.NAME -> filtered.sortedWith(compareByDescending<VaultEntry> { it.isDirectory }.thenBy { it.cleanName.lowercase() })
            VaultSortOption.SIZE -> filtered.sortedWith(compareByDescending<VaultEntry> { it.isDirectory }.thenByDescending { it.sizeBytes })
            VaultSortOption.DATE -> filtered.sortedWith(compareByDescending<VaultEntry> { it.isDirectory }.thenByDescending { it.lastModified })
        }
    }

    // 4. Recursive Folder Tree Export Launcher (Exports selected files/folders to chosen device directory)
    val exportToFolderTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            val selectedItems = currentEntries.filter { selectedPaths.contains(it.path) }
            viewModel.exportEntriesToDeviceFolderTree(
                entries = selectedItems,
                treeUri = treeUri
            ) { filesCount, foldersCount, bytesExported ->
                selectedPaths = emptySet()
                Toast.makeText(
                    context,
                    "Exported $filesCount file(s)" +
                        (if (foldersCount > 0) " in $foldersCount folder(s)" else "") +
                        " (${formatBytes(bytesExported)}) to selected folder",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Gallery media items (Photos + Videos across scoped volumes)
    val galleryMediaEntries = remember(scopedBuckets, favoritePaths, searchQuery, sortOption, refreshTick, localTick) {
        val allFiles = VaultFileScanner.listAllFilesAcrossBuckets(scopedBuckets, favoritePaths)
            .filter { it.category == VaultFileCategory.PHOTOS || it.category == VaultFileCategory.VIDEOS }
            .filter { searchQuery.isBlank() || it.cleanName.contains(searchQuery.trim(), ignoreCase = true) }

        when (sortOption) {
            VaultSortOption.NAME -> allFiles.sortedBy { it.cleanName.lowercase() }
            VaultSortOption.SIZE -> allFiles.sortedByDescending { it.sizeBytes }
            VaultSortOption.DATE -> allFiles.sortedByDescending { it.lastModified }
        }
    }

    // Gallery albums grouped by volume & folder
    val galleryAlbums = remember(galleryMediaEntries) {
        galleryMediaEntries
            .groupBy { "${it.sourcePackage}::${it.file.parentFile?.name ?: "Media"}" }
            .mapNotNull { (key, list) ->
                if (list.isEmpty()) return@mapNotNull null
                val first = list.first()
                val folderName = first.file.parentFile?.name ?: "Media"
                GalleryAlbumItem(
                    id = key,
                    title = "${first.sourceAppName} • $folderName",
                    subtitle = "${list.size} item${if (list.size == 1) "" else "s"}",
                    coverEntry = first,
                    entries = list,
                    isSharedAlbum = first.sourcePackage == "shared.vault"
                )
            }
    }

    val recycleBinItems = remember(showRecycleBin, refreshTick, localTick) {
        VaultFileScanner.listRecycleBinItems(recycleBinDir, appNameLookup)
    }

    // Auto-switch to grid view when filtering by Photos or Videos
    LaunchedEffect(selectedCategory) {
        if (selectedCategory == VaultFileCategory.PHOTOS || selectedCategory == VaultFileCategory.VIDEOS) {
            isGridView = true
        }
    }

    // Back navigation handler
    BackHandler(
        enabled = isSelectionMode || showRecycleBin || selectedAlbum != null || currentDir != null
    ) {
        when {
            isSelectionMode -> selectedPaths = emptySet()
            showRecycleBin -> showRecycleBin = false
            selectedAlbum != null -> selectedAlbum = null
            currentDir != null && selectedBucket != null -> {
                val root = selectedBucket!!.rootDir
                if (currentDir!!.canonicalPath == root.canonicalPath) {
                    currentDir = null
                    selectedBucket = null
                } else {
                    currentDir = currentDir!!.parentFile ?: root
                }
            }
        }
    }

    fun openEntry(entry: VaultEntry, siblingEntries: List<VaultEntry> = currentEntries) {
        if (entry.isDirectory) {
            val requiredPin = folderPins[entry.path]
            if (requiredPin != null && !unlockedFolders.contains(entry.path)) {
                lockedFolderPromptTarget = entry
                lockedFolderPinInput = ""
                lockedFolderPinError = false
            } else {
                currentDir = entry.file
            }
            return
        }

        when {
            entry.category == VaultFileCategory.PHOTOS -> {
                val photosInFolder = siblingEntries.filter { !it.isDirectory && it.category == VaultFileCategory.PHOTOS }
                val idx = photosInFolder.indexOfFirst { it.path == entry.path }.coerceAtLeast(0)
                photoViewerList = photosInFolder.ifEmpty { listOf(entry) }
                photoViewerInitialIndex = idx
                showPhotoViewer = true
            }
            entry.category == VaultFileCategory.VIDEOS -> {
                videoPlayerEntry = entry
            }
            entry.category == VaultFileCategory.AUDIO -> {
                audioPlayerEntry = entry
            }
            VaultFileScanner.isPdfFile(entry.cleanName) -> {
                pdfViewerEntry = entry
            }
            VaultFileScanner.isTextFile(entry.cleanName) || entry.category == VaultFileCategory.DOCUMENTS -> {
                textViewerEntry = entry
            }
            else -> {
                detailsTarget = entry
            }
        }
    }

    Scaffold(
        containerColor = colorScheme.background,
        floatingActionButton = {
            if (!showRecycleBin && !isSelectionMode && browserMode == VaultBrowserMode.FILES) {
                FloatingActionButton(
                    onClick = { showImportOptionsSheet = true },
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.testTag("files_import_fab")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Import Folders, Files or Create Volume",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideScreen = maxWidth >= 600.dp
            val horizontalPad = if (isWideScreen) 24.dp else 16.dp

            if (showRecycleBin) {
                RecycleBinScreenContent(
                    items = recycleBinItems,
                    onBack = { showRecycleBin = false },
                    onRestoreItem = { binItem ->
                        val targetRoot = if (binItem.sourcePackage == "shared.vault") {
                            sharedSpaceDir
                        } else {
                            File(sandboxBaseDir, binItem.sourcePackage)
                        }
                        val restoredDir = File(targetRoot, "Pictures").apply { if (!exists()) mkdirs() }
                        val restoredFile = File(restoredDir, binItem.file.name.substringAfterLast("__"))
                        if (binItem.file.isDirectory) {
                            binItem.file.copyRecursively(restoredFile, overwrite = true)
                            binItem.file.deleteRecursively()
                        } else {
                            binItem.file.copyTo(restoredFile, overwrite = true)
                            binItem.file.delete()
                        }
                        localTick += 1
                        viewModel.notifyVaultFilesChanged()
                        Toast.makeText(context, "Restored ${binItem.originalName}", Toast.LENGTH_SHORT).show()
                    },
                    onEmptyBin = {
                        recycleBinDir.listFiles()?.forEach { it.deleteRecursively() }
                        localTick += 1
                        Toast.makeText(context, "Recycle bin emptied", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPad)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Transfer Progress Card (visible while encrypting/importing or exporting folders)
                    AnimatedVisibility(visible = transferProgress.isActive) {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = colorScheme.primary.copy(alpha = 0.14f)
                            ),
                            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.4f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = transferProgress.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = JakartaFontFamily,
                                        color = colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${transferProgress.processedCount} files • ${formatBytes(transferProgress.totalBytesProcessed)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = JakartaFontFamily,
                                        color = colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = transferProgress.currentItemName,
                                    fontSize = 12.sp,
                                    fontFamily = JakartaFontFamily,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = colorScheme.primary,
                                    trackColor = colorScheme.surfaceVariant
                                )
                            }
                        }
                    }

                    // Top Header or Folder Navigation Header
                    if (currentDir == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (browserMode == VaultBrowserMode.FILES) "Encrypted Volumes" else "Vault Gallery",
                                    fontSize = if (isWideScreen) 28.sp else 24.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FrauncesFontFamily,
                                    color = colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isDecoyMode) {
                                        "0 B used across 0 volumes"
                                    } else {
                                        "${formatBytes(totalVaultBytes)} encrypted across ${appBuckets.size} volumes"
                                    },
                                    fontSize = 12.sp,
                                    fontFamily = JakartaFontFamily,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Segmented toggle: Files vs Gallery
                                Surface(
                                    color = colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, colorScheme.outlineVariant)
                                ) {
                                    Row(modifier = Modifier.padding(3.dp)) {
                                        VaultBrowserMode.entries.forEach { mode ->
                                            val selected = browserMode == mode
                                            Surface(
                                                color = if (selected) colorScheme.primary else Color.Transparent,
                                                shape = RoundedCornerShape(11.dp),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(11.dp))
                                                    .clickable {
                                                        browserMode = mode
                                                        selectedAlbum = null
                                                    }
                                            ) {
                                                Text(
                                                    text = mode.label,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontFamily = JakartaFontFamily,
                                                    color = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(
                                    onClick = { showRecycleBin = true },
                                    modifier = Modifier.testTag("open_recycle_bin_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Recycle Bin",
                                        tint = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Storage Overview & Quick Import Card (Responsive FlowRow buttons)
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                            border = BorderStroke(1.dp, colorScheme.outlineVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(colorScheme.primary.copy(alpha = 0.14f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Lock,
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "AES256-GCM-HKDF-4KB Streaming Vault",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = JakartaFontFamily,
                                            color = colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Import entire folders with subfolders or individual files directly into any encrypted volume",
                                            fontSize = 12.sp,
                                            fontFamily = JakartaFontFamily,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = { importFolderTreeLauncher.launch(null) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colorScheme.primary,
                                            contentColor = colorScheme.onPrimary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                                        modifier = Modifier.testTag("import_folder_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.FolderCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Import Folder",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = JakartaFontFamily
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { importFilesLauncher.launch("*/*") },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                                        modifier = Modifier.testTag("import_files_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.FileUpload,
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Import Files",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = JakartaFontFamily,
                                            color = colorScheme.primary
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            importVisualMediaLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                                        modifier = Modifier.testTag("import_media_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PhotoLibrary,
                                            contentDescription = null,
                                            tint = colorScheme.onSurface,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Photos & Videos",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = JakartaFontFamily,
                                            color = colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Scope filter chips: All, Shared Space, and each protected app volume
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            FilterChip(
                                selected = selectedScope == "ALL",
                                onClick = { selectedScope = "ALL" },
                                label = { Text("All Volumes", fontFamily = JakartaFontFamily, fontSize = 12.sp) },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colorScheme.primary.copy(alpha = 0.18f),
                                    selectedLabelColor = colorScheme.primary
                                )
                            )
                            appBuckets.forEach { bucket ->
                                val isSel = selectedScope == bucket.packageName
                                FilterChip(
                                    selected = isSel,
                                    onClick = { selectedScope = bucket.packageName },
                                    leadingIcon = if (bucket.isSharedVolume) {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.FolderShared,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null,
                                    label = {
                                        Text(
                                            text = bucket.appName,
                                            fontFamily = JakartaFontFamily,
                                            fontSize = 12.sp
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colorScheme.primary.copy(alpha = 0.18f),
                                        selectedLabelColor = colorScheme.primary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Search bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = if (browserMode == VaultBrowserMode.GALLERY) "Search vault photos & videos..." else "Search encrypted files & folders...",
                                    fontFamily = JakartaFontFamily,
                                    fontSize = 14.sp
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Search, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = colorScheme.surfaceVariant,
                                unfocusedContainerColor = colorScheme.surfaceVariant,
                                focusedBorderColor = colorScheme.primary,
                                unfocusedBorderColor = colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("files_root_search_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Mode-specific body
                        if (browserMode == VaultBrowserMode.GALLERY) {
                            // Gallery Sub-mode selector: Albums, Timeline, Favorites
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                GallerySubMode.entries.forEach { sub ->
                                    FilterChip(
                                        selected = gallerySubMode == sub && selectedAlbum == null,
                                        onClick = {
                                            gallerySubMode = sub
                                            selectedAlbum = null
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = when (sub) {
                                                    GallerySubMode.ALBUMS -> Icons.Rounded.Collections
                                                    GallerySubMode.TIMELINE -> Icons.Rounded.PhotoLibrary
                                                    GallerySubMode.FAVORITES -> Icons.Rounded.Favorite
                                                },
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = {
                                            Text(sub.label, fontFamily = JakartaFontFamily, fontSize = 12.sp)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = colorScheme.primary.copy(alpha = 0.18f),
                                            selectedLabelColor = colorScheme.primary
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (selectedAlbum != null) {
                                val album = selectedAlbum!!
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp)
                                ) {
                                    IconButton(onClick = { selectedAlbum = null }) {
                                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to Albums")
                                    }
                                    Column {
                                        Text(
                                            text = album.title,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = FrauncesFontFamily,
                                            color = colorScheme.onBackground
                                        )
                                        Text(
                                            text = album.subtitle,
                                            fontSize = 12.sp,
                                            fontFamily = JakartaFontFamily,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 148.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(bottom = 96.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(album.entries, key = { it.path }) { entry ->
                                        val volKey = remember(entry.sourcePackage) { viewModel.getOrCreateVolumeKey(entry.sourcePackage) }
                                        VaultGridCell(
                                            entry = entry,
                                            volumeKey = volKey,
                                            isSelected = false,
                                            onClick = { openEntry(entry, album.entries) },
                                            onLongClick = { detailsTarget = entry }
                                        )
                                    }
                                }
                            } else {
                                when (gallerySubMode) {
                                    GallerySubMode.ALBUMS -> {
                                        if (galleryAlbums.isEmpty()) {
                                            FriendlyEmptyFolderState(
                                                title = "No media albums yet",
                                                subtitle = "Import a folder or photos/videos into Shared Space or any app volume to browse encrypted albums."
                                            )
                                        } else {
                                            LazyVerticalGrid(
                                                columns = GridCells.Adaptive(minSize = 156.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                contentPadding = PaddingValues(bottom = 96.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                items(galleryAlbums, key = { it.id }) { album ->
                                                    val volKey = remember(album.coverEntry.sourcePackage) {
                                                        viewModel.getOrCreateVolumeKey(album.coverEntry.sourcePackage)
                                                    }
                                                    val coverBitmap = rememberDecryptedThumbnail(album.coverEntry, volKey)
                                                    Card(
                                                        shape = RoundedCornerShape(20.dp),
                                                        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                                                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .aspectRatio(0.92f)
                                                            .clip(RoundedCornerShape(20.dp))
                                                            .clickable { selectedAlbum = album }
                                                    ) {
                                                        Box(modifier = Modifier.fillMaxSize()) {
                                                            if (coverBitmap != null) {
                                                                Image(
                                                                    bitmap = coverBitmap,
                                                                    contentDescription = album.title,
                                                                    contentScale = ContentScale.Crop,
                                                                    modifier = Modifier.fillMaxSize()
                                                                )
                                                            } else {
                                                                Box(
                                                                    contentAlignment = Alignment.Center,
                                                                    modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .padding(bottom = 44.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Rounded.Collections,
                                                                        contentDescription = null,
                                                                        tint = colorScheme.primary,
                                                                        modifier = Modifier.size(42.dp)
                                                                    )
                                                                }
                                                            }
                                                            Column(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .align(Alignment.BottomCenter)
                                                                    .background(colorScheme.surface.copy(alpha = 0.92f))
                                                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                                            ) {
                                                                Text(
                                                                    text = album.title,
                                                                    fontSize = 13.sp,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    fontFamily = JakartaFontFamily,
                                                                    color = colorScheme.onSurface,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Text(
                                                                    text = album.subtitle,
                                                                    fontSize = 11.sp,
                                                                    fontFamily = JakartaFontFamily,
                                                                    color = colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    GallerySubMode.TIMELINE, GallerySubMode.FAVORITES -> {
                                        val mediaList = if (gallerySubMode == GallerySubMode.FAVORITES) {
                                            galleryMediaEntries.filter { it.isFavorite }
                                        } else {
                                            galleryMediaEntries
                                        }
                                        if (mediaList.isEmpty()) {
                                            FriendlyEmptyFolderState(
                                                title = if (gallerySubMode == GallerySubMode.FAVORITES) "No favorites yet" else "No media in timeline",
                                                subtitle = if (gallerySubMode == GallerySubMode.FAVORITES) {
                                                    "Tap the heart icon inside the photo viewer to mark encrypted photos as favorites."
                                                } else {
                                                    "Photos and videos saved by isolated apps or imported into volumes appear here."
                                                }
                                            )
                                        } else {
                                            LazyVerticalGrid(
                                                columns = GridCells.Adaptive(minSize = 148.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                contentPadding = PaddingValues(bottom = 96.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                items(mediaList, key = { it.path }) { entry ->
                                                    val volKey = remember(entry.sourcePackage) {
                                                        viewModel.getOrCreateVolumeKey(entry.sourcePackage)
                                                    }
                                                    VaultGridCell(
                                                        entry = entry,
                                                        volumeKey = volKey,
                                                        isSelected = false,
                                                        onClick = { openEntry(entry, mediaList) },
                                                        onLongClick = { detailsTarget = entry }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // FILES MODE AT ROOT
                            if (selectedScope == "ALL" && searchQuery.isBlank() && selectedCategory == VaultFileCategory.ALL) {
                                if (appBuckets.isEmpty()) {
                                    FriendlyEmptyFolderState(
                                        title = "Vault is empty",
                                        subtitle = "Import a folder or protect an app in the Apps tab to create its isolated encrypted volume."
                                    )
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(bottom = 96.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        items(appBuckets, key = { it.packageName }) { bucket ->
                                            Card(
                                                shape = RoundedCornerShape(20.dp),
                                                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                                                border = BorderStroke(
                                                    width = 1.dp,
                                                    color = if (bucket.isSharedVolume) colorScheme.primary.copy(alpha = 0.45f) else colorScheme.outlineVariant
                                                ),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .clickable {
                                                        selectedBucket = bucket
                                                        currentDir = bucket.rootDir
                                                    }
                                                    .testTag("app_bucket_${bucket.packageName}")
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    BrandSquircleIcon(
                                                        name = bucket.appName,
                                                        size = 46.dp,
                                                        packageName = bucket.packageName
                                                    )

                                                    Spacer(modifier = Modifier.width(14.dp))

                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = bucket.appName,
                                                                fontSize = 16.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                fontFamily = JakartaFontFamily,
                                                                color = colorScheme.onSurface,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            if (bucket.isSharedVolume) {
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                StatusBadge(text = "Shared", color = colorScheme.primary)
                                                            } else if (bucket.useSharedSpace) {
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                StatusBadge(text = "Linked Shared", color = colorScheme.primary)
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(3.dp))
                                                        val quotaText = if (bucket.quotaMb > 0) " / ${bucket.quotaMb} MB quota" else ""
                                                        Text(
                                                            text = "${bucket.fileCount} encrypted files • ${formatBytes(bucket.totalBytes)}$quotaText",
                                                            fontSize = 12.sp,
                                                            fontFamily = JakartaFontFamily,
                                                            color = colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    Icon(
                                                        imageVector = Icons.Rounded.ChevronRight,
                                                        contentDescription = "Open volume",
                                                        tint = colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Combined Scoped File List (when a scope chip or search is active)
                                if (currentEntries.isEmpty()) {
                                    FriendlyEmptyFolderState(
                                        title = "No matching files",
                                        subtitle = "Import a folder or files using the buttons above, or adjust your search/scope filter."
                                    )
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(bottom = 96.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        items(currentEntries, key = { it.path }) { entry ->
                                            val volKey = remember(entry.sourcePackage) {
                                                viewModel.getOrCreateVolumeKey(entry.sourcePackage)
                                            }
                                            VaultListRow(
                                                entry = entry,
                                                volumeKey = volKey,
                                                isSelected = false,
                                                onClick = { openEntry(entry) },
                                                onLongClick = { detailsTarget = entry },
                                                onShowDetails = { detailsTarget = entry },
                                                onLockFolderClick = {}
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // INSIDE A VOLUME FOLDER BROWSER
                        val bucket = selectedBucket!!
                        val isAtBucketRoot = currentDir!!.canonicalPath == bucket.rootDir.canonicalPath

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedPaths = emptySet()
                                        } else if (isAtBucketRoot) {
                                            currentDir = null
                                            selectedBucket = null
                                        } else {
                                            currentDir = currentDir!!.parentFile ?: bucket.rootDir
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Back",
                                        tint = colorScheme.onBackground
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isSelectionMode) {
                                            "${selectedPaths.size} selected"
                                        } else if (isAtBucketRoot) {
                                            bucket.appName
                                        } else {
                                            currentDir!!.name
                                        },
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FrauncesFontFamily,
                                        color = colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isAtBucketRoot) {
                                            if (bucket.isSharedVolume) "Shared Space • Linked across opted-in apps" else "Per-App Encrypted Volume"
                                        } else {
                                            "${bucket.appName} / ${currentDir!!.name}"
                                        },
                                        fontSize = 12.sp,
                                        fontFamily = JakartaFontFamily,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSelectionMode) {
                                    IconButton(
                                        onClick = {
                                            selectedPaths = if (selectedPaths.size == currentEntries.size) {
                                                emptySet()
                                            } else {
                                                currentEntries.map { it.path }.toSet()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.SelectAll,
                                            contentDescription = "Select All",
                                            tint = colorScheme.primary
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = { isGridView = !isGridView },
                                        modifier = Modifier.testTag("toggle_grid_list_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isGridView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                                            contentDescription = "Switch View",
                                            tint = colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Box {
                                        IconButton(onClick = { showSortMenu = true }) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.Sort,
                                                contentDescription = "Sort",
                                                tint = colorScheme.onSurfaceVariant
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showSortMenu,
                                            onDismissRequest = { showSortMenu = false }
                                        ) {
                                            VaultSortOption.entries.forEach { opt ->
                                                DropdownMenuItem(
                                                    text = { Text("Sort by ${opt.label}", fontFamily = JakartaFontFamily) },
                                                    onClick = {
                                                        sortOption = opt
                                                        showSortMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick Action Chips Inside Folder: Import Folder, Import Files, New Folder
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Button(
                                onClick = { importFolderTreeLauncher.launch(null) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.primary,
                                    contentColor = colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                                modifier = Modifier.testTag("folder_import_tree_button")
                            ) {
                                Icon(Icons.Rounded.FolderCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import Folder", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily)
                            }

                            OutlinedButton(
                                onClick = { importFilesLauncher.launch("*/*") },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                                modifier = Modifier.testTag("folder_import_files_button")
                            ) {
                                Icon(Icons.Rounded.FileUpload, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import Files", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily, color = colorScheme.primary)
                            }

                            OutlinedButton(
                                onClick = {
                                    newFolderName = ""
                                    showCreateFolderDialog = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                                modifier = Modifier.testTag("create_folder_button")
                            ) {
                                Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, tint = colorScheme.onSurface, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("New Folder", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily, color = colorScheme.onSurface)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Search Box
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Search in ${currentDir!!.name}...",
                                    fontFamily = JakartaFontFamily,
                                    fontSize = 14.sp
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Search, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = colorScheme.surfaceVariant,
                                unfocusedContainerColor = colorScheme.surfaceVariant,
                                focusedBorderColor = colorScheme.primary,
                                unfocusedBorderColor = colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("files_search_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Category Filter Chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            VaultFileCategory.entries.forEach { cat ->
                                val selected = selectedCategory == cat
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedCategory = cat },
                                    label = {
                                        Text(
                                            text = cat.label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = JakartaFontFamily
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colorScheme.primary.copy(alpha = 0.18f),
                                        selectedLabelColor = colorScheme.primary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val activeVolumeKey = remember(bucket.packageName) {
                            viewModel.getOrCreateVolumeKey(bucket.packageName)
                        }

                        if (currentEntries.isEmpty()) {
                            Box(modifier = Modifier.weight(1f)) {
                                FriendlyEmptyFolderState(
                                    title = "This folder is empty",
                                    subtitle = "Tap \"Import Folder\" or \"Import Files\" above to encrypt folders, photos, videos, or documents here."
                                )
                            }
                        } else if (isGridView) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 148.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = if (isSelectionMode) 120.dp else 88.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(currentEntries, key = { it.path }) { entry ->
                                    val isSelected = selectedPaths.contains(entry.path)
                                    VaultGridCell(
                                        entry = entry,
                                        volumeKey = activeVolumeKey,
                                        isSelected = isSelected,
                                        onClick = {
                                            if (isSelectionMode) {
                                                selectedPaths = if (isSelected) selectedPaths - entry.path else selectedPaths + entry.path
                                            } else {
                                                openEntry(entry)
                                            }
                                        },
                                        onLongClick = {
                                            selectedPaths = if (isSelected) selectedPaths - entry.path else selectedPaths + entry.path
                                        }
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = if (isSelectionMode) 120.dp else 88.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(currentEntries, key = { it.path }) { entry ->
                                    val isSelected = selectedPaths.contains(entry.path)
                                    VaultListRow(
                                        entry = entry,
                                        volumeKey = activeVolumeKey,
                                        isSelected = isSelected,
                                        onClick = {
                                            if (isSelectionMode) {
                                                selectedPaths = if (isSelected) selectedPaths - entry.path else selectedPaths + entry.path
                                            } else {
                                                openEntry(entry)
                                            }
                                        },
                                        onLongClick = {
                                            selectedPaths = if (isSelected) selectedPaths - entry.path else selectedPaths + entry.path
                                        },
                                        onShowDetails = { detailsTarget = entry },
                                        onLockFolderClick = {
                                            folderPinSetupTarget = entry.file
                                            folderPinSetupInput = folderPins[entry.path] ?: ""
                                        }
                                    )
                                }
                            }
                        }

                        // Multi-select bottom action bar (horizontally scrollable for compact screens)
                        AnimatedVisibility(visible = isSelectionMode) {
                            val selectedItems = currentEntries.filter { selectedPaths.contains(it.path) }
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                                border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. Export to Chosen Device Folder (SAF OpenDocumentTree)
                                    SelectionActionButton(
                                        icon = Icons.Rounded.FolderZip,
                                        label = "Export to Folder",
                                        onClick = {
                                            exportToFolderTreeLauncher.launch(null)
                                        }
                                    )

                                    // 2. Quick Copy/Move to Shared Space
                                    if (!bucket.isSharedVolume) {
                                        SelectionActionButton(
                                            icon = Icons.Rounded.FolderShared,
                                            label = "To Shared",
                                            onClick = {
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    val destFolder = File(sharedSpaceDir, "Pictures").apply { if (!exists()) mkdirs() }
                                                    val srcKey = viewModel.getOrCreateVolumeKey(bucket.packageName)
                                                    val dstKey = viewModel.getOrCreateVolumeKey("shared.vault")
                                                    var count = 0
                                                    for (item in selectedItems) {
                                                        val ok = VaultFileScanner.transferEntryBetweenVolumes(
                                                            entry = item,
                                                            destinationFolder = destFolder,
                                                            sourceKey = srcKey,
                                                            destKey = dstKey,
                                                            isMove = false
                                                        )
                                                        if (ok) count++
                                                    }
                                                    withContext(Dispatchers.Main) {
                                                        selectedPaths = emptySet()
                                                        localTick += 1
                                                        viewModel.notifyVaultFilesChanged()
                                                        Toast.makeText(context, "Copied $count item(s) to Shared Space", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        )
                                    }

                                    // 3. Move to Volume / Folder
                                    SelectionActionButton(
                                        icon = Icons.AutoMirrored.Rounded.DriveFileMove,
                                        label = "Move",
                                        onClick = {
                                            isMoveOperation = true
                                            showTransferSheet = true
                                        }
                                    )

                                    // 4. Copy to Volume / Folder
                                    SelectionActionButton(
                                        icon = Icons.Rounded.ContentCopy,
                                        label = "Copy",
                                        onClick = {
                                            isMoveOperation = false
                                            showTransferSheet = true
                                        }
                                    )

                                    // 5. Rename (when 1 item selected)
                                    if (selectedItems.size == 1) {
                                        SelectionActionButton(
                                            icon = Icons.Rounded.DriveFileRenameOutline,
                                            label = "Rename",
                                            onClick = {
                                                val item = selectedItems.first()
                                                renameTarget = item
                                                renameInput = item.cleanName
                                            }
                                        )
                                    }

                                    // 6. Share Decrypted Stream via Temporary FileProvider Cache
                                    SelectionActionButton(
                                        icon = Icons.Rounded.Share,
                                        label = "Share",
                                        onClick = {
                                            val firstFile = selectedItems.firstOrNull { !it.isDirectory }
                                            if (firstFile != null) {
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    try {
                                                        val shareCacheDir = File(context.cacheDir, "vault_stream_preview").apply { mkdirs() }
                                                        val tempOut = File(shareCacheDir, firstFile.cleanName)
                                                        val volKey = viewModel.getOrCreateVolumeKey(firstFile.sourcePackage)
                                                        AesVaultCipher.newDecryptingInputStream(firstFile.file, volKey).use { plainIn ->
                                                            tempOut.outputStream().use { out -> plainIn.copyTo(out) }
                                                        }
                                                        val uri = FileProvider.getUriForFile(
                                                            context,
                                                            "${context.packageName}.fileprovider",
                                                            tempOut
                                                        )
                                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                            type = "*/*"
                                                            putExtra(Intent.EXTRA_STREAM, uri)
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        }
                                                        withContext(Dispatchers.Main) {
                                                            context.startActivity(Intent.createChooser(sendIntent, "Share File"))
                                                        }
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            } else {
                                                Toast.makeText(context, "Select a file to share, or use Export to Folder for directories", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )

                                    // 7. Delete to 30-Day Recycle Bin
                                    SelectionActionButton(
                                        icon = Icons.Rounded.Delete,
                                        label = "Delete",
                                        tint = colorScheme.error,
                                        onClick = {
                                            var deletedCount = 0
                                            selectedItems.forEach { item ->
                                                if (VaultFileScanner.moveToRecycleBin(item, recycleBinDir)) {
                                                    deletedCount++
                                                }
                                            }
                                            selectedPaths = emptySet()
                                            localTick += 1
                                            viewModel.notifyVaultFilesChanged()
                                            Toast.makeText(context, "Moved $deletedCount item(s) to Recycle Bin (30 days)", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Import & Create Action Sheet (Opened via FAB)
    if (showImportOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showImportOptionsSheet = false },
            containerColor = colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Import or Create in Vault",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FrauncesFontFamily,
                    color = colorScheme.onSurface
                )
                Text(
                    text = "Target: $activeImportAppName" + (currentDir?.let { " / ${it.name}" } ?: ""),
                    fontSize = 13.sp,
                    fontFamily = JakartaFontFamily,
                    color = colorScheme.primary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle: Move (delete originals after encrypting) vs Copy
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Move into Vault (Delete Originals)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = JakartaFontFamily,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "Automatically remove source files from public storage after encrypting",
                                fontSize = 11.sp,
                                fontFamily = JakartaFontFamily,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = deleteOriginalsOnImport,
                            onCheckedChange = { deleteOriginalsOnImport = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colorScheme.onPrimary,
                                checkedTrackColor = colorScheme.primary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Option 1: Import Entire Folder (Recursive SAF Tree)
                ImportSheetOptionCard(
                    icon = Icons.Rounded.FolderCopy,
                    title = "Import Entire Folder",
                    subtitle = "Select any folder on your phone to recursively encrypt all its files and nested subfolders",
                    onClick = {
                        showImportOptionsSheet = false
                        importFolderTreeLauncher.launch(null)
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Option 2: Import Photos & Videos
                ImportSheetOptionCard(
                    icon = Icons.Rounded.PhotoLibrary,
                    title = "Import Photos & Videos",
                    subtitle = "Pick multiple images and videos from your gallery using Android Photo Picker",
                    onClick = {
                        showImportOptionsSheet = false
                        importVisualMediaLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Option 3: Import Files & Documents
                ImportSheetOptionCard(
                    icon = Icons.Rounded.FileUpload,
                    title = "Import Files & Documents",
                    subtitle = "Select PDFs, audio, ZIPs, or any files from your device storage",
                    onClick = {
                        showImportOptionsSheet = false
                        importFilesLauncher.launch("*/*")
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Option 4: Create New Folder or Volume
                ImportSheetOptionCard(
                    icon = Icons.Rounded.CreateNewFolder,
                    title = if (currentDir != null) "Create New Subfolder" else "Create New Encrypted Volume",
                    subtitle = if (currentDir != null) {
                        "Create a new folder inside ${currentDir!!.name}"
                    } else {
                        "Create a new isolated volume with its own 256-bit encryption key"
                    },
                    onClick = {
                        showImportOptionsSheet = false
                        if (currentDir != null) {
                            newFolderName = ""
                            showCreateFolderDialog = true
                        } else {
                            newVolumeName = ""
                            newVolumeQuotaMb = ""
                            newVolumeUseShared = true
                            showCreateVolumeDialog = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Create Encrypted Volume Dialog
    if (showCreateVolumeDialog) {
        AlertDialog(
            onDismissRequest = { showCreateVolumeDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = colorScheme.surface,
            title = {
                Text(
                    text = "Create Encrypted Volume",
                    fontFamily = FrauncesFontFamily,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Each volume gets its own 256-bit key wrapped by the Android Keystore master key and automatic media folders (DCIM, Pictures, Download, Movies, Music, Documents).",
                        fontSize = 12.sp,
                        fontFamily = JakartaFontFamily,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = newVolumeName,
                        onValueChange = { newVolumeName = it },
                        label = { Text("Volume Name") },
                        placeholder = { Text("e.g. Work Vault or Camera Locker") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newVolumeQuotaMb,
                        onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 6) newVolumeQuotaMb = it },
                        label = { Text("Optional Storage Quota (MB)") },
                        placeholder = { Text("Leave blank for unlimited") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Link to Shared Space",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = JakartaFontFamily,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "Allow shared media visibility",
                                fontSize = 11.sp,
                                fontFamily = JakartaFontFamily,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = newVolumeUseShared,
                            onCheckedChange = { newVolumeUseShared = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colorScheme.onPrimary,
                                checkedTrackColor = colorScheme.primary
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newVolumeName.trim()
                        if (trimmed.isNotEmpty()) {
                            val slug = trimmed.lowercase().replace(Regex("[^a-z0-9]"), ".")
                            val pkgId = "volume.$slug.${System.currentTimeMillis() % 10000}"
                            val quota = newVolumeQuotaMb.toIntOrNull() ?: 0
                            viewModel.createVolume(
                                volumeName = trimmed,
                                packageName = pkgId,
                                useSharedSpace = newVolumeUseShared,
                                quotaMb = quota
                            )
                            showCreateVolumeDialog = false
                            localTick += 1
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Create Volume")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateVolumeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Create Folder Dialog
    if (showCreateFolderDialog && currentDir != null) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = colorScheme.surface,
            title = {
                Text("New Folder", fontFamily = FrauncesFontFamily, fontWeight = FontWeight.SemiBold)
            },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    placeholder = { Text("Folder name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            val safe = newFolderName.trim().replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
                            val folder = File(currentDir!!, safe)
                            folder.mkdirs()
                            File(folder, ".nomedia").createNewFile()
                            showCreateFolderDialog = false
                            localTick += 1
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Dialog
    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = colorScheme.surface,
            title = {
                Text("Rename", fontFamily = FrauncesFontFamily, fontWeight = FontWeight.SemiBold)
            },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            val newRawName = if (target.isEncrypted && !renameInput.endsWith(".isovault")) {
                                "${renameInput.trim()}.isovault"
                            } else {
                                renameInput.trim()
                            }
                            val dest = File(target.file.parentFile, newRawName)
                            target.file.renameTo(dest)
                            renameTarget = null
                            selectedPaths = emptySet()
                            localTick += 1
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Secondary PIN Setup Dialog for a Folder
    folderPinSetupTarget?.let { folderFile ->
        val existingPin = folderPins[folderFile.absolutePath]
        AlertDialog(
            onDismissRequest = { folderPinSetupTarget = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = colorScheme.surface,
            title = {
                Text("Folder Secondary PIN", fontFamily = FrauncesFontFamily, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column {
                    Text(
                        text = "Protect \"${folderFile.name}\" with an extra 4-digit PIN required before opening.",
                        fontSize = 13.sp,
                        fontFamily = JakartaFontFamily,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = folderPinSetupInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) folderPinSetupInput = it },
                        placeholder = { Text("4-digit PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderPinSetupInput.length == 4) {
                            viewModel.setFolderSecondaryPin(folderFile.absolutePath, folderPinSetupInput)
                            folderPinSetupTarget = null
                            localTick += 1
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Set Lock")
                }
            },
            dismissButton = {
                Row {
                    if (existingPin != null) {
                        TextButton(
                            onClick = {
                                viewModel.setFolderSecondaryPin(folderFile.absolutePath, null)
                                folderPinSetupTarget = null
                                localTick += 1
                            }
                        ) {
                            Text("Remove Lock", color = colorScheme.error)
                        }
                    }
                    TextButton(onClick = { folderPinSetupTarget = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Prompt Secondary PIN when opening a locked folder
    lockedFolderPromptTarget?.let { lockedEntry ->
        val expectedPin = folderPins[lockedEntry.path] ?: ""
        AlertDialog(
            onDismissRequest = { lockedFolderPromptTarget = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = colorScheme.surface,
            title = {
                Text("Unlock Folder", fontFamily = FrauncesFontFamily, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column {
                    Text(
                        text = "Enter the secondary PIN for \"${lockedEntry.cleanName}\".",
                        fontSize = 13.sp,
                        fontFamily = JakartaFontFamily,
                        color = if (lockedFolderPinError) colorScheme.error else colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = lockedFolderPinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                lockedFolderPinInput = it
                                lockedFolderPinError = false
                            }
                        },
                        placeholder = { Text("4-digit Folder PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (lockedFolderPinInput == expectedPin) {
                            viewModel.unlockFolderInSession(lockedEntry.path)
                            currentDir = lockedEntry.file
                            lockedFolderPromptTarget = null
                        } else {
                            lockedFolderPinError = true
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { lockedFolderPromptTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Move / Copy Between Per-App Volumes & Shared Space Sheet (Re-encrypts with destination volume key)
    if (showTransferSheet) {
        val selectedItems = currentEntries.filter { selectedPaths.contains(it.path) }
        ModalBottomSheet(
            onDismissRequest = { showTransferSheet = false },
            containerColor = colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            TransferDestinationSheetContent(
                isMove = isMoveOperation,
                appBuckets = appBuckets,
                onSelectDestination = { destFolder, destAppName, destPackage ->
                    coroutineScope.launch(Dispatchers.IO) {
                        val destKey = viewModel.getOrCreateVolumeKey(destPackage)
                        var count = 0
                        for (item in selectedItems) {
                            val srcKey = viewModel.getOrCreateVolumeKey(item.sourcePackage)
                            val ok = VaultFileScanner.transferEntryBetweenVolumes(
                                entry = item,
                                destinationFolder = destFolder,
                                sourceKey = srcKey,
                                destKey = destKey,
                                isMove = isMoveOperation
                            )
                            if (ok) count++
                        }
                        withContext(Dispatchers.Main) {
                            showTransferSheet = false
                            selectedPaths = emptySet()
                            localTick += 1
                            viewModel.notifyVaultFilesChanged()
                            Toast.makeText(
                                context,
                                "${if (isMoveOperation) "Moved" else "Copied"} $count item(s) to $destAppName / ${destFolder.name}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )
        }
    }

    // Per-File Details Sheet
    detailsTarget?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { detailsTarget = null },
            containerColor = colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            FileDetailsSheetContent(
                entry = item,
                onClose = { detailsTarget = null }
            )
        }
    }

    // Built-In Media Viewers
    if (showPhotoViewer && photoViewerList.isNotEmpty()) {
        VaultPhotoViewerDialog(
            photos = photoViewerList,
            initialIndex = photoViewerInitialIndex,
            resolveVolumeKey = { pkg -> viewModel.getOrCreateVolumeKey(pkg) },
            favoritePaths = favoritePaths,
            onToggleFavorite = { path -> viewModel.toggleFavorite(path) },
            onShowDetails = {
                showPhotoViewer = false
                detailsTarget = it
            },
            onDismiss = { showPhotoViewer = false }
        )
    }

    videoPlayerEntry?.let { entry ->
        val key = remember(entry.sourcePackage) { viewModel.getOrCreateVolumeKey(entry.sourcePackage) }
        VaultVideoPlayerDialog(
            entry = entry,
            volumeKey = key,
            onShowDetails = {
                videoPlayerEntry = null
                detailsTarget = it
            },
            onDismiss = { videoPlayerEntry = null }
        )
    }

    audioPlayerEntry?.let { entry ->
        val key = remember(entry.sourcePackage) { viewModel.getOrCreateVolumeKey(entry.sourcePackage) }
        VaultAudioPlayerDialog(
            entry = entry,
            volumeKey = key,
            onDismiss = { audioPlayerEntry = null }
        )
    }

    pdfViewerEntry?.let { entry ->
        val key = remember(entry.sourcePackage) { viewModel.getOrCreateVolumeKey(entry.sourcePackage) }
        VaultPdfViewerDialog(
            entry = entry,
            volumeKey = key,
            onDismiss = { pdfViewerEntry = null }
        )
    }

    textViewerEntry?.let { entry ->
        val key = remember(entry.sourcePackage) { viewModel.getOrCreateVolumeKey(entry.sourcePackage) }
        VaultTextViewerDialog(
            entry = entry,
            volumeKey = key,
            onDismiss = { textViewerEntry = null }
        )
    }
}

@Composable
private fun ImportSheetOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colorScheme.primary.copy(alpha = 0.14f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = JakartaFontFamily,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    fontFamily = JakartaFontFamily,
                    color = colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultGridCell(
    entry: VaultEntry,
    volumeKey: javax.crypto.SecretKey,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val thumbnail = rememberDecryptedThumbnail(entry, volumeKey)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) colorScheme.primary else colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.95f)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = entry.cleanName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 36.dp)
                ) {
                    Icon(
                        imageVector = when {
                            entry.isDirectory -> Icons.Rounded.Folder
                            entry.category == VaultFileCategory.VIDEOS -> Icons.Rounded.PlayCircle
                            entry.category == VaultFileCategory.PHOTOS -> Icons.Rounded.Image
                            entry.category == VaultFileCategory.AUDIO -> Icons.Rounded.AudioFile
                            entry.category == VaultFileCategory.DOCUMENTS -> Icons.Rounded.Description
                            else -> Icons.AutoMirrored.Rounded.InsertDriveFile
                        },
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            if (isSelected) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary)
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            } else if (entry.isFolderPinLocked) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surface.copy(alpha = 0.85f))
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = "PIN Locked", tint = colorScheme.primary, modifier = Modifier.size(14.dp))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(colorScheme.surface.copy(alpha = 0.9f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = entry.cleanName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = JakartaFontFamily,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (entry.isDirectory) "Folder • ${formatBytes(entry.sizeBytes)}" else formatBytes(entry.sizeBytes),
                    fontSize = 11.sp,
                    fontFamily = JakartaFontFamily,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultListRow(
    entry: VaultEntry,
    volumeKey: javax.crypto.SecretKey,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShowDetails: () -> Unit,
    onLockFolderClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val thumbnail = rememberDecryptedThumbnail(entry, volumeKey)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) colorScheme.primary else colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colorScheme.primary.copy(alpha = 0.12f))
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = when {
                            entry.isDirectory -> Icons.Rounded.Folder
                            entry.category == VaultFileCategory.PHOTOS -> Icons.Rounded.Image
                            entry.category == VaultFileCategory.VIDEOS -> Icons.Rounded.VideoFile
                            entry.category == VaultFileCategory.AUDIO -> Icons.Rounded.AudioFile
                            entry.category == VaultFileCategory.DOCUMENTS -> Icons.Rounded.Description
                            else -> Icons.AutoMirrored.Rounded.InsertDriveFile
                        },
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.cleanName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = JakartaFontFamily,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.isFolderPinLocked) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Locked Folder",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                val dateStr = remember(entry.lastModified) {
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(entry.lastModified))
                }
                Text(
                    text = if (entry.isDirectory) {
                        "Folder • ${formatBytes(entry.sizeBytes)}"
                    } else {
                        "${entry.sourceAppName} • ${formatBytes(entry.sizeBytes)} • $dateStr"
                    },
                    fontSize = 12.sp,
                    fontFamily = JakartaFontFamily,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Selected",
                    tint = colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else if (entry.isDirectory) {
                IconButton(onClick = onLockFolderClick) {
                    Icon(
                        imageVector = if (entry.isFolderPinLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        contentDescription = "Folder PIN Lock",
                        tint = if (entry.isFolderPinLocked) colorScheme.primary else colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                IconButton(onClick = onShowDetails) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = "File Details",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = JakartaFontFamily,
            color = tint
        )
    }
}

@Composable
private fun FileDetailsSheetContent(
    entry: VaultEntry,
    onClose: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val formattedDate = remember(entry.lastModified) {
        SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(Date(entry.lastModified))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "File Details",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FrauncesFontFamily,
            color = colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))

        DetailInfoRow(label = "Name", value = entry.cleanName)
        DetailInfoRow(label = "Size", value = formatBytes(entry.sizeBytes))
        DetailInfoRow(label = "Date Modified", value = formattedDate)
        DetailInfoRow(label = "Volume", value = entry.sourceAppName)
        DetailInfoRow(
            label = "Encryption",
            value = if (entry.isEncrypted) "AES256_GCM_HKDF_4KB (Per-Volume DEK)" else "Vault Isolated Volume"
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onClose,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Done", fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    val colorScheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontFamily = JakartaFontFamily,
            color = colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = JakartaFontFamily,
            color = colorScheme.onSurface
        )
    }
}

@Composable
private fun TransferDestinationSheetContent(
    isMove: Boolean,
    appBuckets: List<VaultAppBucket>,
    onSelectDestination: (File, String, String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val standardSubfolders = listOf("Pictures", "DCIM", "Download", "Movies", "Music", "Documents")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            text = if (isMove) "Move to Volume & Folder" else "Copy to Volume & Folder",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FrauncesFontFamily,
            color = colorScheme.onSurface
        )
        Text(
            text = "Transferring between volumes automatically re-encrypts with the destination volume's 256-bit key",
            fontSize = 12.sp,
            fontFamily = JakartaFontFamily,
            color = colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(appBuckets, key = { it.packageName }) { bucket ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BrandSquircleIcon(
                                name = bucket.appName,
                                size = 34.dp,
                                packageName = bucket.packageName
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = bucket.appName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = JakartaFontFamily,
                                color = colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            standardSubfolders.forEach { folderName ->
                                Surface(
                                    color = colorScheme.surface,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            onSelectDestination(
                                                File(bucket.rootDir, folderName),
                                                bucket.appName,
                                                bucket.packageName
                                            )
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Folder,
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = folderName,
                                            fontSize = 13.sp,
                                            fontFamily = JakartaFontFamily,
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
    }
}

@Composable
private fun RecycleBinScreenContent(
    items: List<RecycleBinItem>,
    onBack: () -> Unit,
    onRestoreItem: (RecycleBinItem) -> Unit,
    onEmptyBin: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colorScheme.onBackground)
                }
                Column {
                    Text(
                        text = "Recycle Bin",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FrauncesFontFamily,
                        color = colorScheme.onBackground
                    )
                    Text(
                        text = "Deleted files stay here for 30 days before removal",
                        fontSize = 12.sp,
                        fontFamily = JakartaFontFamily,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            if (items.isNotEmpty()) {
                TextButton(onClick = onEmptyBin) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Empty", color = colorScheme.error, fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (items.isEmpty()) {
            FriendlyEmptyFolderState(
                title = "Recycle bin is empty",
                subtitle = "Items you delete from your volumes stay here for 30 days so you can restore them anytime."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(items, key = { it.file.absolutePath }) { binItem ->
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = binItem.originalName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = JakartaFontFamily,
                                    color = colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "${binItem.sourceAppName} • ${formatBytes(binItem.sizeBytes)} • ${binItem.daysRemaining} days left",
                                    fontSize = 12.sp,
                                    fontFamily = JakartaFontFamily,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Button(
                                onClick = { onRestoreItem(binItem) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.primary.copy(alpha = 0.16f),
                                    contentColor = colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Rounded.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restore", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendlyEmptyFolderState(
    title: String,
    subtitle: String
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 40.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primary.copy(alpha = 0.12f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOff,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FrauncesFontFamily,
                color = colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subtitle,
                fontSize = 14.sp,
                fontFamily = JakartaFontFamily,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
