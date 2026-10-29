package com.example.isolation.module.isolation

import android.graphics.drawable.Drawable

enum class IsolationMode(
    val title: String,
    val description: String,
    val badge: String
) {
    SHARED_WORKSPACE(
        title = "Hollow Drive (Default)",
        description = "Connects this app to Hollow Drive — the default shared virtual storage.",
        badge = "Hollow Drive"
    ),
    SANDBOX_REDIRECT(
        title = "Separate App Drive",
        description = "Assigns this app its own Separate Drive.",
        badge = "Separate Drive"
    ),
    ENCRYPTED_VAULT(
        title = "Encrypted Separate Drive",
        description = "Assigns this app an AES-256 encrypted Separate Drive.",
        badge = "Encrypted Drive"
    ),
    FAKE_EMPTY(
        title = "Blank Gallery",
        description = "Shows an empty gallery and folder list.",
        badge = "Blank Gallery"
    ),
    READ_ONLY(
        title = "View Only",
        description = "Prevents writes to public directories.",
        badge = "View Only"
    )
}

data class AppIsolationEntry(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val icon: Drawable? = null,
    val isIsolated: Boolean = false,
    val isolationMode: IsolationMode = IsolationMode.SHARED_WORKSPACE,
    val blockRootPollution: Boolean = true
)
