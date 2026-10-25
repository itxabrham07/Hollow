package com.example.isolation.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
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
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material.icons.rounded.ViewList
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.ui.components.BrandSquircleIcon
import com.example.isolation.ui.components.formatBytes
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.AccentMint
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.JakartaFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(
    viewModel: IsoVaultViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    val configs by viewModel.configs.collectAsState()
    val isPanicModeActive by viewModel.isPanicModeActive.collectAsState()
    val selectedAppFromNav by viewModel.selectedVaultAppPackage.collectAsState()
    val folderPins by viewModel.folderPins.collectAsState()
    val unlockedFolders by viewModel.unlockedFoldersInSession.collectAsState()
    val favoritePaths by viewModel.favoritePaths.collectAsState()
    val sharedVolumeQuotaMb by viewModel.sharedVolumeQuotaMb.collectAsState()
    val vaultRefreshTick by viewModel.vaultRefreshTick.collectAsState()

    var browserMode by remember { mutableStateOf(VaultBrowserMode.FILES) }
    var gallerySubMode by remember { mutableStateOf(GallerySubMode.ALBUMS) }
    // Volume Scope Filter: "ALL", "shared.vault" ("Shared"), or a specific app packageName
    var selectedVolumeScope by remember { mutableStateOf("ALL") }

    var activeAppBucket by remember { mutableStateOf<VaultAppBucket?>(null) }
    var currentDir by remember { mutableStateOf<File?>(null) }
    var showRecycleBin by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(VaultFileCategory.ALL) }
    var sortOption by remember { mutableStateOf(VaultSortOption.DATE) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(false) }

    // Selection Mode State (Long-press)
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectionMode = selectedPaths.isNotEmpty()

    // Dialog / Sheet States
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderInput by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<VaultEntry?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var detailsTarget by remember { mutableStateOf<VaultEntry?>(null) }

    // Move / Copy Destination Sheet
    var showTransferSheet by remember { mutableStateOf(false) }
    var isMoveOperation by remember { mutableStateOf(false) }

    // Secondary PIN Lock Dialogs
    var folderPinSetupTarget by remember { mutableStateOf<File?>(null) }
    var folderPinSetupInput by remember { mutableStateOf("") }
    var lockedFolderPromptTarget by remember { mutableStateOf<VaultEntry?>(null) }
    var lockedFolderPinInput by remember { mutableStateOf("") }
    var lockedFolderPinError by remember { mutableStateOf(false) }

    // Built-in Media Viewer States
    var photoViewerList by remember { mutableStateOf<List<VaultEntry>>(emptyList()) }
    var photoViewerInitialIndex by remember { mutableIntStateOf(0) }
    var showPhotoViewer by remember { mutableStateOf(false) }
    var videoPlayerEntry by remember { mutableStateOf<VaultEntry?>(null) }
    var audioPlayerEntry by remember { mutableStateOf<VaultEntry?>(null) }
    var pdfViewerEntry by remember { mutableStateOf<VaultEntry?>(null) }
    var textViewerEntry by remember { mutableStateOf<VaultEntry?>(null) }

    var localTick by remember { mutableIntStateOf(0) }

    // Build Volume Buckets: Shared Space Volume + Per-App Volumes
    val appBuckets = remember(configs, isPanicModeActive, sharedVolumeQuotaMb, vaultRefreshTick, localTick) {
        if (isPanicModeActive) {
            emptyList()
        } else {
            val sharedDir = viewModel.getSharedVolumeDirectory()
            val sharedFiles = sharedDir.walkTopDown().filter { it.isFile && it.name != ".nomedia" && !it.name.startsWith(".seeded") }.toList()
            val sharedBucket = VaultAppBucket(
                packageName = IsolationRuleManager.SHARED_VOLUME_ID,
                appName = "Shared Space",
                rootDir = sharedDir,
                fileCount = sharedFiles.size,
                totalBytes = sharedFiles.sumOf { it.length() },
                isHookActive = true,
                isSharedVolume = true,
                useSharedSpace = true,
                quotaMb = sharedVolumeQuotaMb
            )
            val perAppBuckets = configs.map { cfg ->
                val dir = viewModel.getAppSandboxDirectory(cfg.packageName)
                val files = dir.walkTopDown().filter { it.isFile && it.name != ".nomedia" && !it.name.startsWith(".seeded") }.toList()
                val diskBytes = files.sumOf { it.length() }
                VaultAppBucket(
                    packageName = cfg.packageName,
                    appName = cfg.appName,
                    rootDir = dir,
                    fileCount = files.size,
                    totalBytes = maxOf(diskBytes, cfg.totalBytesEncrypted),
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

    // Filter buckets by selected scope ("ALL", "shared.vault", or a single app)
    val scopedBuckets = remember(appBuckets, selectedVolumeScope) {
        when (selectedVolumeScope) {
            "ALL" -> appBuckets
            else -> appBuckets.filter { it.packageName == selectedVolumeScope }
        }
    }

    // All files across scoped buckets (used for Gallery view and combined view)
    val allScopedFiles = remember(scopedBuckets, favoritePaths, vaultRefreshTick, localTick) {
        if (isPanicModeActive) emptyList()
        else VaultFileScanner.listAllFilesAcrossBuckets(scopedBuckets, favoritePaths)
    }

    // Consume deep-link from Apps tab if user tapped an app card or Shared Space banner
    LaunchedEffect(selectedAppFromNav, appBuckets) {
        selectedAppFromNav?.let { pkg ->
            val match = appBuckets.find { it.packageName == pkg }
            if (match != null) {
                selectedVolumeScope = pkg
                activeAppBucket = match
                currentDir = match.rootDir
                showRecycleBin = false
                viewModel.selectVaultAppPackage(null)
            }
        }
    }

    // Auto-switch Grid view when filtering Photos or Videos
    LaunchedEffect(selectedCategory) {
        if (selectedCategory == VaultFileCategory.PHOTOS || selectedCategory == VaultFileCategory.VIDEOS) {
            isGridView = true
        }
    }

    // Multi-file picker for importing into current folder or Shared Space Volume
    val importFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val targetFolder = currentDir ?: File(viewModel.getSharedVolumeDirectory(), "Pictures")
            val appName = activeAppBucket?.appName ?: "Shared Space"
            val pkg = activeAppBucket?.packageName ?: IsolationRuleManager.SHARED_VOLUME_ID
            viewModel.importUrisToVault(uris, targetFolder, appName, pkg)
        }
    }

    // Back handler inside Files tab
    BackHandler(enabled = isSelectionMode || showRecycleBin || activeAppBucket != null) {
        when {
            isSelectionMode -> selectedPaths = emptySet()
            showRecycleBin -> showRecycleBin = false
            activeAppBucket != null -> {
                val bucketRoot = activeAppBucket!!.rootDir
                val cur = currentDir
                if (cur != null && cur.canonicalPath != bucketRoot.canonicalPath) {
                    currentDir = cur.parentFile ?: bucketRoot
                } else {
                    activeAppBucket = null
                    currentDir = null
                    searchQuery = ""
                }
            }
        }
    }

    // Entries in current folder
    val currentEntries = remember(activeAppBucket, currentDir, folderPins, favoritePaths, vaultRefreshTick, localTick) {
        val bucket = activeAppBucket
        val dir = currentDir
        if (bucket != null && dir != null && !isPanicModeActive) {
            VaultFileScanner.listEntriesInDirectory(
                directory = dir,
                sourceAppName = bucket.appName,
                sourcePackage = bucket.packageName,
                folderPins = folderPins,
                favorites = favoritePaths
            )
        } else {
            emptyList()
        }
    }

    // Filtered & Sorted Entries
    val filteredEntries = remember(currentEntries, searchQuery, selectedCategory, sortOption) {
        currentEntries.filter { entry ->
            val matchesQuery = searchQuery.isBlank() || entry.cleanName.contains(searchQuery, ignoreCase = true)
            val matchesCat = selectedCategory == VaultFileCategory.ALL ||
                    entry.isDirectory ||
                    entry.category == selectedCategory
            matchesQuery && matchesCat
        }.sortedWith(
            when (sortOption) {
                VaultSortOption.NAME -> compareBy<VaultEntry> { !it.isDirectory }.thenBy { it.cleanName.lowercase() }
                VaultSortOption.DATE -> compareBy<VaultEntry> { !it.isDirectory }.thenByDescending { it.lastModified }
                VaultSortOption.SIZE -> compareBy<VaultEntry> { !it.isDirectory }.thenByDescending { it.sizeBytes }
            }
        )
    }

    fun openEntry(entry: VaultEntry, candidatePhotoPool: List<VaultEntry> = filteredEntries) {
        if (isSelectionMode) {
            selectedPaths = if (selectedPaths.contains(entry.path)) {
                selectedPaths - entry.path
            } else {
                selectedPaths + entry.path
            }
            return
        }

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
                val allPhotos = candidatePhotoPool.filter { !it.isDirectory && it.category == VaultFileCategory.PHOTOS }
                    .ifEmpty { listOf(entry) }
                val idx = allPhotos.indexOfFirst { it.path == entry.path }.coerceAtLeast(0)
                photoViewerList = allPhotos
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
            VaultFileScanner.isTextFile(entry.cleanName) -> {
                textViewerEntry = entry
            }
            else -> {
                detailsTarget = entry
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .testTag("files_screen")
    ) {
        when {
            // 1. RECYCLE BIN VIEW (30 Days Retention)
            showRecycleBin -> {
                val appLookup = appBuckets.associate { it.packageName to it.appName }
                val binItems = remember(vaultRefreshTick, localTick) {
                    if (isPanicModeActive) emptyList()
                    else VaultFileScanner.listRecycleBinItems(viewModel.getRecycleBinDirectory(), appLookup)
                }

                RecycleBinScreenContent(
                    items = binItems,
                    onBack = { showRecycleBin = false },
                    onRestoreItem = { item ->
                        coroutineScope.launch(Dispatchers.IO) {
                            val targetAppDir = viewModel.getAppSandboxDirectory(item.sourcePackage)
                            val restoredFile = File(targetAppDir, item.file.name.substringAfter("__").substringAfter("__"))
                            item.file.copyRecursively(restoredFile, overwrite = true)
                            item.file.deleteRecursively()
                            withContext(Dispatchers.Main) {
                                localTick += 1
                                viewModel.notifyVaultFilesChanged()
                                Toast.makeText(context, "Restored ${item.originalName}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onEmptyBin = {
                        coroutineScope.launch(Dispatchers.IO) {
                            viewModel.getRecycleBinDirectory().listFiles()?.forEach { it.deleteRecursively() }
                            withContext(Dispatchers.Main) {
                                localTick += 1
                                Toast.makeText(context, "Recycle bin emptied", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            // 2. ROOT VIEW: VOLUMES & GALLERY BROWSER (Filter by "All", "Shared", or any single app)
            activeAppBucket == null -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (browserMode == VaultBrowserMode.FILES) "Encrypted Volumes" else "Vault Gallery",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FrauncesFontFamily,
                                    color = colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Per-app private volumes & linked Shared Space",
                                    fontSize = 14.sp,
                                    fontFamily = JakartaFontFamily,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }

                            // Recycle Bin Button
                            Surface(
                                color = colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { showRecycleBin = true }
                                    .testTag("open_recycle_bin_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Recycle Bin",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Bin",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = JakartaFontFamily,
                                        color = colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Mode Switcher (Files Explorer vs Gallery View: Albums / Timeline / Favorites)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            VaultBrowserMode.entries.forEach { mode ->
                                val selected = browserMode == mode
                                FilterChip(
                                    selected = selected,
                                    onClick = { browserMode = mode },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (mode == VaultBrowserMode.FILES) Icons.Rounded.Folder else Icons.Rounded.Collections,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = mode.label,
                                            fontFamily = JakartaFontFamily,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colorScheme.primary.copy(alpha = 0.16f),
                                        selectedLabelColor = colorScheme.primary
                                    ),
                                    modifier = Modifier.testTag("mode_chip_${mode.name.lowercase()}")
                                )
                            }
                        }
                    }

                    // Combined Scope Filter Chips: "All", "Shared", or any single app
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            val scopeOptions = buildList {
                                add("ALL" to "All Volumes")
                                add(IsolationRuleManager.SHARED_VOLUME_ID to "Shared Space")
                                appBuckets.filter { !it.isSharedVolume }.forEach { b ->
                                    add(b.packageName to b.appName)
                                }
                            }
                            scopeOptions.forEach { (scopeKey, label) ->
                                val selected = selectedVolumeScope == scopeKey
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedVolumeScope = scopeKey },
                                    label = {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontFamily = JakartaFontFamily,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colorScheme.primary.copy(alpha = 0.16f),
                                        selectedLabelColor = colorScheme.primary
                                    )
                                )
                            }
                        }
                    }

                    // Storage Overview Card
                    item {
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
                                    .padding(20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Total Vault Storage (${appBuckets.size} volumes)",
                                        fontSize = 13.sp,
                                        fontFamily = JakartaFontFamily,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = formatBytes(totalVaultBytes),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FrauncesFontFamily,
                                        color = colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "AES256_GCM_HKDF_4KB • Per-volume 256-bit keys",
                                        fontSize = 12.sp,
                                        fontFamily = JakartaFontFamily,
                                        color = colorScheme.primary
                                    )
                                }

                                Button(
                                    onClick = { importFilesLauncher.launch("*/*") },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colorScheme.primary,
                                        contentColor = colorScheme.onPrimary
                                    ),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("import_files_root_button")
                                ) {
                                    Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Import", fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily)
                                }
                            }
                        }
                    }

                    if (browserMode == VaultBrowserMode.GALLERY) {
                        // GALLERY MODE: Albums, Timeline, Favorites
                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                GallerySubMode.entries.forEach { sub ->
                                    val sel = gallerySubMode == sub
                                    FilterChip(
                                        selected = sel,
                                        onClick = { gallerySubMode = sub },
                                        label = {
                                            Text(
                                                text = sub.label,
                                                fontSize = 12.sp,
                                                fontFamily = JakartaFontFamily,
                                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }

                        val mediaEntries = allScopedFiles.filter {
                            it.category == VaultFileCategory.PHOTOS || it.category == VaultFileCategory.VIDEOS
                        }

                        when (gallerySubMode) {
                            GallerySubMode.ALBUMS -> {
                                val albums = mediaEntries.groupBy { it.sourcePackage }.mapNotNull { (pkg, list) ->
                                    val first = list.firstOrNull() ?: return@mapNotNull null
                                    GalleryAlbumItem(
                                        id = pkg,
                                        title = first.sourceAppName,
                                        subtitle = "${list.size} media item(s) • ${formatBytes(list.sumOf { it.sizeBytes })}",
                                        coverEntry = first,
                                        entries = list,
                                        isSharedAlbum = pkg == IsolationRuleManager.SHARED_VOLUME_ID
                                    )
                                }
                                if (albums.isEmpty()) {
                                    item {
                                        FriendlyEmptyFolderState(
                                            title = "No media albums yet",
                                            subtitle = "Photos and videos saved by protected apps or placed in Shared Space appear here."
                                        )
                                    }
                                } else {
                                    items(albums, key = { it.id }) { album ->
                                        Card(
                                            shape = RoundedCornerShape(20.dp),
                                            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                                            border = BorderStroke(1.dp, colorScheme.outlineVariant),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(20.dp))
                                                .clickable {
                                                    val bucket = appBuckets.find { it.packageName == album.id }
                                                    if (bucket != null) {
                                                        activeAppBucket = bucket
                                                        currentDir = bucket.rootDir
                                                        selectedCategory = VaultFileCategory.PHOTOS
                                                    }
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val key = remember(album.id) { viewModel.getOrCreateVolumeKey(album.id) }
                                                val thumb = rememberDecryptedThumbnail(album.coverEntry, key)
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier
                                                        .size(64.dp)
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .background(colorScheme.primary.copy(alpha = 0.14f))
                                                ) {
                                                    if (thumb != null) {
                                                        Image(
                                                            bitmap = thumb,
                                                            contentDescription = album.title,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Image,
                                                            contentDescription = null,
                                                            tint = colorScheme.primary,
                                                            modifier = Modifier.size(28.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(16.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = album.title,
                                                        fontSize = 17.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontFamily = JakartaFontFamily,
                                                        color = colorScheme.onSurface
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = album.subtitle,
                                                        fontSize = 13.sp,
                                                        fontFamily = JakartaFontFamily,
                                                        color = colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.Rounded.ChevronRight,
                                                    contentDescription = "Open Album",
                                                    tint = colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            GallerySubMode.TIMELINE, GallerySubMode.FAVORITES -> {
                                val displayedMedia = if (gallerySubMode == GallerySubMode.FAVORITES) {
                                    mediaEntries.filter { favoritePaths.contains(it.path) }
                                } else {
                                    mediaEntries
                                }
                                if (displayedMedia.isEmpty()) {
                                    item {
                                        FriendlyEmptyFolderState(
                                            title = if (gallerySubMode == GallerySubMode.FAVORITES) "No favorites yet" else "Timeline is empty",
                                            subtitle = if (gallerySubMode == GallerySubMode.FAVORITES) {
                                                "Tap the heart icon inside the photo viewer to mark encrypted photos as favorites."
                                            } else {
                                                "Import or capture photos and videos in protected apps to see your timeline."
                                            }
                                        )
                                    }
                                } else {
                                    items(displayedMedia, key = { it.path }) { item ->
                                        val key = remember(item.sourcePackage) { viewModel.getOrCreateVolumeKey(item.sourcePackage) }
                                        VaultListRow(
                                            entry = item,
                                            volumeKey = key,
                                            isSelected = false,
                                            onClick = { openEntry(item, displayedMedia) },
                                            onLongClick = {},
                                            onShowDetails = { detailsTarget = item },
                                            onLockFolderClick = {}
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // FILES MODE: Per-App & Shared Space Volume Cards
                        if (scopedBuckets.isEmpty()) {
                            item {
                                FriendlyEmptyFolderState(
                                    title = "Vault is empty",
                                    subtitle = "Import files or enable apps on the Apps tab to start storing private files."
                                )
                            }
                        } else {
                            items(scopedBuckets, key = { it.packageName }) { bucket ->
                                val quotaLabel = if (bucket.quotaMb > 0) " / ${bucket.quotaMb} MB" else ""
                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (bucket.isSharedVolume) colorScheme.primary.copy(alpha = 0.5f) else colorScheme.outlineVariant
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            activeAppBucket = bucket
                                            currentDir = bucket.rootDir
                                        }
                                        .testTag("vault_app_bucket_${bucket.packageName}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 18.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (bucket.isSharedVolume) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(colorScheme.primary.copy(alpha = 0.16f))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.FolderShared,
                                                    contentDescription = "Shared Space",
                                                    tint = colorScheme.primary,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        } else {
                                            BrandSquircleIcon(name = bucket.appName, size = 46.dp)
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = bucket.appName,
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontFamily = JakartaFontFamily,
                                                    color = colorScheme.onSurface
                                                )
                                                if (!bucket.isSharedVolume && bucket.useSharedSpace) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Surface(
                                                        color = colorScheme.primary.copy(alpha = 0.14f),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "Shared Linked",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontFamily = JakartaFontFamily,
                                                            color = colorScheme.primary,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = "${formatBytes(bucket.totalBytes)}$quotaLabel • ${bucket.fileCount} ${if (bucket.fileCount == 1) "file" else "files"}",
                                                fontSize = 13.sp,
                                                fontFamily = JakartaFontFamily,
                                                color = colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = "Open Volume",
                                            tint = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. INSIDE AN APP OR SHARED SPACE VOLUME / FOLDER VIEW
            else -> {
                val bucket = activeAppBucket!!
                val dir = currentDir ?: bucket.rootDir
                val isRootOfBucket = dir.canonicalPath == bucket.rootDir.canonicalPath
                val isCurrentFolderLockedWithPin = folderPins.containsKey(dir.absolutePath)
                val activeVolumeKey = remember(bucket.packageName) {
                    viewModel.getOrCreateVolumeKey(bucket.packageName)
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Bar (Selection Mode or Folder Navigation Bar)
                    if (isSelectionMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colorScheme.surface)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { selectedPaths = emptySet() }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Exit Selection", tint = colorScheme.onSurface)
                                }
                                Text(
                                    text = "${selectedPaths.size} selected",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = JakartaFontFamily,
                                    color = colorScheme.onSurface
                                )
                            }

                            TextButton(
                                onClick = {
                                    val allPaths = filteredEntries.map { it.path }.toSet()
                                    selectedPaths = if (selectedPaths.size == allPaths.size) emptySet() else allPaths
                                }
                            ) {
                                Icon(Icons.Rounded.SelectAll, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select All", fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (!isRootOfBucket) {
                                        currentDir = dir.parentFile ?: bucket.rootDir
                                    } else {
                                        activeAppBucket = null
                                        currentDir = null
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
                                    text = if (isRootOfBucket) bucket.appName else dir.name,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FrauncesFontFamily,
                                    color = colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isRootOfBucket) {
                                        if (bucket.isSharedVolume) "Shared Space Encrypted Volume" else "Private Encrypted Volume"
                                    } else {
                                        "${bucket.appName} / ${dir.name}"
                                    },
                                    fontSize = 12.sp,
                                    fontFamily = JakartaFontFamily,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }

                            // Secondary Folder Lock Toggle Button
                            IconButton(
                                onClick = {
                                    folderPinSetupTarget = dir
                                    folderPinSetupInput = folderPins[dir.absolutePath] ?: ""
                                }
                            ) {
                                Icon(
                                    imageVector = if (isCurrentFolderLockedWithPin) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                    contentDescription = "Folder PIN Lock",
                                    tint = if (isCurrentFolderLockedWithPin) colorScheme.primary else colorScheme.onSurfaceVariant
                                )
                            }

                            // Grid / List View Switch
                            IconButton(
                                onClick = { isGridView = !isGridView },
                                modifier = Modifier.testTag("toggle_grid_list_button")
                            ) {
                                Icon(
                                    imageVector = if (isGridView) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                                    contentDescription = "Switch View",
                                    tint = colorScheme.onSurfaceVariant
                                )
                            }

                            // Sort Menu
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Sort,
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
                                            text = {
                                                Text(
                                                    text = "Sort by ${opt.label}",
                                                    fontWeight = if (sortOption == opt) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (sortOption == opt) colorScheme.primary else colorScheme.onSurface
                                                )
                                            },
                                            onClick = {
                                                sortOption = opt
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Create Folder Button
                            IconButton(
                                onClick = {
                                    newFolderInput = ""
                                    showNewFolderDialog = true
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CreateNewFolder,
                                    contentDescription = "Create Folder",
                                    tint = colorScheme.primary
                                )
                            }
                        }
                    }

                    // Search Box & Filter Chips
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text("Search files in ${bucket.appName}...", fontSize = 14.sp, fontFamily = JakartaFontFamily)
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Search, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colorScheme.primary,
                                unfocusedBorderColor = colorScheme.outlineVariant,
                                focusedContainerColor = colorScheme.surfaceVariant,
                                unfocusedContainerColor = colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

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
                                            fontFamily = JakartaFontFamily,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colorScheme.primary.copy(alpha = 0.16f),
                                        selectedLabelColor = colorScheme.primary
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // File & Folder Content Area
                    Box(modifier = Modifier.weight(1f)) {
                        if (filteredEntries.isEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp)
                            ) {
                                FriendlyEmptyFolderState(
                                    title = "No files here yet",
                                    subtitle = "Tap the + button below to import photos, videos, or documents into this volume."
                                )
                            }
                        } else if (isGridView) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredEntries, key = { it.path }) { entry ->
                                    val isSelected = selectedPaths.contains(entry.path)
                                    VaultGridCell(
                                        entry = entry,
                                        volumeKey = activeVolumeKey,
                                        isSelected = isSelected,
                                        onClick = { openEntry(entry) },
                                        onLongClick = {
                                            selectedPaths = if (isSelected) selectedPaths - entry.path else selectedPaths + entry.path
                                        }
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredEntries, key = { it.path }) { entry ->
                                    val isSelected = selectedPaths.contains(entry.path)
                                    VaultListRow(
                                        entry = entry,
                                        volumeKey = activeVolumeKey,
                                        isSelected = isSelected,
                                        onClick = { openEntry(entry) },
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
                    }

                    // Multi-Select Bottom Action Toolbar
                    AnimatedVisibility(visible = isSelectionMode) {
                        val selectedItems = currentEntries.filter { selectedPaths.contains(it.path) }
                        Surface(
                            color = colorScheme.surface,
                            tonalElevation = 4.dp,
                            border = BorderStroke(1.dp, colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SelectionActionButton(
                                    icon = Icons.Rounded.FileDownload,
                                    label = "Export",
                                    onClick = {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val exportDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "Vault_Exported")
                                            exportDir.mkdirs()
                                            var count = 0
                                            for (item in selectedItems) {
                                                if (!item.isDirectory) {
                                                    try {
                                                        val dec = AesVaultCipher.decryptToMemory(item.file, activeVolumeKey)
                                                        File(exportDir, item.cleanName).writeBytes(dec)
                                                        count++
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                            withContext(Dispatchers.Main) {
                                                selectedPaths = emptySet()
                                                Toast.makeText(
                                                    context,
                                                    "Exported $count file(s) to Vault_Exported",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                )

                                SelectionActionButton(
                                    icon = Icons.Rounded.DriveFileMove,
                                    label = "Move",
                                    onClick = {
                                        isMoveOperation = true
                                        showTransferSheet = true
                                    }
                                )

                                SelectionActionButton(
                                    icon = Icons.Rounded.ContentCopy,
                                    label = "Copy",
                                    onClick = {
                                        isMoveOperation = false
                                        showTransferSheet = true
                                    }
                                )

                                if (selectedItems.size == 1) {
                                    SelectionActionButton(
                                        icon = Icons.Rounded.Edit,
                                        label = "Rename",
                                        onClick = {
                                            val first = selectedItems.first()
                                            renameTarget = first
                                            renameInput = first.cleanName
                                        }
                                    )
                                }

                                SelectionActionButton(
                                    icon = Icons.Rounded.Share,
                                    label = "Share",
                                    onClick = {
                                        val firstFile = selectedItems.firstOrNull { !it.isDirectory }
                                        if (firstFile != null) {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                try {
                                                    val tempShare = AesVaultCipher.createEphemeralShareFile(
                                                        context,
                                                        firstFile.file,
                                                        activeVolumeKey
                                                    )
                                                    val uri = FileProvider.getUriForFile(
                                                        context,
                                                        "com.aistudio.isovault.xrvf.fileprovider",
                                                        tempShare
                                                    )
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "*/*"
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    withContext(Dispatchers.Main) {
                                                        selectedPaths = emptySet()
                                                        context.startActivity(Intent.createChooser(shareIntent, "Share from Vault"))
                                                    }
                                                } catch (e: Exception) {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )

                                SelectionActionButton(
                                    icon = Icons.Rounded.Delete,
                                    label = "Delete",
                                    tint = colorScheme.error,
                                    onClick = {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val binDir = viewModel.getRecycleBinDirectory()
                                            var moved = 0
                                            for (item in selectedItems) {
                                                if (VaultFileScanner.moveToRecycleBin(item, binDir)) {
                                                    moved++
                                                }
                                            }
                                            withContext(Dispatchers.Main) {
                                                selectedPaths = emptySet()
                                                localTick += 1
                                                viewModel.notifyVaultFilesChanged()
                                                Toast.makeText(context, "Moved $moved item(s) to Recycle Bin (30 days)", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Floating "+" Button to Import Files into Current Volume Folder
                if (!isSelectionMode) {
                    FloatingActionButton(
                        onClick = { importFilesLauncher.launch("*/*") },
                        shape = RoundedCornerShape(20.dp),
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp)
                            .size(60.dp)
                            .testTag("import_into_folder_fab")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Import Files", modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }

    // Create Folder Dialog
    if (showNewFolderDialog && currentDir != null) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = colorScheme.surface,
            title = {
                Text("New Folder", fontFamily = FrauncesFontFamily, fontWeight = FontWeight.SemiBold)
            },
            text = {
                OutlinedTextField(
                    value = newFolderInput,
                    onValueChange = { newFolderInput = it },
                    placeholder = { Text("Folder name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderInput.isNotBlank()) {
                            File(currentDir, newFolderInput.trim()).mkdirs()
                            showNewFolderDialog = false
                            localTick += 1
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
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
                    text = if (entry.isDirectory) "Folder" else formatBytes(entry.sizeBytes),
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
                    color = colorScheme.onSurfaceVariant
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
            .height(460.dp)
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
                            BrandSquircleIcon(name = bucket.appName, size = 34.dp)
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
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
