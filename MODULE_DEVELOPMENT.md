# Hollow Module Development Guide (`Zero-Touch Core Architecture`)

Hollow is architected so that **new protection modules and features can be added and shipped to authorized users without modifying a single file in the Core APK section** (`MainActivity.kt`, `DashboardScreen.kt`, `OnboardingAuthScreen.kt`, `HollowModuleRegistry.kt`, or `/web/server.js`).

Every mounted module automatically:
1. Appears on the **Dashboard** as a Material 3 **Bento Card** (`mountedModules.chunked(2)`).
2. Opens its interactive control screen when the card is tapped.
3. Persists its encrypted binary and feature states inside the hardware-tethered Root Secret Volume at `/data/adb/.hollow_vault/modules/<module_id>.dex.enc` (surviving Android **"Clear App Data"**).

---

## 1. Repository Directory Structure

```text
/
├── Dockerfile                              # Cloud Admin + Module Delivery Container
├── MODULE_DEVELOPMENT.md                   # This developer guide
├── web/
│   └── server.js                           # Auto-scans /modules/*/manifest.json on startup
├── modules/                                # Server-shipped dynamic modules directory
│   ├── module_hider/
│   │   ├── manifest.json
│   │   └── HiderModulePayload.kt
│   ├── module_isolation/
│   │   ├── manifest.json
│   │   └── IsolationModulePayload.kt
│   ├── module_privacy/
│   │   ├── manifest.json
│   │   └── PrivacyModulePayload.kt
│   ├── module_network/
│   │   ├── manifest.json
│   │   └── NetworkModulePayload.kt
│   └── module_sensor_firewall/             # Example 5th zero-touch server module
│       └── manifest.json
└── app/src/main/java/com/example/isolation/
    ├── uikit/                              # Design system & reusable UI cards
    ├── core/                               # DO NOT TOUCH WHEN ADDING MODULES
    │   ├── license/                        # Hardware fingerprint & monotonic clock guard
    │   ├── root/                           # /data/adb/.hollow_vault manager
    │   ├── security/                       # Anti-MITM AES-256-GCM + Proxy.NO_PROXY client
    │   └── module/                         # HollowModule, HollowModuleRegistry, DynamicSchemaModule
    └── module/                             # Local custom Compose modules
        ├── ModuleCatalog.kt                # Single registration list for local Kotlin modules
        ├── hider/AppHiderModule.kt
        ├── isolation/StorageIsolationModule.kt
        ├── privacy/DevicePrivacyModule.kt
        └── network/VpnProxyModule.kt
```

---

## 2. Two Ways to Add a New Module (Zero Core Changes)

You can ship a new module using **Option A (Server-Only Manifest Module in `/modules/`)** without rebuilding the APK at all, or **Option B (Custom Compose Module in `module/`)** if you want a custom multi-tab Compose UI.

---

### Option A: Server-Only Dynamic Module (`/modules/<module_id>/manifest.json`)
**Requires ZERO APK rebuilds and ZERO changes to Android Kotlin files.**

#### Step 1: Create a new folder in `/modules/`
Create `/modules/module_<your_feature>/manifest.json`:

```json
{
  "moduleId": "module_clipboard_guard",
  "name": "Clipboard & Keystroke Shield",
  "cardTitle": "Clipboard Shield",
  "description": "Scrubs sensitive clipboard contents after 15 seconds and blocks background apps from reading primary clip data.",
  "version": 1,
  "minCoreVersion": 1,
  "sizeKb": 340,
  "accentColorHex": "0xFFEC4899",
  "entryClass": "com.example.isolation.module.clipboard.ClipboardPayload",
  "features": [
    {
      "key": "block_bg_clipboard_read",
      "title": "Block Background Clipboard Reads",
      "description": "Returns empty ClipData to any UID not currently in the foreground Activity.",
      "defaultEnabled": true
    },
    {
      "key": "auto_scrub_otp_tokens",
      "title": "Auto-Scrub OTP & Seed Phrases",
      "description": "Wipes clipboard buffer 15 seconds after detecting 6-digit codes or BIP-39 mnemonics.",
      "defaultEnabled": true
    }
  ],
  "hookTargets": [
    "android.content.ClipboardManager",
    "com.android.server.clipboard.ClipboardService"
  ]
}
```

#### Step 2: How it ships & renders automatically
1. **Server Auto-Discovery**: `/web/server.js` automatically scans `/modules/*/manifest.json` and adds `module_clipboard_guard` to MongoDB Atlas and the Admin Web Portal.
2. **Admin Grants Entitlement**: In the Admin Web Dashboard, toggle `module_clipboard_guard` ON for any approved user/device.
3. **Encrypted Root Vault Download**: The user's Hollow app receives a download notification on the Dashboard and downloads the AES-256-GCM encrypted payload into `/data/adb/.hollow_vault/modules/module_clipboard_guard.dex.enc`.
4. **Automatic Dashboard Bento Card & UI**:
   - `HollowModuleRegistry` detects `module_clipboard_guard` in `/data/adb/.hollow_vault/modules/` and instantiates a `DynamicSchemaModule` from its decrypted manifest.
   - `DashboardScreen` automatically renders the **"Clipboard Shield"** Bento Card in the 2-column grid.
   - Tapping the card opens `DynamicModuleScreen`, displaying interactive switches for every item in `features` (persisted in `/data/adb/.hollow_vault/modules/module_clipboard_guard_state.enc`) and its LSPosed/Zygisk `hookTargets`.

---

### Option B: Custom Compose Module (`app/src/main/java/com/example/isolation/module/<name>/`)
Use this option when your module needs a specialized custom Jetpack Compose screen. **You still never touch `MainActivity.kt` or `DashboardScreen.kt`.**

#### Step 1: Create your module package under `com.example.isolation.module.<name>`
Implement the `HollowModule` interface:

```kotlin
package com.example.isolation.module.firewall

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.isolation.core.module.HollowModule
import com.example.isolation.ui.viewmodel.IsoVaultViewModel

object FirewallModule : HollowModule {
    const val ID = "module_firewall"

    override val id: String = ID
    override val displayName: String = "BPF Root Packet Firewall"
    override val cardTitle: String = "Packet Firewall"
    override val summary: String = "Per-UID eBPF/iptables rules with background telemetry blocking."
    override val version: Int = 1
    override val requiredMinCoreVersion: Int = 1
    override val icon: ImageVector = Icons.Default.Security
    override val primaryRoute: String = "FIREWALL"
    override val contributesBottomNavTab: Boolean = false
    override val sizeKb: Int = 460
    override val accentColorHex: Long = 0xFFF43F5EL

    @Composable
    override fun resolveCardBadge(viewModel: IsoVaultViewModel): String = "eBPF"

    @Composable
    override fun resolveCardSubtitle(viewModel: IsoVaultViewModel): String =
        "Per-UID root firewall rules"

    @Composable
    override fun RenderPrimaryScreen(
        viewModel: IsoVaultViewModel,
        onBack: () -> Unit,
        onDetailOpenChanged: (Boolean) -> Unit
    ) {
        // Render your custom Compose screen here
        FirewallScreen(viewModel = viewModel, onBack = onBack)
    }
}
```

#### Step 2: Add 1 line to `ModuleCatalog.kt`
Open `app/src/main/java/com/example/isolation/module/ModuleCatalog.kt` and add `FirewallModule` to `registeredModules`:

```kotlin
object ModuleCatalog {
    val registeredModules: List<HollowModule> = listOf(
        StorageIsolationModule,
        DevicePrivacyModule,
        AppHiderModule,
        VpnProxyModule,
        FirewallModule // <-- Your new module automatically appears on the Dashboard!
    )
}
```

#### Step 3: Add `/modules/module_firewall/manifest.json`
Add a matching `/modules/module_firewall/manifest.json` so the Cloud Licensing Server can authorize and deliver the module to Release Shell users.

---

## 3. Core Invariants (Why Core Never Needs Edits)

- **Dashboard Bento Grid (`DashboardScreen.kt`)**: Iterates over `moduleStates.filter { it.isReadyToUse }.map { it.module }.chunked(2)` and calls `mod.cardTitle`, `mod.resolveCardSubtitle(viewModel)`, `mod.resolveCardBadge(viewModel)`, and `onOpenModule(mod.id)`.
- **Navigation Host (`MainActivity.kt`)**: Stores `activeModuleId: String?` and looks up `HollowModuleRegistry.findModuleById(openModuleId, moduleStates)` to invoke `mod.RenderPrimaryScreen(...)`.
- **Root Vault Persistence (`RootLicenseVaultManager.kt`)**: Stores all downloaded module payloads and per-module feature toggles inside `/data/adb/.hollow_vault/modules/`, encrypted with the device's hardware AES-256-GCM key (`SoC Serial + Android ID + Storage CID`).
