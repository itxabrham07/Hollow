package com.example.isolation.model

import android.content.Context

/** Manager-wide switches shown in Settings, App Hider, and TopAppBar. */
data class AppSettings(
    /** Master switch for the root-only volume features (attach, detach, seal). */
    val rootVolumesEnabled: Boolean = false,
    /** Seal (encrypt and wipe) a volume whenever it is detached. */
    val encryptVolumesAtRest: Boolean = true,
    /** What isolated apps get when they open the system picker. */
    val pickerPolicy: PickerPolicy = PickerPolicy.ALLOW,
    /** Material 3 Pro Dark Theme toggle (enabled by default). */
    val darkTheme: Boolean = true,
    /** Material You Dynamic Color on Android 12+. */
    val dynamicColor: Boolean = false,
    /** How Hollow itself appears in launchers & SystemUI. */
    val hollowVisibilityMode: LauncherVisibilityMode = LauncherVisibilityMode.VISIBLE,
    /** Custom spoofed launcher name for Hollow when disguised. */
    val hollowCustomName: String = "Calculator",
    /** Decoy icon preset for Hollow when disguised. */
    val hollowIconPreset: DisguiseIconPreset = DisguiseIconPreset.CALCULATOR,
    /** Optional custom image Base64 for Hollow when disguised. */
    val hollowCustomIconBase64: String = "",
    /** Optional donor app package for Hollow when disguised. */
    val hollowCustomIconDonorPkg: String = "",
    /** Spoof Hollow's own name & icon in System Settings / App Management. */
    val hollowSpoofInSettings: Boolean = true,
    /** Injects a direct stealth entry trigger into SystemUI (Quick Settings Clock/Header long-press). */
    val systemUiTriggerEnabled: Boolean = true
)

object SettingsStore {
    private const val FILE = "isovault_settings"
    private const val K_ROOT = "root_volumes_enabled"
    private const val K_ENCRYPT = "encrypt_volumes_at_rest"
    private const val K_PICKER = "picker_policy"
    private const val K_DARK = "dark_theme_v2"
    private const val K_DYNAMIC = "dynamic_color"
    private const val K_HOLLOW_VISIBILITY = "hollow_visibility_mode"
    private const val K_HOLLOW_NAME = "hollow_custom_name"
    private const val K_HOLLOW_ICON = "hollow_icon_preset"
    private const val K_HOLLOW_CUSTOM_ICON_B64 = "hollow_custom_icon_b64"
    private const val K_HOLLOW_DONOR_PKG = "hollow_donor_pkg"
    private const val K_HOLLOW_SPOOF_SETTINGS = "hollow_spoof_settings"
    private const val K_SYSTEMUI_TRIGGER = "systemui_trigger_enabled"

    fun load(context: Context): AppSettings {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val picker = try {
            PickerPolicy.valueOf(p.getString(K_PICKER, PickerPolicy.ALLOW.name) ?: PickerPolicy.ALLOW.name)
        } catch (_: IllegalArgumentException) {
            PickerPolicy.ALLOW
        }
        val hollowVis = try {
            LauncherVisibilityMode.valueOf(
                p.getString(K_HOLLOW_VISIBILITY, LauncherVisibilityMode.VISIBLE.name)
                    ?: LauncherVisibilityMode.VISIBLE.name
            )
        } catch (_: IllegalArgumentException) {
            LauncherVisibilityMode.VISIBLE
        }
        val hollowIcon = try {
            DisguiseIconPreset.valueOf(
                p.getString(K_HOLLOW_ICON, DisguiseIconPreset.CALCULATOR.name)
                    ?: DisguiseIconPreset.CALCULATOR.name
            )
        } catch (_: IllegalArgumentException) {
            DisguiseIconPreset.CALCULATOR
        }
        return AppSettings(
            rootVolumesEnabled = p.getBoolean(K_ROOT, false),
            encryptVolumesAtRest = p.getBoolean(K_ENCRYPT, true),
            pickerPolicy = picker,
            darkTheme = p.getBoolean(K_DARK, true),
            dynamicColor = p.getBoolean(K_DYNAMIC, false),
            hollowVisibilityMode = hollowVis,
            hollowCustomName = p.getString(K_HOLLOW_NAME, "Calculator") ?: "Calculator",
            hollowIconPreset = hollowIcon,
            hollowCustomIconBase64 = p.getString(K_HOLLOW_CUSTOM_ICON_B64, "") ?: "",
            hollowCustomIconDonorPkg = p.getString(K_HOLLOW_DONOR_PKG, "") ?: "",
            hollowSpoofInSettings = p.getBoolean(K_HOLLOW_SPOOF_SETTINGS, true),
            systemUiTriggerEnabled = p.getBoolean(K_SYSTEMUI_TRIGGER, true)
        )
    }

    fun save(context: Context, settings: AppSettings) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(K_ROOT, settings.rootVolumesEnabled)
            .putBoolean(K_ENCRYPT, settings.encryptVolumesAtRest)
            .putString(K_PICKER, settings.pickerPolicy.name)
            .putBoolean(K_DARK, settings.darkTheme)
            .putBoolean(K_DYNAMIC, settings.dynamicColor)
            .putString(K_HOLLOW_VISIBILITY, settings.hollowVisibilityMode.name)
            .putString(K_HOLLOW_NAME, settings.hollowCustomName)
            .putString(K_HOLLOW_ICON, settings.hollowIconPreset.name)
            .putString(K_HOLLOW_CUSTOM_ICON_B64, settings.hollowCustomIconBase64)
            .putString(K_HOLLOW_DONOR_PKG, settings.hollowCustomIconDonorPkg)
            .putBoolean(K_HOLLOW_SPOOF_SETTINGS, settings.hollowSpoofInSettings)
            .putBoolean(K_SYSTEMUI_TRIGGER, settings.systemUiTriggerEnabled)
            .commit()
        // Hooked apps read the picker policy and SystemUI trigger through the rule channels.
        IsolationRuleManager.publishPickerPolicy(settings.pickerPolicy)
        IsolationRuleManager.publishSystemUiTrigger(settings.systemUiTriggerEnabled)
    }
}
