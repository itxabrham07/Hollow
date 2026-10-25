package com.example.isolation.ui.screens

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentLavender
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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

@Composable
fun MtFileExplorerScreen(
    viewModel: IsoVaultViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val explorerTargetDir by viewModel.explorerTargetDir.collectAsState()
    val isRootGranted by viewModel.isRootGranted.collectAsState()

    var currentScope by remember { mutableStateOf(StorageScope.VAULT_STORAGE) }
    var vaultPath by remember { mutableStateOf(MountManager.PERSISTENT_VAULT_ROOT) }
    var phonePath by remember { mutableStateOf("/storage/emulated/0") }
    var rootPath by remember { mutableStateOf("/data/data") }

    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(FileSortOrder.NAME) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Clipboard state for MT Manager Copy/Move between directories & panes
    var clipboardEntry by remember { mutableStateOf<ClipboardEntry?>(null) }

    val currentPath = when (currentScope) {
        StorageScope.VAULT_STORAGE -> vaultPath
        StorageScope.PHONE_STORAGE -> phonePath
        StorageScope.ROOT_STORAGE -> rootPath
    }

    fun updateCurrentPath(newPath: String) {
        when (currentScope) {
            StorageScope.VAULT_STORAGE -> vaultPath = newPath
            StorageScope.PHONE_STORAGE -> phonePath = newPath
            StorageScope.ROOT_STORAGE -> rootPath = newPath
        }
    }

    // Consume deep-link directory target from Dashboard or Apps screen
    LaunchedEffect(explorerTargetDir) {
        explorerTargetDir?.let { target ->
            val dir = File(target)
            dir.mkdirs()
            currentScope = StorageScope.VAULT_STORAGE
            vaultPath = target
            viewModel.setExplorerTargetDir(null)
        }
    }

    var fileList by remember { mutableStateOf<List<MtFileItem>>(emptyList()) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var newFileContent by remember { mutableStateOf("") }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<MtFileItem?>(null) }
    var renameNewName by remember { mutableStateOf("") }

    // Text Editor Dialog State
    var showEditorDialog by remember { mutableStateOf(false) }
    var editorTargetFile by remember { mutableStateOf<MtFileItem?>(null) }
    var editorContent by remember { mutableStateOf("") }

    // Image Viewer Dialog State
    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var previewImageTitle by remember { mutableStateOf("") }

    // Properties Dialog State
    var propertiesTarget by remember { mutableStateOf<MtFileItem?>(null) }
    var propertiesSha256 by remember { mutableStateOf("Calculating...") }

    fun refreshFiles() {
        coroutineScope.launch(Dispatchers.IO) {
            val dir = File(currentPath)
            if (currentScope == StorageScope.VAULT_STORAGE && !dir.exists()) {
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
                            isEncrypted = f.extension.equals("isovault", ignoreCase = true),
                            permissions = perms
                        )
                    )
                }
            } else if (isRootGranted) {
                // Root fallback for restricted directories (/data/data, etc.)
                val entries = MountManager.listFilesWithRootFallback(currentPath)
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
                fileList = sorted
            }
        }
    }

    LaunchedEffect(currentScope, currentPath, sortOrder, isRootGranted) {
        refreshFiles()
    }

    // System file picker to import external files directly into current directory
    val importFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                var imported = 0
                val targetDir = File(currentPath)
                targetDir.mkdirs()
                for ((idx, uri) in uris.withIndex()) {
                    try {
                        val mime = context.contentResolver.getType(uri) ?: ""
                        val ext = when {
                            mime.contains("png") -> "png"
                            mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
                            mime.contains("mp4") || mime.contains("video") -> "mp4"
                            mime.contains("pdf") -> "pdf"
                            mime.contains("json") -> "json"
                            mime.contains("text") -> "txt"
                            else -> "dat"
                        }
                        val input = context.contentResolver.openInputStream(uri)
                        val out = File(targetDir, "IMPORT_${System.currentTimeMillis()}_$idx.$ext")
                        val fos = FileOutputStream(out)
                        input?.copyTo(fos)
                        input?.close()
                        fos.close()
                        imported++
                    } catch (_: Exception) {}
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Imported $imported file(s) into ${targetDir.name}", Toast.LENGTH_SHORT).show()
                    refreshFiles()
                }
            }
        }
    }

    val displayedFiles = fileList.filter {
        searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("mt_file_explorer_screen")
    ) {
        // Top Header & Storage Scope Switcher
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurfaceCard)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "MT File Manager",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JakartaFontFamily,
                            color = TextWhite
                        )
                        if (isRootGranted) {
                            Spacer(modifier = Modifier.width(8.dp))
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
                        }
                    }
                    Text(
                        text = "${displayedFiles.count { it.isDirectory }} folders • ${displayedFiles.count { !it.isDirectory }} files",
                        fontSize = 11.sp,
                        fontFamily = JakartaFontFamily,
                        color = TextMuted
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Search Toggle
                    IconButton(
                        onClick = {
                            showSearchBar = !showSearchBar
                            if (!showSearchBar) searchQuery = ""
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

                    // Sort Menu Button
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceElevated)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort",
                                tint = TextMuted,
                                modifier = Modifier.size(17.dp)
                            )
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

                    // Refresh Button
                    IconButton(
                        onClick = { refreshFiles() },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = AccentSky,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = showSearchBar) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter current directory...", fontSize = 13.sp, color = TextMuted) },
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentSky,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3-Way Scope Switcher (Vault / Phone / Root)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceElevated)
                    .padding(3.dp)
            ) {
                StorageScope.entries.forEach { scope ->
                    val selected = currentScope == scope
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (selected) AccentSky else Color.Transparent)
                            .clickable {
                                if (scope == StorageScope.ROOT_STORAGE && !isRootGranted) {
                                    viewModel.requestRootAccess()
                                }
                                currentScope = scope
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (scope) {
                                    StorageScope.VAULT_STORAGE -> Icons.Default.Security
                                    StorageScope.PHONE_STORAGE -> Icons.Default.PhoneAndroid
                                    StorageScope.ROOT_STORAGE -> Icons.Default.Terminal
                                },
                                contentDescription = null,
                                tint = if (selected) Color.White else TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = scope.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = JakartaFontFamily,
                                color = if (selected) Color.White else TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Interactive Path Breadcrumb & Quick Creation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurfaceElevated)
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

            // New Text File
            IconButton(
                onClick = { showNewFileDialog = true },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.NoteAdd,
                    contentDescription = "New File",
                    tint = AccentSky,
                    modifier = Modifier.size(17.dp)
                )
            }

            // New Folder
            IconButton(
                onClick = { showNewFolderDialog = true },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = "New Folder",
                    tint = AccentMint,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        // Clipboard Floating Banner (when a file/folder is copied or cut)
        AnimatedVisibility(visible = clipboardEntry != null) {
            clipboardEntry?.let { clip ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentSky.copy(alpha = 0.16f))
                        .border(1.dp, AccentSky.copy(alpha = 0.4f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                            text = "Navigate to destination folder and tap Paste Here",
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
                                            if (src.isDirectory) {
                                                src.copyRecursively(dst, overwrite = true)
                                            } else {
                                                src.copyTo(dst, overwrite = true)
                                            }
                                            if (clip.isCutOperation) {
                                                src.deleteRecursively()
                                            }
                                            true
                                        } else {
                                            val copied = MountManager.copyViaRoot(src.absolutePath, dst.absolutePath).isSuccess
                                            if (copied && clip.isCutOperation) {
                                                MountManager.deleteViaRoot(src.absolutePath)
                                            }
                                            copied
                                        }
                                    } catch (_: Exception) {
                                        false
                                    }

                                    withContext(Dispatchers.Main) {
                                        if (ok) {
                                            Toast.makeText(
                                                context,
                                                if (clip.isCutOperation) "Moved to $currentPath" else "Pasted into $currentPath",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            clipboardEntry = null
                                            refreshFiles()
                                        } else {
                                            Toast.makeText(context, "Operation failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentSky),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste Here", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = { clipboardEntry = null },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Quick Action Row: Import External File / Export Folder to Phone
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                Text("Import File Here", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Button(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val exportDir = File("/storage/emulated/0/Download/IsoVault_Exported")
                            exportDir.mkdirs()
                            val cur = File(currentPath)
                            val target = File(exportDir, cur.name)
                            if (cur.canRead()) {
                                cur.copyRecursively(target, overwrite = true)
                            } else {
                                MountManager.copyViaRoot(cur.absolutePath, target.absolutePath)
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Exported folder to /Download/IsoVault_Exported/${cur.name}", Toast.LENGTH_LONG).show()
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

        // Directory Listing
        if (displayedFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 50.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = TextSubtle,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No matching files" else "Directory is empty",
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
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayedFiles, key = { it.path }) { item ->
                    MtFileRow(
                        item = item,
                        onClick = {
                            if (item.isDirectory) {
                                updateCurrentPath(item.path)
                            } else {
                                // Determine if image or text/editable file
                                val cleanName = item.name.removeSuffix(".isovault").lowercase()
                                val isImg = cleanName.endsWith(".jpg") || cleanName.endsWith(".jpeg") ||
                                        cleanName.endsWith(".png") || cleanName.endsWith(".webp")

                                if (isImg) {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val bmp = try {
                                            val f = File(item.path)
                                            val bytes = if (item.isEncrypted) {
                                                AesVaultCipher.decrypt(f.readBytes())
                                            } else {
                                                f.readBytes()
                                            }
                                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                        } catch (_: Exception) {
                                            null
                                        }
                                        withContext(Dispatchers.Main) {
                                            if (bmp != null) {
                                                previewImageTitle = item.name
                                                previewBitmap = bmp
                                            } else {
                                                Toast.makeText(context, "Unable to decode image", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                } else {
                                    // Open in built-in MT Text/Code Editor
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val f = File(item.path)
                                        val text = try {
                                            val bytes = if (item.isEncrypted) {
                                                AesVaultCipher.decrypt(f.readBytes())
                                            } else {
                                                f.readBytes()
                                            }
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
                                            editorTargetFile = item
                                            editorContent = text
                                            showEditorDialog = true
                                        }
                                    }
                                }
                            }
                        },
                        onAction = { action ->
                            when (action) {
                                "COPY" -> {
                                    clipboardEntry = ClipboardEntry(item, isCutOperation = false)
                                }
                                "CUT" -> {
                                    clipboardEntry = ClipboardEntry(item, isCutOperation = true)
                                }
                                "ENCRYPT_DECRYPT" -> {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            val src = File(item.path)
                                            if (item.isEncrypted) {
                                                val decrypted = AesVaultCipher.decrypt(src.readBytes())
                                                val out = File(src.parentFile, item.name.removeSuffix(".isovault"))
                                                out.writeBytes(decrypted)
                                                src.delete()
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Decrypted: ${out.name}", Toast.LENGTH_SHORT).show()
                                                    refreshFiles()
                                                }
                                            } else {
                                                val encrypted = AesVaultCipher.encrypt(src.readBytes())
                                                val out = File(src.parentFile, "${item.name}.isovault")
                                                out.writeBytes(encrypted)
                                                src.delete()
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "AES-256 Encrypted: ${out.name}", Toast.LENGTH_SHORT).show()
                                                    refreshFiles()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Cipher error: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                "EXPORT" -> {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            val src = File(item.path)
                                            val dest = File("/storage/emulated/0/Download/IsoVault_Exported/${item.name.removeSuffix(".isovault")}")
                                            dest.parentFile?.mkdirs()
                                            if (item.isEncrypted) {
                                                val dec = AesVaultCipher.decrypt(src.readBytes())
                                                dest.writeBytes(dec)
                                            } else if (src.isDirectory) {
                                                src.copyRecursively(dest, overwrite = true)
                                            } else {
                                                src.copyTo(dest, overwrite = true)
                                            }
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Exported to ${dest.absolutePath}", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                "IMPORT_TO_VAULT" -> {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            val src = File(item.path)
                                            val dest = File("${MountManager.PERSISTENT_VAULT_ROOT}/imported/${item.name}")
                                            dest.parentFile?.mkdirs()
                                            if (src.isDirectory) {
                                                src.copyRecursively(dest, overwrite = true)
                                            } else {
                                                src.copyTo(dest, overwrite = true)
                                            }
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Copied into Vault /imported/${item.name}", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
                                            if (!f.isDirectory && f.canRead() && f.length() < 20 * 1024 * 1024) {
                                                val md = MessageDigest.getInstance("SHA-256")
                                                val digest = md.digest(f.readBytes())
                                                digest.joinToString("") { "%02x".format(it) }.take(32) + "..."
                                            } else {
                                                "Directory / Stream"
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
                                            if (deleted) {
                                                refreshFiles()
                                            } else {
                                                Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
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
                                File(currentPath, newFolderName.trim()).mkdirs()
                                showNewFolderDialog = false
                                newFolderName = ""
                                refreshFiles()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentSky)
                    ) {
                        Text("Create", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewFolderDialog = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                },
                containerColor = DarkSurfaceElevated,
                shape = RoundedCornerShape(18.dp)
            )
        }

        // Create New Text/Script File Dialog
        if (showNewFileDialog) {
            AlertDialog(
                onDismissRequest = { showNewFileDialog = false },
                title = { Text("Create New File", color = TextWhite, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = newFileName,
                            onValueChange = { newFileName = it },
                            placeholder = { Text("Filename (e.g. notes.txt, config.json)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentSky,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = newFileContent,
                            onValueChange = { newFileContent = it },
                            placeholder = { Text("File contents...") },
                            minLines = 4,
                            maxLines = 8,
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
                                try {
                                    val target = File(currentPath, newFileName.trim())
                                    target.parentFile?.mkdirs()
                                    target.writeText(newFileContent)
                                    showNewFileDialog = false
                                    newFileName = ""
                                    newFileContent = ""
                                    refreshFiles()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentSky)
                    ) {
                        Text("Save File", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewFileDialog = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                },
                containerColor = DarkSurfaceElevated,
                shape = RoundedCornerShape(18.dp)
            )
        }

        // Rename Dialog
        if (showRenameDialog && renameTarget != null) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename Item", color = TextWhite, fontWeight = FontWeight.Bold) },
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
                            if (renameNewName.isNotBlank()) {
                                val old = File(renameTarget!!.path)
                                val target = File(old.parentFile, renameNewName.trim())
                                old.renameTo(target)
                                showRenameDialog = false
                                refreshFiles()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentSky)
                    ) {
                        Text("Rename", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                },
                containerColor = DarkSurfaceElevated,
                shape = RoundedCornerShape(18.dp)
            )
        }

        // Built-in MT Text / Code Editor Dialog
        if (showEditorDialog && editorTargetFile != null) {
            val target = editorTargetFile!!
            AlertDialog(
                onDismissRequest = { showEditorDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, null, tint = AccentSky, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = target.name,
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                text = {
                    OutlinedTextField(
                        value = editorContent,
                        onValueChange = { editorContent = it },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = AccentMint
                        ),
                        minLines = 10,
                        maxLines = 15,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentSky,
                            unfocusedBorderColor = DarkBorder,
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val f = File(target.path)
                                    val rawBytes = editorContent.toByteArray(Charsets.UTF_8)
                                    if (target.isEncrypted) {
                                        f.writeBytes(AesVaultCipher.encrypt(rawBytes))
                                    } else {
                                        f.writeBytes(rawBytes)
                                    }
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Saved changes to ${target.name}", Toast.LENGTH_SHORT).show()
                                        showEditorDialog = false
                                        refreshFiles()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentSky)
                    ) {
                        Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditorDialog = false }) {
                        Text("Close", color = TextMuted)
                    }
                },
                containerColor = DarkSurfaceElevated,
                shape = RoundedCornerShape(18.dp)
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

        // Properties & SHA-256 Inspector Dialog
        propertiesTarget?.let { prop ->
            AlertDialog(
                onDismissRequest = { propertiesTarget = null },
                title = {
                    Text("File Properties", color = TextWhite, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PropertyLine("Name", prop.name)
                        PropertyLine("Full Path", prop.path)
                        PropertyLine("Type", if (prop.isDirectory) "Directory" else if (prop.isEncrypted) "AES-256 Encrypted (.isovault)" else "Regular File")
                        PropertyLine("Size", if (prop.isDirectory) "${prop.sizeBytes} items" else "${prop.sizeBytes} bytes (${prop.sizeBytes / 1024} KB)")
                        PropertyLine("Permissions", prop.permissions)
                        PropertyLine("Modified", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(prop.lastModified)))
                        if (!prop.isDirectory) {
                            PropertyLine("SHA-256", propertiesSha256)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { propertiesTarget = null },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentSky)
                    ) {
                        Text("OK", color = Color.White)
                    }
                },
                containerColor = DarkSurfaceElevated,
                shape = RoundedCornerShape(18.dp)
            )
        }
    }
}

@Composable
private fun PropertyLine(label: String, value: String) {
    Column {
        Text(text = label, color = TextSubtle, fontSize = 11.sp)
        Text(
            text = value,
            color = TextWhite,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MtFileRow(
    item: MtFileItem,
    onClick: () -> Unit,
    onAction: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceCard)
            .border(1.dp, DarkBorderSubtle, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val cleanName = item.name.removeSuffix(".isovault").lowercase()
        val isImage = cleanName.endsWith(".jpg") || cleanName.endsWith(".jpeg") || cleanName.endsWith(".png") || cleanName.endsWith(".webp")
        val isVideo = cleanName.endsWith(".mp4") || cleanName.endsWith(".mkv") || cleanName.endsWith(".mov")

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    when {
                        item.isDirectory -> AccentSky.copy(alpha = 0.15f)
                        item.isEncrypted -> AccentLavender.copy(alpha = 0.16f)
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
                    isImage -> Icons.Default.Image
                    isVideo -> Icons.Default.VideoFile
                    else -> Icons.Default.Description
                },
                contentDescription = null,
                tint = when {
                    item.isDirectory -> AccentSky
                    item.isEncrypted -> AccentLavender
                    isImage -> AccentMint
                    isVideo -> AccentAmber
                    else -> TextMuted
                },
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = TextWhite,
                fontSize = 14.sp,
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
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                if (item.isEncrypted) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AES-256",
                        color = AccentLavender,
                        fontSize = 10.sp,
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
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "File Actions",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(DarkSurfaceElevated)
            ) {
                DropdownMenuItem(
                    text = { Text("Copy", color = TextWhite) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = AccentSky) },
                    onClick = {
                        showMenu = false
                        onAction("COPY")
                    }
                )
                DropdownMenuItem(
                    text = { Text("Cut / Move", color = TextWhite) },
                    leadingIcon = { Icon(Icons.Default.ContentCut, null, tint = AccentAmber) },
                    onClick = {
                        showMenu = false
                        onAction("CUT")
                    }
                )
                if (!item.isDirectory) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (item.isEncrypted) "Decrypt (.isovault -> Raw)" else "Encrypt (AES-256 .isovault)",
                                color = AccentLavender
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (item.isEncrypted) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = null,
                                tint = AccentLavender
                            )
                        },
                        onClick = {
                            showMenu = false
                            onAction("ENCRYPT_DECRYPT")
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Copy into Vault (/imported)", color = TextWhite) },
                    leadingIcon = { Icon(Icons.Default.Upload, null, tint = AccentMint) },
                    onClick = {
                        showMenu = false
                        onAction("IMPORT_TO_VAULT")
                    }
                )
                DropdownMenuItem(
                    text = { Text("Export to Phone (/Download)", color = TextWhite) },
                    leadingIcon = { Icon(Icons.Default.Download, null, tint = AccentSky) },
                    onClick = {
                        showMenu = false
                        onAction("EXPORT")
                    }
                )
                DropdownMenuItem(
                    text = { Text("Rename", color = TextWhite) },
                    leadingIcon = { Icon(Icons.Default.Edit, null, tint = TextMuted) },
                    onClick = {
                        showMenu = false
                        onAction("RENAME")
                    }
                )
                DropdownMenuItem(
                    text = { Text("Properties & SHA-256", color = TextWhite) },
                    leadingIcon = { Icon(Icons.Default.Info, null, tint = TextMuted) },
                    onClick = {
                        showMenu = false
                        onAction("PROPERTIES")
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = AccentCoral) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = AccentCoral) },
                    onClick = {
                        showMenu = false
                        onAction("DELETE")
                    }
                )
            }
        }
    }
}
