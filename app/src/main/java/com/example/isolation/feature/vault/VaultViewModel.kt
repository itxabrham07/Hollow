package com.example.isolation.feature.vault

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.isolation.core.data.repository.VaultRepository
import com.example.isolation.core.result.onError
import com.example.isolation.core.result.onSuccess
import com.example.isolation.data.entity.VaultFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Focused ViewModel for the Private Encrypted Vault & Locker.
 * Handles AES-GCM-256 encryption, secure text/photo previews, imports, exports, and file deletion.
 */
class VaultViewModel(
    application: Application,
    private val vaultRepository: VaultRepository
) : AndroidViewModel(application) {

    val vaultFiles: StateFlow<List<VaultFileItem>> = vaultRepository.getAllVaultFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalEncryptedBytes: StateFlow<Long> = vaultRepository.getTotalEncryptedBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _previewDecryptedText = MutableStateFlow<Pair<String, String>?>(null)
    val previewDecryptedText: StateFlow<Pair<String, String>?> = _previewDecryptedText.asStateFlow()

    private val _previewDecryptedBitmap = MutableStateFlow<Pair<String, Bitmap>?>(null)
    val previewDecryptedBitmap: StateFlow<Pair<String, Bitmap>?> = _previewDecryptedBitmap.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun postStatusMessage(msg: String) {
        _statusMessage.value = msg
    }

    fun clearPreview() {
        _previewDecryptedText.value = null
        _previewDecryptedBitmap.value = null
    }

    fun addNote(name: String, content: String) {
        viewModelScope.launch {
            vaultRepository.addNote(name, content)
                .onSuccess { item ->
                    _statusMessage.value = "Saved ${item.relativePath} to the encrypted locker."
                }
                .onError { _, message ->
                    _statusMessage.value = "Could not save note: $message"
                }
        }
    }

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            vaultRepository.importFile(uri)
                .onSuccess { item ->
                    _statusMessage.value = "Encrypted ${item.relativePath} into the Private Locker."
                }
                .onError { _, message ->
                    _statusMessage.value = "Import failed: $message"
                }
        }
    }

    fun encryptLocalFileToLocker(sourceFile: File, deleteOriginal: Boolean = false) {
        viewModelScope.launch {
            vaultRepository.encryptLocalFile(sourceFile, deleteOriginal)
                .onSuccess { item ->
                    _statusMessage.value = if (deleteOriginal) {
                        "Encrypted & shredded original: ${item.relativePath}"
                    } else {
                        "Encrypted copy of ${item.relativePath} into Private Locker."
                    }
                }
                .onError { _, message ->
                    _statusMessage.value = "Encryption failed: $message"
                }
        }
    }

    fun exportFile(item: VaultFileItem, destination: Uri) {
        viewModelScope.launch {
            vaultRepository.exportFile(item, destination)
                .onSuccess {
                    _statusMessage.value = "Exported ${item.relativePath.substringAfterLast('/')}."
                }
                .onError { _, message ->
                    _statusMessage.value = "Export failed: $message"
                }
        }
    }

    fun decryptVaultFileToWorkspace(item: VaultFileItem) {
        viewModelScope.launch {
            vaultRepository.decryptVaultFileToWorkspace(item)
                .onSuccess { file ->
                    _statusMessage.value = "Decrypted ${file.name} to Workspace/Decrypted."
                }
                .onError { _, message ->
                    _statusMessage.value = "Decryption failed: $message"
                }
        }
    }

    fun deleteVaultFile(item: VaultFileItem) {
        viewModelScope.launch {
            vaultRepository.deleteVaultFile(item)
                .onSuccess {
                    _statusMessage.value = "Deleted ${item.relativePath.substringAfterLast('/')}."
                }
                .onError { _, message ->
                    _statusMessage.value = "Deletion failed: $message"
                }
        }
    }

    fun decryptAndPreviewFile(item: VaultFileItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val isImage = item.mimeType.startsWith("image/") ||
                    item.relativePath.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

            if (isImage) {
                vaultRepository.decryptPreviewBitmap(item)
                    .onSuccess { pair ->
                        withContext(Dispatchers.Main) { _previewDecryptedBitmap.value = pair }
                    }
                    .onError { _, message ->
                        withContext(Dispatchers.Main) { _statusMessage.value = message }
                    }
            } else {
                vaultRepository.decryptPreviewText(item)
                    .onSuccess { pair ->
                        withContext(Dispatchers.Main) { _previewDecryptedText.value = pair }
                    }
                    .onError { _, message ->
                        withContext(Dispatchers.Main) { _statusMessage.value = message }
                    }
            }
        }
    }

    fun purgeVaultForPackage(packageName: String) {
        viewModelScope.launch {
            vaultRepository.purgeVaultForPackage(packageName)
                .onSuccess {
                    _statusMessage.value = "Purged all vault entries for $packageName."
                }
                .onError { _, message ->
                    _statusMessage.value = "Purge failed: $message"
                }
        }
    }
}
