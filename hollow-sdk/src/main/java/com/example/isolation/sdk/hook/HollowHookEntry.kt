package com.example.isolation.sdk.hook

/**
 * Standard Hook Contract implemented by feature modules that contain LSPosed/Xposed hooks.
 * Invoked by HookBridge in Core when target packages load.
 */
interface HollowHookEntry {
    val hookTargets: List<String>
    fun initZygote(startupParam: Any? = null) {}
    fun handleLoadPackage(loadPackageParam: Any) {}
}
