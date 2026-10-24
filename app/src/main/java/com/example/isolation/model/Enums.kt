package com.example.isolation.model

enum class IsolationMode(
    val title: String,
    val description: String,
    val badge: String
) {
    ENCRYPTED_VAULT(
        title = "Encrypted Vault (AES-256)",
        description = "Transparently redirects and encrypts storage I/O with AES-GCM.",
        badge = "AES-256"
    ),
    SANDBOX_REDIRECT(
        title = "Virtual Sandbox Redirection",
        description = "Redirects /sdcard requests into an isolated private directory.",
        badge = "REDIRECT"
    ),
    FAKE_EMPTY(
        title = "Empty Storage Spoofing",
        description = "Provides clean, empty filesystem so trackers find no personal files.",
        badge = "EMPTY"
    ),
    READ_ONLY(
        title = "Read-Only Enforced",
        description = "Blocks apps from polluting root storage with junk or tracker folders.",
        badge = "READ-ONLY"
    )
}

enum class AuditAction(val label: String, val isSevere: Boolean) {
    REDIRECTED("Redirected to Sandbox", false),
    ENCRYPTED("Encrypted on Write", false),
    BLOCKED("Blocked Root Pollution", true),
    SPOOFED_EMPTY("Spoofed Empty Listing", false),
    PERMITTED("Whitelisted Pass-Through", false)
}

enum class MountEngineStatus {
    ACTIVE_LSPOSED,
    STANDALONE_USERSPACE,
    ROOT_FUSE_ATTACHED,
    INACTIVE
}
