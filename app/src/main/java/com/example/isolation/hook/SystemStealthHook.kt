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
import android.content.pm.PackageInfo
import android.content.pm.PackageItemInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Process
import android.util.Base64
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lock-free, non-blocking System-wide App Hider, Custom Name/Icon Disguise, Play Store Hider,
 * System Settings App Management Spoofer, and SystemUI Stealth Trigger.
 *
 * Designed specifically to prevent any main-thread or Binder-thread blocking in `system_server`
 * (`android`) and `com.android.systemui`:
 *  - Rules are read from a volatile in-memory snapshot (`cachedStealthMap`) in 0ms; background
 *    reloads happen strictly off-thread on a single daemon executor.
 *  - Hooks attach at most once per process (`attachedInProcess`) and use `ThreadLocal`
 *    re-entrancy guards on overloaded `PackageManagerService` / `ComputerEngine` methods.
 */
object SystemStealthHook {

    private const val MODULE_PKG = IsolationRuleManager.MODULE_PKG
    private const val PLAY_STORE_PKG = "com.android.vending"
    private const val SETTINGS_PKG = "com.android.settings"
    private const val PROVIDER_URI = "content://$MODULE_PKG.provider"
    const val CATEGORY_LAUNCH_HOLLOW = "$MODULE_PKG.LAUNCH_HOLLOW"

    @Volatile
    private var cachedStealthMap: Map<String, IsolationRuleManager.StealthRule> = emptyMap()

    @Volatile
    private var cachedSystemUiTrigger: Boolean = true

    @Volatile
    private var lastStealthFetchMs: Long = 0L

    @Volatile
    private var fallbackXSharedPrefs: XSharedPreferences? = null

    @Volatile
    private var cachedHollowAppId: Int = -1

    @Volatile
    private var cachedPlayStoreAppId: Int = -1

    @Volatile
    private var hostPackageName: String = ""

    private val attachedInProcess = AtomicBoolean(false)
    private val isRefreshing = AtomicBoolean(false)

    private val isInPmQueryHook = object : ThreadLocal<Boolean>() {
        override fun initialValue(): Boolean = false
    }

    private val isInPmInfoHook = object : ThreadLocal<Boolean>() {
        override fun initialValue(): Boolean = false
    }

    private val refreshExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Hollow-StealthPrefs").apply { isDaemon = true }
    }

    private val decoyBitmapCache = ConcurrentHashMap<DisguiseIconPreset, Bitmap>()
    private val customBitmapCache = ConcurrentHashMap<String, Bitmap>()
    private val knownPackageIconRes = ConcurrentHashMap<String, Int>()

    fun attachForPackage(
        lpparam: XC_LoadPackage.LoadPackageParam,
        currentPkg: String,
        xSharedPrefs: XSharedPreferences?
    ) {
        // Never hide or spoof apps inside Hollow itself
        if (currentPkg == MODULE_PKG) return

        val proc = lpparam.processName ?: currentPkg
        val isSysServer = currentPkg == "android" && (proc == "android" || proc == "system_server")

        // Ignore secondary sub-packages loaded inside system_server or plugin packages in SystemUI
        if (!isSysServer && (proc == "android" || proc == "system_server")) {
            return
        }
        if (proc.startsWith("com.android.systemui") && currentPkg != "com.android.systemui") {
            return
        }
        if (!isSysServer && !lpparam.isFirstApplication && proc != currentPkg) {
            return
        }

        if (!attachedInProcess.compareAndSet(false, true)) {
            return
        }

        hostPackageName = currentPkg
        if (xSharedPrefs != null && fallbackXSharedPrefs == null) {
            fallbackXSharedPrefs = xSharedPrefs
        }

        triggerAsyncRuleRefresh(xSharedPrefs, isSysServer)

        if (isSysServer) {
            attachSystemServerLauncherAppsHooks(lpparam, xSharedPrefs)
            attachSystemServerPackageManagerHooks(lpparam, xSharedPrefs)
            attachSystemServerActivitySupervisorHooks(lpparam, xSharedPrefs)
            return
        }

        attachGlobalPackageAndLauncherHooks(lpparam, currentPkg, xSharedPrefs)

        if (currentPkg == PLAY_STORE_PKG) {
            attachPlayStoreClientHooks(lpparam, xSharedPrefs)
        }

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

    /**
     * Always returns the current in-memory stealth map in 0ms without blocking the caller thread.
     */
    private fun getStealthRules(
        xSharedPrefs: XSharedPreferences?
    ): Map<String, IsolationRuleManager.StealthRule> {
        val now = System.currentTimeMillis()
        if ((now - lastStealthFetchMs) > 2000L) {
            val isSys = hostPackageName == "android" || Process.myUid() == Process.SYSTEM_UID
            triggerAsyncRuleRefresh(xSharedPrefs, isSys)
        }
        return cachedStealthMap
    }

    private fun triggerAsyncRuleRefresh(xSharedPrefs: XSharedPreferences?, isSysServer: Boolean) {
        if (!isRefreshing.compareAndSet(false, true)) return
        lastStealthFetchMs = System.currentTimeMillis()
        try {
            refreshExecutor.execute {
                try {
                    var loadedFromXPrefs = false
                    val prefs = obtainXSharedPrefs(xSharedPrefs)
                    if (prefs != null) {
                        if (prefs.hasFileChanged()) {
                            prefs.reload()
                        }
                        val raw = prefs.getString(IsolationRuleManager.KEY_STEALTH_TABLE, null)
                        cachedSystemUiTrigger = prefs.getBoolean(IsolationRuleManager.KEY_SYSTEMUI_TRIGGER, true)
                        if (raw != null) {
                            val parsed = IsolationRuleManager.parseStealthTable(raw) { pkg ->
                                prefs.getString(IsolationRuleManager.customIconKey(pkg), null)
                            }
                            updateCachesWithRules(parsed)
                            loadedFromXPrefs = true
                        }
                    }

                    // For client apps (Launchers, Settings, Play Store), if SELinux blocks direct reading of
                    // XSharedPreferences, fetch asynchronously via IsoVaultConfigProvider on this background thread.
                    if (!isSysServer && (!loadedFromXPrefs || cachedStealthMap.isEmpty())) {
                        val app = try { AndroidAppHelper.currentApplication() } catch (_: Throwable) { null }
                        if (app != null) {
                            val bundle = try {
                                app.contentResolver.call(
                                    Uri.parse(PROVIDER_URI),
                                    "get_stealth_table",
                                    null,
                                    null
                                )
                            } catch (_: Throwable) {
                                null
                            }
                            if (bundle != null) {
                                val raw = bundle.getString("stealthTable")
                                cachedSystemUiTrigger = bundle.getBoolean("systemUiTriggerEnabled", true)
                                val iconsBundle = bundle.getBundle("customIcons")
                                if (raw != null) {
                                    val parsed = IsolationRuleManager.parseStealthTable(raw) { pkg ->
                                        iconsBundle?.getString(pkg)
                                    }
                                    updateCachesWithRules(parsed)
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {
                } finally {
                    lastStealthFetchMs = System.currentTimeMillis()
                    isRefreshing.set(false)
                }
            }
        } catch (_: Throwable) {
            isRefreshing.set(false)
        }
    }

    private fun updateCachesWithRules(parsed: Map<String, IsolationRuleManager.StealthRule>) {
        for ((pkg, rule) in parsed) {
            if (rule.customIconBase64.isNotBlank()) {
                try {
                    val bytes = Base64.decode(rule.customIconBase64, Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        customBitmapCache[pkg] = bmp
                    }
                } catch (_: Throwable) {}
            } else {
                customBitmapCache.remove(pkg)
            }
        }
        cachedStealthMap = parsed
    }

    private fun shouldSpoofInCurrentClient(
        currentPkg: String,
        rule: IsolationRuleManager.StealthRule
    ): Boolean {
        if (rule.visibilityMode != LauncherVisibilityMode.DISGUISED) return false
        if (currentPkg == "com.android.systemui" && !rule.hideInRecents) return false
        if ((currentPkg == SETTINGS_PKG || currentPkg.contains("permissioncontroller")) && !rule.spoofInSettings) {
            return false
        }
        return true
    }

    private fun attachGlobalPackageAndLauncherHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        currentPkg: String,
        xSharedPrefs: XSharedPreferences?
    ) {
        // A. Hook LauncherApps.getActivityList(String, UserHandle) -> used by all modern launchers
        try {
            XposedBridge.hookAllMethods(
                LauncherApps::class.java,
                "getActivityList",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val rules = getStealthRules(xSharedPrefs)
                        if (rules.isEmpty()) return
                        val list = param.result as? List<*> ?: return

                        val filtered = list.filter { item ->
                            val info = item as? LauncherActivityInfo ?: return@filter true
                            val targetPkg = info.componentName?.packageName ?: return@filter true
                            if (targetPkg == currentPkg) return@filter true
                            val rule = rules[targetPkg]
                            if (currentPkg == PLAY_STORE_PKG && rule?.hideFromPlayStore == true) {
                                return@filter false
                            }
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
            val apmClass = XposedHelpers.findClassIfExists(
                "android.app.ApplicationPackageManager",
                lpparam.classLoader
            )
            if (apmClass != null) {
                val queryHook = object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val rules = getStealthRules(xSharedPrefs)
                        if (rules.isEmpty()) return

                        val intent = param.args.firstOrNull { it is Intent } as? Intent ?: return
                        val categories = intent.categories
                        val isLauncherQuery = categories?.contains(Intent.CATEGORY_LAUNCHER) == true ||
                                categories?.contains(Intent.CATEGORY_INFO) == true ||
                                intent.action == Intent.ACTION_MAIN
                        if (!isLauncherQuery) return

                        if (currentPkg == "com.android.systemui" &&
                            (intent.`package` == MODULE_PKG || intent.component?.packageName == MODULE_PKG)
                        ) {
                            return
                        }

                        val list = param.result as? List<*> ?: return
                        val filtered = list.filter { item ->
                            val ri = item as? ResolveInfo ?: return@filter true
                            val targetPkg = ri.activityInfo?.packageName
                                ?: ri.resolvePackageName
                                ?: return@filter true
                            if (targetPkg == currentPkg) return@filter true
                            val rule = rules[targetPkg]
                            if (currentPkg == PLAY_STORE_PKG && rule?.hideFromPlayStore == true) {
                                return@filter false
                            }
                            if (rule != null &&
                                shouldSpoofInCurrentClient(currentPkg, rule) &&
                                rule.customLabel.isNotBlank()
                            ) {
                                applyDisguiseToResolveInfo(ri, rule, false)
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
            }
        } catch (_: Throwable) {}

        // C. Hook LauncherActivityInfo.getLabel() -> custom user-typed name in any launcher
        try {
            XposedBridge.hookAllMethods(
                LauncherActivityInfo::class.java,
                "getLabel",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val rules = getStealthRules(xSharedPrefs)
                        if (rules.isEmpty()) return
                        val info = param.thisObject as? LauncherActivityInfo ?: return
                        val targetPkg = info.componentName?.packageName ?: return
                        if (targetPkg == currentPkg) return
                        val rule = rules[targetPkg] ?: return
                        if (shouldSpoofInCurrentClient(currentPkg, rule) && rule.customLabel.isNotBlank()) {
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
                        val rules = getStealthRules(xSharedPrefs)
                        if (rules.isEmpty()) return
                        val item = param.thisObject as? PackageItemInfo ?: return
                        val targetPkg = item.packageName ?: return
                        if (targetPkg == currentPkg) return
                        if (item.icon != 0) {
                            knownPackageIconRes[targetPkg] = item.icon
                        }
                        val rule = rules[targetPkg] ?: return
                        if (shouldSpoofInCurrentClient(currentPkg, rule) && rule.customLabel.isNotBlank()) {
                            param.result = rule.customLabel
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        val apmClass = XposedHelpers.findClassIfExists(
            "android.app.ApplicationPackageManager",
            lpparam.classLoader
        )
        if (apmClass != null) {
            try {
                XposedBridge.hookAllMethods(
                    apmClass,
                    "getApplicationLabel",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val rules = getStealthRules(xSharedPrefs)
                            if (rules.isEmpty()) return
                            val appInfo = param.args.firstOrNull() as? ApplicationInfo ?: return
                            val targetPkg = appInfo.packageName ?: return
                            if (targetPkg == currentPkg) return
                            if (appInfo.icon != 0) {
                                knownPackageIconRes[targetPkg] = appInfo.icon
                            }
                            val rule = rules[targetPkg] ?: return
                            if (shouldSpoofInCurrentClient(currentPkg, rule) && rule.customLabel.isNotBlank()) {
                                param.result = rule.customLabel
                            }
                        }
                    }
                )
            } catch (_: Throwable) {}

            // E1. Hook ApplicationPackageManager.getApplicationIcon / getActivityIcon / loadItemIcon / loadUnbadgedItemIcon / getDrawable
            val apmIconHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val rules = getStealthRules(xSharedPrefs)
                    if (rules.isEmpty()) return
                    val firstArg = param.args.firstOrNull() ?: return
                    val targetPkg = when (firstArg) {
                        is String -> firstArg
                        is ApplicationInfo -> firstArg.packageName
                        is PackageItemInfo -> firstArg.packageName
                        is ComponentName -> firstArg.packageName
                        is Intent -> firstArg.`package` ?: firstArg.component?.packageName
                        else -> null
                    } ?: return
                    if (targetPkg == currentPkg) return
                    val rule = rules[targetPkg] ?: return
                    if (!shouldSpoofInCurrentClient(currentPkg, rule)) return
                    if (rule.iconPreset == DisguiseIconPreset.DEFAULT && rule.customIconBase64.isBlank()) return

                    // For getDrawable(String packageName, int resId, ApplicationInfo appInfo), only intercept
                    // when loading the app's icon resource (not arbitrary internal drawables)
                    if (param.method.name == "getDrawable" && param.args.size >= 3) {
                        val resId = param.args[1] as? Int ?: 0
                        val appInfo = param.args[2] as? ApplicationInfo
                        val roundRes = if (appInfo != null) {
                            try { XposedHelpers.getIntField(appInfo, "roundIconRes") } catch (_: Throwable) { 0 }
                        } else 0
                        val isIconRes = (appInfo != null && (resId == appInfo.icon || resId == appInfo.logo || (roundRes != 0 && resId == roundRes))) ||
                                (knownPackageIconRes[targetPkg] != null && resId == knownPackageIconRes[targetPkg]) ||
                                (rule.donorIconRes != 0 && resId == rule.donorIconRes)
                        if (!isIconRes && appInfo != null && appInfo.icon != 0) {
                            return
                        }
                    }

                    param.result = resolveDisguiseDrawable(rule)
                }
            }

            for (methodName in listOf(
                "getApplicationIcon",
                "getActivityIcon",
                "loadItemIcon",
                "loadUnbadgedItemIcon",
                "getDrawable"
            )) {
                try {
                    XposedBridge.hookAllMethods(apmClass, methodName, apmIconHook)
                } catch (_: Throwable) {}
            }
        }

        // E2. Hook LauncherActivityInfo.getIcon / getBadgedIcon & PackageItemInfo.loadIcon / loadUnbadgedIcon
        val itemIconHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val rules = getStealthRules(xSharedPrefs)
                if (rules.isEmpty()) return
                val targetPkg = when (val obj = param.thisObject) {
                    is LauncherActivityInfo -> obj.componentName?.packageName
                    is PackageItemInfo -> obj.packageName
                    is ActivityInfo -> obj.packageName
                    is ApplicationInfo -> obj.packageName
                    else -> null
                } ?: return
                if (targetPkg == currentPkg) return
                val rule = rules[targetPkg] ?: return
                if (!shouldSpoofInCurrentClient(currentPkg, rule)) return
                if (rule.iconPreset == DisguiseIconPreset.DEFAULT && rule.customIconBase64.isBlank()) return
                param.result = resolveDisguiseDrawable(rule)
            }
        }

        try {
            XposedBridge.hookAllMethods(LauncherActivityInfo::class.java, "getIcon", itemIconHook)
            XposedBridge.hookAllMethods(LauncherActivityInfo::class.java, "getBadgedIcon", itemIconHook)
            XposedBridge.hookAllMethods(PackageItemInfo::class.java, "loadIcon", itemIconHook)
            XposedBridge.hookAllMethods(PackageItemInfo::class.java, "loadUnbadgedIcon", itemIconHook)
        } catch (_: Throwable) {}

        // E3. Hook Launcher3 / Pixel / OEM IconProvider.getIcon(ActivityInfo / LauncherActivityInfo, int)
        val iconProviderClasses = listOf(
            "com.android.launcher3.icons.IconProvider",
            "com.android.launcher3.IconProvider"
        )
        for (clsName in iconProviderClasses) {
            try {
                val iconProviderCls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                XposedBridge.hookAllMethods(
                    iconProviderCls,
                    "getIcon",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val rules = getStealthRules(xSharedPrefs)
                            if (rules.isEmpty()) return
                            val firstArg = param.args.firstOrNull() ?: return
                            val targetPkg = when (firstArg) {
                                is ActivityInfo -> firstArg.packageName
                                is LauncherActivityInfo -> firstArg.componentName?.packageName
                                is ComponentName -> firstArg.packageName
                                else -> null
                            } ?: return
                            if (targetPkg == currentPkg) return
                            val rule = rules[targetPkg] ?: return
                            if (!shouldSpoofInCurrentClient(currentPkg, rule)) return
                            if (rule.iconPreset == DisguiseIconPreset.DEFAULT && rule.customIconBase64.isBlank()) return
                            param.result = resolveDisguiseDrawable(rule)
                        }
                    }
                )
            } catch (_: Throwable) {}
        }
    }

    /**
     * Client-side hooks inside Google Play Store (`com.android.vending`) when Play Store is in scope.
     */
    private fun attachPlayStoreClientHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        xSharedPrefs: XSharedPreferences?
    ) {
        val apmClass = XposedHelpers.findClassIfExists(
            "android.app.ApplicationPackageManager",
            lpparam.classLoader
        ) ?: return

        try {
            XposedBridge.hookAllMethods(
                apmClass,
                "getInstalledPackages",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val rules = getStealthRules(xSharedPrefs)
                        if (rules.isEmpty()) return
                        val list = param.result as? List<*> ?: return
                        val filtered = list.filter { item ->
                            val pkgInfo = item as? PackageInfo ?: return@filter true
                            rules[pkgInfo.packageName]?.hideFromPlayStore != true
                        }
                        if (filtered.size != list.size) {
                            param.result = ArrayList(filtered)
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        try {
            XposedBridge.hookAllMethods(
                apmClass,
                "getInstalledApplications",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val rules = getStealthRules(xSharedPrefs)
                        if (rules.isEmpty()) return
                        val list = param.result as? List<*> ?: return
                        val filtered = list.filter { item ->
                            val appInfo = item as? ApplicationInfo ?: return@filter true
                            rules[appInfo.packageName]?.hideFromPlayStore != true
                        }
                        if (filtered.size != list.size) {
                            param.result = ArrayList(filtered)
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        val notFoundHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val rules = getStealthRules(xSharedPrefs)
                if (rules.isEmpty()) return
                val pkg = param.args.firstOrNull { it is String } as? String ?: return
                if (rules[pkg]?.hideFromPlayStore == true) {
                    param.throwable = PackageManager.NameNotFoundException(pkg)
                }
            }
        }
        try {
            XposedBridge.hookAllMethods(apmClass, "getPackageInfo", notFoundHook)
            XposedBridge.hookAllMethods(apmClass, "getApplicationInfo", notFoundHook)
        } catch (_: Throwable) {}
    }

    /**
     * System Server (`android`) hook on `LauncherAppsService$LauncherAppsImpl.getLauncherActivities`.
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
                        val rules = getStealthRules(xSharedPrefs)
                        if (rules.isEmpty()) return

                        val callingPkg = param.args.firstOrNull { it is String } as? String
                        if (callingPkg == MODULE_PKG) return
                        val callingAppId = Binder.getCallingUid() % 100_000
                        val allowDonorResourceSwap = callingAppId >= Process.FIRST_APPLICATION_UID &&
                                callingAppId != cachedHollowAppId

                        val rawResult = param.result ?: return
                        if (rawResult is List<*>) {
                            param.result = filterAndDisguiseLauncherItems(
                                rawResult,
                                callingPkg,
                                rules,
                                allowDonorResourceSwap
                            )
                        } else if (rawResult.javaClass.name == "android.content.pm.ParceledListSlice") {
                            try {
                                val list = XposedHelpers.callMethod(rawResult, "getList") as? List<*> ?: return
                                val modified = filterAndDisguiseLauncherItems(
                                    list,
                                    callingPkg,
                                    rules,
                                    allowDonorResourceSwap
                                )
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
        rules: Map<String, IsolationRuleManager.StealthRule>,
        allowDonorResourceSwap: Boolean
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
                    if (callingPkg == PLAY_STORE_PKG && rule.hideFromPlayStore) {
                        continue
                    }
                    if (rule.visibilityMode == LauncherVisibilityMode.HIDDEN) {
                        continue
                    }
                    if (rule.visibilityMode == LauncherVisibilityMode.DISGUISED && activityInfo != null) {
                        applyDisguiseToActivityInfo(item, activityInfo, rule, allowDonorResourceSwap)
                    }
                }
            }
            out.add(item)
        }
        return out
    }

    private fun applyDisguiseToActivityInfo(
        container: Any,
        originalInfo: ActivityInfo,
        rule: IsolationRuleManager.StealthRule,
        allowDonorResourceSwap: Boolean
    ) {
        try {
            val swapRes = allowDonorResourceSwap &&
                    rule.donorSourceDir.isNotBlank() &&
                    rule.donorIconRes != 0 &&
                    rule.iconPreset != DisguiseIconPreset.CUSTOM_IMAGE &&
                    rule.iconPreset != DisguiseIconPreset.DEFAULT

            val clonedActivityInfo = ActivityInfo(originalInfo).apply {
                if (rule.customLabel.isNotBlank()) {
                    nonLocalizedLabel = rule.customLabel
                    labelRes = 0
                }
                if (swapRes) {
                    icon = rule.donorIconRes
                    logo = rule.donorIconRes
                }
                if (applicationInfo != null) {
                    applicationInfo = ApplicationInfo(applicationInfo).apply {
                        if (rule.customLabel.isNotBlank()) {
                            nonLocalizedLabel = rule.customLabel
                            labelRes = 0
                        }
                        if (swapRes) {
                            icon = rule.donorIconRes
                            logo = rule.donorIconRes
                            try { XposedHelpers.setIntField(this, "roundIconRes", 0) } catch (_: Throwable) {}
                            sourceDir = rule.donorSourceDir
                            publicSourceDir = rule.donorSourceDir
                            splitSourceDirs = null
                            splitPublicSourceDirs = null
                        }
                    }
                }
            }
            when (container) {
                is ResolveInfo -> container.activityInfo = clonedActivityInfo
                else -> XposedHelpers.setObjectField(container, "mActivityInfo", clonedActivityInfo)
            }
        } catch (_: Throwable) {}
    }

    private fun applyDisguiseToResolveInfo(
        ri: ResolveInfo,
        rule: IsolationRuleManager.StealthRule,
        allowDonorResourceSwap: Boolean
    ) {
        val ai = ri.activityInfo ?: return
        applyDisguiseToActivityInfo(ri, ai, rule, allowDonorResourceSwap)
        if (rule.customLabel.isNotBlank()) {
            ri.nonLocalizedLabel = rule.customLabel
            ri.labelRes = 0
        }
        if (allowDonorResourceSwap &&
            rule.donorSourceDir.isNotBlank() &&
            rule.donorIconRes != 0 &&
            rule.iconPreset != DisguiseIconPreset.CUSTOM_IMAGE &&
            rule.iconPreset != DisguiseIconPreset.DEFAULT
        ) {
            ri.icon = rule.donorIconRes
        }
    }

    /**
     * System Server (`android`) hooks on `ComputerEngine` / `PackageManagerService` for:
     *  1. `queryIntentActivitiesInternal` (Launcher queries & donor icon resource swap)
     *  2. `getInstalledApplications` & `getInstalledPackages` (Play Store hiding + Settings App Management spoofing)
     *  3. `getApplicationInfo` & `getPackageInfo` (Play Store hiding + Settings App Management spoofing + Launcher IconCache invalidation)
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

                // 1. queryIntentActivitiesInternal
                XposedBridge.hookAllMethods(
                    cls,
                    "queryIntentActivitiesInternal",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val rules = getStealthRules(xSharedPrefs)
                            if (rules.isEmpty()) return

                            if (isInPmQueryHook.get() == true) return
                            isInPmQueryHook.set(true)
                            try {
                                val intent = param.args.firstOrNull { it is Intent } as? Intent ?: return
                                val categories = intent.categories
                                val isLauncherQuery = categories?.contains(Intent.CATEGORY_LAUNCHER) == true ||
                                        categories?.contains(Intent.CATEGORY_INFO) == true
                                if (!isLauncherQuery) return

                                val list = param.result as? List<*> ?: return
                                val callingAppId = Binder.getCallingUid() % 100_000

                                for (item in list) {
                                    val ri = item as? ResolveInfo ?: continue
                                    val pkg = ri.activityInfo?.packageName ?: continue
                                    val uid = ri.activityInfo?.applicationInfo?.uid ?: -1
                                    if (uid > 0) {
                                        if (pkg == MODULE_PKG) cachedHollowAppId = uid % 100_000
                                        if (pkg == PLAY_STORE_PKG) cachedPlayStoreAppId = uid % 100_000
                                    }
                                }
                                if (callingAppId == cachedHollowAppId) return

                                val explicitPkg = intent.`package` ?: intent.component?.packageName
                                if (!explicitPkg.isNullOrEmpty() &&
                                    (callingAppId == Process.SYSTEM_UID || callingAppId == cachedHollowAppId)
                                ) {
                                    return
                                }

                                val allowDonorSwap = callingAppId >= Process.FIRST_APPLICATION_UID &&
                                        callingAppId != cachedHollowAppId

                                val out = ArrayList<ResolveInfo>(list.size)
                                var modified = false
                                for (item in list) {
                                    val ri = item as? ResolveInfo ?: continue
                                    val targetPkg = ri.activityInfo?.packageName ?: run {
                                        out.add(ri)
                                        continue
                                    }
                                    val targetAppId = (ri.activityInfo?.applicationInfo?.uid ?: -1) % 100_000
                                    if (targetAppId > 0 && targetAppId == callingAppId) {
                                        out.add(ri)
                                        continue
                                    }
                                    val rule = rules[targetPkg]
                                    if (callingAppId == cachedPlayStoreAppId && rule?.hideFromPlayStore == true) {
                                        modified = true
                                        continue
                                    }
                                    if (rule?.visibilityMode == LauncherVisibilityMode.HIDDEN) {
                                        modified = true
                                        continue
                                    }
                                    if (rule != null && rule.visibilityMode == LauncherVisibilityMode.DISGUISED) {
                                        val clonedRi = ResolveInfo(ri)
                                        applyDisguiseToResolveInfo(clonedRi, rule, allowDonorSwap)
                                        out.add(clonedRi)
                                        modified = true
                                    } else {
                                        out.add(ri)
                                    }
                                }
                                if (modified) {
                                    param.result = out
                                }
                            } finally {
                                isInPmQueryHook.set(false)
                            }
                        }
                    }
                )

                // 2. getInstalledApplications / getInstalledPackages (Play Store hiding & Settings App Management spoofing)
                val listHook = object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val rules = getStealthRules(xSharedPrefs)
                        if (rules.isEmpty()) return
                        if (isInPmInfoHook.get() == true) return
                        isInPmInfoHook.set(true)
                        try {
                            val callingAppId = Binder.getCallingUid() % 100_000
                            if (callingAppId == cachedHollowAppId) return

                            val rawResult = param.result ?: return
                            val isParceledSlice = rawResult.javaClass.name == "android.content.pm.ParceledListSlice"
                            val list: List<*> = if (rawResult is List<*>) {
                                rawResult
                            } else if (isParceledSlice) {
                                XposedHelpers.callMethod(rawResult, "getList") as? List<*> ?: return
                            } else {
                                return
                            }

                            val out = ArrayList<Any?>(list.size)
                            var modified = false

                            for (item in list) {
                                when (item) {
                                    is ApplicationInfo -> {
                                        val pkg = item.packageName ?: continue
                                        if (pkg == MODULE_PKG && item.uid > 0) cachedHollowAppId = item.uid % 100_000
                                        if (pkg == PLAY_STORE_PKG && item.uid > 0) cachedPlayStoreAppId = item.uid % 100_000
                                        if (callingAppId == cachedHollowAppId) {
                                            out.add(item)
                                            continue
                                        }
                                        val rule = rules[pkg]
                                        if (rule != null) {
                                            if (callingAppId == cachedPlayStoreAppId && rule.hideFromPlayStore) {
                                                modified = true
                                                continue
                                            }
                                            if (rule.spoofInSettings &&
                                                rule.visibilityMode == LauncherVisibilityMode.DISGUISED &&
                                                rule.customLabel.isNotBlank() &&
                                                callingAppId != (item.uid % 100_000)
                                            ) {
                                                val cloned = ApplicationInfo(item).apply {
                                                    nonLocalizedLabel = rule.customLabel
                                                    labelRes = 0
                                                }
                                                out.add(cloned)
                                                modified = true
                                                continue
                                            }
                                        }
                                        out.add(item)
                                    }
                                    is PackageInfo -> {
                                        val pkg = item.packageName ?: continue
                                        val uid = item.applicationInfo?.uid ?: -1
                                        if (pkg == MODULE_PKG && uid > 0) cachedHollowAppId = uid % 100_000
                                        if (pkg == PLAY_STORE_PKG && uid > 0) cachedPlayStoreAppId = uid % 100_000
                                        if (callingAppId == cachedHollowAppId) {
                                            out.add(item)
                                            continue
                                        }
                                        val rule = rules[pkg]
                                        if (rule != null) {
                                            if (callingAppId == cachedPlayStoreAppId && rule.hideFromPlayStore) {
                                                modified = true
                                                continue
                                            }
                                            if (rule.visibilityMode == LauncherVisibilityMode.DISGUISED &&
                                                callingAppId != (uid % 100_000)
                                            ) {
                                                val clonedPkg = cloneAndSpoofPackageInfo(item, rule)
                                                out.add(clonedPkg)
                                                modified = true
                                                continue
                                            }
                                        }
                                        out.add(item)
                                    }
                                    else -> out.add(item)
                                }
                            }

                            if (modified) {
                                if (isParceledSlice) {
                                    param.result = XposedHelpers.newInstance(rawResult.javaClass, out)
                                } else {
                                    param.result = out
                                }
                            }
                        } catch (_: Throwable) {
                        } finally {
                            isInPmInfoHook.set(false)
                        }
                    }
                }

                for (m in listOf(
                    "getInstalledApplications",
                    "getInstalledPackages"
                )) {
                    try {
                        XposedBridge.hookAllMethods(cls, m, listHook)
                    } catch (_: Throwable) {}
                }

                // 3. getApplicationInfo / getPackageInfo (Single-package queries by Settings, Launcher IconCache, or Play Store)
                val singleInfoHook = object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val rules = getStealthRules(xSharedPrefs)
                        if (rules.isEmpty()) return
                        if (isInPmInfoHook.get() == true) return
                        isInPmInfoHook.set(true)
                        try {
                            val callingAppId = Binder.getCallingUid() % 100_000
                            if (callingAppId == cachedHollowAppId) return

                            when (val res = param.result) {
                                is ApplicationInfo -> {
                                    val pkg = res.packageName ?: return
                                    if (pkg == MODULE_PKG && res.uid > 0) cachedHollowAppId = res.uid % 100_000
                                    if (pkg == PLAY_STORE_PKG && res.uid > 0) cachedPlayStoreAppId = res.uid % 100_000
                                    if (callingAppId == cachedHollowAppId || callingAppId == (res.uid % 100_000)) return
                                    val rule = rules[pkg] ?: return
                                    if (callingAppId == cachedPlayStoreAppId && rule.hideFromPlayStore) {
                                        param.result = null
                                        return
                                    }
                                    if (rule.spoofInSettings &&
                                        rule.visibilityMode == LauncherVisibilityMode.DISGUISED &&
                                        rule.customLabel.isNotBlank()
                                    ) {
                                        param.result = ApplicationInfo(res).apply {
                                            nonLocalizedLabel = rule.customLabel
                                            labelRes = 0
                                        }
                                    }
                                }
                                is PackageInfo -> {
                                    val pkg = res.packageName ?: return
                                    val uid = res.applicationInfo?.uid ?: -1
                                    if (pkg == MODULE_PKG && uid > 0) cachedHollowAppId = uid % 100_000
                                    if (pkg == PLAY_STORE_PKG && uid > 0) cachedPlayStoreAppId = uid % 100_000
                                    if (callingAppId == cachedHollowAppId || (uid > 0 && callingAppId == (uid % 100_000))) return
                                    val rule = rules[pkg] ?: return
                                    if (callingAppId == cachedPlayStoreAppId && rule.hideFromPlayStore) {
                                        param.result = null
                                        return
                                    }
                                    if (rule.visibilityMode == LauncherVisibilityMode.DISGUISED) {
                                        param.result = cloneAndSpoofPackageInfo(res, rule)
                                    }
                                }
                            }
                        } catch (_: Throwable) {
                        } finally {
                            isInPmInfoHook.set(false)
                        }
                    }
                }

                for (m in listOf(
                    "getApplicationInfo",
                    "getPackageInfo"
                )) {
                    try {
                        XposedBridge.hookAllMethods(cls, m, singleInfoHook)
                    } catch (_: Throwable) {}
                }

                break
            } catch (_: Throwable) {}
        }
    }

    private fun cloneAndSpoofPackageInfo(
        original: PackageInfo,
        rule: IsolationRuleManager.StealthRule
    ): PackageInfo {
        val parcel = android.os.Parcel.obtain()
        return try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val copy = PackageInfo.CREATOR.createFromParcel(parcel)
            // Bump lastUpdateTime deterministically based on disguise settings so Launcher3's
            // SQLite IconCache (app_icons.db) automatically invalidates stale cached icons!
            val ruleHash = (rule.customLabel.hashCode() xor
                    rule.iconPreset.name.hashCode() xor
                    rule.donorPkg.hashCode() xor
                    rule.customIconBase64.hashCode()).toLong() and 0xFFFFL
            copy.lastUpdateTime = original.lastUpdateTime + 1000L + ruleHash
            if (rule.spoofInSettings && rule.customLabel.isNotBlank() && copy.applicationInfo != null) {
                copy.applicationInfo = ApplicationInfo(copy.applicationInfo).apply {
                    nonLocalizedLabel = rule.customLabel
                    labelRes = 0
                }
            }
            copy
        } catch (_: Throwable) {
            original
        } finally {
            parcel.recycle()
        }
    }

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
                            val rules = getStealthRules(xSharedPrefs)
                            if (rules.isEmpty()) return
                            val originalInfo = param.result as? ActivityInfo ?: return
                            val targetPkg = originalInfo.packageName ?: return
                            val rule = rules[targetPkg] ?: return

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
     * Non-intrusive SystemUI Quick Settings `DateView` long-click hook.
     */
    private fun attachSystemUiDirectHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        xSharedPrefs: XSharedPreferences?
    ) {
        try {
            val dateViewCls = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.policy.DateView",
                lpparam.classLoader
            ) ?: return

            XposedBridge.hookAllMethods(
                dateViewCls,
                "onAttachedToWindow",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as? View ?: return
                        view.setOnLongClickListener { v ->
                            getStealthRules(xSharedPrefs)
                            if (!cachedSystemUiTrigger) return@setOnLongClickListener false
                            launchHollowFromSystemUi(v.context)
                            true
                        }
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    @SuppressLint("WrongConstant")
    fun launchHollowFromSystemUi(context: Context) {
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
     * Resolves the disguise icon Drawable for a [rule]:
     *  1. If a custom image or cloned app icon bitmap is cached in `customBitmapCache[packageName]`,
     *     wraps it in an `AdaptiveIconDrawable` (Android 8.0+) or `BitmapDrawable`.
     *  2. Otherwise renders the selected [DisguiseIconPreset] wrapped in an `AdaptiveIconDrawable`.
     */
    fun resolveDisguiseDrawable(rule: IsolationRuleManager.StealthRule): Drawable {
        val customBmp = customBitmapCache[rule.packageName]
        if (customBmp != null) {
            return wrapBitmapAsAdaptiveIcon(customBmp, Color.TRANSPARENT)
        }
        return buildDecoyDrawable(rule.iconPreset)
    }

    private fun wrapBitmapAsAdaptiveIcon(bitmap: Bitmap, bgColor: Int): Drawable {
        val fgDrawable = BitmapDrawable(Resources.getSystem(), bitmap)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val bgDrawable = if (bgColor == Color.TRANSPARENT) {
                ColorDrawable(Color.parseColor("#1E293B"))
            } else {
                ColorDrawable(bgColor)
            }
            return AdaptiveIconDrawable(bgDrawable, InsetDrawable(fgDrawable, 0.14f))
        }
        return fgDrawable
    }

    fun buildDecoyDrawable(preset: DisguiseIconPreset): Drawable {
        val bgColor = when (preset) {
            DisguiseIconPreset.CALCULATOR -> Color.parseColor("#1E3A5F")
            DisguiseIconPreset.NOTES -> Color.parseColor("#3E2E14")
            DisguiseIconPreset.CALENDAR -> Color.parseColor("#18392B")
            DisguiseIconPreset.CLOCK -> Color.parseColor("#2A244D")
            DisguiseIconPreset.WEATHER -> Color.parseColor("#16384C")
            DisguiseIconPreset.SYSTEM_UTILITY -> Color.parseColor("#262B33")
            else -> Color.parseColor("#1E3A5F")
        }
        val bmp = decoyBitmapCache.getOrPut(preset) {
            val size = 192
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            paint.color = bgColor
            canvas.drawRoundRect(RectF(8f, 8f, 184f, 184f), 44f, 44f, paint)

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
                else -> "H"
            }
            paint.textSize = 76f
            val yPos = (size / 2f) - ((paint.descent() + paint.ascent()) / 2f)
            canvas.drawText(symbol, size / 2f, yPos, paint)
            bitmap
        }
        return wrapBitmapAsAdaptiveIcon(bmp, bgColor)
    }
}
