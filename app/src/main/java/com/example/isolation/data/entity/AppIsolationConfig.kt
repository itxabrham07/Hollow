package com.example.isolation.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.isolation.model.DisguiseIconPreset
import com.example.isolation.model.IsolationMode
import com.example.isolation.model.LauncherVisibilityMode

@Entity(tableName = "app_isolation_configs")
data class AppIsolationConfig(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val isIsolated: Boolean = false,
    val isolationMode: IsolationMode = IsolationMode.ENCRYPTED_VAULT,
    val encryptMediaOnly: Boolean = false,
    val blockRootPollution: Boolean = true,
    val whitelistedFolders: String = "", // comma-separated
    val customMountPath: String = "",
    val totalFilesRedirected: Long = 0,
    val totalBytesEncrypted: Long = 0,
    val lastAccessedTimestamp: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    // Independent App Hider & Disguise fields (separate from Storage Protection / isIsolated)
    val isHiddenOrDisguised: Boolean = false,
    val launcherVisibilityMode: LauncherVisibilityMode = LauncherVisibilityMode.VISIBLE,
    val customLauncherLabel: String = "",
    val disguiseIconPreset: DisguiseIconPreset = DisguiseIconPreset.CALCULATOR,
    val hideInSystemUiRecents: Boolean = true
)
