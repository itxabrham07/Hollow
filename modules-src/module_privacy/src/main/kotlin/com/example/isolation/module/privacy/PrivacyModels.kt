package com.example.isolation.module.privacy

import android.app.ActivityManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

enum class DeviceProfilePreset(
    val label: String,
    val brand: String,
    val manufacturer: String,
    val model: String,
    val device: String,
    val product: String,
    val board: String,
    val fingerprint: String,
    val glRenderer: String = "Adreno (TM) 750",
    val glVendor: String = "Qualcomm"
) {
    REAL_HARDWARE(
        label = "Real Device (Editable)",
        brand = Build.BRAND ?: "google",
        manufacturer = Build.MANUFACTURER ?: "Google",
        model = Build.MODEL ?: "Pixel",
        device = Build.DEVICE ?: "generic",
        product = Build.PRODUCT ?: "generic",
        board = Build.BOARD ?: "generic",
        fingerprint = Build.FINGERPRINT ?: "google/generic/generic:15/AP3A.241005.015/12366759:user/release-keys",
        glRenderer = "Adreno (TM) 740",
        glVendor = "Qualcomm"
    ),
    PIXEL_9_PRO(
        label = "Pixel 9 Pro",
        brand = "google",
        manufacturer = "Google",
        model = "Pixel 9 Pro",
        device = "caiman",
        product = "caiman",
        board = "caiman",
        fingerprint = "google/caiman/caiman:15/AP3A.241005.015/12366759:user/release-keys",
        glRenderer = "Mali-G715-Immortalis MC11",
        glVendor = "ARM"
    ),
    SAMSUNG_S24_ULTRA(
        label = "Galaxy S24 Ultra",
        brand = "samsung",
        manufacturer = "samsung",
        model = "SM-S928B",
        device = "e3q",
        product = "e3qxeea",
        board = "pineapple",
        fingerprint = "samsung/e3qxeea/e3q:14/UP1A.231005.007/S928BXXS3AXFE:user/release-keys",
        glRenderer = "Adreno (TM) 750",
        glVendor = "Qualcomm"
    ),
    ONEPLUS_12(
        label = "OnePlus 12",
        brand = "OnePlus",
        manufacturer = "OnePlus",
        model = "CPH2581",
        device = "OP5929L1",
        product = "CPH2581",
        board = "pineapple",
        fingerprint = "OnePlus/CPH2581/OP5929L1:14/UKQ1.230924.001/U.16992ef-1-2:user/release-keys",
        glRenderer = "Adreno (TM) 750",
        glVendor = "Qualcomm"
    )
}

data class RealHardwareSnapshot(
    val brand: String,
    val manufacturer: String,
    val model: String,
    val device: String,
    val product: String,
    val board: String,
    val fingerprint: String,
    val serial: String,
    val androidId: String,
    val carrierName: String,
    val glVendor: String,
    val glRenderer: String,
    val cpuCores: Int,
    val ramGb: Int,
    val screenResolution: String,
    val screenDpi: Int,
    val timezoneId: String,
    val localeTag: String
)

object RealDeviceHardware {
    @Volatile
    private var cached: RealHardwareSnapshot? = null

    fun read(context: Context): RealHardwareSnapshot {
        cached?.let { return it }
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "9774d56d682e549c"
        } catch (_: Throwable) { "9774d56d682e549c" }

        val snapshot = RealHardwareSnapshot(
            brand = Build.BRAND ?: "google",
            manufacturer = Build.MANUFACTURER ?: "Google",
            model = Build.MODEL ?: "Pixel",
            device = Build.DEVICE ?: "generic",
            product = Build.PRODUCT ?: "generic",
            board = Build.BOARD ?: "generic",
            fingerprint = Build.FINGERPRINT ?: "google/generic:15/release-keys",
            serial = "unknown",
            androidId = androidId,
            carrierName = "T-Mobile",
            glVendor = "Qualcomm",
            glRenderer = "Adreno (TM) 740",
            cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(4),
            ramGb = 8,
            screenResolution = "1080x2400",
            screenDpi = 420,
            timezoneId = TimeZone.getDefault().id,
            localeTag = Locale.getDefault().toLanguageTag()
        )
        cached = snapshot
        return snapshot
    }
}

data class SpoofedAppEntry(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val icon: Drawable? = null,
    val isSpoofed: Boolean = false,
    val preset: DeviceProfilePreset = DeviceProfilePreset.PIXEL_9_PRO,
    val brand: String = "",
    val model: String = "",
    val androidId: String = ""
)
