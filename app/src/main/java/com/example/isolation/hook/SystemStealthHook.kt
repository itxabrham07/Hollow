package com.example.isolation.hook

import android.annotation.SuppressLint
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lock-free, non-blocking System-wide App Hider, Custom Name/Icon Disguise, and SystemUI Stealth Trigger.
 *
 * Designed specifically to prevent any main-thread or Binder-thread blocking in `system_server`
 * (`android`) and `com.android.systemui`:
 *  - Rules are read from a volatile in-memory snapshot (`cachedStealthMap`) in 0ms; background
 *    reloads happen strictly off-thread on a single daemon executor without ever calling
 *    synchronous `ContentResolver.call` or blocking `XSharedPreferences.awaitLoadedLocked()`
 *    on UI / system_server threads.
 *  - Hooks attach at most once per process (`attachedInProcess`) and use a `ThreadLocal`
 *    re-entrancy guard on overloaded `PackageManagerService` / `ComputerEngine` methods.
 */
object SystemStealthHook {

    private const val MODULE_PKG = IsolationRuleManager.MODULE_PKG
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

    private val attachedInProcess = AtomicBoolean(false)
    private val isRefreshing = AtomicBoolean(false)

    private val isInPmQueryHook = object : ThreadLocal<Boolean>() {
        override fun initialValue(): Boolean = false
    }

    private val refreshExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Hollow-StealthPrefs").apply { isDaemon = true }
    }

    private val decoyBitmapCache = ConcurrentHashMap<DisguiseIconPreset, Bitmap>()

    fun attachForPackage(
        lpparam: XC_LoadPackage.LoadPackageParam,
        currentPkg: String,
        xSharedPrefs: XSharedPreferences?
    ) {
        // Never hide or spoof apps inside Hollow itself
        if (currentPkg == MODULE_PKG) return

        val proc = lpparam.processName ?: currentPkg
        val isSysServer = currentPkg == "android" && (proc == "android" || proc == "system_server")

        // Ignore secondary sub-packages loaded inside system_server (e.g. com.android.inputdevices,
        // com.android.location.fused, com.android.server.telecom) or plugin packages in SystemUI.
        if (!isSysServer && (proc == "android" || proc == "system_server")) {
            return
        }
        if (proc.startsWith("com.android.systemui") && currentPkg != "com.android.systemui") {
            return
        }
        if (!isSysServer && !lpparam.isFirstApplication && proc != currentPkg) {
            return
        }

        // Guarantee hooks are attached at most once per process so BootClassLoader methods are never double-hooked
        if (!attachedInProcess.compareAndSet(false, true)) {
            return
        }

        if (xSharedPrefs != null && fallbackXSharedPrefs == null) {
            fallbackXSharedPrefs = xSharedPrefs
        }

        // Kick off an initial non-blocking background rule fetch
        triggerAsyncRuleRefresh(xSharedPrefs)

        if (isSysServer) {
            // Inside system_server: ONLY hook LauncherAppsService, ComputerEngine/PMS (with recursion guard),
            // and ActivityTaskSupervisor. Never hook BootClassLoader PackageItemInfo/ApplicationPackageManager
            // inside system_server.
            attachSystemServerLauncherAppsHooks(lpparam, xSharedPrefs)
            attachSystemServerPackageManagerHooks(lpparam, xSharedPrefs)
            attachSystemServerActivitySupervisorHooks(lpparam, xSharedPrefs)
            return
        }

        // Client process hooks (Launchers, Share sheet `system`, and SystemUI Recents)
        attachGlobalPackageAndLauncherHooks(lpparam, currentPkg, xSharedPrefs)

        // Direct SystemUI expanded header DateView long-press trigger
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
     * If the 3000ms TTL has elapsed, schedules an asynchronous reload on [refreshExecutor].
     */
    private fun getStealthRules(
        xSharedPrefs: XSharedPreferences?
    ): Map<String, IsolationRuleManager.StealthRule> {
        val now = System.currentTimeMillis()
        if ((now - lastStealthFetchMs) > 3000L) {
            triggerAsyncRuleRefresh(xSharedPrefs)
        }
        return cachedStealthMap
    }

    private fun triggerAsyncRuleRefresh(xSharedPrefs: XSharedPreferences?) {
        if (!isRefreshing.compareAndSet(false, true)) return
        lastStealthFetchMs = System.currentTimeMillis()
        try {
            refreshExecutor.execute {
                try {
                    val prefs = obtainXSharedPrefs(xSharedPrefs)
                    if (prefs != null) {
                        if (prefs.hasFileChanged()) {
                            prefs.reload()
                        }
                        val raw = prefs.getString(IsolationRuleManager.KEY_STEALTH_TABLE, null)
                        cachedSystemUiTrigger = prefs.getBoolean(IsolationRuleManager.KEY_SYSTEMUI_TRIGGER, true)
                        if (raw != null) {
                            cachedStealthMap = IsolationRuleManager.parseStealthTable(raw)
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
                        val rules = getStealthRules(xSharedPrefs)
                        if (rules.isEmpty()) return
                        val item = param.thisObject as? PackageItemInfo ?: return
                        val targetPkg = item.packageName ?: return
                        if (targetPkg == currentPkg) return
                        val rule = rules[targetPkg] ?: return
                        if (rule.visibilityMode == LauncherVisibilityMode.DISGUISED && rule.customLabel.isNotBlank()) {
                            if (currentPkg == "com.android.systemui" && !rule.hideInRecents) return
                            param.result = rule.customLabel
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        try {
            val apmClass = XposedHelpers.findClassIfExists(
                "android.app.ApplicationPackageManager",
                lpparam.classLoader
            )
            if (apmClass != null) {
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
                            val rule = rules[targetPkg] ?: return
                            if (rule.visibilityMode == LauncherVisibilityMode.DISGUISED && rule.customLabel.isNotBlank()) {
                                if (currentPkg == "com.android.systemui" && !rule.hideInRecents) return
                                param.result = rule.customLabel
                            }
                        }
                    }
                )
            }
        } catch (_: Throwable) {}

        // E. Hook LauncherActivityInfo.getIcon / getBadgedIcon & PackageItemInfo.loadIcon
        val iconHook = object : XC_MethodHook() {
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

                        val rawResult = param.result ?: return
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
                        continue
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
     * System Server (`android`) hook on `ComputerEngine` / `PackageManagerService` guarded by
     * [isInPmQueryHook] so overloaded `queryIntentActivitiesInternal` methods never recurse.
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

                                val explicitPkg = intent.`package` ?: intent.component?.packageName
                                if (!explicitPkg.isNullOrEmpty() &&
                                    (callingAppId == Process.SYSTEM_UID || callingAppId == cachedHollowAppId)
                                ) {
                                    return
                                }

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
                            } finally {
                                isInPmQueryHook.set(false)
                            }
                        }
                    }
                )
                // Only hook the first matching class (ComputerEngine on Android 12L+, or PackageManagerService on older)
                break
            } catch (_: Throwable) {}
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
     * Avoids modifying statusbar/lockscreen `Clock` touch flags so lockscreen wake and statusbar
     * gestures are never affected.
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

    fun buildDecoyDrawable(preset: DisguiseIconPreset): Drawable {
        val bmp = decoyBitmapCache.getOrPut(preset) {
            val size = 192
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

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
