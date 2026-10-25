package com.example.isolation.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.isolation.model.IsolationMode

@Entity(tableName = "app_isolation_configs")
data class AppIsolationConfig(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val isIsolated: Boolean = false,
    val isolationMode: IsolationMode = IsolationMode.ENCRYPTED_VAULT,
    val encryptMediaOnly: Boolean = false,
    val blockRootPollution: Boolean = true,
    val whitelistedFolders: String = "Download,Documents", // Comma-separated relative folder names
    val totalFilesRedirected: Int = 0,
    val totalBytesEncrypted: Long = 0L,
    val lastAccessedTimestamp: Long = System.currentTimeMillis(),
    // v2 Storage Volumes & Hook Status fields
    val useSharedSpace: Boolean = false,
    val volumeQuotaMb: Int = 0, // 0 = unlimited
    val wrappedVolumeKey: String = "", // Base64 AES-256-GCM MasterKey-wrapped 256-bit volume DEK
    val activeHooksMask: Int = 0x1F // Bitmask: 1=File, 2=Os/IoBridge, 4=MediaStore, 8=Picker, 16=SharedSpace
)
