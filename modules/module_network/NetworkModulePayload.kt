package com.hollow.server.modules.network

/**
 * Server-Delivered Module Source (`module_network`) stored in root `/modules/module_network/`.
 * Packaged and encrypted by `/web/server.js` into `/data/adb/.hollow_vault/modules/module_network.dex.enc`
 * when an authorized device downloads `module_network`.
 */
object NetworkModulePayload {
    const val MODULE_ID = "module_network"
    const val VERSION = 2
    val HOOK_TARGETS = listOf(
        "java.net.Socket#connect",
        "java.net.URL#openConnection",
        "android.net.ConnectivityManager#getNetworkCapabilities",
        "java.net.NetworkInterface#getNetworkInterfaces",
        "javax.net.ssl.SSLContext#init"
    )
}
