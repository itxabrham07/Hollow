package com.example.isolation.hook

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
 * System-wide App Hider, Custom Name/Icon Disguise, and Direct SystemUI Injection engine.
 *
 * Why this hooks globally (`android`, `com.android.systemui`, and all external processes)
 * instead of targeting individual launchers:
 *  1. Any existing or newly installed launcher (Pixel Launcher, OneUI, Nova, Lawnchair, Niagara, etc.)
 *     queries `LauncherApps` or `PackageManager` and automatically receives the filtered app list
 *     or the custom user-typed name and decoy icon.
 *  2. `com.android.systemui` is hooked directly to:
 *     - Provide a stealth entry point (long-pressing the Quick Settings Clock / Date header in
 *       SystemUI opens Hollow directly, even when Hollow is completely hidden from all launchers).
 *     - Mask disguised apps in the SystemUI Recents / Overview screen.
 */
object SystemStealthHook {

    private const val MODULE_PKG = IsolationRuleManager.MODULE_PKG
    private const val PROVIDER_URI = "content://com.aistudio.isovault.xrvf.provider"

    @Volatile
    private var cachedStealthMap: Map<String, IsolationRuleManager.StealthRule> = emptyMap()

    @Volatile
    private var cachedSystemUiTrigger: Boolean = true

    @Volatile
    private var lastStealthFetchMs: Long = 0L

    private val decoyBitmapCache = ConcurrentHashMap<DisguiseIconPreset, Bitmap>()

    fun attachForPackage(
        lpparam: XC_LoadPackage.LoadPackageParam,
        currentPkg: String,
        xSharedPrefs: XSharedPreferences?
    ) {
        // Never hide or spoof apps inside Hollow itself so Hollow can always see and launch everything
        if (currentPkg == MODULE_PKG) return

        // 1. Attach global PackageManager / LauncherApps / PackageItemInfo hooks
        //    so any launcher (including newly installed ones) and SystemUI obey hide & disguise rules.
        attachGlobalPackageAndLauncherHooks(lpparam, currentPkg, xSharedPrefs)

        // 2. If inside System Framework (`android`), hook PackageManagerService intent resolution
        if (currentPkg == "android") {
            attachSystemServerPackageManagerHooks(lpparam, xSharedPrefs)
        }

        // 3. If inside SystemUI (`com.android.systemui`), inject the direct SystemUI stealth trigger
        //    and Recents task header disguise hooks.
        if (currentPkg == "com.android.systemui") {
            attachSystemUiDirectHooks(lpparam, xSharedPrefs)
        }
    }

    private fun getStealthRules(xSharedPrefs: XSharedPreferences?): Map<String, IsolationRuleManager.StealthRule> {
        val now = System.currentTimeMillis()
        if (now - lastStealthFetchMs < 2500L && cachedStealthMap.isNotEmpty()) {
            return cachedStealthMap
        }
        lastStealthFetchMs = now

        // Channel 1: XSharedPreferences (works in system_server, SystemUI, and all client apps)
        try {
            if (xSharedPrefs != null) {
                xSharedPrefs.reload()
                val raw = xSharedPrefs.getString(IsolationRuleManager.KEY_STEALTH_TABLE, null)
                cachedSystemUiTrigger = xSharedPrefs.getBoolean(IsolationRuleManager.KEY_SYSTEMUI_TRIGGER, true)
                if (raw != null) {
                    val parsed = IsolationRuleManager.parseStealthTable(raw)
                    cachedStealthMap = parsed
                    return parsed
                }
            }
        } catch (_: Throwable) {}

        // Channel 2: ContentProvider IPC fallback
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
        // A. Hook LauncherApps.getActivityList(String, UserHandle) -> used by all modern launchers
        try {
            XposedBridge.hookAllMethods(
                LauncherApps::class.java,
                "getActivityList",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val list = param.result as? List<*> ?: return
                        val rules = getStealthRules(xSharedPrefs)
                        if (rules.isEmpty()) return

                        val filtered = list.filter { item ->
                            val info = item as? LauncherActivityInfo ?: return@filter true
                            val targetPkg = info.componentName?.packageName ?: return@filter true
                            // Never hide an app from itself
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
        //    -> used by custom/third-party launchers, Share sheets, and app pickers
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

                    val list = param.result as? List<*> ?: return
                    val rules = getStealthRules(xSharedPrefs)
                    if (rules.isEmpty()) return

                    val filtered = list.filter { item ->
                        val ri = item as? ResolveInfo ?: return@filter true
                        val targetPkg = ri.activityInfo?.packageName
                            ?: ri. resolvePackageName
                            ?: return@filter true
                        if (targetPkg == currentPkg) return@filter true
                        val rule = rules[targetPkg]
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
                        val rule = getStealthRules(xSharedPrefs)[targetPkg] ?: return
                        if (rule.visibilityMode == LauncherVisibilityMode.DISGUISED && rule.customLabel.isNotBlank()) {
                            param.result = rule.customLabel
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // D. Hook PackageItemInfo.loadLabel(PackageManager) & ApplicationPackageManager.getApplicationLabel
        //    -> spoofs name in all launchers, SystemUI Recents, and Settings lists
        try {
            XposedBridge.hookAllMethods(
                PackageItemInfo::class.java,
                "loadLabel",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val item = param.thisObject as? PackageItemInfo ?: return
                        val targetPkg = item.packageName ?: return
                        if (targetPkg == currentPkg) return
                        val rule = getStealthRules(xSharedPrefs)[targetPkg] ?: return
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
                        val rule = getStealthRules(xSharedPrefs)[targetPkg] ?: return
                        if (rule.visibilityMode == LauncherVisibilityMode.DISGUISED && rule.customLabel.isNotBlank()) {
                            if (currentPkg == "com.android.systemui" && !rule.hideInRecents) return
                            param.result = rule.customLabel
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // E. Hook LauncherActivityInfo.getIcon / getBadgedIcon & PackageItemInfo.loadIcon
        //    -> spoofs decoy icon across all launchers and SystemUI Recents
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
                val rule = getStealthRules(xSharedPrefs)[targetPkg] ?: return
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
     * System Server (`android`) hook on PackageManagerService / ComputerEngine so even newly
     * installed launchers querying the OS directly see hidden apps omitted from launcher queries.
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

                            // Exempt system/root or Hollow's own queries if needed
                            val list = param.result as? List<*> ?: return
                            val rules = getStealthRules(xSharedPrefs)
                            if (rules.isEmpty()) return

                            val filtered = list.filter { item ->
                                val ri = item as? ResolveInfo ?: return@filter true
                                val targetPkg = ri.activityInfo?.packageName ?: return@filter true
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
        }
    }

    /**
     * Direct SystemUI (`com.android.systemui`) injection:
     * Attaches a stealth long-press handler to the Quick Settings / Status Bar Clock & Date views
     * so the user can open Hollow directly from SystemUI even when Hollow is completely hidden from
     * all launchers.
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
    }

    fun launchHollowFromSystemUi(context: Context) {
        val candidates = listOf(
            ComponentName(MODULE_PKG, "com.example.MainActivity"),
            ComponentName(MODULE_PKG, "com.example.MainActivityCalculatorAlias"),
            ComponentName(MODULE_PKG, "com.example.MainActivityNotesAlias")
        )
        for (component in candidates) {
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    this.component = component
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
