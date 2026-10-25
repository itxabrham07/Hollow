package com.example.isolation.ui.screens.files

data class VaultDisplayFile(
    val id: Long,
    val name: String,
    val relativePath: String,
    val volume: String,
    val physicalPath: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val mimeType: String,
    val isEncrypted: Boolean = true,
    val isFavorite: Boolean = false
)

enum class FileViewMode {
    EXPLORER,
    GALLERY,
    RECYCLE_BIN
}

enum class GallerySubTab {
    TIMELINE,
    ALBUMS,
    FAVORITES
}
