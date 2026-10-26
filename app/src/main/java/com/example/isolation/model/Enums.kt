package com.example.isolation.model

enum class IsolationMode(
    val title: String,
    val description: String,
    val badge: String
) {
    ENCRYPTED_VAULT(
        title = "Per-App Private Locker",
        description = "Gives this app its own isolated private vault away from all other apps.",
        badge = "Per-App Locker"
    ),
    SANDBOX_REDIRECT(
        title = "Per-App Separate Folder",
        description = "Gives this app its own clean sandbox folder away from your personal files.",
        badge = "Per-App Sandbox"
    ),
    SHARED_WORKSPACE(
        title = "Shared Hollow Workspace",
        description = "Connects this app to the shared Hollow Workspace folder while hiding your real phone storage.",
        badge = "Hollow Workspace"
    ),
    FAKE_EMPTY(
        title = "Blank Gallery",
        description = "Shows an empty gallery and folder list so your personal media stays unseen.",
        badge = "Blank Gallery"
    ),
    READ_ONLY(
        title = "View Only",
        description = "Stops the app from creating extra clutter or hidden folders on your phone.",
        badge = "View Only"
    )
}

/**
 * Controls how an app (or Hollow itself) appears across Android System Framework,
 * SystemUI, System Settings, and any existing or newly installed launcher.
 */
enum class LauncherVisibilityMode(
    val title: String,
    val description: String,
    val badge: String
) {
    VISIBLE(
        title = "Visible (Normal)",
        description = "App appears with its real name and icon in launchers and SystemUI.",
        badge = "Normal"
    ),
    DISGUISED(
        title = "Disguise Name & Icon",
        description = "Replaces the app's name and icon across launchers, System Settings, and SystemUI.",
        badge = "Disguised"
    ),
    HIDDEN(
        title = "Hide Completely",
        description = "Removes the app from every launcher drawer system-wide. Launch it from inside Hollow.",
        badge = "Hidden"
    )
}

/**
 * Disguise icon options: supports Custom Image from Gallery, Cloning any Installed App's Icon,
 * or built-in decoy icon presets.
 */
enum class DisguiseIconPreset(
    val label: String,
    val suggestedName: String
) {
    CUSTOM_IMAGE("Custom Image", ""),
    CLONED_APP("Clone App Icon", ""),
    CALCULATOR("Calculator", "Calculator"),
    NOTES("Notes", "Notes"),
    CALENDAR("Calendar", "Calendar"),
    CLOCK("Clock", "Clock"),
    WEATHER("Weather", "Weather"),
    SYSTEM_UTILITY("System Service", "System Service"),
    DEFAULT("Original Icon", "")
}

enum class AuditAction(val label: String, val isSevere: Boolean) {
    REDIRECTED("Kept Separate", false),
    ENCRYPTED("Saved Privately", false),
    BLOCKED("Hidden & Blocked", true),
    SPOOFED_EMPTY("Blank Gallery Shown", false),
    PERMITTED("Allowed Folder", false)
}

enum class MountEngineStatus {
    ACTIVE_LSPOSED,
    STANDALONE_USERSPACE,
    ROOT_FUSE_ATTACHED,
    INACTIVE
}

/** What a hooked app may do when it opens the system photo / file picker. */
enum class PickerPolicy(val title: String, val description: String) {
    ALLOW(
        title = "Allow",
        description = "The system picker opens normally and shows your whole phone. The app only receives what you choose."
    ),
    BLOCK(
        title = "Block",
        description = "The system picker is cancelled for isolated apps. They can only use their own in-app gallery, which is isolated."
    )
}

/**
 * Selectable launcher icon styles for Hollow when visible in the home launcher.
 * Supports Android 12+ dynamic wallpaper-reactive colors and Android 13+ monochrome themed icons.
 */
enum class HollowIconTheme(
    val label: String,
    val subtitle: String,
    val aliasClassName: String?
) {
    DYNAMIC_MATERIAL_YOU(
        label = "Dynamic",
        subtitle = "Adapts to wallpaper & Material You",
        aliasClassName = null // Uses default MainActivity with drawable-v31 dynamic vectors + monochrome
    ),
    CYBER_ECLIPSE(
        label = "Cyber Cyan",
        subtitle = "Obsidian & electric cyan portal",
        aliasClassName = "com.example.MainActivityCyberAlias"
    ),
    CRIMSON_VOID(
        label = "Crimson",
        subtitle = "Carbon black & crimson ring",
        aliasClassName = "com.example.MainActivityCrimsonAlias"
    ),
    OBSIDIAN_MINIMAL(
        label = "Minimal",
        subtitle = "Stealth monochrome titanium",
        aliasClassName = "com.example.MainActivityMinimalAlias"
    )
}

