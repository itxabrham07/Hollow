package com.example.isolation.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.isolation.model.DeviceProfilePreset
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
    val hideInSystemUiRecents: Boolean = true,
    val hideFromPlayStore: Boolean = false,
    val spoofInSettings: Boolean = true,
    val customIconBase64: String = "",
    val customIconDonorPkg: String = "",
    // Independent Per-App Device Identity & Privacy Spoofing fields
    val isIdentitySpoofed: Boolean = false,
    val deviceProfilePreset: DeviceProfilePreset = DeviceProfilePreset.PIXEL_9_PRO,
    val spoofBrand: String = "",
    val spoofModel: String = "",
    val spoofDevice: String = "",
    val spoofAndroidId: String = "",
    val spoofImei: String = "",
    val spoofAdvertisingId: String = "",
    val spoofMacAddress: String = "",
    val spoofCarrierName: String = "",
    val spoofSimOperator: String = "",
    val spoofSerial: String = "",
    // Location / GPS Mocking
    val spoofLocationEnabled: Boolean = false,
    val spoofLatitude: Double = 37.7749,
    val spoofLongitude: Double = -122.4194,
    val hideMockLocation: Boolean = true,
    // Privacy & System Guards
    val preventScreenshots: Boolean = false,
    val hideOtherApps: Boolean = false,
    val disableLogcatLogging: Boolean = false,
    val fakeBatteryEnabled: Boolean = false,
    val fakeBatteryLevel: Int = 100,
    val knoxWarrantyBitSpoof: Boolean = false,
    // Environment & Security Shields
    val hideRootAndXposed: Boolean = true,
    val hideAdbAndDevOptions: Boolean = true,
    val hideVpnAndProxy: Boolean = true,
    val blockClipboardRead: Boolean = false,
    // Networking
    val disableAllNetworking: Boolean = false
)
