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
