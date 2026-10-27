package com.hollow.server.modules.privacy

/**
 * Server-Delivered Module Source (`module_privacy`) stored in root `/modules/module_privacy/`.
 * Packaged and encrypted by `/web/server.js` into `/data/adb/.hollow_vault/modules/module_privacy.dex.enc`
 * when an authorized device downloads `module_privacy`.
 */
object PrivacyModulePayload {
    const val MODULE_ID = "module_privacy"
    const val VERSION = 5
    val HOOK_TARGETS = listOf(
        "android.os.Build",
        "android.os.SystemProperties#native_get",
        "android.telephony.TelephonyManager#getImei",
        "android.provider.Settings\$Secure#getString",
        "android.media.MediaDrm#getPropertyByteArray",
        "android.location.LocationManager#getLastKnownLocation",
        "android.hardware.SystemSensorManager"
    )
}
