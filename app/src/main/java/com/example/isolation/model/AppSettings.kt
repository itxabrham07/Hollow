package com.example.isolation.model

import android.content.Context

/** Manager-wide switches shown in Settings and TopAppBar. */
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
    val dynamicColor: Boolean = false
)

object SettingsStore {
    private const val FILE = "isovault_settings"
    private const val K_ROOT = "root_volumes_enabled"
    private const val K_ENCRYPT = "encrypt_volumes_at_rest"
    private const val K_PICKER = "picker_policy"
    private const val K_DARK = "dark_theme_v2"
    private const val K_DYNAMIC = "dynamic_color"

    fun load(context: Context): AppSettings {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val picker = try {
            PickerPolicy.valueOf(p.getString(K_PICKER, PickerPolicy.ALLOW.name) ?: PickerPolicy.ALLOW.name)
        } catch (_: IllegalArgumentException) {
            PickerPolicy.ALLOW
        }
        return AppSettings(
            rootVolumesEnabled = p.getBoolean(K_ROOT, false),
            encryptVolumesAtRest = p.getBoolean(K_ENCRYPT, true),
            pickerPolicy = picker,
            darkTheme = p.getBoolean(K_DARK, true),
            dynamicColor = p.getBoolean(K_DYNAMIC, false)
        )
    }

    fun save(context: Context, settings: AppSettings) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(K_ROOT, settings.rootVolumesEnabled)
            .putBoolean(K_ENCRYPT, settings.encryptVolumesAtRest)
            .putString(K_PICKER, settings.pickerPolicy.name)
            .putBoolean(K_DARK, settings.darkTheme)
            .putBoolean(K_DYNAMIC, settings.dynamicColor)
            .commit()
        // Hooked apps read the picker policy through the rule channels, so publish it there too.
        IsolationRuleManager.publishPickerPolicy(settings.pickerPolicy)
    }
}
