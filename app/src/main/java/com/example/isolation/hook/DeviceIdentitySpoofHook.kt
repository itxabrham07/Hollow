package com.example.isolation.hook

import android.app.AndroidAppHelper
import android.bluetooth.BluetoothAdapter
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.database.MatrixCursor
import android.media.MediaDrm
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiInfo
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import com.example.isolation.model.IsolationRuleManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.Collections
import java.util.Enumeration
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Per-App Device Identity & Privacy Spoofing Engine for LSPosed / Vector.
 *
 * Spoofs hardware identifiers (Android ID, IMEI/MEID, IMSI, ICCID, Advertising ID, GSF ID,
 * Widevine MediaDrm Unique ID, Wi-Fi/BT MAC, Build/SystemProperties, Carrier) and provides
 * Privacy Shields (Root/Magisk/KSU/LSPosed hiding, ADB/Dev Options hiding, VPN/Proxy hiding,
 * and Clipboard read protection).
 */
object DeviceIdentitySpoofHook {

    private const val MODULE_PKG = IsolationRuleManager.MODULE_PKG
    private const val PROVIDER_URI = "content://$MODULE_PKG.provider"

    private val ROOT_AND_XPOSED_PACKAGES = setOf(
        MODULE_PKG,
        "com.topjohnwu.magisk",
        "io.github.vvb2060.magisk",
        "io.github.huskydg.magisk",
        "me.weishu.kernelsu",
        "com.rifsxd.ksunext",
        "me.bmax.apatch",
        "org.lsposed.manager",
        "org.lsposed.lspatch",
        "de.robv.android.xposed.installer",
        "org.meowcat.edxposed.manager",
        "com.tsng.hidemyapplist",
        "eu.chainfire.supersu"
    )

    private val VPN_IFACE_PREFIXES = listOf("tun", "ppp", "wg", "tap", "ipsec", "utun", "pptp")

    @Volatile
    private var cachedIdentityMap: Map<String, IsolationRuleManager.IdentitySpoofRule> = emptyMap()

    @Volatile
    private var lastFetchMs: Long = 0L

    @Volatile
    private var fallbackXPrefs: XSharedPreferences? = null

    @Volatile
    private var sysMirrorXPrefs: XSharedPreferences? = null

    @Volatile
    private var tmpMirrorXPrefs: XSharedPreferences? = null

    private val attachedInProcess = AtomicBoolean(false)
    private val isRefreshing = AtomicBoolean(false)
    private val hasLoadedInitial = AtomicBoolean(false)
    private val buildFieldsApplied = AtomicBoolean(false)

    private val refreshExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Hollow-IdentityPrefs").apply { isDaemon = true }
    }

    fun attachForPackage(
        lpparam: XC_LoadPackage.LoadPackageParam,
        currentPkg: String,
        xSharedPrefs: XSharedPreferences?
    ) {
        if (currentPkg == MODULE_PKG || currentPkg == "android" || currentPkg == "com.android.systemui") {
            return
        }
        val proc = lpparam.processName ?: currentPkg
        if (!lpparam.isFirstApplication && proc != currentPkg) {
            return
        }
        if (!attachedInProcess.compareAndSet(false, true)) {
            return
        }

        if (xSharedPrefs != null && fallbackXPrefs == null) {
            fallbackXPrefs = xSharedPrefs
        }

        tryLoadFromLocalFiles(xSharedPrefs)
        triggerAsyncRefresh(xSharedPrefs)

        // Apply Build static fields immediately if rule is already loaded
        getRuleForPackage(currentPkg, xSharedPrefs)?.let { applyBuildStaticFieldsOnce(it) }

        attachSettingsAndSystemPropertiesHooks(lpparam, currentPkg, xSharedPrefs)
        attachTelephonyAndHardwareHooks(lpparam, currentPkg, xSharedPrefs)
        attachNetworkAndMacHooks(lpparam, currentPkg, xSharedPrefs)
        attachDrmGsfAndAdIdHooks(lpparam, currentPkg, xSharedPrefs)
        attachPrivacyShieldHooks(lpparam, currentPkg, xSharedPrefs)
    }

    private fun obtainXPrefs(passed: XSharedPreferences?): XSharedPreferences? {
        if (passed != null) return passed
        val existing = fallbackXPrefs
        if (existing != null) return existing
        return try {
            XSharedPreferences(MODULE_PKG, IsolationRuleManager.PREFS_NAME).also {
                fallbackXPrefs = it
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun tryLoadFromPrefsInstance(prefs: XSharedPreferences?): Boolean {
        if (prefs == null) return false
        return try {
            if (prefs.hasFileChanged()) {
                prefs.reload()
            }
            val raw = prefs.getString(IsolationRuleManager.KEY_IDENTITY_TABLE, null)
            if (raw != null) {
                cachedIdentityMap = IsolationRuleManager.parseIdentityTable(raw)
                hasLoadedInitial.set(true)
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun tryLoadFromLocalFiles(xSharedPrefs: XSharedPreferences?): Boolean {
        if (tryLoadFromPrefsInstance(obtainXPrefs(xSharedPrefs))) return true
        try {
            val sysFile = File("/data/system/isovault_rules.xml")
            if (sysFile.canRead()) {
                val sp = sysMirrorXPrefs ?: XSharedPreferences(sysFile).also { sysMirrorXPrefs = it }
                if (tryLoadFromPrefsInstance(sp)) return true
            }
        } catch (_: Throwable) {}
        try {
            val tmpFile = File("/data/local/tmp/isovault_rules.xml")
            if (tmpFile.canRead()) {
                val sp = tmpMirrorXPrefs ?: XSharedPreferences(tmpFile).also { tmpMirrorXPrefs = it }
                if (tryLoadFromPrefsInstance(sp)) return true
            }
        } catch (_: Throwable) {}
        return false
    }

    private fun tryLoadFromProvider(): Boolean {
        val app = try { AndroidAppHelper.currentApplication() } catch (_: Throwable) { null } ?: return false
        return try {
            val bundle = app.contentResolver.call(
                Uri.parse(PROVIDER_URI),
                "get_stealth_table",
                null,
                null
            )
            if (bundle != null) {
                val raw = bundle.getString("identityTable")
                if (raw != null) {
                    cachedIdentityMap = IsolationRuleManager.parseIdentityTable(raw)
                    hasLoadedInitial.set(true)
                    return true
                }
            }
            false
        } catch (_: Throwable) {
            false
        }
    }

    private fun getRuleForPackage(
        pkg: String,
        xSharedPrefs: XSharedPreferences?
    ): IsolationRuleManager.IdentitySpoofRule? {
        if (!hasLoadedInitial.get()) {
            if (!tryLoadFromLocalFiles(xSharedPrefs)) {
                tryLoadFromProvider()
            }
        }
        val now = System.currentTimeMillis()
        if ((now - lastFetchMs) > 2000L) {
            triggerAsyncRefresh(xSharedPrefs)
        }
        val rule = cachedIdentityMap[pkg]
        if (rule != null) {
            applyBuildStaticFieldsOnce(rule)
        }
        return rule
    }

    private fun triggerAsyncRefresh(xSharedPrefs: XSharedPreferences?) {
        if (!isRefreshing.compareAndSet(false, true)) return
        lastFetchMs = System.currentTimeMillis()
        try {
            refreshExecutor.execute {
                try {
                    if (!tryLoadFromLocalFiles(xSharedPrefs)) {
                        tryLoadFromProvider()
                    }
                } catch (_: Throwable) {
                } finally {
                    lastFetchMs = if (hasLoadedInitial.get()) System.currentTimeMillis() else 0L
                    isRefreshing.set(false)
                }
            }
        } catch (_: Throwable) {
            isRefreshing.set(false)
        }
    }

    private fun applyBuildStaticFieldsOnce(rule: IsolationRuleManager.IdentitySpoofRule) {
        if (!buildFieldsApplied.compareAndSet(false, true)) return
        try {
            XposedHelpers.setStaticObjectField(Build::class.java, "BRAND", rule.brand)
            XposedHelpers.setStaticObjectField(Build::class.java, "MANUFACTURER", rule.manufacturer)
            XposedHelpers.setStaticObjectField(Build::class.java, "MODEL", rule.model)
            XposedHelpers.setStaticObjectField(Build::class.java, "DEVICE", rule.device)
            XposedHelpers.setStaticObjectField(Build::class.java, "PRODUCT", rule.product)
            XposedHelpers.setStaticObjectField(Build::class.java, "BOARD", rule.board)
            XposedHelpers.setStaticObjectField(Build::class.java, "HARDWARE", rule.board)
            XposedHelpers.setStaticObjectField(Build::class.java, "FINGERPRINT", rule.fingerprint)
            XposedHelpers.setStaticObjectField(Build::class.java, "SERIAL", rule.serial)
            if (rule.hideRootAndXposed) {
                XposedHelpers.setStaticObjectField(Build::class.java, "TAGS", "release-keys")
                XposedHelpers.setStaticObjectField(Build::class.java, "TYPE", "user")
            }
        } catch (_: Throwable) {}
    }

    @Suppress("UNUSED_PARAMETER")
    private fun attachSettingsAndSystemPropertiesHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        currentPkg: String,
        xSharedPrefs: XSharedPreferences?
    ) {
        // 1. Hook SystemProperties.get(String) & get(String, String)
        try {
            val spCls = XposedHelpers.findClassIfExists("android.os.SystemProperties", lpparam.classLoader)
            if (spCls != null) {
                XposedBridge.hookAllMethods(
                    spCls,
                    "get",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val key = param.args.firstOrNull() as? String ?: return
                            val rule = getRuleForPackage(currentPkg, xSharedPrefs) ?: return
                            val spoofed: String? = when (key) {
                                "ro.product.brand", "ro.product.system.brand", "ro.product.vendor.brand" -> rule.brand
                                "ro.product.manufacturer", "ro.product.system.manufacturer", "ro.product.vendor.manufacturer" -> rule.manufacturer
                                "ro.product.model", "ro.product.system.model", "ro.product.vendor.model" -> rule.model
                                "ro.product.device", "ro.product.system.device", "ro.product.vendor.device", "ro.build.product" -> rule.device
                                "ro.product.name", "ro.product.system.name", "ro.product.vendor.name" -> rule.product
                                "ro.product.board", "ro.hardware" -> rule.board
                                "ro.build.fingerprint", "ro.system.build.fingerprint", "ro.vendor.build.fingerprint", "ro.bootimage.build.fingerprint" -> rule.fingerprint
                                "ro.serialno", "ro.boot.serialno" -> rule.serial
                                "gsm.sim.operator.alpha", "gsm.operator.alpha" -> rule.carrierName
                                "gsm.sim.operator.numeric", "gsm.operator.numeric" -> rule.simOperator
                                "ro.build.tags" -> if (rule.hideRootAndXposed) "release-keys" else null
                                "ro.build.type" -> if (rule.hideRootAndXposed) "user" else null
                                "ro.debuggable" -> if (rule.hideRootAndXposed) "0" else null
                                "ro.secure" -> if (rule.hideRootAndXposed) "1" else null
                                "ro.boot.vbmeta.device_state" -> if (rule.hideRootAndXposed) "locked" else null
                                "ro.boot.verifiedbootstate" -> if (rule.hideRootAndXposed) "green" else null
                                "ro.boot.flash.locked" -> if (rule.hideRootAndXposed) "1" else null
                                else -> null
                            }
                            if (spoofed != null) {
                                param.result = spoofed
                            }
                        }
                    }
                )
            }
        } catch (_: Throwable) {}

        // 2. Hook Settings.Secure / Settings.Global / Settings.System
        val settingsStringHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val name = param.args.getOrNull(1) as? String ?: return
                val rule = getRuleForPackage(currentPkg, xSharedPrefs) ?: return
                when (name) {
                    Settings.Secure.ANDROID_ID, "android_id" -> {
                        if (rule.androidId.isNotBlank()) param.result = rule.androidId
                    }
                    "advertising_id", "aaid" -> {
                        if (rule.advertisingId.isNotBlank()) param.result = rule.advertisingId
                    }
                    "limit_ad_tracking" -> param.result = "1"
                    "bluetooth_name", "device_name" -> {
                        if (rule.model.isNotBlank()) param.result = rule.model
                    }
                    Settings.Global.ADB_ENABLED,
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                    "adb_wifi_enabled",
                    "mock_location" -> {
                        if (rule.hideAdbAndDevOptions) param.result = "0"
                    }
                }
            }
        }

        val settingsIntHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val name = param.args.getOrNull(1) as? String ?: return
                val rule = getRuleForPackage(currentPkg, xSharedPrefs) ?: return
                if (rule.hideAdbAndDevOptions) {
                    when (name) {
                        Settings.Global.ADB_ENABLED,
                        Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                        "adb_wifi_enabled",
                        "mock_location" -> param.result = 0
                    }
                }
            }
        }

        for (cls in listOf(Settings.Secure::class.java, Settings.Global::class.java, Settings.System::class.java)) {
            for (m in listOf("getString", "getStringForUser")) {
                try {
                    XposedBridge.hookAllMethods(cls, m, settingsStringHook)
                } catch (_: Throwable) {}
            }
            for (m in listOf("getInt", "getIntForUser")) {
                try {
                    XposedBridge.hookAllMethods(cls, m, settingsIntHook)
                } catch (_: Throwable) {}
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun attachTelephonyAndHardwareHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        currentPkg: String,
        xSharedPrefs: XSharedPreferences?
    ) {
        // 1. Build.getSerial()
        try {
            XposedBridge.hookAllMethods(
                Build::class.java,
                "getSerial",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val rule = getRuleForPackage(currentPkg, xSharedPrefs) ?: return
                        if (rule.serial.isNotBlank()) param.result = rule.serial
                    }
                }
            )
        } catch (_: Throwable) {}

        // 2. TelephonyManager hooks
        val tmCls = TelephonyManager::class.java
        val imeiHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val rule = getRuleForPackage(currentPkg, xSharedPrefs) ?: return
                if (rule.imei.isNotBlank()) param.result = rule.imei
            }
        }
        for (m in listOf("getDeviceId", "getImei", "getMeid")) {
            try {
                XposedBridge.hookAllMethods(tmCls, m, imeiHook)
            } catch (_: Throwable) {}
        }

        // IMSI (getSubscriberId)
        try {
            XposedBridge.hookAllMethods(
                tmCls,
                "getSubscriberId",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val rule = getRuleForPackage(currentPkg, xSharedPrefs) ?: return
                        val prefix = rule.simOperator.ifBlank { "310260" }.take(6)
                        val suffix = rule.imei.takeLast(15 - prefix.length).padEnd(15 - prefix.length, '4')
                        param.result = prefix + suffix
                    }
                }
            )
        } catch (_: Throwable) {}

        // SIM ICCID (getSimSerialNumber)
        try {
            XposedBridge.hookAllMethods(
                tmCls,
                "getSimSerialNumber",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val rule = getRuleForPackage(currentPkg, xSharedPrefs) ?: return
                        val op = rule.simOperator.ifBlank { "310260" }.take(6)
                        val tail = rule.imei.takeLast(11).padEnd(11, '7')
                        param.result = "89$op$tail"
                    }
                }
            )
        } catch (_: Throwable) {}

        // Phone number protection (getLine1Number / getMsisdn) -> empty string so apps can't harvest phone number
        for (m in listOf("getLine1Number", "getMsisdn")) {
            try {
                XposedBridge.hookAllMethods(
                    tmCls,
                    m,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (getRuleForPackage(currentPkg, xSharedPrefs) != null) {
                                param.result = ""
                            }
                        }
                    }
                )
            } catch (_: Throwable) {}
        }

        // Carrier Name & SIM Operator
        for (m in listOf("getNetworkOperatorName", "getSimOperatorName")) {
            try {
                XposedBridge.hookAllMethods(
                    tmCls,
                    m,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val rule = getRuleForPackage(currentPkg, xSharedPrefs) ?: return
                            if (rule.carrierName.isNotBlank()) param.result = rule.carrierName
                        }
                    }
                )
            } catch (_: Throwable) {}
        }
        for (m in listOf("getNetworkOperator", "getSimOperator")) {
            try {
                XposedBridge.hookAllMethods(
                    tmCls,
                    m,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val rule = getRuleForPackage(currentPkg, xSharedPrefs) ?: return
                            if (rule.simOperator.isNotBlank()) param.result = rule.simOperator
                        }
                    }
                )
            } catch (_: Throwable) {}
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun attachNetworkAndMacHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        currentPkg: String,
        xSharedPrefs: XSharedPreferences?
    ) {
        // 1. WifiInfo.getMacAddress / getSSID / getBSSID
        try {
            XposedBridge.hookAllMethods(
                WifiInfo::class.java,
                "getMacAddress",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val rule = getRuleForPackage(currentPkg, xSharedPrefs) ?: return
                        if (rule.macAddress.isNotBlank()) param.result = rule.macAddress
                    }
                }
            )
            XposedBridge.hookAllMethods(
                WifiInfo::class.java,
                "getSSID",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val rule = getRuleForPackage(currentPkg, xSharedPrefs) ?: return
                        param.result = "\"${rule.brand.replaceFirstChar { it.uppercase() }}-5G\""
                    }
                }
            )
            XposedBridge.hookAllMethods(
                WifiInfo::class.java,
                "getBSSID",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val rule = getRuleForPackage(currentPkg, xSharedPrefs) ?: return
                        if (rule.macAddress.isNotBlank()) {
                            param.result = rule.macAddress.dropLast(2) + "01"
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // 2. BluetoothAdapter.getAddress
        try {
            XposedBridge.hookAllMethods(
                BluetoothAdapter::class.java,
                "getAddress",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val rule = getRuleForPackage(currentPkg, xSharedPrefs) ?: return
                        if (rule.macAddress.isNotBlank()) param.result = rule.macAddress
                    }
                }
            )
        } catch (_: Throwable) {}

        // 3. NetworkInterface.getHardwareAddress
        try {
            XposedBridge.hookAllMethods(
                NetworkInterface::class.java,
                "getHardwareAddress",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val rule = getRuleForPackage(currentPkg, xSharedPrefs) ?: return
                        if (rule.macAddress.isNotBlank()) {
                            val parts = rule.macAddress.split(":")
                            if (parts.size == 6) {
                                param.result = ByteArray(6) { idx ->
                                    parts[idx].toIntOrNull(16)?.toByte() ?: 0x02.toByte()
                                }
                            }
                        }
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    private fun attachDrmGsfAndAdIdHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        currentPkg: String,
        xSharedPrefs: XSharedPreferences?
    ) {
        // 1. Widevine MediaDrm.getPropertyByteArray("deviceUniqueId")
        try {
            XposedBridge.hookAllMethods(
                MediaDrm::class.java,
                "getPropertyByteArray",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val prop = param.args.firstOrNull() as? String ?: return
                        if (prop == MediaDrm.PROPERTY_DEVICE_UNIQUE_ID || prop == "deviceUniqueId") {
                            val rule = getRuleForPackage(currentPkg, xSharedPrefs) ?: return
                            val seed = "${rule.androidId}:${rule.imei}:${rule.serial}"
                            val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
                            param.result = digest
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // 2. Google Services Framework ID (GSF ID) query on content://com.google.android.gsf.gservices
        try {
            XposedBridge.hookAllMethods(
                ContentResolver::class.java,
                "query",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val uri = param.args.firstOrNull() as? Uri ?: return
                        if (uri.authority == "com.google.android.gsf.gservices") {
                            val rule = getRuleForPackage(currentPkg, xSharedPrefs) ?: return
                            val fakeDecId = (rule.androidId.take(15).toLongOrNull(16) ?: 3891204918204192L).toString()
                            val cursor = MatrixCursor(arrayOf("key", "value"), 1)
                            cursor.addRow(arrayOf<Any>("android_id", fakeDecId))
                            param.result = cursor
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // 3. Google Play Services AdvertisingIdClient$Info
        try {
            val adInfoCls = XposedHelpers.findClassIfExists(
                "com.google.android.gms.ads.identifier.AdvertisingIdClient\$Info",
                lpparam.classLoader
            )
            if (adInfoCls != null) {
                XposedBridge.hookAllMethods(
                    adInfoCls,
                    "getId",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val rule = getRuleForPackage(currentPkg, xSharedPrefs) ?: return
                            if (rule.advertisingId.isNotBlank()) param.result = rule.advertisingId
                        }
                    }
                )
                XposedBridge.hookAllMethods(
                    adInfoCls,
                    "isLimitAdTrackingEnabled",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (getRuleForPackage(currentPkg, xSharedPrefs) != null) {
                                param.result = true
                            }
                        }
                    }
                )
            }
        } catch (_: Throwable) {}
    }

    private fun attachPrivacyShieldHooks(
        lpparam: XC_LoadPackage.LoadPackageParam,
        currentPkg: String,
        xSharedPrefs: XSharedPreferences?
    ) {
        // 1. VPN & Proxy Hiding (NetworkInterface.getNetworkInterfaces & NetworkCapabilities.hasTransport)
        try {
            XposedBridge.hookAllMethods(
                NetworkInterface::class.java,
                "getNetworkInterfaces",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val rule = getRuleForPackage(currentPkg, xSharedPrefs) ?: return
                        if (!rule.hideVpnAndProxy) return
                        val origEnum = param.result as? Enumeration<*> ?: return
                        val filtered = ArrayList<NetworkInterface>()
                        while (origEnum.hasMoreElements()) {
                            val ni = origEnum.nextElement() as? NetworkInterface ?: continue
                            val name = ni.name?.lowercase().orEmpty()
                            if (VPN_IFACE_PREFIXES.none { name.startsWith(it) }) {
                                filtered.add(ni)
                            }
                        }
                        param.result = Collections.enumeration(filtered)
                    }
                }
            )
            XposedBridge.hookAllMethods(
                NetworkCapabilities::class.java,
                "hasTransport",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val transport = param.args.firstOrNull() as? Int ?: return
                        if (transport == NetworkCapabilities.TRANSPORT_VPN) {
                            val rule = getRuleForPackage(currentPkg, xSharedPrefs) ?: return
                            if (rule.hideVpnAndProxy) {
                                param.result = false
                            }
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // 2. Hide Root, Magisk, KernelSU & LSPosed packages and stacktraces
        val apmClass = XposedHelpers.findClassIfExists(
            "android.app.ApplicationPackageManager",
            lpparam.classLoader
        )
        if (apmClass != null) {
            val hideRootPkgHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val rule = getRuleForPackage(currentPkg, xSharedPrefs) ?: return
                    if (!rule.hideRootAndXposed) return
                    val queriedPkg = param.args.firstOrNull { it is String } as? String ?: return
                    if (queriedPkg in ROOT_AND_XPOSED_PACKAGES) {
                        param.throwable = PackageManager.NameNotFoundException(queriedPkg)
                    }
                }
            }
            for (m in listOf("getPackageInfo", "getApplicationInfo", "getPackageUid")) {
                try {
                    XposedBridge.hookAllMethods(apmClass, m, hideRootPkgHook)
                } catch (_: Throwable) {}
            }
        }

        // Scrub Xposed/LSPosed stack frames when hideRootAndXposed is enabled
        try {
            XposedBridge.hookAllMethods(
                Throwable::class.java,
                "getStackTrace",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val rule = getRuleForPackage(currentPkg, xSharedPrefs) ?: return
                        if (!rule.hideRootAndXposed) return
                        val original = param.result as? Array<*> ?: return
                        val filtered = original.filterIsInstance<StackTraceElement>().filter { el ->
                            val cls = el.className
                            !cls.startsWith("de.robv.android.xposed") &&
                                    !cls.contains("LSPHooker") &&
                                    !cls.startsWith("org.lsposed") &&
                                    !cls.startsWith("com.example.isolation.hook")
                        }
                        if (filtered.size != original.size) {
                            param.result = filtered.toTypedArray()
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // 3. Clipboard Privacy Shield
        try {
            XposedBridge.hookAllMethods(
                ClipboardManager::class.java,
                "getPrimaryClip",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val rule = getRuleForPackage(currentPkg, xSharedPrefs) ?: return
                        if (rule.blockClipboardRead) {
                            param.result = null
                        }
                    }
                }
            )
            XposedBridge.hookAllMethods(
                ClipboardManager::class.java,
                "hasPrimaryClip",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val rule = getRuleForPackage(currentPkg, xSharedPrefs) ?: return
                        if (rule.blockClipboardRead) {
                            param.result = false
                        }
                    }
                }
            )
        } catch (_: Throwable) {}
    }
}
