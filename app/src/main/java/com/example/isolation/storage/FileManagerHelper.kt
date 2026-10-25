package com.example.isolation.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.isolation.crypto.AesVaultCipher
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class HexLine(
    val offset: String,
    val hexString: String,
    val asciiString: String
)

object FileManagerHelper {

    const val FILE_PROVIDER_AUTHORITY = "com.aistudio.isovault.xrvf.fileprovider"

    /**
     * Checks whether full storage access (MANAGE_EXTERNAL_STORAGE) is granted on Android 11+
     */
    fun hasFullStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * Launch system settings page to grant MANAGE_EXTERNAL_STORAGE
     */
    fun launchManageStorageSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Accurately extracts the real filename from a Content Uri using OpenableColumns.DISPLAY_NAME.
     * Never discards the file's original name or extension!
     */
    fun queryDisplayName(context: Context, uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val colIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (colIndex != -1) {
                            name = cursor.getString(colIndex)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        if (name.isNullOrBlank()) {
            val path = uri.path
            name = if (!path.isNullOrBlank() && path.contains("/")) {
                path.substringAfterLast('/')
            } else {
                val mime = context.contentResolver.getType(uri) ?: ""
                val ext = when {
                    mime.contains("image/jpeg") || mime.contains("image/jpg") -> ".jpg"
                    mime.contains("image/png") -> ".png"
                    mime.contains("image/webp") -> ".webp"
                    mime.contains("video/mp4") -> ".mp4"
                    mime.contains("application/pdf") -> ".pdf"
                    mime.contains("application/zip") -> ".zip"
                    mime.contains("application/json") -> ".json"
                    mime.contains("text/plain") -> ".txt"
                    else -> ".dat"
                }
                "Imported_File_${System.currentTimeMillis()}$ext"
            }
        }
        // Sanitize illegal characters for filesystem safety
        return name!!.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    /**
     * Resolves unique non-colliding filename in destination directory
     * e.g., "report.pdf" -> "report (1).pdf" if "report.pdf" already exists.
     */
    fun resolveUniqueFile(directory: File, baseName: String): File {
        val candidate = File(directory, baseName)
        if (!candidate.exists()) return candidate

        val dotIndex = baseName.lastIndexOf('.')
        val prefix = if (dotIndex > 0) baseName.substring(0, dotIndex) else baseName
        val ext = if (dotIndex > 0) baseName.substring(dotIndex) else ""

        var counter = 1
        while (true) {
            val newFile = File(directory, "$prefix ($counter)$ext")
            if (!newFile.exists()) return newFile
            counter++
        }
    }

    /**
     * Imports a file from Uri into destination directory, preserving filename and optionally AES encrypting it.
     */
    fun importFromUri(
        context: Context,
        uri: Uri,
        destinationDir: File,
        encryptWithAes: Boolean = false
    ): Result<File> {
        return try {
            destinationDir.mkdirs()
            val rawName = queryDisplayName(context, uri)
            val finalTargetName = if (encryptWithAes && !rawName.endsWith(".isovault", ignoreCase = true)) {
                "$rawName.isovault"
            } else {
                rawName
            }

            val targetFile = resolveUniqueFile(destinationDir, finalTargetName)
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(IllegalStateException("Cannot open input stream for: $uri"))

            if (encryptWithAes) {
                val rawBytes = inputStream.use { it.readBytes() }
                val encrypted = AesVaultCipher.encrypt(rawBytes)
                targetFile.writeBytes(encrypted)
            } else {
                inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }
                }
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Exports a file (e.g. from Vault or internal storage) to phone's public Downloads directory.
     * Decrypts AES .isovault containers so MT Manager and phone apps can read the exported file directly.
     */
    fun exportToPublicDownload(
        context: Context,
        sourceFile: File,
        decryptIfEncrypted: Boolean = true
    ): Result<String> {
        return try {
            val isEncrypted = sourceFile.name.endsWith(".isovault", ignoreCase = true)
            val exportFileName = if (isEncrypted && decryptIfEncrypted) {
                sourceFile.name.removeSuffix(".isovault")
            } else {
                sourceFile.name
            }

            // Method 1: On Android 10+ (Q+), use MediaStore.Downloads (Zero permissions required!)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, exportFileName)
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/IsoVault_Exported")
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val contentUri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)

                if (contentUri != null) {
                    resolver.openOutputStream(contentUri)?.use { output ->
                        writeSourceToOutputStream(sourceFile, output, isEncrypted && decryptIfEncrypted)
                    }

                    values.clear()
                    values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(contentUri, values, null, null)

                    return Result.success("Download/IsoVault_Exported/$exportFileName")
                }
            }

            // Method 2: Direct public filesystem directory (for Android 9 or when full storage permission granted)
            val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "IsoVault_Exported")
            publicDir.mkdirs()
            val targetFile = resolveUniqueFile(publicDir, exportFileName)

            FileOutputStream(targetFile).use { output ->
                writeSourceToOutputStream(sourceFile, output, isEncrypted && decryptIfEncrypted)
            }

            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                null,
                null
            )

            Result.success(targetFile.absolutePath)
        } catch (e: Exception) {
            // Method 3: Fallback to app-specific external files dir
            try {
                val appExternalDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.filesDir
                val exportDir = File(appExternalDir, "IsoVault_Exported").apply { mkdirs() }
                val targetName = if (sourceFile.name.endsWith(".isovault") && decryptIfEncrypted) {
                    sourceFile.name.removeSuffix(".isovault")
                } else sourceFile.name
                val targetFile = File(exportDir, targetName)

                FileOutputStream(targetFile).use { output ->
                    writeSourceToOutputStream(sourceFile, output, sourceFile.name.endsWith(".isovault") && decryptIfEncrypted)
                }

                Result.success(targetFile.absolutePath)
            } catch (fallbackError: Exception) {
                Result.failure(fallbackError)
            }
        }
    }

    private fun writeSourceToOutputStream(
        sourceFile: File,
        output: OutputStream,
        decrypt: Boolean
    ) {
        if (decrypt) {
            val decryptedBytes = AesVaultCipher.decrypt(sourceFile.readBytes())
            output.write(decryptedBytes)
            output.flush()
        } else {
            FileInputStream(sourceFile).use { input ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }
    }

    /**
     * Writes source file into a user-selected SAF Document Uri (from CreateDocument launcher)
     */
    fun exportToDocumentUri(
        context: Context,
        sourceFile: File,
        targetUri: Uri,
        decryptIfEncrypted: Boolean = true
    ): Result<Unit> {
        return try {
            val isEncrypted = sourceFile.name.endsWith(".isovault", ignoreCase = true)
            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                writeSourceToOutputStream(sourceFile, output, isEncrypted && decryptIfEncrypted)
            } ?: return Result.failure(IllegalStateException("Cannot open output stream for: $targetUri"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Prepares a FileProvider URI and opens standard system Share / "Open With..." chooser.
     * Allows opening directly in MT Manager, Hex Editors, Video players, etc.
     */
    fun shareFileWithExternalApp(
        context: Context,
        file: File,
        decryptIfEncrypted: Boolean = true
    ): Result<Unit> {
        return try {
            val isEncrypted = file.name.endsWith(".isovault", ignoreCase = true)
            val fileToShare = if (isEncrypted && decryptIfEncrypted) {
                // Prepare temporary decrypted copy in app cache
                val shareDir = File(context.cacheDir, "shared_exports").apply { mkdirs() }
                val tempFile = File(shareDir, file.name.removeSuffix(".isovault"))
                val decrypted = AesVaultCipher.decrypt(file.readBytes())
                tempFile.writeBytes(decrypted)
                tempFile
            } else {
                file
            }

            val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, fileToShare)
            val mimeType = resolveMimeType(fileToShare.name)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Open with MT Manager / Send file").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Batch shares multiple files via ACTION_SEND_MULTIPLE
     */
    fun batchShareFiles(
        context: Context,
        files: List<File>,
        decryptIfEncrypted: Boolean = true
    ): Result<Unit> {
        return try {
            val uris = ArrayList<Uri>()
            for (f in files) {
                if (f.isDirectory) continue
                val isEncrypted = f.name.endsWith(".isovault", ignoreCase = true)
                val targetFile = if (isEncrypted && decryptIfEncrypted) {
                    val shareDir = File(context.cacheDir, "shared_exports").apply { mkdirs() }
                    val temp = File(shareDir, f.name.removeSuffix(".isovault"))
                    temp.writeBytes(AesVaultCipher.decrypt(f.readBytes()))
                    temp
                } else f
                uris.add(FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, targetFile))
            }

            if (uris.isEmpty()) return Result.failure(IllegalStateException("No valid files to share"))

            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Batch Share (${uris.size} files)").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Compresses given files and directories into a standard .zip archive
     */
    fun compressToZip(
        sourceFiles: List<File>,
        destinationZip: File
    ): Result<File> {
        return try {
            destinationZip.parentFile?.mkdirs()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(destinationZip))).use { zos ->
                for (source in sourceFiles) {
                    addFileToZip(zos, source, "")
                }
            }
            Result.success(destinationZip)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, parentPath: String) {
        val entryName = if (parentPath.isEmpty()) file.name else "$parentPath/${file.name}"
        if (file.isDirectory) {
            val dirEntry = ZipEntry("$entryName/")
            zos.putNextEntry(dirEntry)
            zos.closeEntry()
            file.listFiles()?.forEach { child ->
                addFileToZip(zos, child, entryName)
            }
        } else {
            val entry = ZipEntry(entryName)
            zos.putNextEntry(entry)
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(32 * 1024)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    zos.write(buffer, 0, read)
                }
            }
            zos.closeEntry()
        }
    }

    /**
     * Extracts a .zip archive into targetDirectory
     */
    fun extractZip(zipFile: File, targetDirectory: File): Result<Int> {
        return try {
            targetDirectory.mkdirs()
            var extractedCount = 0
            val destCanonical = targetDirectory.canonicalPath

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val newFile = File(targetDirectory, entry.name)
                    // Protect against Zip Slip directory traversal vulnerability
                    if (!newFile.canonicalPath.startsWith(destCanonical)) {
                        throw SecurityException("Zip Slip detected in entry: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        FileOutputStream(newFile).use { fos ->
                            val buffer = ByteArray(32 * 1024)
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                        extractedCount++
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            Result.success(extractedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates Hex dump lines (16 bytes per line) for inspecting binary & encrypted files
     */
    fun generateHexDump(file: File, maxBytes: Int = 4096): List<HexLine> {
        val lines = mutableListOf<HexLine>()
        try {
            val buffer = ByteArray(maxBytes)
            val bytesRead = FileInputStream(file).use { it.read(buffer) }
            if (bytesRead <= 0) return lines

            for (i in 0 until bytesRead step 16) {
                val chunkSize = minOf(16, bytesRead - i)
                val offset = String.format("%08X", i)

                val hexSb = StringBuilder()
                val asciiSb = StringBuilder()

                for (j in 0 until 16) {
                    if (j < chunkSize) {
                        val b = buffer[i + j].toInt() and 0xFF
                        hexSb.append(String.format("%02X ", b))
                        if (j == 7) hexSb.append(" ")
                        asciiSb.append(if (b in 32..126) b.toChar() else '.')
                    } else {
                        hexSb.append("   ")
                        if (j == 7) hexSb.append(" ")
                    }
                }

                lines.add(
                    HexLine(
                        offset = offset,
                        hexString = hexSb.toString().trimEnd(),
                        asciiString = asciiSb.toString()
                    )
                )
            }
        } catch (_: Exception) {}
        return lines
    }

    fun resolveMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "pdf" -> "application/pdf"
            "txt", "log", "prop", "rc", "sh", "json", "xml" -> "text/plain"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "*/*"
        }
    }
}
