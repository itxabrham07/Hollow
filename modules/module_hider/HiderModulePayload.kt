package com.hollow.server.modules.hider

/**
 * Server-Delivered Module Source (`module_hider`) stored in root `/modules/module_hider/`.
 * Packaged and encrypted by `/web/server.js` into `/data/adb/.hollow_vault/modules/module_hider.dex.enc`
 * when an authorized device downloads `module_hider`.
 */
object HiderModulePayload {
    const val MODULE_ID = "module_hider"
    const val VERSION = 3
    val HOOK_TARGETS = listOf(
        "android.app.ApplicationPackageManager#getInstalledApplications",
        "android.app.ApplicationPackageManager#getInstalledPackages",
        "android.app.ApplicationPackageManager#queryIntentActivities",
        "android.content.pm.LauncherApps#getActivityList",
        "com.android.systemui.recents"
    )
}
