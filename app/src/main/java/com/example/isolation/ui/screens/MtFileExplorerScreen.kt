package com.example.isolation.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.crypto.AesVaultCipher
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class StorageScope {
    VAULT_STORAGE,
    PHONE_STORAGE
}

data class MtFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val isEncrypted: Boolean
)

@Composable
fun MtFileExplorerScreen(
    viewModel: IsoVaultViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentScope by remember { mutableStateOf(StorageScope.VAULT_STORAGE) }
    var vaultPath by remember { mutableStateOf("/storage/emulated/0/.IsoVault") }
    var phonePath by remember { mutableStateOf("/storage/emulated/0") }

    val currentPath = if (currentScope == StorageScope.VAULT_STORAGE) vaultPath else phonePath

    var fileList by remember { mutableStateOf<List<MtFileItem>>(emptyList()) }
    var selectedItemForAction by remember { mutableStateOf<MtFileItem?>(null) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<MtFileItem?>(null) }
    var renameNewName by remember { mutableStateOf("") }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var previewTitle by remember { mutableStateOf("") }
    var previewContent by remember { mutableStateOf("") }

    fun refreshFiles() {
        val dir = File(currentPath)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val items = mutableListOf<MtFileItem>()
        val list = dir.listFiles()
        if (list != null) {
            for (f in list) {
                items.add(
                    MtFileItem(
                        name = f.name,
                        path = f.absolutePath,
                        isDirectory = f.isDirectory,
                        sizeBytes = if (f.isDirectory) 0L else f.length(),
                        lastModified = f.lastModified(),
                        isEncrypted = f.extension.equals("isovault", ignoreCase = true)
                    )
                )
            }
        }
        // Sort: directories first, then alphabetically
        fileList = items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    LaunchedEffect(currentScope, currentPath) {
        refreshFiles()
    }

    // Phone file picker launcher to import files directly into the current Vault folder
    val importFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                var imported = 0
                val targetDir = File(vaultPath)
                targetDir.mkdirs()
                for ((idx, uri) in uris.withIndex()) {
                    try {
                        val input = context.contentResolver.openInputStream(uri)
                        val out = File(targetDir, "IMPORTED_${System.currentTimeMillis()}_$idx.bin")
                        val fos = FileOutputStream(out)
                        input?.copyTo(fos)
                        input?.close()
                        fos.close()
                        imported++
                    } catch (_: Exception) {}
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Imported $imported files into Vault!", Toast.LENGTH_SHORT).show()
                    refreshFiles()
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("mt_file_explorer_screen")
    ) {
        // MT Manager Header & Scope Switcher
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurfaceCard)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "File Explorer",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JakartaFontFamily,
                        color = TextWhite
                    )
                    Text(
                        text = "MT Manager style dual-storage import & export",
                        fontSize = 12.sp,
                        fontFamily = JakartaFontFamily,
                        color = TextMuted
                    )
                }

                IconButton(
                    onClick = { refreshFiles() },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceElevated)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = AccentSky,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dual Storage Tabs (Vault Storage vs Phone Storage)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(DarkSurfaceElevated)
                    .padding(4.dp)
            ) {
                // Vault Storage Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (currentScope == StorageScope.VAULT_STORAGE) AccentSky else Color.Transparent
                        )
                        .clickable { currentScope = StorageScope.VAULT_STORAGE },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (currentScope == StorageScope.VAULT_STORAGE) Color.White else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Vault Storage",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = JakartaFontFamily,
                            color = if (currentScope == StorageScope.VAULT_STORAGE) Color.White else TextMuted
                        )
                    }
                }

                // Phone Storage Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (currentScope == StorageScope.PHONE_STORAGE) AccentSky else Color.Transparent
                        )
                        .clickable { currentScope = StorageScope.PHONE_STORAGE },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = if (currentScope == StorageScope.PHONE_STORAGE) Color.White else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Phone Storage",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = JakartaFontFamily,
                            color = if (currentScope == StorageScope.PHONE_STORAGE) Color.White else TextMuted
                        )
                    }
                }
            }
        }

        // Breadcrumb & Up Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurfaceElevated)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Up Button
            val canGoUp = currentPath != "/" && currentPath != "/storage/emulated/0" && currentPath != "/storage/emulated/0/.IsoVault"
            IconButton(
                onClick = {
                    val parent = File(currentPath).parentFile?.absolutePath
                    if (parent != null) {
                        if (currentScope == StorageScope.VAULT_STORAGE) vaultPath = parent else phonePath = parent
                    }
                },
                enabled = canGoUp,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Up",
                    tint = if (canGoUp) AccentSky else TextSubtle,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = currentPath.removePrefix("/storage/emulated/0").ifBlank { "/" },
                color = TextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // New Folder Action
            IconButton(
                onClick = { showNewFolderDialog = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = "New Folder",
                    tint = AccentMint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Action Toolbar (Import to Vault / Export to Phone)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Import Button
            Button(
                onClick = {
                    importFilePicker.launch("*/*")
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentSky),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("import_to_vault_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Import to Vault",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            // Export All / Folder Button
            Button(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val exportDir = File("/storage/emulated/0/Download/IsoVault_Exported")
                            exportDir.mkdirs()
                            val cur = File(currentPath)
                            if (cur.isDirectory) {
                                cur.copyRecursively(File(exportDir, cur.name), overwrite = true)
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Exported to /Download/IsoVault_Exported!", Toast.LENGTH_LONG).show()
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
                    .height(40.dp)
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                    .testTag("export_to_phone_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Upload,
                    contentDescription = null,
                    tint = AccentMint,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Export to Phone",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextWhite
                )
            }
        }

        // File List
        if (fileList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = TextSubtle,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Directory is empty",
                        color = TextMuted,
                        fontSize = 15.sp,
                        fontFamily = JakartaFontFamily
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
                items(fileList, key = { it.path }) { item ->
                    MtFileRow(
                        item = item,
                        onClick = {
                            if (item.isDirectory) {
                                if (currentScope == StorageScope.VAULT_STORAGE) {
                                    vaultPath = item.path
                                } else {
                                    phonePath = item.path
                                }
                            } else {
                                // Inspect / Decrypt
                                coroutineScope.launch(Dispatchers.IO) {
                                    val f = File(item.path)
                                    val content = try {
                                        if (item.isEncrypted) {
                                            val enc = f.readBytes()
                                            val dec = AesVaultCipher.decrypt(enc)
                                            String(dec, Charsets.UTF_8)
                                        } else {
                                            f.readText()
                                        }
                                    } catch (e: Exception) {
                                        "Binary or encrypted stream (${f.length()} bytes): ${e.message}"
                                    }
                                    withContext(Dispatchers.Main) {
                                        previewTitle = item.name
                                        previewContent = content
                                        showPreviewDialog = true
                                    }
                                }
                            }
                        },
                        onAction = { action ->
                            when (action) {
                                "EXPORT" -> {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            val src = File(item.path)
                                            val dest = File("/storage/emulated/0/Download/IsoVault_Exported/${item.name.removeSuffix(".isovault")}")
                                            dest.parentFile?.mkdirs()
                                            if (item.isEncrypted) {
                                                val dec = AesVaultCipher.decrypt(src.readBytes())
                                                dest.writeBytes(dec)
                                            } else {
                                                src.copyTo(dest, overwrite = true)
                                            }
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Exported to ${dest.path}", Toast.LENGTH_SHORT).show()
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
                                            val dest = File("/storage/emulated/0/.IsoVault/imported/${item.name}")
                                            dest.parentFile?.mkdirs()
                                            src.copyTo(dest, overwrite = true)
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Saved into Vault!", Toast.LENGTH_SHORT).show()
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
                                "DELETE" -> {
                                    File(item.path).deleteRecursively()
                                    refreshFiles()
                                }
                            }
                        }
                    )
                }
            }
        }

        // New Folder Dialog
        if (showNewFolderDialog) {
            AlertDialog(
                onDismissRequest = { showNewFolderDialog = false },
                title = { Text("Create Folder", color = TextWhite, fontWeight = FontWeight.Bold) },
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
                            unfocusedTextColor = TextWhite,
                            cursorColor = AccentSky
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
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Rename Dialog
        if (showRenameDialog && renameTarget != null) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename", color = TextWhite, fontWeight = FontWeight.Bold) },
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
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Preview File Content Dialog
        if (showPreviewDialog) {
            AlertDialog(
                onDismissRequest = { showPreviewDialog = false },
                title = {
                    Text(
                        text = previewTitle,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                text = {
                    Surface(
                        color = DarkBackground,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) {
                        Text(
                            text = previewContent,
                            color = AccentMint,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showPreviewDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentSky)
                    ) {
                        Text("Close", color = Color.White)
                    }
                },
                containerColor = DarkSurfaceElevated,
                shape = RoundedCornerShape(16.dp)
            )
        }
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
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    when {
                        item.isDirectory -> AccentSky.copy(alpha = 0.15f)
                        item.isEncrypted -> AccentLavender.copy(alpha = 0.15f)
                        else -> DarkSurfaceElevated
                    }
                )
        ) {
            Icon(
                imageVector = when {
                    item.isDirectory -> Icons.Default.Folder
                    item.isEncrypted -> Icons.Default.Lock
                    item.name.endsWith(".jpg", true) || item.name.endsWith(".png", true) -> Icons.Default.Image
                    item.name.endsWith(".mp4", true) -> Icons.Default.VideoFile
                    else -> Icons.Default.Description
                },
                contentDescription = null,
                tint = when {
                    item.isDirectory -> AccentSky
                    item.isEncrypted -> AccentLavender
                    item.name.endsWith(".jpg", true) || item.name.endsWith(".png", true) -> AccentMint
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
                Text(
                    text = if (item.isDirectory) "Folder • $dateStr" else "${item.sizeBytes / 1024} KB • $dateStr",
                    color = TextSubtle,
                    fontSize = 11.sp,
                    fontFamily = JakartaFontFamily
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

        // More Menu
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(DarkSurfaceElevated)
            ) {
                if (!item.isDirectory) {
                    DropdownMenuItem(
                        text = { Text("Export to Phone Storage", color = TextWhite) },
                        leadingIcon = { Icon(Icons.Default.Download, null, tint = AccentSky) },
                        onClick = {
                            showMenu = false
                            onAction("EXPORT")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Import to Vault", color = TextWhite) },
                        leadingIcon = { Icon(Icons.Default.Upload, null, tint = AccentMint) },
                        onClick = {
                            showMenu = false
                            onAction("IMPORT_TO_VAULT")
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Rename", color = TextWhite) },
                    leadingIcon = { Icon(Icons.Default.Edit, null, tint = TextMuted) },
                    onClick = {
                        showMenu = false
                        onAction("RENAME")
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
