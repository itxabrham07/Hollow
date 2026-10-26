package com.example.isolation.hook

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageItemInfo
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Binder
import android.os.Process
import android.view.View
import com.example.isolation.model.DisguiseIconPreset
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.model.LauncherVisibilityMode
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.ConcurrentHashMap

/**
 * System-wide App Hider, Custom Name/Icon Disguise, and Direct SystemUI Injection engine,
 * architected to match Vector (`Vector-master`):
 *
 *  1. Hooks `system_server` (`lpparam.packageName == "android"` via `LegacyDelegateImpl.onSystemServerLoaded`):
 *     - `LauncherAppsService$LauncherAppsImpl.getLauncherActivities`: Filters hidden apps and injects
 *       `nonLocalizedLabel = customLabel` into `ActivityInfo`/`ApplicationInfo` across Binder so
 *       **any existing or newly installed launcher** (even one not in Vector's scope) automatically
 *       hides apps and displays custom user-typed names.
 *     - `ComputerEngine` / `PackageManagerService.queryIntentActivitiesInternal`: Filters hidden apps
 *       and spoofs labels on `ResolveInfo.activityInfo` while exempting Hollow's own queries and
 *       direct package launches.
 *     - `ActivityTaskSupervisor.resolveActivity` (modeled on Vector's `ParasiticManagerSystemHooker`):
 *       Resolves stealth launch intents (`LAUNCH_HOLLOW`) and applies `FLAG_EXCLUDE_FROM_RECENTS`
 *       when recents masking is enabled.
 *  2. Hooks `com.android.systemui` directly:
 *     - Hooks `onAttachedToWindow` and `performLongClick` on Quick Settings / Status Bar `Clock`,
 *       `DateView`, and `QuickStatusBarHeader` (preventing SystemUI controllers from overwriting
 *       the listener) and collapses the notification shade before launching Hollow.
 *  3. Hooks `LauncherApps`, `ApplicationPackageManager`, and `PackageItemInfo` in client processes
 *     so custom decoy icons and labels render in launchers, Share sheets (`system` / `android:ui`),
 *     and SystemUI Recents.
 */
object SystemStealthHook {

    private const val MODULE_PKG = IsolationRuleManager.MODULE_PKG
    private const val PROVIDER_URI = "content://com.aistudio.isovault.xrvf.provider"
    const val CATEGORY_LAUNCH_HOLLOW = "$MODULE_PKG.LAUNCH_HOLLOW"

    @Volatile
    private var cachedStealthMap: Map<String, IsolationRuleManager.StealthRule> = emptyMap()

    @Volatile
    private var cachedSystemUiTrigger: Boolean = true

    @Volatile
    private var lastStealthFetchMs: Long = 0L

    @Volatile
    private var hasFetchedOnce: Boolean = false

    @Volatile
    private var fallbackXSharedPrefs: XSharedPreferences? = null

    @Volatile
    private var cachedHollowAppId: Int = -1

    private val decoyBitmapCache = ConcurrentHashMap<DisguiseIconPreset, Bitmap>()

    fun attachForPackage(
        lpparam: XC_LoadPackage.LoadPackageParam,
        currentPkg: String,
        xSharedPrefs: XSharedPreferences?
    ) {
        // Never hide or spoof apps inside Hollow itself so Hollow can always see and launch everything
        if (currentPkg == MODULE_PKG) return

        if (xSharedPrefs != null && fallbackXSharedPrefs == null) {
            fallbackXSharedPrefs = xSharedPrefs
        }

        // 1. Attach global PackageManager / LauncherApps / PackageItemInfo hooks
        attachGlobalPackageAndLauncherHooks(lpparam, currentPkg, xSharedPrefs)

        // 2. If inside System Server (`android` in Vector's LegacyDelegateImpl), hook
        //    LauncherAppsService, ComputerEngine/PackageManagerService, and ActivityTaskSupervisor
        if (currentPkg == "android") {
            attachSystemServerPackageManagerHooks(lpparam, xSharedPrefs)
            attachSystemServerLauncherAppsHooks(lpparam, xSharedPrefs)
            attachSystemServerActivitySupervisorHooks(lpparam, xSharedPrefs)
        }

        // 3. If inside SystemUI (`com.android.systemui`), inject the direct SystemUI stealth trigger
        if (currentPkg == "com.android.systemui") {
            attachSystemUiDirectHooks(lpparam, xSharedPrefs)
        }
    }

    private fun obtainXSharedPrefs(passed: XSharedPreferences?): XSharedPreferences? {
        if (passed != null) return passed
        val existing = fallbackXSharedPrefs
        if (existing != null) return existing
        return try {
            XSharedPreferences(MODULE_PKG, IsolationRuleManager.PREFS_NAME).also {
                fallbackXSharedPrefs = it
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun getStealthRules(
        xSharedPrefs: XSharedPreferences?,
        isSystemServer: Boolean = false
    ): Map<String, IsolationRuleManager.StealthRule> {
        val now = System.currentTimeMillis()
        if (hasFetchedOnce && (now - lastStealthFetchMs) < 2500L) {
            return cachedStealthMap
        }
        lastStealthFetchMs = now
        hasFetchedOnce = true

        // Channel 1: XSharedPreferences (backed by VectorServiceClient.getPrefsPath in Vector)
        try {
            val prefs = obtainXSharedPrefs(xSharedPrefs)
            if (prefs != null) {
                if (prefs.hasFileChanged()) {
                    prefs.reload()
                }
                val raw = prefs.getString(IsolationRuleManager.KEY_STEALTH_TABLE, null)
                cachedSystemUiTrigger = prefs.getBoolean(IsolationRuleManager.KEY_SYSTEMUI_TRIGGER, true)
                if (raw != null) {
                    val parsed = IsolationRuleManager.parseStealthTable(raw)
                    cachedStealthMap = parsed
                    return parsed
                }
            }
        } catch (_: Throwable) {}

        // Never make synchronous ContentResolver IPC calls from inside system_server
        if (isSystemServer || Process.myUid() == Process.SYSTEM_UID) {
            return cachedStealthMap
        }

        // Channel 2: ContentProvider IPC fallback for regular client processes
        try {
            val app = AndroidAppHelper.currentApplication()
            if (app != null) {
                val bundle = app.contentResolver.call(
                    Uri.parse(PROVIDER_URI),
                    "get_stealth_table",
                    app.packageName,
                    null
                )
                if (bundle != null) {
                    val raw = bundle.getString("stealthTable", "")
                    cachedSystemUiTrigger = bundle.getBoolean("systemUiTriggerEnabled", true)
                    val parsed = IsolationRuleManager.parseStealthTable(raw)
                    cachedStealthMap = parsed
                    return parsed
                }
            }
        } catch (_: Throwable) {}

        return cachedStealthMap
    }

    private fun attachGlobalPackageAndLauncherHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        currentPkg: String,
        xSharedPrefs: XSharedPreferences?
    ) {
        val isSysServer = currentPkg == "android"

        // A. Hook LauncherApps.getActivityList(String, UserHandle) -> used by all modern launchers
        try {
            XposedBridge.hookAllMethods(
                LauncherApps::class.java,
                "getActivityList",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val list = param.result as? List<*> ?: return
                        val rules = getStealthRules(xSharedPrefs, isSysServer)
                        if (rules.isEmpty()) return

                        val filtered = list.filter { item ->
                            val info = item as? LauncherActivityInfo ?: return@filter true
                            val targetPkg = info.componentName?.packageName ?: return@filter true
                            if (targetPkg == currentPkg) return@filter true
                            val rule = rules[targetPkg]
                            rule?.visibilityMode != LauncherVisibilityMode.HIDDEN
                        }
                        if (filtered.size != list.size) {
                            param.result = ArrayList(filtered)
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // B. Hook ApplicationPackageManager.queryIntentActivities / queryIntentActivitiesAsUser
        try {
            val apmClass = XposedHelpers.findClass(
                "android.app.ApplicationPackageManager",
                lpparam.classLoader
            )
            val queryHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val intent = param.args.firstOrNull { it is Intent } as? Intent ?: return
                    val categories = intent.categories
                    val isLauncherQuery = categories?.contains(Intent.CATEGORY_LAUNCHER) == true ||
                            categories?.contains(Intent.CATEGORY_INFO) == true ||
                            intent.action == Intent.ACTION_MAIN
                    if (!isLauncherQuery) return

                    // Allow direct launch intent lookup from SystemUI so SystemUI can launch hidden Hollow
                    if (currentPkg == "com.android.systemui" &&
                        (intent.`package` == MODULE_PKG || intent.component?.packageName == MODULE_PKG)
                    ) {
                        return
                    }

                    val list = param.result as? List<*> ?: return
                    val rules = getStealthRules(xSharedPrefs, isSysServer)
                    if (rules.isEmpty()) return

                    val filtered = list.filter { item ->
                        val ri = item as? ResolveInfo ?: return@filter true
                        val targetPkg = ri.activityInfo?.packageName
                            ?: ri.resolvePackageName
                            ?: return@filter true
                        if (targetPkg == currentPkg) return@filter true
                        val rule = rules[targetPkg]
                        if (rule != null &&
                            rule.visibilityMode == LauncherVisibilityMode.DISGUISED &&
                            rule.customLabel.isNotBlank()
                        ) {
                            applyDisguiseLabelToResolveInfo(ri, rule.customLabel)
                        }
                        rule?.visibilityMode != LauncherVisibilityMode.HIDDEN
                    }
                    if (filtered.size != list.size) {
                        param.result = ArrayList(filtered)
                    }
                }
            }
            XposedBridge.hookAllMethods(apmClass, "queryIntentActivities", queryHook)
            XposedBridge.hookAllMethods(apmClass, "queryIntentActivitiesAsUser", queryHook)
        } catch (_: Throwable) {}

        // C. Hook LauncherActivityInfo.getLabel() -> custom user-typed name in any launcher
        try {
            XposedBridge.hookAllMethods(
                LauncherActivityInfo::class.java,
                "getLabel",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val info = param.thisObject as? LauncherActivityInfo ?: return
                        val targetPkg = info.componentName?.packageName ?: return
                        if (targetPkg == currentPkg) return
                        val rule = getStealthRules(xSharedPrefs, isSysServer)[targetPkg] ?: return
                        if (rule.visibilityMode == LauncherVisibilityMode.DISGUISED && rule.customLabel.isNotBlank()) {
                            param.result = rule.customLabel
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // D. Hook PackageItemInfo.loadLabel(PackageManager) & ApplicationPackageManager.getApplicationLabel
        try {
            XposedBridge.hookAllMethods(
                PackageItemInfo::class.java,
                "loadLabel",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val item = param.thisObject as? PackageItemInfo ?: return
                        val targetPkg = item.packageName ?: return
                        if (targetPkg == currentPkg) return
                        val rule = getStealthRules(xSharedPrefs, isSysServer)[targetPkg] ?: return
                        if (rule.visibilityMode == LauncherVisibilityMode.DISGUISED && rule.customLabel.isNotBlank()) {
                            if (currentPkg == "com.android.systemui" && !rule.hideInRecents) return
                            param.result = rule.customLabel
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        try {
            val apmClass = XposedHelpers.findClass(
                "android.app.ApplicationPackageManager",
                lpparam.classLoader
            )
            XposedBridge.hookAllMethods(
                apmClass,
                "getApplicationLabel",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val appInfo = param.args.firstOrNull() as? ApplicationInfo ?: return
                        val targetPkg = appInfo.packageName ?: return
                        if (targetPkg == currentPkg) return
                        val rule = getStealthRules(xSharedPrefs, isSysServer)[targetPkg] ?: return
                        if (rule.visibilityMode == LauncherVisibilityMode.DISGUISED && rule.customLabel.isNotBlank()) {
                            if (currentPkg == "com.android.systemui" && !rule.hideInRecents) return
                            param.result = rule.customLabel
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // E. Hook LauncherActivityInfo.getIcon / getBadgedIcon & PackageItemInfo.loadIcon
        val iconHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val targetPkg = when (val obj = param.thisObject) {
                    is LauncherActivityInfo -> obj.componentName?.packageName
                    is PackageItemInfo -> obj.packageName
                    is ActivityInfo -> obj.packageName
                    is ApplicationInfo -> obj.packageName
                    else -> null
                } ?: return
                if (targetPkg == currentPkg) return
                val rule = getStealthRules(xSharedPrefs, isSysServer)[targetPkg] ?: return
                if (rule.visibilityMode == LauncherVisibilityMode.DISGUISED &&
                    rule.iconPreset != DisguiseIconPreset.DEFAULT
                ) {
                    if (currentPkg == "com.android.systemui" && !rule.hideInRecents) return
                    param.result = buildDecoyDrawable(rule.iconPreset)
                }
            }
        }

        try {
            XposedBridge.hookAllMethods(LauncherActivityInfo::class.java, "getIcon", iconHook)
            XposedBridge.hookAllMethods(LauncherActivityInfo::class.java, "getBadgedIcon", iconHook)
            XposedBridge.hookAllMethods(PackageItemInfo::class.java, "loadIcon", iconHook)
            XposedBridge.hookAllMethods(PackageItemInfo::class.java, "loadUnbadgedIcon", iconHook)
        } catch (_: Throwable) {}
    }

    /**
     * System Server (`android`) hook on `LauncherAppsService$LauncherAppsImpl.getLauncherActivities`.
     *
     * Because every modern launcher (including newly installed launchers that are NOT in Vector's
     * module scope!) fetches its app drawer list from `LauncherAppsService` over Binder, filtering
     * hidden apps and writing `nonLocalizedLabel` into the returned `ActivityInfo` in `system_server`
     * makes hiding and custom app names work in **any newly installed launcher** with zero extra setup.
     */
    private fun attachSystemServerLauncherAppsHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        xSharedPrefs: XSharedPreferences?
    ) {
        try {
            val implCls = XposedHelpers.findClassIfExists(
                "com.android.server.pm.LauncherAppsService\$LauncherAppsImpl",
                lpparam.classLoader
            ) ?: return

            XposedBridge.hookAllMethods(
                implCls,
                "getLauncherActivities",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val callingPkg = param.args.firstOrNull { it is String } as? String
                        // Never filter or disguise when Hollow itself queries LauncherApps
                        if (callingPkg == MODULE_PKG) return

                        val rules = getStealthRules(xSharedPrefs, isSystemServer = true)
                        if (rules.isEmpty()) return

                        val rawResult = param.result ?: return
                        // On Android 10+, getLauncherActivities returns ParceledListSlice<LauncherActivityInfoInternal>
                        // (or List<ResolveInfo> on older versions).
                        if (rawResult is List<*>) {
                            param.result = filterAndDisguiseLauncherItems(rawResult, callingPkg, rules)
                        } else if (rawResult.javaClass.name == "android.content.pm.ParceledListSlice") {
                            try {
                                val list = XposedHelpers.callMethod(rawResult, "getList") as? List<*> ?: return
                                val modified = filterAndDisguiseLauncherItems(list, callingPkg, rules)
                                param.result = XposedHelpers.newInstance(rawResult.javaClass, modified)
                            } catch (_: Throwable) {}
                        }
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    private fun filterAndDisguiseLauncherItems(
        items: List<*>,
        callingPkg: String?,
        rules: Map<String, IsolationRuleManager.StealthRule>
    ): List<Any?> {
        val out = ArrayList<Any?>(items.size)
        for (item in items) {
            if (item == null) continue
            val activityInfo: ActivityInfo? = when (item) {
                is ResolveInfo -> item.activityInfo
                is ActivityInfo -> item
                else -> try {
                    XposedHelpers.getObjectField(item, "mActivityInfo") as? ActivityInfo
                } catch (_: Throwable) {
                    null
                }
            }
            val targetPkg = activityInfo?.packageName
            if (targetPkg != null && targetPkg != callingPkg) {
                val rule = rules[targetPkg]
                if (rule != null) {
                    if (rule.visibilityMode == LauncherVisibilityMode.HIDDEN) {
                        continue // Omit hidden app from launcher drawer
                    }
                    if (rule.visibilityMode == LauncherVisibilityMode.DISGUISED &&
                        rule.customLabel.isNotBlank() &&
                        activityInfo != null
                    ) {
                        applyDisguiseLabelToActivityInfo(item, activityInfo, rule.customLabel)
                    }
                }
            }
            out.add(item)
        }
        return out
    }

    private fun applyDisguiseLabelToActivityInfo(
        container: Any,
        originalInfo: ActivityInfo,
        customLabel: String
    ) {
        try {
            val clonedActivityInfo = ActivityInfo(originalInfo).apply {
                nonLocalizedLabel = customLabel
                labelRes = 0
                if (applicationInfo != null) {
                    applicationInfo = ApplicationInfo(applicationInfo).apply {
                        nonLocalizedLabel = customLabel
                        labelRes = 0
                    }
                }
            }
            when (container) {
                is ResolveInfo -> container.activityInfo = clonedActivityInfo
                else -> XposedHelpers.setObjectField(container, "mActivityInfo", clonedActivityInfo)
            }
        } catch (_: Throwable) {}
    }

    private fun applyDisguiseLabelToResolveInfo(ri: ResolveInfo, customLabel: String) {
        val ai = ri.activityInfo ?: return
        applyDisguiseLabelToActivityInfo(ri, ai, customLabel)
        ri.nonLocalizedLabel = customLabel
        ri.labelRes = 0
    }

    /**
     * System Server (`android`) hook on `ComputerEngine` / `PackageManagerService` so any launcher
     * querying `queryIntentActivities` or `getApplicationInfo` receives the filtered list and
     * custom `nonLocalizedLabel`.
     */
    private fun attachSystemServerPackageManagerHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        xSharedPrefs: XSharedPreferences?
    ) {
        val candidateClasses = listOf(
            "com.android.server.pm.ComputerEngine",
            "com.android.server.pm.PackageManagerService"
        )
        for (clsName in candidateClasses) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                XposedBridge.hookAllMethods(
                    cls,
                    "queryIntentActivitiesInternal",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val intent = param.args.firstOrNull { it is Intent } as? Intent ?: return
                            val categories = intent.categories
                            val isLauncherQuery = categories?.contains(Intent.CATEGORY_LAUNCHER) == true ||
                                    categories?.contains(Intent.CATEGORY_INFO) == true
                            if (!isLauncherQuery) return

                            val list = param.result as? List<*> ?: return
                            val callingAppId = Binder.getCallingUid() % 100_000

                            // Cache Hollow's appId whenever we see it in ResolveInfo so we never filter Hollow's own queries
                            if (cachedHollowAppId < 0) {
                                for (item in list) {
                                    val ri = item as? ResolveInfo ?: continue
                                    if (ri.activityInfo?.packageName == MODULE_PKG) {
                                        val uid = ri.activityInfo?.applicationInfo?.uid ?: -1
                                        if (uid > 0) cachedHollowAppId = uid % 100_000
                                        break
                                    }
                                }
                            }
                            if (callingAppId == cachedHollowAppId) return

                            // If this is a direct single-package launch lookup (e.g. getLaunchIntentForPackage
                            // called from Hollow or SystemUI), allow it so hidden apps can still be launched!
                            val explicitPkg = intent.`package` ?: intent.component?.packageName
                            if (!explicitPkg.isNullOrEmpty() &&
                                (callingAppId == Process.SYSTEM_UID || callingAppId == cachedHollowAppId)
                            ) {
                                return
                            }

                            val rules = getStealthRules(xSharedPrefs, isSystemServer = true)
                            if (rules.isEmpty()) return

                            val out = ArrayList<ResolveInfo>(list.size)
                            var modified = false
                            for (item in list) {
                                val ri = item as? ResolveInfo ?: continue
                                val targetPkg = ri.activityInfo?.packageName ?: run {
                                    out.add(ri)
                                    continue
                                }
                                // Never hide an app from itself
                                val targetAppId = (ri.activityInfo?.applicationInfo?.uid ?: -1) % 100_000
                                if (targetAppId > 0 && targetAppId == callingAppId) {
                                    out.add(ri)
                                    continue
                                }
                                val rule = rules[targetPkg]
                                if (rule?.visibilityMode == LauncherVisibilityMode.HIDDEN) {
                                    modified = true
                                    continue
                                }
                                if (rule != null &&
                                    rule.visibilityMode == LauncherVisibilityMode.DISGUISED &&
                                    rule.customLabel.isNotBlank()
                                ) {
                                    val clonedRi = ResolveInfo(ri)
                                    applyDisguiseLabelToResolveInfo(clonedRi, rule.customLabel)
                                    out.add(clonedRi)
                                    modified = true
                                } else {
                                    out.add(ri)
                                }
                            }
                            if (modified) {
                                param.result = out
                            }
                        }
                    }
                )
            } catch (_: Throwable) {}
        }
    }

    /**
     * System Server (`android`) hook on `ActivityTaskSupervisor.resolveActivity` (modeled directly
     * after `ParasiticManagerSystemHooker.kt` in `Vector-master`):
     *  - Applies `ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS` to apps whose stealth rule has
     *    `hideInRecents = true` and `visibilityMode == HIDDEN` so they never leave a card in Recents.
     */
    @SuppressLint("PrivateApi")
    private fun attachSystemServerActivitySupervisorHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        xSharedPrefs: XSharedPreferences?
    ) {
        val supervisorClasses = listOf(
            "com.android.server.wm.ActivityTaskSupervisor",
            "com.android.server.wm.ActivityStackSupervisor",
            "com.android.server.am.ActivityStackSupervisor"
        )
        for (clsName in supervisorClasses) {
            try {
                val supervisorClass = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                XposedBridge.hookAllMethods(
                    supervisorClass,
                    "resolveActivity",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val originalInfo = param.result as? ActivityInfo ?: return
                            val targetPkg = originalInfo.packageName ?: return
                            val rule = getStealthRules(xSharedPrefs, isSystemServer = true)[targetPkg] ?: return

                            if (rule.hideInRecents && rule.visibilityMode == LauncherVisibilityMode.HIDDEN) {
                                val copy = ActivityInfo(originalInfo).apply {
                                    flags = flags or ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS
                                }
                                param.result = copy
                            } else if (rule.visibilityMode == LauncherVisibilityMode.DISGUISED &&
                                rule.customLabel.isNotBlank()
                            ) {
                                val copy = ActivityInfo(originalInfo).apply {
                                    nonLocalizedLabel = rule.customLabel
                                    labelRes = 0
                                }
                                param.result = copy
                            }
                        }
                    }
                )
                break
            } catch (_: Throwable) {}
        }
    }

    /**
     * Direct SystemUI (`com.android.systemui`) injection:
     * Hooks both `onAttachedToWindow` and `performLongClick` on the Quick Settings / Status Bar
     * Clock & Date views so SystemUI's internal `ShadeHeaderController` cannot overwrite our
     * listener, and collapses the notification shade before launching Hollow.
     */
    private fun attachSystemUiDirectHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        xSharedPrefs: XSharedPreferences?
    ) {
        val systemUiHeaderViews = listOf(
            "com.android.systemui.statusbar.policy.Clock",
            "com.android.systemui.statusbar.policy.DateView",
            "com.android.systemui.qs.QuickStatusBarHeader"
        )

        for (viewClassName in systemUiHeaderViews) {
            try {
                val viewCls = XposedHelpers.findClassIfExists(viewClassName, lpparam.classLoader) ?: continue

                XposedBridge.hookAllConstructors(
                    viewCls,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val view = param.thisObject as? View ?: return
                            view.isLongClickable = true
                        }
                    }
                )

                XposedBridge.hookAllMethods(
                    viewCls,
                    "onAttachedToWindow",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val view = param.thisObject as? View ?: return
                            view.isLongClickable = true
                        }
                    }
                )

                XposedBridge.hookAllMethods(
                    viewCls,
                    "performLongClick",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val view = param.thisObject as? View ?: return
                            getStealthRules(xSharedPrefs, isSystemServer = false)
                            if (!cachedSystemUiTrigger) return
                            launchHollowFromSystemUi(view.context)
                            param.result = true
                        }
                    }
                )
            } catch (_: Throwable) {}
        }
    }

    @SuppressLint("WrongConstant")
    fun launchHollowFromSystemUi(context: Context) {
        // Collapse the SystemUI Notification / Quick Settings shade so Hollow opens in front of the user
        try {
            val sbm = context.getSystemService("statusbar")
            if (sbm != null) {
                XposedHelpers.callMethod(sbm, "collapsePanels")
            }
        } catch (_: Throwable) {}

        val candidates = listOf(
            ComponentName(MODULE_PKG, "com.example.MainActivity"),
            ComponentName(MODULE_PKG, "com.example.MainActivityCalculatorAlias"),
            ComponentName(MODULE_PKG, "com.example.MainActivityNotesAlias")
        )
        for (component in candidates) {
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    this.component = component
                    addCategory(CATEGORY_LAUNCH_HOLLOW)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(intent)
                return
            } catch (_: Throwable) {}
        }
    }

    /**
     * Generates a clean, high-DPI Material icon Drawable in-memory for the chosen [DisguiseIconPreset]
     * so any process (`com.android.systemui` or any launcher) can render the decoy icon without
     * needing cross-package resource loading.
     */
    fun buildDecoyDrawable(preset: DisguiseIconPreset): Drawable {
        val bmp = decoyBitmapCache.getOrPut(preset) {
            val size = 192
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Background rounded squircle
            val bgColor = when (preset) {
                DisguiseIconPreset.CALCULATOR -> Color.parseColor("#1E3A5F")
                DisguiseIconPreset.NOTES -> Color.parseColor("#3E2E14")
                DisguiseIconPreset.CALENDAR -> Color.parseColor("#18392B")
                DisguiseIconPreset.CLOCK -> Color.parseColor("#2A244D")
                DisguiseIconPreset.WEATHER -> Color.parseColor("#16384C")
                DisguiseIconPreset.SYSTEM_UTILITY -> Color.parseColor("#262B33")
                DisguiseIconPreset.DEFAULT -> Color.parseColor("#1E3A5F")
            }
            paint.color = bgColor
            canvas.drawRoundRect(RectF(8f, 8f, 184f, 184f), 44f, 44f, paint)

            // Foreground symbol
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val symbol = when (preset) {
                DisguiseIconPreset.CALCULATOR -> "±="
                DisguiseIconPreset.NOTES -> "≡"
                DisguiseIconPreset.CALENDAR -> "31"
                DisguiseIconPreset.CLOCK -> "◷"
                DisguiseIconPreset.WEATHER -> "☀"
                DisguiseIconPreset.SYSTEM_UTILITY -> "⚙"
                DisguiseIconPreset.DEFAULT -> "H"
            }
            paint.textSize = 76f
            val yPos = (size / 2f) - ((paint.descent() + paint.ascent()) / 2f)
            canvas.drawText(symbol, size / 2f, yPos, paint)
            bitmap
        }
        return BitmapDrawable(Resources.getSystem(), bmp)
    }
}
