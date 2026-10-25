package com.example.isolation.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.mount.MountManager
import com.example.isolation.storage.FileManagerHelper
import com.example.isolation.storage.HexLine
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentLavender
import com.example.ui.theme.AccentMint
import com.example.ui.theme.AccentSky
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkBorderSubtle
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSubtle
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class StorageScope(val label: String) {
    VAULT_STORAGE("Vault (/.IsoVault)"),
    PHONE_STORAGE("Phone (/sdcard)"),
    ROOT_STORAGE("Root (/data)")
}

enum class FileSortOrder(val label: String) {
    NAME("Name (A-Z)"),
    DATE("Newest First"),
    SIZE("Largest First")
}

data class MtFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val isEncrypted: Boolean,
    val permissions: String
)

data class ClipboardEntry(
    val sourceItem: MtFileItem,
    val isCutOperation: Boolean
)

enum class MtPanelId {
    PANEL_A,
    PANEL_B
}

@Composable
fun MtFileExplorerScreen(
    viewModel: IsoVaultViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val explorerTargetDir by viewModel.explorerTargetDir.collectAsState()
    val isRootGranted by viewModel.isRootGranted.collectAsState()

    var hasStoragePermission by remember { mutableStateOf(FileManagerHelper.hasFullStorageAccess()) }
    var hidePermissionBanner by remember { mutableStateOf(false) }

    // Active panel selector: Panel A (usually Vault) and Panel B (usually Phone / SDCard)
    var activePanel by remember { mutableStateOf(MtPanelId.PANEL_A) }
    var isDualPaneSplit by remember { mutableStateOf(false) }

    // Panel A State
    var scopeA by remember { mutableStateOf(StorageScope.VAULT_STORAGE) }
    var pathA by remember { mutableStateOf(MountManager.PERSISTENT_VAULT_ROOT) }
    var fileListA by remember { mutableStateOf<List<MtFileItem>>(emptyList()) }
    var selectedA by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchQueryA by remember { mutableStateOf("") }

    // Panel B State
    var scopeB by remember { mutableStateOf(StorageScope.PHONE_STORAGE) }
    var pathB by remember { mutableStateOf("/storage/emulated/0") }
    var fileListB by remember { mutableStateOf<List<MtFileItem>>(emptyList()) }
    var selectedB by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchQueryB by remember { mutableStateOf("") }

    var showSearchBar by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(FileSortOrder.NAME) }
    var showSortMenu by remember { mutableStateOf(false) }
    var clipboardEntry by remember { mutableStateOf<ClipboardEntry?>(null) }

    // Dialog States
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var newFileContent by remember { mutableStateOf("") }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<MtFileItem?>(null) }
    var renameNewName by remember { mutableStateOf("") }

    // Text Editor Dialog
    var showEditorDialog by remember { mutableStateOf(false) }
    var editorTargetFile by remember { mutableStateOf<MtFileItem?>(null) }
    var editorContent by remember { mutableStateOf("") }

    // Hex Inspector Dialog
    var showHexDialog by remember { mutableStateOf(false) }
    var hexTargetFile by remember { mutableStateOf<File?>(null) }
    var hexLines by remember { mutableStateOf<List<HexLine>>(emptyList()) }

    // ZIP Compress Dialog
    var showZipDialog by remember { mutableStateOf(false) }

    // Properties Dialog
    var propertiesTarget by remember { mutableStateOf<MtFileItem?>(null) }
    var propertiesSha256 by remember { mutableStateOf("Calculating...") }

    // Image Preview Dialog
    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var previewImageTitle by remember { mutableStateOf("") }

    // Import options dialog (Choose normal vs AES-256 encrypted container)
    var pendingImportUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showImportChoiceDialog by remember { mutableStateOf(false) }

    // SAF Save-As Document Picker for export
    var pendingExportFile by remember { mutableStateOf<File?>(null) }
    val exportDocumentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null && pendingExportFile != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val res = FileManagerHelper.exportToDocumentUri(context, pendingExportFile!!, uri, decryptIfEncrypted = true)
                withContext(Dispatchers.Main) {
                    if (res.isSuccess) {
                        Toast.makeText(context, "Exported successfully via SAF!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "SAF Export failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                    pendingExportFile = null
                }
            }
        }
    }

    val currentPath = if (activePanel == MtPanelId.PANEL_A) pathA else pathB
    val currentScope = if (activePanel == MtPanelId.PANEL_A) scopeA else scopeB
    val currentSelected = if (activePanel == MtPanelId.PANEL_A) selectedA else selectedB

    fun updateCurrentPath(newPath: String) {
        if (activePanel == MtPanelId.PANEL_A) {
            pathA = newPath
        } else {
            pathB = newPath
        }
    }

    fun updateCurrentScope(newScope: StorageScope) {
        if (activePanel == MtPanelId.PANEL_A) {
            scopeA = newScope
        } else {
            scopeB = newScope
        }
    }

    fun clearCurrentSelection() {
        if (activePanel == MtPanelId.PANEL_A) selectedA = emptySet() else selectedB = emptySet()
    }

    // Refresh directory contents
    fun loadDirectory(path: String, scope: StorageScope, onResult: (List<MtFileItem>) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            val dir = File(path)
            if (scope == StorageScope.VAULT_STORAGE && !dir.exists()) {
                dir.mkdirs()
            }

            val items = mutableListOf<MtFileItem>()
            val standardList = dir.listFiles()

            if (standardList != null) {
                for (f in standardList) {
                    val perms = buildString {
                        append(if (f.isDirectory) "d" else "-")
                        append(if (f.canRead()) "r" else "-")
                        append(if (f.canWrite()) "w" else "-")
                        append(if (f.canExecute()) "x" else "-")
                    }
                    items.add(
                        MtFileItem(
                            name = f.name,
                            path = f.absolutePath,
                            isDirectory = f.isDirectory,
                            sizeBytes = if (f.isDirectory) (f.listFiles()?.size?.toLong() ?: 0L) else f.length(),
                            lastModified = f.lastModified(),
                            isEncrypted = f.name.endsWith(".isovault", ignoreCase = true),
                            permissions = perms
                        )
                    )
                }
            } else if (isRootGranted) {
                val entries = MountManager.listFilesWithRootFallback(path)
                for (entry in entries) {
                    items.add(
                        MtFileItem(
                            name = entry.name,
                            path = entry.absolutePath,
                            isDirectory = entry.isDirectory,
                            sizeBytes = entry.length(),
                            lastModified = entry.lastModified(),
                            isEncrypted = entry.name.endsWith(".isovault", ignoreCase = true),
                            permissions = "rwxr-x--- (su)"
                        )
                    )
                }
            }

            val sorted = when (sortOrder) {
                FileSortOrder.NAME -> items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                FileSortOrder.DATE -> items.sortedWith(compareBy<MtFileItem> { !it.isDirectory }.thenByDescending { it.lastModified })
                FileSortOrder.SIZE -> items.sortedWith(compareBy<MtFileItem> { !it.isDirectory }.thenByDescending { it.sizeBytes })
            }

            withContext(Dispatchers.Main) {
                onResult(sorted)
            }
        }
    }

    fun refreshAll() {
        hasStoragePermission = FileManagerHelper.hasFullStorageAccess()
        loadDirectory(pathA, scopeA) { fileListA = it }
        loadDirectory(pathB, scopeB) { fileListB = it }
    }

    LaunchedEffect(pathA, scopeA, sortOrder, isRootGranted) {
        loadDirectory(pathA, scopeA) { fileListA = it }
    }

    LaunchedEffect(pathB, scopeB, sortOrder, isRootGranted) {
        loadDirectory(pathB, scopeB) { fileListB = it }
    }

    // Handle deep-link targets from Dashboard or Apps screen
    LaunchedEffect(explorerTargetDir) {
        explorerTargetDir?.let { target ->
            File(target).mkdirs()
            activePanel = MtPanelId.PANEL_A
            scopeA = StorageScope.VAULT_STORAGE
            pathA = target
            viewModel.setExplorerTargetDir(null)
        }
    }

    // System File Picker for importing external files
    val importFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            pendingImportUris = uris
            showImportChoiceDialog = true
        }
    }

    fun executeImport(encryptWithAes: Boolean) {
        val uris = pendingImportUris
        showImportChoiceDialog = false
        pendingImportUris = emptyList()

        if (uris.isEmpty()) return

        coroutineScope.launch(Dispatchers.IO) {
            val targetDir = File(currentPath)
            targetDir.mkdirs()
            var importedCount = 0
            val importedNames = mutableListOf<String>()

            for (uri in uris) {
                val res = FileManagerHelper.importFromUri(context, uri, targetDir, encryptWithAes)
                if (res.isSuccess) {
                    val file = res.getOrThrow()
                    importedCount++
                    importedNames.add(file.name)
                }
            }

            withContext(Dispatchers.Main) {
                if (importedCount > 0) {
                    val sample = importedNames.take(2).joinToString(", ")
                    val extra = if (importedNames.size > 2) " and ${importedNames.size - 2} more" else ""
                    Toast.makeText(
                        context,
                        "Imported $importedCount file(s) into ${targetDir.name}: $sample$extra",
                        Toast.LENGTH_LONG
                    ).show()
                    refreshAll()
                } else {
                    Toast.makeText(context, "Import failed: could not read selected file(s)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val currentFiles = if (activePanel == MtPanelId.PANEL_A) fileListA else fileListB
    val currentSearch = if (activePanel == MtPanelId.PANEL_A) searchQueryA else searchQueryB
    val displayedFiles = currentFiles.filter {
        currentSearch.isBlank() || it.name.contains(currentSearch, ignoreCase = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("mt_file_explorer_screen")
    ) {
        // Storage Permission Alert Banner (if full storage access isn't yet granted on Android 11+)
        if (!hasStoragePermission && !hidePermissionBanner) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AccentAmber.copy(alpha = 0.14f))
                    .border(1.dp, AccentAmber.copy(alpha = 0.35f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = AccentAmber, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Full Storage Access recommended for /sdcard MT Manager operations.",
                        color = TextWhite,
                        fontSize = 11.sp,
                        fontFamily = JakartaFontFamily
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            FileManagerHelper.launchManageStorageSettings(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Grant", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { hidePermissionBanner = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // Top App Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurfaceCard)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "MT Manager Suite",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JakartaFontFamily,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isRootGranted) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentMint.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ROOT",
                                color = AccentMint,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentSky.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "VAULT",
                                color = AccentSky,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Split/Dual Pane View Toggle
                    IconButton(
                        onClick = { isDualPaneSplit = !isDualPaneSplit },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isDualPaneSplit) AccentSky.copy(alpha = 0.2f) else DarkSurfaceElevated)
                    ) {
                        Icon(
                            imageVector = if (isDualPaneSplit) Icons.Default.ViewSidebar else Icons.Default.ViewAgenda,
                            contentDescription = "Toggle Dual Pane",
                            tint = if (isDualPaneSplit) AccentSky else TextMuted,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Search Toggle
                    IconButton(
                        onClick = {
                            showSearchBar = !showSearchBar
                            if (!showSearchBar) {
                                searchQueryA = ""
                                searchQueryB = ""
                            }
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (showSearchBar) AccentSky.copy(alpha = 0.2f) else DarkSurfaceElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (showSearchBar) AccentSky else TextMuted,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Sort Menu
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceElevated)
                        ) {
                            Icon(Icons.Default.Sort, null, tint = TextMuted, modifier = Modifier.size(17.dp))
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.background(DarkSurfaceElevated)
                        ) {
                            FileSortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = order.label,
                                            color = if (sortOrder == order) AccentSky else TextWhite,
                                            fontWeight = if (sortOrder == order) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        sortOrder = order
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Refresh
                    IconButton(
                        onClick = { refreshAll() },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceElevated)
                    ) {
                        Icon(Icons.Default.Refresh, null, tint = AccentSky, modifier = Modifier.size(17.dp))
                    }
                }
            }

            AnimatedVisibility(visible = showSearchBar) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = if (activePanel == MtPanelId.PANEL_A) searchQueryA else searchQueryB,
                        onValueChange = {
                            if (activePanel == MtPanelId.PANEL_A) searchQueryA = it else searchQueryB = it
                        },
                        placeholder = { Text("Filter current directory...", fontSize = 12.sp, color = TextMuted) },
                        singleLine = true,
                        trailingIcon = {
                            if (currentSearch.isNotEmpty()) {
                                IconButton(onClick = {
                                    if (activePanel == MtPanelId.PANEL_A) searchQueryA = "" else searchQueryB = ""
                                }) {
                                    Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(15.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentSky,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // MT Manager Dual Panel Switcher Tabs (Panel A vs Panel B)
            TabRow(
                selectedTabIndex = if (activePanel == MtPanelId.PANEL_A) 0 else 1,
                containerColor = DarkSurfaceElevated,
                contentColor = TextWhite,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[if (activePanel == MtPanelId.PANEL_A) 0 else 1]),
                        color = AccentSky,
                        height = 3.dp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = activePanel == MtPanelId.PANEL_A,
                    onClick = { activePanel = MtPanelId.PANEL_A },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = if (activePanel == MtPanelId.PANEL_A) AccentSky else TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Panel 1 (${File(pathA).name.ifEmpty { "Vault" }})",
                                fontSize = 12.sp,
                                fontWeight = if (activePanel == MtPanelId.PANEL_A) FontWeight.Bold else FontWeight.Normal,
                                color = if (activePanel == MtPanelId.PANEL_A) AccentSky else TextMuted
                            )
                        }
                    }
                )

                Tab(
                    selected = activePanel == MtPanelId.PANEL_B,
                    onClick = { activePanel = MtPanelId.PANEL_B },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = if (activePanel == MtPanelId.PANEL_B) AccentSky else TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Panel 2 (${File(pathB).name.ifEmpty { "Phone" }})",
                                fontSize = 12.sp,
                                fontWeight = if (activePanel == MtPanelId.PANEL_B) FontWeight.Bold else FontWeight.Normal,
                                color = if (activePanel == MtPanelId.PANEL_B) AccentSky else TextMuted
                            )
                        }
                    }
                )
            }
        }

        // Quick Jump Bookmarks Bar
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurfaceElevated)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                BookmarkChip("🛡️ Vault", MountManager.PERSISTENT_VAULT_ROOT, StorageScope.VAULT_STORAGE) { p, s ->
                    updateCurrentPath(p); updateCurrentScope(s)
                }
            }
            item {
                BookmarkChip("📱 /sdcard", "/storage/emulated/0", StorageScope.PHONE_STORAGE) { p, s ->
                    updateCurrentPath(p); updateCurrentScope(s)
                }
            }
            item {
                BookmarkChip("📥 Download", "/storage/emulated/0/Download", StorageScope.PHONE_STORAGE) { p, s ->
                    updateCurrentPath(p); updateCurrentScope(s)
                }
            }
            item {
                BookmarkChip("📷 DCIM", "/storage/emulated/0/DCIM", StorageScope.PHONE_STORAGE) { p, s ->
                    updateCurrentPath(p); updateCurrentScope(s)
                }
            }
            item {
                BookmarkChip("📂 Android/data", "/storage/emulated/0/Android/data", StorageScope.PHONE_STORAGE) { p, s ->
                    updateCurrentPath(p); updateCurrentScope(s)
                }
            }
            item {
                BookmarkChip("⚡ /data", "/data/data", StorageScope.ROOT_STORAGE) { p, s ->
                    if (!isRootGranted) viewModel.requestRootAccess()
                    updateCurrentPath(p); updateCurrentScope(s)
                }
            }
        }

        // Path Breadcrumb & Quick Actions Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val canGoUp = currentPath != "/" && currentPath != "/storage/emulated/0"
            IconButton(
                onClick = {
                    val parent = File(currentPath).parentFile?.absolutePath
                    if (parent != null) updateCurrentPath(parent)
                },
                enabled = canGoUp,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Up",
                    tint = if (canGoUp) AccentSky else TextSubtle,
                    modifier = Modifier.size(17.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = currentPath,
                color = TextWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
            )

            // Equalize / Sync with opposite panel
            IconButton(
                onClick = {
                    val dest = if (activePanel == MtPanelId.PANEL_A) pathA else pathB
                    if (activePanel == MtPanelId.PANEL_A) pathB = dest else pathA = dest
                    Toast.makeText(context, "Matched opposite panel path", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                    contentDescription = "Match Panels",
                    tint = AccentLavender,
                    modifier = Modifier.size(17.dp)
                )
            }

            // New File
            IconButton(
                onClick = { showNewFileDialog = true },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(Icons.Default.NoteAdd, contentDescription = "New File", tint = AccentSky, modifier = Modifier.size(17.dp))
            }

            // New Folder
            IconButton(
                onClick = { showNewFolderDialog = true },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", tint = AccentMint, modifier = Modifier.size(17.dp))
            }
        }

        // Multi-Select Batch Actions Floating Ribbon
        AnimatedVisibility(visible = currentSelected.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AccentSky.copy(alpha = 0.2f))
                    .border(1.dp, AccentSky.copy(alpha = 0.45f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${currentSelected.size} selected",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val allPaths = displayedFiles.map { it.path }.toSet()
                            if (activePanel == MtPanelId.PANEL_A) selectedA = allPaths else selectedB = allPaths
                        },
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Select All", color = AccentSky, fontSize = 11.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Copy to Opposite Panel
                    IconButton(
                        onClick = {
                            val destDir = if (activePanel == MtPanelId.PANEL_A) pathB else pathA
                            coroutineScope.launch(Dispatchers.IO) {
                                var count = 0
                                for (p in currentSelected) {
                                    val src = File(p)
                                    val dst = File(destDir, src.name)
                                    try {
                                        if (src.isDirectory) src.copyRecursively(dst, overwrite = true)
                                        else src.copyTo(dst, overwrite = true)
                                        count++
                                    } catch (_: Exception) {}
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Copied $count item(s) to opposite panel", Toast.LENGTH_SHORT).show()
                                    clearCurrentSelection()
                                    refreshAll()
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy to Other Panel", tint = Color.White, modifier = Modifier.size(16.dp))
                    }

                    // Move to Opposite Panel
                    IconButton(
                        onClick = {
                            val destDir = if (activePanel == MtPanelId.PANEL_A) pathB else pathA
                            coroutineScope.launch(Dispatchers.IO) {
                                var count = 0
                                for (p in currentSelected) {
                                    val src = File(p)
                                    val dst = File(destDir, src.name)
                                    try {
                                        if (src.isDirectory) {
                                            src.copyRecursively(dst, overwrite = true)
                                            src.deleteRecursively()
                                        } else {
                                            src.copyTo(dst, overwrite = true)
                                            src.delete()
                                        }
                                        count++
                                    } catch (_: Exception) {}
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Moved $count item(s) to opposite panel", Toast.LENGTH_SHORT).show()
                                    clearCurrentSelection()
                                    refreshAll()
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCut, contentDescription = "Move to Other Panel", tint = AccentAmber, modifier = Modifier.size(16.dp))
                    }

                    // Batch Compress to ZIP
                    IconButton(
                        onClick = { showZipDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Archive, contentDescription = "Compress ZIP", tint = AccentMint, modifier = Modifier.size(16.dp))
                    }

                    // Batch Share
                    IconButton(
                        onClick = {
                            val files = currentSelected.map { File(it) }
                            FileManagerHelper.batchShareFiles(context, files, decryptIfEncrypted = true)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Batch Share", tint = AccentSky, modifier = Modifier.size(16.dp))
                    }

                    // Batch Delete
                    IconButton(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                var deleted = 0
                                for (p in currentSelected) {
                                    val f = File(p)
                                    if (f.deleteRecursively() || MountManager.deleteViaRoot(p).isSuccess) {
                                        deleted++
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Deleted $deleted item(s)", Toast.LENGTH_SHORT).show()
                                    clearCurrentSelection()
                                    refreshAll()
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Batch Delete", tint = AccentCoral, modifier = Modifier.size(16.dp))
                    }

                    // Close Selection Mode
                    IconButton(
                        onClick = { clearCurrentSelection() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Deselect", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Clipboard Floating Banner (Single Copy/Cut)
        AnimatedVisibility(visible = clipboardEntry != null) {
            clipboardEntry?.let { clip ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentSky.copy(alpha = 0.16f))
                        .border(1.dp, AccentSky.copy(alpha = 0.4f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (clip.isCutOperation) "Moving: ${clip.sourceItem.name}" else "Copied: ${clip.sourceItem.name}",
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Target folder: ${File(currentPath).name}",
                            color = AccentSky,
                            fontSize = 10.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val src = File(clip.sourceItem.path)
                                    val dst = File(currentPath, clip.sourceItem.name)
                                    val ok = try {
                                        if (src.canRead() && File(currentPath).canWrite()) {
                                            if (src.isDirectory) src.copyRecursively(dst, overwrite = true)
                                            else src.copyTo(dst, overwrite = true)
                                            if (clip.isCutOperation) src.deleteRecursively()
                                            true
                                        } else {
                                            val copied = MountManager.copyViaRoot(src.absolutePath, dst.absolutePath).isSuccess
                                            if (copied && clip.isCutOperation) MountManager.deleteViaRoot(src.absolutePath)
                                            copied
                                        }
                                    } catch (_: Exception) {
                                        false
                                    }

                                    withContext(Dispatchers.Main) {
                                        if (ok) {
                                            Toast.makeText(context, if (clip.isCutOperation) "Moved" else "Pasted", Toast.LENGTH_SHORT).show()
                                            clipboardEntry = null
                                            refreshAll()
                                        } else {
                                            Toast.makeText(context, "Paste operation failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentSky),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, null, tint = Color.White, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste Here", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        IconButton(onClick = { clipboardEntry = null }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Primary Import & Export Action Bar (Key User Focus Area)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Import File Button (Preserves exact filename and offers AES encryption!)
            Button(
                onClick = { importFilePicker.launch("*/*") },
                colors = ButtonDefaults.buttonColors(containerColor = AccentSky),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .testTag("import_to_vault_button")
            ) {
                Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import Files", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            // Export Current Folder to Public Downloads
            Button(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val cur = File(currentPath)
                            val targetDir = File(
                                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                                "IsoVault_Exported/${cur.name}"
                            )
                            targetDir.mkdirs()

                            if (cur.canRead()) {
                                cur.copyRecursively(targetDir, overwrite = true)
                            } else {
                                MountManager.copyViaRoot(cur.absolutePath, targetDir.absolutePath)
                            }

                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Exported '${cur.name}' to Download/IsoVault_Exported/${cur.name}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                    .testTag("export_to_phone_button")
            ) {
                Icon(Icons.Default.Upload, null, tint = AccentMint, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextWhite)
            }
        }

        // File Directory Listing
        if (displayedFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Folder, null, tint = TextSubtle, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (currentSearch.isNotBlank()) "No files match search" else "Folder is empty",
                        color = TextMuted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = JakartaFontFamily
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentPath,
                        color = TextSubtle,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayedFiles, key = { it.path }) { item ->
                    val isSelected = currentSelected.contains(item.path)
                    MtFileItemRow(
                        item = item,
                        isSelected = isSelected,
                        onToggleSelect = {
                            val updated = currentSelected.toMutableSet()
                            if (isSelected) updated.remove(item.path) else updated.add(item.path)
                            if (activePanel == MtPanelId.PANEL_A) selectedA = updated else selectedB = updated
                        },
                        onClick = {
                            if (currentSelected.isNotEmpty()) {
                                val updated = currentSelected.toMutableSet()
                                if (isSelected) updated.remove(item.path) else updated.add(item.path)
                                if (activePanel == MtPanelId.PANEL_A) selectedA = updated else selectedB = updated
                            } else if (item.isDirectory) {
                                updateCurrentPath(item.path)
                            } else {
                                handleOpenFile(
                                    item = item,
                                    context = context,
                                    coroutineScope = coroutineScope,
                                    onShowImage = { title, bmp ->
                                        previewImageTitle = title
                                        previewBitmap = bmp
                                    },
                                    onShowEditor = { target, text ->
                                        editorTargetFile = target
                                        editorContent = text
                                        showEditorDialog = true
                                    },
                                    onExtractZip = { zipFile ->
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val targetDir = File(zipFile.parentFile, zipFile.nameWithoutExtension)
                                            val res = FileManagerHelper.extractZip(zipFile, targetDir)
                                            withContext(Dispatchers.Main) {
                                                if (res.isSuccess) {
                                                    Toast.makeText(context, "Extracted ${res.getOrNull()} files into ${targetDir.name}", Toast.LENGTH_LONG).show()
                                                    refreshAll()
                                                } else {
                                                    Toast.makeText(context, "Extract error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        },
                        onAction = { action ->
                            when (action) {
                                "SELECT" -> {
                                    val updated = currentSelected.toMutableSet()
                                    updated.add(item.path)
                                    if (activePanel == MtPanelId.PANEL_A) selectedA = updated else selectedB = updated
                                }
                                "COPY" -> {
                                    clipboardEntry = ClipboardEntry(item, isCutOperation = false)
                                }
                                "CUT" -> {
                                    clipboardEntry = ClipboardEntry(item, isCutOperation = true)
                                }
                                "COPY_TO_OTHER_PANEL" -> {
                                    val destDir = if (activePanel == MtPanelId.PANEL_A) pathB else pathA
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val src = File(item.path)
                                        val dst = File(destDir, src.name)
                                        try {
                                            if (src.isDirectory) src.copyRecursively(dst, overwrite = true)
                                            else src.copyTo(dst, overwrite = true)
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Copied to opposite panel (${File(destDir).name})", Toast.LENGTH_SHORT).show()
                                                refreshAll()
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Copy failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                "MOVE_TO_OTHER_PANEL" -> {
                                    val destDir = if (activePanel == MtPanelId.PANEL_A) pathB else pathA
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val src = File(item.path)
                                        val dst = File(destDir, src.name)
                                        try {
                                            if (src.isDirectory) {
                                                src.copyRecursively(dst, overwrite = true)
                                                src.deleteRecursively()
                                            } else {
                                                src.copyTo(dst, overwrite = true)
                                                src.delete()
                                            }
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Moved to opposite panel (${File(destDir).name})", Toast.LENGTH_SHORT).show()
                                                refreshAll()
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Move failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                "EXPORT_DOWNLOADS" -> {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val res = FileManagerHelper.exportToPublicDownload(context, File(item.path), decryptIfEncrypted = true)
                                        withContext(Dispatchers.Main) {
                                            if (res.isSuccess) {
                                                Toast.makeText(context, "Exported: ${res.getOrNull()}", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Export failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                "EXPORT_SAVE_AS" -> {
                                    pendingExportFile = File(item.path)
                                    val defaultName = item.name.removeSuffix(".isovault")
                                    exportDocumentPicker.launch(defaultName)
                                }
                                "SHARE_WITH_APP" -> {
                                    FileManagerHelper.shareFileWithExternalApp(context, File(item.path), decryptIfEncrypted = true)
                                }
                                "HEX_VIEWER" -> {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val file = File(item.path)
                                        val dump = FileManagerHelper.generateHexDump(file, 4096)
                                        withContext(Dispatchers.Main) {
                                            hexTargetFile = file
                                            hexLines = dump
                                            showHexDialog = true
                                        }
                                    }
                                }
                                "EXTRACT_ZIP" -> {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val zipFile = File(item.path)
                                        val targetDir = File(zipFile.parentFile, zipFile.nameWithoutExtension)
                                        val res = FileManagerHelper.extractZip(zipFile, targetDir)
                                        withContext(Dispatchers.Main) {
                                            if (res.isSuccess) {
                                                Toast.makeText(context, "Extracted ${res.getOrNull()} files into ${targetDir.name}", Toast.LENGTH_LONG).show()
                                                refreshAll()
                                            } else {
                                                Toast.makeText(context, "Extract error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                "ENCRYPT_DECRYPT" -> {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            val src = File(item.path)
                                            if (item.isEncrypted) {
                                                val dec = AesVaultCipher.decrypt(src.readBytes())
                                                val out = File(src.parentFile, item.name.removeSuffix(".isovault"))
                                                out.writeBytes(dec)
                                                src.delete()
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Decrypted: ${out.name}", Toast.LENGTH_SHORT).show()
                                                    refreshAll()
                                                }
                                            } else {
                                                val enc = AesVaultCipher.encrypt(src.readBytes())
                                                val out = File(src.parentFile, "${item.name}.isovault")
                                                out.writeBytes(enc)
                                                src.delete()
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "AES-256 Encrypted: ${out.name}", Toast.LENGTH_SHORT).show()
                                                    refreshAll()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Cipher error: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                "RENAME" -> {
                                    renameTarget = item
                                    renameNewName = item.name
                                    showRenameDialog = true
                                }
                                "PROPERTIES" -> {
                                    propertiesTarget = item
                                    propertiesSha256 = "Calculating..."
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val hash = try {
                                            val f = File(item.path)
                                            if (!f.isDirectory && f.canRead() && f.length() < 25 * 1024 * 1024) {
                                                val md = MessageDigest.getInstance("SHA-256")
                                                val digest = md.digest(f.readBytes())
                                                digest.joinToString("") { "%02x".format(it) }
                                            } else {
                                                "Directory / Not Readable"
                                            }
                                        } catch (_: Exception) {
                                            "N/A"
                                        }
                                        withContext(Dispatchers.Main) {
                                            propertiesSha256 = hash
                                        }
                                    }
                                }
                                "DELETE" -> {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val f = File(item.path)
                                        val deleted = f.deleteRecursively() || MountManager.deleteViaRoot(item.path).isSuccess
                                        withContext(Dispatchers.Main) {
                                            if (deleted) refreshAll()
                                            else Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }

        // Import Options Dialog (Normal vs AES-256 Encrypted container)
        if (showImportChoiceDialog) {
            AlertDialog(
                onDismissRequest = { showImportChoiceDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Download, null, tint = AccentSky, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import Selected Files", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "${pendingImportUris.size} file(s) selected to import into:\n$currentPath",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontFamily = JakartaFontFamily
                        )
                        Text(
                            text = "Original filenames and file structures are strictly preserved.",
                            color = AccentMint,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { executeImport(encryptWithAes = false) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentSky)
                    ) {
                        Text("Import Normal", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { executeImport(encryptWithAes = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentLavender)
                    ) {
                        Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Encrypt (AES-256)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = DarkSurfaceElevated,
                shape = RoundedCornerShape(18.dp)
            )
        }

        // Create Folder Dialog
        if (showNewFolderDialog) {
            AlertDialog(
                onDismissRequest = { showNewFolderDialog = false },
                title = { Text("Create New Folder", color = TextWhite, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        placeholder = { Text("Folder Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentSky,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newFolderName.isNotBlank()) {
                                val target = File(currentPath, newFolderName.trim())
                                val created = target.mkdirs() || MountManager.createDirViaRoot(target.absolutePath).isSuccess
                                if (created) {
                                    refreshAll()
                                    showNewFolderDialog = false
                                    newFolderName = ""
                                } else {
                                    Toast.makeText(context, "Cannot create folder", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentMint)
                    ) {
                        Text("Create", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel", color = TextMuted) }
                },
                containerColor = DarkSurfaceElevated,
                shape = RoundedCornerShape(18.dp)
            )
        }

        // Create Text File Dialog
        if (showNewFileDialog) {
            AlertDialog(
                onDismissRequest = { showNewFileDialog = false },
                title = { Text("Create New File", color = TextWhite, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newFileName,
                            onValueChange = { newFileName = it },
                            placeholder = { Text("Filename (e.g. config.json, notes.txt)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentSky,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newFileContent,
                            onValueChange = { newFileContent = it },
                            placeholder = { Text("Optional initial file content...") },
                            minLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentSky,
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
                            if (newFileName.isNotBlank()) {
                                val target = File(currentPath, newFileName.trim())
                                try {
                                    target.writeText(newFileContent)
                                    refreshAll()
                                    showNewFileDialog = false
                                    newFileName = ""
                                    newFileContent = ""
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentSky)
                    ) {
                        Text("Create", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewFileDialog = false }) { Text("Cancel", color = TextMuted) }
                },
                containerColor = DarkSurfaceElevated,
                shape = RoundedCornerShape(18.dp)
            )
        }

        // Rename Dialog
        if (showRenameDialog && renameTarget != null) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename File", color = TextWhite, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = renameNewName,
                        onValueChange = { renameNewName = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentSky,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val src = File(renameTarget!!.path)
                            val dst = File(src.parentFile, renameNewName.trim())
                            val ok = src.renameTo(dst) || MountManager.renameViaRoot(src.absolutePath, dst.absolutePath).isSuccess
                            if (ok) {
                                refreshAll()
                                showRenameDialog = false
                            } else {
                                Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentSky)
                    ) {
                        Text("Rename", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) { Text("Cancel", color = TextMuted) }
                },
                containerColor = DarkSurfaceElevated,
                shape = RoundedCornerShape(18.dp)
            )
        }

        // Code Editor Dialog
        if (showEditorDialog && editorTargetFile != null) {
            CodeEditorDialog(
                file = File(editorTargetFile!!.path),
                initialContent = editorContent,
                onSave = { updated ->
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val f = File(editorTargetFile!!.path)
                            if (editorTargetFile!!.isEncrypted) {
                                val enc = AesVaultCipher.encrypt(updated.toByteArray(Charsets.UTF_8))
                                f.writeBytes(enc)
                            } else {
                                f.writeText(updated, Charsets.UTF_8)
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Saved ${f.name}", Toast.LENGTH_SHORT).show()
                                showEditorDialog = false
                                refreshAll()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Save error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onDismiss = { showEditorDialog = false }
            )
        }

        // Hex Inspector Dialog
        if (showHexDialog && hexTargetFile != null) {
            HexViewerDialog(
                file = hexTargetFile!!,
                hexLines = hexLines,
                onDismiss = { showHexDialog = false }
            )
        }

        // Zip Compress Dialog
        if (showZipDialog && currentSelected.isNotEmpty()) {
            ZipCompressDialog(
                selectedCount = currentSelected.size,
                defaultName = "Archive_${System.currentTimeMillis()}.zip",
                onCompress = { zipName ->
                    showZipDialog = false
                    val destZip = File(currentPath, zipName)
                    val filesToCompress = currentSelected.map { File(it) }
                    coroutineScope.launch(Dispatchers.IO) {
                        val res = FileManagerHelper.compressToZip(filesToCompress, destZip)
                        withContext(Dispatchers.Main) {
                            if (res.isSuccess) {
                                Toast.makeText(context, "Created archive: ${destZip.name}", Toast.LENGTH_LONG).show()
                                clearCurrentSelection()
                                refreshAll()
                            } else {
                                Toast.makeText(context, "Zip error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onDismiss = { showZipDialog = false }
            )
        }

        // Properties Dialog
        propertiesTarget?.let { prop ->
            FilePropertiesDialog(
                item = prop,
                sha256 = propertiesSha256,
                onDismiss = { propertiesTarget = null }
            )
        }

        // Image Viewer Dialog
        if (previewBitmap != null) {
            AlertDialog(
                onDismissRequest = { previewBitmap = null },
                title = {
                    Text(
                        text = previewImageTitle,
                        color = TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = previewBitmap!!,
                            contentDescription = previewImageTitle,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { previewBitmap = null },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentSky)
                    ) {
                        Text("Close", color = Color.White)
                    }
                },
                containerColor = DarkSurfaceElevated,
                shape = RoundedCornerShape(18.dp)
            )
        }
    }
}

@Composable
private fun BookmarkChip(
    label: String,
    path: String,
    scope: StorageScope,
    onSelect: (String, StorageScope) -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceCard)
            .border(1.dp, DarkBorderSubtle, RoundedCornerShape(8.dp))
            .clickable { onSelect(path, scope) }
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = TextWhite,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = JakartaFontFamily
        )
    }
}

private fun handleOpenFile(
    item: MtFileItem,
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onShowImage: (String, ImageBitmap) -> Unit,
    onShowEditor: (MtFileItem, String) -> Unit,
    onExtractZip: (File) -> Unit
) {
    val cleanName = item.name.removeSuffix(".isovault").lowercase()
    val isImage = cleanName.endsWith(".jpg") || cleanName.endsWith(".jpeg") ||
            cleanName.endsWith(".png") || cleanName.endsWith(".webp")
    val isZip = cleanName.endsWith(".zip")

    if (isZip) {
        onExtractZip(File(item.path))
    } else if (isImage) {
        coroutineScope.launch(Dispatchers.IO) {
            val bmp = try {
                val f = File(item.path)
                val bytes = if (item.isEncrypted) AesVaultCipher.decrypt(f.readBytes()) else f.readBytes()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
            withContext(Dispatchers.Main) {
                if (bmp != null) onShowImage(item.name, bmp)
                else Toast.makeText(context, "Unable to decode image", Toast.LENGTH_SHORT).show()
            }
        }
    } else {
        coroutineScope.launch(Dispatchers.IO) {
            val f = File(item.path)
            val text = try {
                val bytes = if (item.isEncrypted) AesVaultCipher.decrypt(f.readBytes()) else f.readBytes()
                if (bytes.size > 256 * 1024) {
                    "File is large (${bytes.size / 1024} KB). Showing first 8 KB:\n\n" +
                            String(bytes.copyOfRange(0, 8192), Charsets.UTF_8)
                } else {
                    String(bytes, Charsets.UTF_8)
                }
            } catch (e: Exception) {
                "Cannot read file directly (${e.message})"
            }
            withContext(Dispatchers.Main) {
                onShowEditor(item, text)
            }
        }
    }
}

@Composable
private fun MtFileItemRow(
    item: MtFileItem,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onAction: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) AccentSky.copy(alpha = 0.15f) else DarkSurfaceCard)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) AccentSky else DarkBorderSubtle,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Selection Checkbox
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggleSelect() },
            colors = CheckboxDefaults.colors(
                checkedColor = AccentSky,
                uncheckedColor = DarkBorder,
                checkmarkColor = Color.White
            ),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        val cleanName = item.name.removeSuffix(".isovault").lowercase()
        val isImage = cleanName.endsWith(".jpg") || cleanName.endsWith(".jpeg") || cleanName.endsWith(".png") || cleanName.endsWith(".webp")
        val isVideo = cleanName.endsWith(".mp4") || cleanName.endsWith(".mkv") || cleanName.endsWith(".mov")
        val isZip = cleanName.endsWith(".zip")

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    when {
                        item.isDirectory -> AccentSky.copy(alpha = 0.15f)
                        item.isEncrypted -> AccentLavender.copy(alpha = 0.16f)
                        isZip -> AccentMint.copy(alpha = 0.16f)
                        isImage -> AccentMint.copy(alpha = 0.15f)
                        isVideo -> AccentAmber.copy(alpha = 0.15f)
                        else -> DarkSurfaceElevated
                    }
                )
        ) {
            Icon(
                imageVector = when {
                    item.isDirectory -> Icons.Default.Folder
                    item.isEncrypted -> Icons.Default.Lock
                    isZip -> Icons.Default.FolderZip
                    isImage -> Icons.Default.Image
                    isVideo -> Icons.Default.VideoFile
                    else -> Icons.Default.Description
                },
                contentDescription = null,
                tint = when {
                    item.isDirectory -> AccentSky
                    item.isEncrypted -> AccentLavender
                    isZip -> AccentMint
                    isImage -> AccentMint
                    isVideo -> AccentAmber
                    else -> TextMuted
                },
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = TextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = JakartaFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val dateStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(item.lastModified))
                val sizeLabel = if (item.isDirectory) {
                    "${item.sizeBytes} items"
                } else if (item.sizeBytes < 1024) {
                    "${item.sizeBytes} B"
                } else {
                    "${item.sizeBytes / 1024} KB"
                }

                Text(
                    text = "$sizeLabel • ${item.permissions} • $dateStr",
                    color = TextSubtle,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                if (item.isEncrypted) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AES-256",
                        color = AccentLavender,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = TextMuted, modifier = Modifier.size(18.dp))
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(DarkSurfaceElevated)
            ) {
                DropdownMenuItem(
                    text = { Text("Select", color = TextWhite) },
                    leadingIcon = { Icon(Icons.Default.Check, null, tint = AccentSky) },
                    onClick = { showMenu = false; onAction("SELECT") }
                )
                DropdownMenuItem(
                    text = { Text("Copy to Opposite Panel ->", color = TextWhite) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, null, tint = AccentSky) },
                    onClick = { showMenu = false; onAction("COPY_TO_OTHER_PANEL") }
                )
                DropdownMenuItem(
                    text = { Text("Move to Opposite Panel ->", color = TextWhite) },
                    leadingIcon = { Icon(Icons.Default.ContentCut, null, tint = AccentAmber) },
                    onClick = { showMenu = false; onAction("MOVE_TO_OTHER_PANEL") }
                )
                DropdownMenuItem(
                    text = { Text("Copy", color = TextWhite) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = AccentSky) },
                    onClick = { showMenu = false; onAction("COPY") }
                )
                DropdownMenuItem(
                    text = { Text("Cut / Move", color = TextWhite) },
                    leadingIcon = { Icon(Icons.Default.ContentCut, null, tint = AccentAmber) },
                    onClick = { showMenu = false; onAction("CUT") }
                )
                if (cleanName.endsWith(".zip")) {
                    DropdownMenuItem(
                        text = { Text("Extract ZIP Here", color = AccentMint) },
                        leadingIcon = { Icon(Icons.Default.Unarchive, null, tint = AccentMint) },
                        onClick = { showMenu = false; onAction("EXTRACT_ZIP") }
                    )
                }
                if (!item.isDirectory) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (item.isEncrypted) "Decrypt (AES -> Plain)" else "Encrypt (AES-256 .isovault)",
                                color = AccentLavender
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (item.isEncrypted) Icons.Default.LockOpen else Icons.Default.Lock,
                                null,
                                tint = AccentLavender
                            )
                        },
                        onClick = { showMenu = false; onAction("ENCRYPT_DECRYPT") }
                    )
                    DropdownMenuItem(
                        text = { Text("Hex / Binary Inspector", color = AccentSky) },
                        leadingIcon = { Icon(Icons.Default.DataObject, null, tint = AccentSky) },
                        onClick = { showMenu = false; onAction("HEX_VIEWER") }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Export to Phone (/Download)", color = TextWhite) },
                    leadingIcon = { Icon(Icons.Default.Download, null, tint = AccentSky) },
                    onClick = { showMenu = false; onAction("EXPORT_DOWNLOADS") }
                )
                DropdownMenuItem(
                    text = { Text("Save As (SAF Picker)", color = TextWhite) },
                    leadingIcon = { Icon(Icons.Default.Upload, null, tint = AccentMint) },
                    onClick = { showMenu = false; onAction("EXPORT_SAVE_AS") }
                )
                DropdownMenuItem(
                    text = { Text("Open with MT Manager / Share", color = TextWhite) },
                    leadingIcon = { Icon(Icons.Default.Share, null, tint = AccentSky) },
                    onClick = { showMenu = false; onAction("SHARE_WITH_APP") }
                )
                DropdownMenuItem(
                    text = { Text("Rename", color = TextWhite) },
                    leadingIcon = { Icon(Icons.Default.Edit, null, tint = TextMuted) },
                    onClick = { showMenu = false; onAction("RENAME") }
                )
                DropdownMenuItem(
                    text = { Text("Properties & SHA-256", color = TextWhite) },
                    leadingIcon = { Icon(Icons.Default.Info, null, tint = TextMuted) },
                    onClick = { showMenu = false; onAction("PROPERTIES") }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = AccentCoral) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = AccentCoral) },
                    onClick = { showMenu = false; onAction("DELETE") }
                )
            }
        }
    }
}
