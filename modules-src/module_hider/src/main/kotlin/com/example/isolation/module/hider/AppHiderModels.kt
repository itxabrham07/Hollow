package com.example.isolation.module.hider

import android.graphics.drawable.Drawable

enum class LauncherVisibilityMode(val title: String, val badge: String) {
    VISIBLE("Visible (Normal)", "Normal"),
    DISGUISED("Disguise Name & Icon", "Disguised"),
    HIDDEN("Hide Completely", "Hidden")
}

enum class DisguiseIconPreset(val label: String, val suggestedName: String) {
    CALCULATOR("Calculator", "Calculator"),
    NOTES("Notes", "Notes"),
    CALENDAR("Calendar", "Calendar"),
    CLOCK("Clock", "Clock"),
    WEATHER("Weather", "Weather"),
    SYSTEM_UTILITY("System Service", "System Service"),
    DEFAULT("Original Icon", "")
}

data class StealthAppItem(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val icon: Drawable? = null,
    val launcherVisibilityMode: LauncherVisibilityMode = LauncherVisibilityMode.VISIBLE,
    val customLauncherLabel: String = "",
    val disguiseIconPreset: DisguiseIconPreset = DisguiseIconPreset.CALCULATOR,
    val hideInSystemUiRecents: Boolean = true,
    val hideFromPlayStore: Boolean = false,
    val spoofInSettings: Boolean = true
) {
    val isHiddenOrDisguised: Boolean
        get() = launcherVisibilityMode != LauncherVisibilityMode.VISIBLE || hideFromPlayStore
}
