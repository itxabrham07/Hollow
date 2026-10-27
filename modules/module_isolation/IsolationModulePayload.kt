package com.hollow.server.modules.isolation

/**
 * Server-Delivered Module Source (`module_isolation`) stored in root `/modules/module_isolation/`.
 * Packaged and encrypted by `/web/server.js` into `/data/adb/.hollow_vault/modules/module_isolation.dex.enc`
 * when an authorized device downloads `module_isolation`.
 */
object IsolationModulePayload {
    const val MODULE_ID = "module_isolation"
    const val VERSION = 4
    val HOOK_TARGETS = listOf(
        "java.io.File#<init>",
        "android.os.Environment#getExternalStorageDirectory",
        "android.content.ContextWrapper#getExternalFilesDir",
        "android.content.ContentResolver#query"
    )
}
