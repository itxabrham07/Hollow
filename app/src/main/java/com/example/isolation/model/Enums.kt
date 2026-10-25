package com.example.isolation.model

enum class IsolationMode(
    val title: String,
    val description: String,
    val badge: String
) {
    ENCRYPTED_VAULT(
        title = "Private Locker",
        description = "Keeps photos & files saved by this app locked in its own private space.",
        badge = "Private Locker"
    ),
    SANDBOX_REDIRECT(
        title = "Separate Folder",
        description = "Gives this app its own clean folder away from your personal files.",
        badge = "Separate Folder"
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
