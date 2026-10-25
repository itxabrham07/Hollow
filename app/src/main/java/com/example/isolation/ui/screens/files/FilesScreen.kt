package com.example.isolation.ui.screens.files

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.data.entity.VaultFileItem
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentMint
import com.example.ui.theme.AccentSky
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSubtle
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    viewModel: IsoVaultViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val vaultFiles by viewModel.vaultFiles.collectAsState()
    val configs by viewModel.configs.collectAsState()

    var activeSubTab by remember { mutableIntStateOf(0) } // 0: Files, 1: Gallery
    var selectedVolume by remember { mutableStateOf("All") } // "All", "shared", or pkg
    var searchQuery by remember { mutableStateOf("") }
    var isSelectMode by remember { mutableStateOf(false) }
    val selectedItemIds = remember { mutableStateListOf<Long>() }

    var previewFile by remember { mutableStateOf<VaultDisplayFile?>(null) }
    var showRecycleBin by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<VaultDisplayFile?>(null) }
    var newRenameName by remember { mutableStateOf("") }

    val recycleSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // SAF Import Launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val volume = if (selectedVolume == "All") "shared" else selectedVolume
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            val displayName = cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) it.getString(idx) else null
                } else null
            } ?: "imported_${System.currentTimeMillis()}.dat"
            viewModel.importFileToVault(uri, volume, displayName)
        }
    }

    // Convert entity VaultFileItem to VaultDisplayFile
    val displayFiles = remember(vaultFiles, selectedVolume, searchQuery) {
        vaultFiles.map { item ->
            VaultDisplayFile(
                id = item.id,
                name = item.relativePath.substringAfterLast('/'),
                relativePath = item.relativePath,
                volume = item.packageName,
                physicalPath = item.physicalPath,
                sizeBytes = item.originalSizeBytes,
                lastModified = item.lastModified,
                mimeType = item.mimeType,
                isEncrypted = item.isEncrypted
            )
        }.filter { file ->
            val matchesVolume = when (selectedVolume) {
                "All" -> true
                else -> file.volume.equals(selectedVolume, ignoreCase = true)
            }
            val matchesSearch = searchQuery.isBlank() || file.name.contains(searchQuery, ignoreCase = true)
            matchesVolume && matchesSearch
        }
    }

    // Dynamic volumes list
    val volumeList = remember(configs) {
        listOf("All", "shared") + configs.filter { it.isIsolated }.map { it.packageName }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp)
            .testTag("files_screen")
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Header Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Encrypted Files",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily,
                    color = TextWhite
                )
                Text(
                    text = "${displayFiles.size} files in private storage",
                    fontSize = 12.sp,
                    color = TextMuted,
                    fontFamily = JakartaFontFamily
                )
            }

            Row {
                IconButton(onClick = { showRecycleBin = true }, modifier = Modifier.testTag("btn_open_recycle_bin")) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Recycle Bin", tint = TextMuted)
                }
                IconButton(
                    onClick = {
                        isSelectMode = !isSelectMode
                        if (!isSelectMode) selectedItemIds.clear()
                    },
                    modifier = Modifier.testTag("btn_toggle_select_mode")
                ) {
                    Icon(
                        imageVector = if (isSelectMode) Icons.Default.CheckCircle else Icons.Default.MoreVert,
                        contentDescription = "Select Mode",
                        tint = if (isSelectMode) AccentSky else TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Volume Selector Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(volumeList) { vol ->
                val isSelected = selectedVolume.equals(vol, ignoreCase = true)
                val label = when (vol) {
                    "All" -> "All Volumes"
                    "shared" -> "Shared Space"
                    else -> vol.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                }
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedVolume = vol },
                    label = { Text(label, fontSize = 12.sp, fontFamily = JakartaFontFamily) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentSky.copy(alpha = 0.2f),
                        selectedLabelColor = AccentSky,
                        containerColor = DarkSurfaceElevated,
                        labelColor = TextMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) AccentSky else DarkBorder
                    ),
                    shape = RoundedCornerShape(50)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Sub-tabs (Files / Gallery)
        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = DarkSurfaceCard,
            contentColor = AccentSky,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeSubTab]),
                    color = AccentSky
                )
            },
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = activeSubTab == 0,
                onClick = { activeSubTab = 0 },
                text = { Text("Explorer View", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily) }
            )
            Tab(
                selected = activeSubTab == 1,
                onClick = { activeSubTab = 1 },
                text = { Text("Gallery View", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // File Grid with stable keys
        if (displayFiles.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = TextSubtle, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("No files in this volume yet", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Import files using + button or isolate an active app", color = TextMuted, fontSize = 12.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (activeSubTab == 1) 3 else 2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(
                    items = displayFiles,
                    key = { it.id }
                ) { file ->
                    val isSelected = selectedItemIds.contains(file.id)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) AccentSky.copy(alpha = 0.15f) else DarkSurfaceCard
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) AccentSky else DarkBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSelectMode) {
                                    if (isSelected) selectedItemIds.remove(file.id) else selectedItemIds.add(file.id)
                                } else {
                                    previewFile = file
                                }
                            }
                            .testTag("file_item_${file.id}")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkSurfaceElevated)
                            ) {
                                Icon(
                                    imageVector = when {
                                        file.mimeType.startsWith("image") -> Icons.Default.Image
                                        file.mimeType.startsWith("video") -> Icons.Default.Videocam
                                        file.mimeType.startsWith("audio") -> Icons.Default.Audiotrack
                                        file.mimeType.contains("pdf") -> Icons.Default.PictureAsPdf
                                        else -> Icons.Default.Description
                                    },
                                    contentDescription = null,
                                    tint = AccentSky,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = file.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = JakartaFontFamily
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${file.sizeBytes} B",
                                fontSize = 10.sp,
                                color = TextMuted,
                                fontFamily = JakartaFontFamily
                            )
                        }
                    }
                }
            }
        }

        // Multi-select toolbar
        if (isSelectMode && selectedItemIds.isNotEmpty()) {
            FileSelectionBar(
                selectedCount = selectedItemIds.size,
                onMove = {},
                onCopy = {},
                onRename = {
                    val target = displayFiles.firstOrNull { it.id == selectedItemIds.first() }
                    if (target != null) {
                        renameTarget = target
                        newRenameName = target.name
                        showRenameDialog = true
                    }
                },
                onDelete = {
                    selectedItemIds.forEach { id ->
                        val target = vaultFiles.firstOrNull { it.id == id }
                        if (target != null) {
                            viewModel.deleteFileToRecycleBin(target)
                        }
                    }
                    selectedItemIds.clear()
                    isSelectMode = false
                },
                onExport = {},
                onClearSelection = { selectedItemIds.clear() }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Import FAB
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            FloatingActionButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                containerColor = AccentSky,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("fab_import_file")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Import File")
            }
        }
    }

    // In-App Viewer
    previewFile?.let { file ->
        FileViewerDialog(
            fileItem = file,
            onDismiss = { previewFile = null }
        )
    }

    // Recycle Bin Bottom Sheet
    if (showRecycleBin) {
        val binDir = File(context.filesDir, "recycle_bin")
        val binFiles = binDir.listFiles()?.map { file ->
            RecycleBinItem(
                file = file,
                name = file.name.substringAfter('_'),
                sizeBytes = file.length(),
                daysRemaining = 30
            )
        } ?: emptyList()

        RecycleBinSheet(
            recycleItems = binFiles,
            sheetState = recycleSheetState,
            onDismiss = { showRecycleBin = false },
            onRestore = { item ->
                val destDir = File(context.filesDir, "vaults/shared")
                destDir.mkdirs()
                item.file.renameTo(File(destDir, item.name))
                showRecycleBin = false
            },
            onPermanentlyDelete = { item ->
                item.file.delete()
            },
            onEmptyBin = {
                binDir.deleteRecursively()
                binDir.mkdirs()
                showRecycleBin = false
            }
        )
    }
}
