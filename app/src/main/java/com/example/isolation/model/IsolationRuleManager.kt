package com.example.isolation.model

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import com.example.IsoVaultApp
import com.example.R
import com.example.isolation.data.entity.AppIsolationConfig
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Isolation & System-Wide Stealth rule storage and path helpers.
 *
 * Rules are published to hooked apps, SystemUI, Settings, Play Store, and Android System Framework
 * through two channels that only the manager can write:
 * the [com.example.isolation.provider.IsoVaultConfigProvider] (live, caller-authenticated) and a
 * world-readable SharedPreferences file read via XSharedPreferences.
 */
object IsolationRuleManager {

    const val MODULE_PKG = "com.aistudio.isovault.xrvf"
    const val PREFS_NAME = "isovault_rules"

    data class AppRule(
        val packageName: String,
        val isIsolated: Boolean,
        val mode: IsolationMode,
        val blockRootPollution: Boolean,
        val whitelistedFolders: Set<String>,
        val pickerPolicy: PickerPolicy = PickerPolicy.ALLOW
    )

    /**
     * System-wide stealth rule read by Android System Framework (`android`), `com.android.systemui`,
     * `com.android.settings`, `com.android.vending`, and any existing or newly installed launcher.
     */
    data class StealthRule(
        val packageName: String,
        val visibilityMode: LauncherVisibilityMode,
        val customLabel: String,
        val iconPreset: DisguiseIconPreset,
        val hideInRecents: Boolean = true,
        val hideFromPlayStore: Boolean = false,
        val spoofInSettings: Boolean = true,
        val donorPkg: String = "",
        val donorSourceDir: String = "",
        val donorIconRes: Int = 0,
        val customIconBase64: String = ""
    )

    /**
     * Per-App Device Identity & Privacy Spoofing rule read by hooked target apps.
     */
    data class IdentitySpoofRule(
        val packageName: String,
        val preset: DeviceProfilePreset = DeviceProfilePreset.PIXEL_9_PRO,
        val brand: String,
        val manufacturer: String,
        val model: String,
        val device: String,
        val product: String,
        val board: String,
        val fingerprint: String,
        val androidId: String,
        val imei: String,
        val advertisingId: String,
        val macAddress: String,
        val carrierName: String,
        val simOperator: String,
        val serial: String,
        val hideRootAndXposed: Boolean = true,
        val hideAdbAndDevOptions: Boolean = true,
        val hideVpnAndProxy: Boolean = true,
        val blockClipboardRead: Boolean = false,
        // Modular Features
        val isIdentitySpoofed: Boolean = false,
        val spoofLocationEnabled: Boolean = false,
        val spoofLatitude: Double = 37.7749,
        val spoofLongitude: Double = -122.4194,
        val hideMockLocation: Boolean = true,
        val preventScreenshots: Boolean = false,
        val hideOtherApps: Boolean = false,
        val disableLogcatLogging: Boolean = false,
        val fakeBatteryEnabled: Boolean = false,
        val fakeBatteryLevel: Int = 100,
        val knoxWarrantyBitSpoof: Boolean = false,
        val disableAllNetworking: Boolean = false,
        // Digital Footprint & Anti-Tracking Shield (Snap, Meta, Browsers)
        val spoofGpuFingerprint: Boolean = true,
        val spoofBrowserUserAgent: Boolean = true,
        val anonymizeSensors: Boolean = true,
        val blockCellTowerTracking: Boolean = true,
        val autoSyncTimezone: Boolean = true
    )

    const val KEY_PICKER_POLICY = "global_picker_policy"
    const val KEY_STEALTH_TABLE = "global_stealth_table_v1"
    const val KEY_IDENTITY_TABLE = "global_identity_table_v1"
    const val KEY_SYSTEMUI_TRIGGER = "systemui_trigger_enabled"
    private const val PREFIX_CUSTOM_ICON = "stealth_icon_b64_"

    fun publishPickerPolicy(policy: PickerPolicy) {
        val app = IsoVaultApp.instanceOrNull() ?: return
        try {
            getReadableSharedPrefs(app).edit().putString(KEY_PICKER_POLICY, policy.name).commit()
            ensureWorldReadable(app)
        } catch (_: Throwable) {
        }
    }

    fun publishSystemUiTrigger(enabled: Boolean) {
        val app = IsoVaultApp.instanceOrNull() ?: return
        try {
            getReadableSharedPrefs(app).edit().putBoolean(KEY_SYSTEMUI_TRIGGER, enabled).commit()
            ensureWorldReadable(app)
        } catch (_: Throwable) {
        }
    }

    fun customIconKey(packageName: String): String = "$PREFIX_CUSTOM_ICON$packageName"

    fun presetDrawableResId(preset: DisguiseIconPreset): Int {
        return when (preset) {
            DisguiseIconPreset.CALCULATOR -> R.drawable.ic_disguise_calculator
            DisguiseIconPreset.NOTES -> R.drawable.ic_disguise_notes
            DisguiseIconPreset.CALENDAR -> R.drawable.ic_disguise_calendar
            DisguiseIconPreset.CLOCK -> R.drawable.ic_disguise_clock
            DisguiseIconPreset.WEATHER -> R.drawable.ic_disguise_weather
            DisguiseIconPreset.SYSTEM_UTILITY -> R.drawable.ic_disguise_system
            else -> R.drawable.ic_disguise_calculator
        }
    }

    private fun resolveDonorResourceInfo(
        context: Context,
        preset: DisguiseIconPreset,
        donorPkg: String
    ): Pair<String, Int> {
        val pm = context.packageManager
        if (preset == DisguiseIconPreset.CLONED_APP && donorPkg.isNotBlank()) {
            try {
                val donorAppInfo = pm.getApplicationInfo(donorPkg, 0)
                val srcDir = donorAppInfo.publicSourceDir ?: donorAppInfo.sourceDir ?: ""
                val iconRes = if (donorAppInfo.icon != 0) donorAppInfo.icon else donorAppInfo.logo
                if (srcDir.isNotBlank() && iconRes != 0) {
                    return Pair(srcDir, iconRes)
                }
            } catch (_: Throwable) {
            }
        }
        if (preset != DisguiseIconPreset.DEFAULT) {
            val hollowSrc = context.applicationInfo?.publicSourceDir
                ?: context.applicationInfo?.sourceDir
                ?: ""
            val resId = presetDrawableResId(preset)
            if (hollowSrc.isNotBlank() && resId != 0) {
                return Pair(hollowSrc, resId)
            }
        }
        return Pair("", 0)
    }

    fun drawableToBase64Png(drawable: Drawable?, sizePx: Int = 144): String {
        if (drawable == null) return ""
        return try {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                val scaled = Bitmap.createScaledBitmap(drawable.bitmap, sizePx, sizePx, true)
                canvas.drawBitmap(scaled, 0f, 0f, null)
            } else {
                drawable.setBounds(0, 0, sizePx, sizePx)
                drawable.draw(canvas)
            }
            bitmapToBase64Png(bitmap)
        } catch (_: Throwable) {
            ""
        }
    }

    fun bitmapToBase64Png(bitmap: Bitmap, sizePx: Int = 144): String {
        return try {
            val scaled = if (bitmap.width != sizePx || bitmap.height != sizePx) {
                Bitmap.createScaledBitmap(bitmap, sizePx, sizePx, true)
            } else {
                bitmap
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 90, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Serializes all active app stealth rules + Hollow's own stealth settings into a compact
     * newline-delimited table in world-readable SharedPreferences so `android`, `com.android.systemui`,
     * `com.android.settings`, `com.android.vending`, and any launcher process can read the full
     * stealth map in a single fast lookup.
     *
     * Backwards-compatible format per line (10 pipe-delimited fields, with customLabel at index 4):
     * 0:pkg | 1:visMode | 2:iconPreset | 3:hideRecents | 4:customLabel | 5:hidePlayStore | 6:spoofSettings | 7:donorPkg | 8:donorSourceDir | 9:donorIconRes
     */
    fun publishStealthTable(
        configs: List<AppIsolationConfig>,
        settings: AppSettings
    ) {
        val app = IsoVaultApp.instanceOrNull() ?: return
        val prefs = getReadableSharedPrefs(app)
        val editor = prefs.edit()
        val entries = mutableListOf<String>()

        // 1. Hollow itself if disguised or hidden
        if (settings.hollowVisibilityMode != LauncherVisibilityMode.VISIBLE) {
            val rawLabel = settings.hollowCustomName.ifBlank {
                settings.hollowIconPreset.suggestedName.ifBlank { "Calculator" }
            }
            val safeLabel = rawLabel.replace("|", " ").replace("\n", " ").trim()
            val (donorSrc, donorRes) = resolveDonorResourceInfo(
                app,
                settings.hollowIconPreset,
                settings.hollowCustomIconDonorPkg
            )
            entries.add(
                listOf(
                    MODULE_PKG,
                    settings.hollowVisibilityMode.name,
                    settings.hollowIconPreset.name,
                    "1",
                    safeLabel,
                    "0",
                    if (settings.hollowSpoofInSettings) "1" else "0",
                    settings.hollowCustomIconDonorPkg.replace("|", ""),
                    donorSrc.replace("|", ""),
                    donorRes.toString()
                ).joinToString("|")
            )
            if (settings.hollowCustomIconBase64.isNotBlank()) {
                editor.putString(customIconKey(MODULE_PKG), settings.hollowCustomIconBase64)
            } else {
                editor.remove(customIconKey(MODULE_PKG))
            }
        } else {
            editor.remove(customIconKey(MODULE_PKG))
        }

        // 2. All apps enrolled in Hide & Disguise Apps (or hidden from Play Store)
        for (cfg in configs) {
            val active = (cfg.isHiddenOrDisguised && cfg.launcherVisibilityMode != LauncherVisibilityMode.VISIBLE) ||
                    cfg.hideFromPlayStore
            if (active) {
                val rawLabel = if (cfg.launcherVisibilityMode == LauncherVisibilityMode.DISGUISED && cfg.customLauncherLabel.isBlank()) {
                    cfg.disguiseIconPreset.suggestedName.ifBlank { "Calculator" }
                } else {
                    cfg.customLauncherLabel
                }
                val safeLabel = rawLabel.replace("|", " ").replace("\n", " ").trim()
                val (donorSrc, donorRes) = resolveDonorResourceInfo(
                    app,
                    cfg.disguiseIconPreset,
                    cfg.customIconDonorPkg
                )
                entries.add(
                    listOf(
                        cfg.packageName,
                        cfg.launcherVisibilityMode.name,
                        cfg.disguiseIconPreset.name,
                        if (cfg.hideInSystemUiRecents) "1" else "0",
                        safeLabel,
                        if (cfg.hideFromPlayStore) "1" else "0",
                        if (cfg.spoofInSettings) "1" else "0",
                        cfg.customIconDonorPkg.replace("|", ""),
                        donorSrc.replace("|", ""),
                        donorRes.toString()
                    ).joinToString("|")
                )
                if (cfg.customIconBase64.isNotBlank()) {
                    editor.putString(customIconKey(cfg.packageName), cfg.customIconBase64)
                } else {
                    editor.remove(customIconKey(cfg.packageName))
                }
            } else {
                editor.remove(customIconKey(cfg.packageName))
            }
        }

        // 3. All apps enrolled in Device Identity & Privacy Spoofing
        val identityEntries = mutableListOf<String>()
        for (cfg in configs) {
            val hasActivePrivacy = cfg.isIdentitySpoofed ||
                    cfg.spoofLocationEnabled ||
                    cfg.preventScreenshots ||
                    cfg.hideOtherApps ||
                    cfg.disableLogcatLogging ||
                    cfg.fakeBatteryEnabled ||
                    cfg.knoxWarrantyBitSpoof ||
                    cfg.disableAllNetworking ||
                    !cfg.hideRootAndXposed ||
                    !cfg.hideAdbAndDevOptions ||
                    !cfg.hideVpnAndProxy ||
                    cfg.blockClipboardRead

            if (hasActivePrivacy) {
                val preset = cfg.deviceProfilePreset
                val brand = cfg.spoofBrand.ifBlank { preset.brand }.replace("|", " ").trim()
                val manufacturer = if (cfg.spoofBrand.isNotBlank()) cfg.spoofBrand.replace("|", " ").trim() else preset.manufacturer
                val model = cfg.spoofModel.ifBlank { preset.model }.replace("|", " ").trim()
                val device = cfg.spoofDevice.ifBlank { preset.device }.replace("|", " ").trim()
                val product = preset.product
                val board = preset.board
                val fingerprint = preset.fingerprint
                val androidId = cfg.spoofAndroidId.ifBlank { generateAndroidId() }.replace("|", "").trim()
                val imei = cfg.spoofImei.ifBlank { generateLuhnImei() }.replace("|", "").trim()
                val adId = cfg.spoofAdvertisingId.ifBlank { generateAdvertisingId() }.replace("|", "").trim()
                val mac = cfg.spoofMacAddress.ifBlank { generateMacAddress() }.replace("|", "").trim()
                val carrier = cfg.spoofCarrierName.ifBlank { "T-Mobile" }.replace("|", " ").trim()
                val simOp = cfg.spoofSimOperator.ifBlank { "310260" }.replace("|", "").trim()
                val serial = cfg.spoofSerial.ifBlank { generateSerial() }.replace("|", "").trim()

                identityEntries.add(
                    listOf(
                        cfg.packageName,
                        preset.name,
                        brand,
                        manufacturer,
                        model,
                        device,
                        product,
                        board,
                        fingerprint,
                        androidId,
                        imei,
                        adId,
                        mac,
                        carrier,
                        simOp,
                        serial,
                        if (cfg.hideRootAndXposed) "1" else "0",
                        if (cfg.hideAdbAndDevOptions) "1" else "0",
                        if (cfg.hideVpnAndProxy) "1" else "0",
                        if (cfg.blockClipboardRead) "1" else "0",
                        if (cfg.isIdentitySpoofed) "1" else "0",
                        if (cfg.spoofLocationEnabled) "1" else "0",
                        cfg.spoofLatitude.toString(),
                        cfg.spoofLongitude.toString(),
                        if (cfg.hideMockLocation) "1" else "0",
                        if (cfg.preventScreenshots) "1" else "0",
                        if (cfg.hideOtherApps) "1" else "0",
                        if (cfg.disableLogcatLogging) "1" else "0",
                        if (cfg.fakeBatteryEnabled) "1" else "0",
                        cfg.fakeBatteryLevel.toString(),
                        if (cfg.knoxWarrantyBitSpoof) "1" else "0",
                        if (cfg.disableAllNetworking) "1" else "0",
                        if (cfg.spoofGpuFingerprint) "1" else "0",
                        if (cfg.spoofBrowserUserAgent) "1" else "0",
                        if (cfg.anonymizeSensors) "1" else "0",
                        if (cfg.blockCellTowerTracking) "1" else "0",
                        if (cfg.autoSyncTimezone) "1" else "0"
                    ).joinToString("|")
                )
            }
        }

        val serialized = entries.joinToString("\n")
        val serializedIdentity = identityEntries.joinToString("\n")
        try {
            editor
                .putString(KEY_STEALTH_TABLE, serialized)
                .putString(KEY_IDENTITY_TABLE, serializedIdentity)
                .putBoolean(KEY_SYSTEMUI_TRIGGER, settings.systemUiTriggerEnabled)
                .commit()
            ensureWorldReadable(app)
        } catch (_: Throwable) {
        }
    }

    fun parseIdentityTable(raw: String?): Map<String, IdentitySpoofRule> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = HashMap<String, IdentitySpoofRule>()
        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val p = trimmed.split("|")
            if (p.size < 20) continue
            val pkg = p[0]
            val preset = try {
                DeviceProfilePreset.valueOf(p[1])
            } catch (_: Exception) {
                DeviceProfilePreset.PIXEL_9_PRO
            }
            out[pkg] = IdentitySpoofRule(
                packageName = pkg,
                preset = preset,
                brand = p[2].ifBlank { preset.brand },
                manufacturer = p[3].ifBlank { preset.manufacturer },
                model = p[4].ifBlank { preset.model },
                device = p[5].ifBlank { preset.device },
                product = p[6].ifBlank { preset.product },
                board = p[7].ifBlank { preset.board },
                fingerprint = p[8].ifBlank { preset.fingerprint },
                androidId = p[9],
                imei = p[10],
                advertisingId = p[11],
                macAddress = p[12],
                carrierName = p[13],
                simOperator = p[14],
                serial = p[15],
                hideRootAndXposed = p[16] == "1",
                hideAdbAndDevOptions = p[17] == "1",
                hideVpnAndProxy = p[18] == "1",
                blockClipboardRead = p[19] == "1",
                isIdentitySpoofed = if (p.size > 20) p[20] == "1" else true,
                spoofLocationEnabled = if (p.size > 21) p[21] == "1" else false,
                spoofLatitude = if (p.size > 22) p[22].toDoubleOrNull() ?: 37.7749 else 37.7749,
                spoofLongitude = if (p.size > 23) p[23].toDoubleOrNull() ?: -122.4194 else -122.4194,
                hideMockLocation = if (p.size > 24) p[24] == "1" else true,
                preventScreenshots = if (p.size > 25) p[25] == "1" else false,
                hideOtherApps = if (p.size > 26) p[26] == "1" else false,
                disableLogcatLogging = if (p.size > 27) p[27] == "1" else false,
                fakeBatteryEnabled = if (p.size > 28) p[28] == "1" else false,
                fakeBatteryLevel = if (p.size > 29) p[29].toIntOrNull() ?: 100 else 100,
                knoxWarrantyBitSpoof = if (p.size > 30) p[30] == "1" else false,
                disableAllNetworking = if (p.size > 31) p[31] == "1" else false,
                spoofGpuFingerprint = if (p.size > 32) p[32] == "1" else true,
                spoofBrowserUserAgent = if (p.size > 33) p[33] == "1" else true,
                anonymizeSensors = if (p.size > 34) p[34] == "1" else true,
                blockCellTowerTracking = if (p.size > 35) p[35] == "1" else true,
                autoSyncTimezone = if (p.size > 36) p[36] == "1" else true
            )
        }
        return out
    }

    fun generateAndroidId(): String {
        val hex = "0123456789abcdef"
        val rnd = java.security.SecureRandom()
        return buildString(16) {
            repeat(16) { append(hex[rnd.nextInt(hex.length)]) }
        }
    }

    fun generateLuhnImei(): String {
        val tacs = listOf("35489111", "35842010", "35290411", "86740205", "35918210")
        val rnd = java.security.SecureRandom()
        val base = buildString(14) {
            append(tacs[rnd.nextInt(tacs.size)])
            repeat(6) { append(rnd.nextInt(10)) }
        }
        var sum = 0
        for (i in base.indices) {
            var d = base[i] - '0'
            if (i % 2 == 1) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
        }
        val checkDigit = (10 - (sum % 10)) % 10
        return base + checkDigit
    }

    fun generateAdvertisingId(): String = java.util.UUID.randomUUID().toString()

    fun generateMacAddress(): String {
        val rnd = java.security.SecureRandom()
        val bytes = ByteArray(6)
        rnd.nextBytes(bytes)
        // Set locally administered unicast bit (0x02)
        bytes[0] = ((bytes[0].toInt() and 0xFC) or 0x02).toByte()
        return bytes.joinToString(":") { "%02X".format(it) }
    }

    fun generateSerial(): String {
        val chars = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val rnd = java.security.SecureRandom()
        return buildString(12) {
            repeat(12) { append(chars[rnd.nextInt(chars.length)]) }
        }
    }

    fun generateRandomCarrier(): Pair<String, String> {
        val carriers = listOf(
            "T-Mobile" to "310260",
            "Verizon" to "311480",
            "AT&T" to "310410",
            "Vodafone" to "23415",
            "Jio 5G" to "405854",
            "Deutsche Telekom" to "26201"
        )
        return carriers[java.security.SecureRandom().nextInt(carriers.size)]
    }

    fun parseStealthTable(
        raw: String?,
        customIconLookup: ((String) -> String?)? = null
    ): Map<String, StealthRule> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = HashMap<String, StealthRule>()
        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split("|")
            if (parts.size < 4) continue
            val pkg = parts[0]
            val visMode = try {
                LauncherVisibilityMode.valueOf(parts[1])
            } catch (_: Exception) {
                LauncherVisibilityMode.VISIBLE
            }
            val preset = try {
                DisguiseIconPreset.valueOf(parts[2])
            } catch (_: Exception) {
                DisguiseIconPreset.CALCULATOR
            }
            val hideRecents = parts[3] == "1"

            val hidePlayStore: Boolean
            val spoofSettings: Boolean
            val donorPkg: String
            val donorSourceDir: String
            val donorIconRes: Int
            val customLabel: String

            // Detect transitional format where parts[4] & parts[5] were "0"/"1" flags and parts[8] was integer resId
            val isTransitionalOrder = parts.size >= 10 &&
                    (parts[4] == "0" || parts[4] == "1") &&
                    (parts[5] == "0" || parts[5] == "1") &&
                    (parts[8].toIntOrNull() != null) &&
                    parts[9].toIntOrNull() == null

            if (isTransitionalOrder) {
                hidePlayStore = parts[4] == "1"
                spoofSettings = parts[5] == "1"
                donorPkg = parts[6]
                donorSourceDir = parts[7]
                donorIconRes = parts[8].toIntOrNull() ?: 0
                customLabel = parts.subList(9, parts.size).joinToString(" ").trim()
            } else if (parts.size >= 10) {
                customLabel = parts[4].trim()
                hidePlayStore = parts[5] == "1"
                spoofSettings = parts[6] == "1"
                donorPkg = parts[7]
                donorSourceDir = parts[8]
                donorIconRes = parts[9].toIntOrNull() ?: 0
            } else {
                val rawLabel = if (parts.size >= 5) parts[4].trim() else ""
                // Guard against numeric resource IDs or "0"/"1" flags leaking into customLabel
                customLabel = if (rawLabel == "0" || rawLabel == "1" || (rawLabel.length >= 8 && rawLabel.all { it.isDigit() })) {
                    preset.suggestedName.ifBlank { "Calculator" }
                } else {
                    rawLabel
                }
                hidePlayStore = false
                spoofSettings = true
                donorPkg = ""
                donorSourceDir = ""
                donorIconRes = 0
            }

            if (visMode == LauncherVisibilityMode.VISIBLE && !hidePlayStore) continue

            // Final sanity check: never allow a pure 8+ digit resource ID or single "0"/"1" flag as the label
            val cleanLabel = if (visMode == LauncherVisibilityMode.DISGUISED &&
                (customLabel.isBlank() || customLabel == "0" || customLabel == "1" || (customLabel.length >= 8 && customLabel.all { it.isDigit() }))
            ) {
                preset.suggestedName.ifBlank { "Calculator" }
            } else {
                customLabel
            }

            val iconB64 = customIconLookup?.invoke(pkg).orEmpty()

            out[pkg] = StealthRule(
                packageName = pkg,
                visibilityMode = visMode,
                customLabel = cleanLabel,
                iconPreset = preset,
                hideInRecents = hideRecents,
                hideFromPlayStore = hidePlayStore,
                spoofInSettings = spoofSettings,
                donorPkg = donorPkg,
                donorSourceDir = donorSourceDir,
                donorIconRes = donorIconRes,
                customIconBase64 = iconB64
            )
        }
        return out
    }

    /**
     * Checks if Vector / LSPosed has hooked Context.getSharedPreferences(MODE_WORLD_READABLE).
     * When a module with xposedsharedprefs="true" is enabled in Vector/LSPosed,
     * MODE_WORLD_READABLE (1) succeeds without throwing SecurityException.
     */
    @SuppressLint("WorldReadableFiles")
    @Suppress("DEPRECATION")
    fun isXSharedPrefsHooked(context: Context): Boolean {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE)
            true
        } catch (_: Throwable) {
            false
        }
    }

    @SuppressLint("WorldReadableFiles")
    @Suppress("DEPRECATION")
    fun getReadableSharedPrefs(context: Context): SharedPreferences {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE)
        } catch (_: Throwable) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Ensures that `isovault_rules.xml` and its parent directories have world-readable permissions
     * after every `.commit()`, since Android's SharedPreferencesImpl recreates the XML file on write.
     */
    @SuppressLint("SetWorldReadable")
    fun ensureWorldReadable(context: Context) {
        try {
            val dataDir = context.dataDir ?: File(context.applicationInfo.dataDir)
            dataDir.setExecutable(true, false)
            dataDir.setReadable(true, false)
            val prefsDir = File(dataDir, "shared_prefs")
            if (prefsDir.exists()) {
                prefsDir.setExecutable(true, false)
                prefsDir.setReadable(true, false)
                val prefsFile = File(prefsDir, "$PREFS_NAME.xml")
                if (prefsFile.exists()) {
                    prefsFile.setReadable(true, false)
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun updateRule(
        packageName: String,
        mode: IsolationMode,
        blockRootPollution: Boolean = true,
        whitelistedFolders: Set<String> = emptySet()
    ) {
        val cleanWhitelist = whitelistedFolders.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        publish(
            pkg = packageName,
            isolated = true,
            mode = mode,
            blockRootPollution = blockRootPollution,
            whitelist = cleanWhitelist.joinToString(",")
        )
    }

    /** Marks [packageName] as explicitly not isolated, keeping its other settings. */
    fun disableRule(
        packageName: String,
        mode: IsolationMode,
        blockRootPollution: Boolean,
        whitelistedFolders: Set<String>
    ) {
        publish(
            pkg = packageName,
            isolated = false,
            mode = mode,
            blockRootPollution = blockRootPollution,
            whitelist = whitelistedFolders.joinToString(",")
        )
    }

    private fun publish(
        pkg: String,
        isolated: Boolean,
        mode: IsolationMode,
        blockRootPollution: Boolean,
        whitelist: String
    ) {
        val app = IsoVaultApp.instanceOrNull() ?: return
        try {
            getReadableSharedPrefs(app).edit()
                .putBoolean("${pkg}_configured", true)
                .putBoolean("${pkg}_isolated", isolated)
                .putBoolean("${pkg}_explicitly_disabled", !isolated)
                .putString("${pkg}_mode", mode.name)
                .putBoolean("${pkg}_pollution", blockRootPollution)
                .putString("${pkg}_whitelist", whitelist)
                .commit() // Synchronous so XSharedPreferences readers see the change immediately
            ensureWorldReadable(app)
        } catch (_: Throwable) {
        }
    }

    fun isRootPollution(path: String): Boolean {
        val patterns = listOf("/.tencent", "/.baidu", "/.pdd", "/.snssdk", "/.amap", "/.alipay", "/tencent/", "/baidu/")
        return patterns.any { path.contains(it, ignoreCase = true) }
    }

    fun extractRelative(path: String): String {
        val prefixes = listOf(
            "/storage/emulated/0/",
            "/storage/self/primary/",
            "/sdcard/",
            "/mnt/sdcard/"
        )
        for (p in prefixes) {
            if (path.startsWith(p)) return sanitizeRelative(path.removePrefix(p))
        }
        // Bare roots ("/sdcard", "/storage/emulated/0") map to the sandbox root
        val roots = listOf("/storage/emulated/0", "/storage/self/primary", "/sdcard", "/mnt/sdcard")
        if (roots.any { path == it }) return ""
        return sanitizeRelative(path.trimStart('/'))
    }

    /**
     * Drops "." and ".." segments so a redirected path can never escape the sandbox root
     * (e.g. "/sdcard/../../data/data/other.app/databases").
     */
    fun sanitizeRelative(relative: String): String {
        val out = ArrayList<String>()
        for (segment in relative.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (out.isNotEmpty()) out.removeAt(out.size - 1)
                else -> out.add(segment)
            }
        }
        return out.joinToString("/")
    }
}
