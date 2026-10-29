# Hollow Module Developer Guide (`MODULE_DEVELOPMENT.md`)

**Single Source of Truth for Developing, Building, Signing, and Publishing Standalone Hollow DEX Modules.**

The Hollow Core APK (`:app`) is a **Zero-Feature Host Shell**. No feature screens, ViewModels, or Xposed hook payloads exist in the Core APK's `classes.dex`. Every feature ships as an independently developed, cryptographically signed standalone DEX module (`module.dex.bin` + `manifest.json`) compiled exclusively against the frozen `:hollow-sdk` library.

---

## 1. Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                              HOLLOW CLOUD SERVER                             │
│  /modules/<module_id>/manifest.json + module.dex.bin                         │
│  GET  /api/v1/modules/pending          (Device-bound pending update queue)   │
│  POST /api/v1/modules/download/:id     (AES-256-GCM + Ed25519 signed bundle) │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │ HTTPS (HollowSecureClient + Anti-MITM)
                                ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                           HOLLOW CORE APK (:app)                             │
│                                                                              │
│  ┌─────────────────────┐    ┌──────────────────────┐   ┌──────────────────┐  │
│  │  ModuleSyncWorker   │───▶│  RootLicenseVault    │──▶│  SafeModeManager │  │
│  │ (15m poll + Notif)  │    │ /data/adb/.hollow_   │   │  & Quarantine    │  │
│  └─────────────────────┘    │   vault/modules/     │   └──────────────────┘  │
│                             │  <id>.dex.enc (.bak) │                         │
│                             └──────────┬───────────┘                         │
│                                        │                                     │
│                                        ▼                                     │
│                        ┌───────────────────────────────┐                     │
│                        │     HollowDexModuleLoader     │                     │
│                        │  1. SHA-256 Checksum Verify   │                     │
│                        │  2. Ed25519 Signature Verify  │                     │
│                        │  3. minCoreVersion Gate       │                     │
│                        │  4. Read-Only DexClassLoader  │                     │
│                        └───────────────┬───────────────┘                     │
│                                        │                                     │
│         ┌──────────────────────────────┴──────────────────────────────┐      │
│         ▼                                                             ▼      │
│  ┌─────────────────────────────┐                               ┌───────────┐ │
│  │   :hollow-sdk (API Bridge)  │                               │HookBridge │ │
│  │ - HollowModule              │                               │(Zygote /  │ │
│  │ - HollowModuleContext       │                               │Xposed)    │ │
│  │ - UI Kit (ContentCard, etc) │                               └─────┬─────┘ │
│  └──────────────┬──────────────┘                                     │       │
└─────────────────┼────────────────────────────────────────────────────┼───────┘
                  │ Parent ClassLoader Delegation                      │
                  ▼                                                    ▼
┌──────────────────────────────────────┐            ┌──────────────────────────┐
│     STANDALONE MODULE DEX (.dex)     │            │   TARGET APP / SYSTEM    │
│  Implements HollowModule             │            │  Implements              │
│  Renders Compose UI via SDK Kit      │            │  HollowHookEntry         │
│  Stores state via HollowModuleContext│            │  (Isolated try/catch)    │
└──────────────────────────────────────┘            └──────────────────────────┘
```

### Why Are There Two Module Folders in the Repository?
1. **`modules-src/<module_id>/` (Source Code & Build Input):**
   Contains the human-written Kotlin source code (`src/main/kotlin/...`), `module.json` specification, and `README.md` for each standalone module.
2. **`modules/<module_id>/` (Compiled & Signed Distribution Output):**
   Contains the compiled `module.dex.bin` and Ed25519-signed `manifest.json` produced by `./gradlew buildModule` or `./gradlew buildAllModules`. The Node.js server (`web/server.js`) serves files directly from `/modules/<module_id>/` to authorized devices.

---

## 2. `:hollow-sdk` API Reference

Modules compile **only** against `:hollow-sdk` (`compileOnly`). At runtime, the Core APK provides these classes via the parent `ClassLoader`.

### 2.1 `HollowModule` Interface
Every feature module must expose an entry class (Kotlin `object` or no-arg `class`) implementing `HollowModule`:

```kotlin
package com.example.isolation.core.module

interface HollowModule {
    val id: String
    val displayName: String
    val cardTitle: String get() = displayName
    val summary: String
    val version: Int
    val requiredMinCoreVersion: Int
    val icon: ImageVector
    val primaryRoute: String
    val contributesBottomNavTab: Boolean
    val sizeKb: Int
    val accentColorHex: Long get() = 0xFF10B981L
    val cardTestTag: String get() = "module_card_$id"

    // Lifecycle Hooks (invoked inside Core's crash-isolation guard)
    fun onMount(context: HollowModuleContext) {}
    fun onUnmount() {}
    fun onHookInit(bridge: Any) {}

    @Composable
    fun resolveCardBadge(context: HollowModuleContext): String = "v$version"

    @Composable
    fun resolveCardSubtitle(context: HollowModuleContext): String = summary

    @Composable
    fun RenderPrimaryScreen(
        context: HollowModuleContext,
        onBack: () -> Unit,
        onDetailOpenChanged: (Boolean) -> Unit
    )
}
```

### 2.2 `HollowModuleContext` Capabilities & Examples
`HollowModuleContext` replaces direct `ViewModel` or Core database access. Each module receives an isolated context scoped to its `moduleId`.

#### 1. Module-Private Key-Value Store (`context.preferences`)
Backed by encrypted Root Vault state (`/data/adb/.hollow_vault/modules/<moduleId>_state.enc`) with local mirror fallback.
```kotlin
// Read & write primitives
val isEnabled = context.preferences.getBoolean("guard_enabled", defaultValue = true)
context.preferences.putBoolean("guard_enabled", false)

val mode = context.preferences.getString("filter_mode", "STRICT")
context.preferences.putString("filter_mode", "BALANCED")

// Reactive state flow for Compose UI
val enabledFlow: StateFlow<Boolean> = context.preferences.observeBoolean("guard_enabled", true)
```

#### 2. Module-Private SQLite Database Factory (`context.databaseFactory`)
Creates a sandboxed SQLite database file (`hollow_mod_<moduleId>_<dbName>.db`) owned exclusively by the module:
```kotlin
val db = context.databaseFactory.openDatabase("clipboard_rules.db", version = 1) { sqliteDb ->
    sqliteDb.execSQL(
        "CREATE TABLE IF NOT EXISTS rules (pkg TEXT PRIMARY KEY, block_read INTEGER)"
    )
}
```

#### 3. Cross-Module Event Bus (`context.eventBus`)
Publish/subscribe JSON or key-value events without coupling modules:
```kotlin
// Publish an event
context.eventBus.publish(
    topic = "hollow.security.policy_changed",
    payloadJson = """{"moduleId":"$id","timestamp":${System.currentTimeMillis()}}"""
)

// Subscribe to events
LaunchedEffect(Unit) {
    context.eventBus.events.collect { event ->
        if (event.topic == "hollow.security.policy_changed") {
            context.logger.i("MyModule", "Received policy update: ${event.payloadJson}")
        }
    }
}
```

#### 4. Root Shell Helper (`context.rootShell`)
Execute privileged root commands safely off the main thread:
```kotlin
if (context.rootShell.isRootAvailable()) {
    val result = context.rootShell.exec("ls -la /data/adb/.hollow_vault")
    if (result.exitCode == 0) {
        context.logger.i("MyModule", "Output: ${result.stdout}")
    }
}
```

#### 5. Audit Log Writer (`context.auditLogger`)
Appends tamper-evident entries to the Core Security Audit Trail:
```kotlin
context.auditLogger.logAction(
    targetPackage = "com.snapchat.android",
    actionType = "CLIPBOARD_SCRUBBED",
    details = "Blocked background clipboard read attempt"
)
```

#### 6. Permission & Feature Flag Helper (`context.permissions`, `context.featureFlags`)
```kotlin
val hasOverlay = context.permissions.isPermissionGranted(android.Manifest.permission.SYSTEM_ALERT_WINDOW)
val isProFeatureEnabled = context.featureFlags.isFeatureEnabled("deep_clipboard_hook")
```

#### 7. Shared UI Kit (`com.example.isolation.ui.components.*`)
Reuse Hollow's Material 3 design system from `:hollow-sdk`:
- `ContentCard { ... }`
- `SectionHeader(title = "...", subtitle = "...")`
- `StatusIndicator(label = "ACTIVE", color = M3Emerald)`
- `ListRow(title = "...", subtitle = "...", leadingIcon = ..., trailing = { ... })`
- `PrimaryButton(text = "Apply Rules", onClick = { ... })`

---

## 3. `module.json` Schema Reference

Every module directory `modules-src/<module_id>/` must contain a `module.json` file:

```json
{
  "moduleId": "module_clipboard_guard",
  "name": "Clipboard Guard",
  "version": 1,
  "minCoreVersion": 1,
  "entryClass": "com.example.isolation.module.clipboardguard.ClipboardGuardModule",
  "primaryRoute": "CLIPBOARD_GUARD",
  "contributesBottomNavTab": false,
  "features": [
    "Background Clipboard Access Blocking",
    "Auto-Scrub Sensitive Tokens After 30s"
  ],
  "permissions": [],
  "hookTargets": [
    "*"
  ],
  "accentColorHex": 4279376257
}
```

| Field | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `moduleId` | `String` | Yes | Unique snake_case identifier starting with `module_`. Must match folder name. |
| `name` | `String` | Yes | Human-readable module title displayed in notifications and catalog. |
| `version` | `Int` | Yes | Monotonically increasing integer version (`1`, `2`, `3`...). Triggers update notifications when incremented. |
| `minCoreVersion` | `Int` | Yes | Minimum `HOLLOW_SDK_VERSION` / `CORE_ENGINE_VERSION` required. If `minCoreVersion > core`, Core skips mounting gracefully. |
| `entryClass` | `String` | Yes | Fully-qualified class name implementing `HollowModule` (and optionally `HollowHookEntry`). |
| `primaryRoute` | `String` | Yes | Unique uppercase navigation route key used by Core's generic router. |
| `contributesBottomNavTab` | `Boolean` | No | Whether this module adds a top-level bottom navigation tab when mounted. |
| `features` | `String[]` | Yes | Bullet points shown on the Install / Update inspection sheet. |
| `permissions` | `String[]` | No | Android permissions required by the module. |
| `hookTargets` | `String[]` | No | Package names (`"com.android.systemui"`, `"android"`, or `"*"` for all third-party apps) targeted by `HollowHookEntry`. |
| `accentColorHex` | `Long` | No | ARGB 64-bit integer color used for dashboard badges. |

---

## 4. Step-by-Step Development Workflow

### Step 1: Scaffold a New Module
Run the built-in generator task (never touches `:app`):
```bash
gradle newModule -Pid=module_clipboard_guard -Pname="Clipboard Guard"
```
This creates:
- `modules-src/module_clipboard_guard/module.json`
- `modules-src/module_clipboard_guard/README.md`
- `modules-src/module_clipboard_guard/src/main/kotlin/com/example/isolation/module/clipboardguard/ClipboardGuardModule.kt`

### Step 2: Develop UI & Logic Against `:hollow-sdk`
Write your Compose UI and state persistence inside `modules-src/<module_id>/src/main/kotlin/...` using `HollowModuleContext` and `:hollow-sdk` components.

### Step 3: Build & Sign the Standalone Module
Compile against `:hollow-sdk` only, convert to DEX via `d8`, compute SHA-256, and sign with Ed25519:
```bash
export HOLLOW_SIGNING_KEY="<base64_ed25519_private_key_pem>"
gradle buildModule -Pmodule=module_clipboard_guard
```
Or build all modules at once:
```bash
gradle buildAllModules
```
Outputs are written to:
- `modules/<module_id>/module.dex.bin`
- `modules/<module_id>/manifest.json`
- `app/src/debug/assets/debug_modules/<module_id>.json` (for offline debug testing)

### Step 4: Test Locally
1. **Debug Asset Mode:** Toggle **Dev Full Build** in Settings or run `./gradlew testDebugUnitTest` to verify loading via `HollowDexModuleLoader`.
2. **Root Vault Push:** Push the signed bundle directly to `/data/adb/.hollow_vault/modules/<module_id>.dex.enc` or use the in-app **Download & Mount** button connected to your local Node server.

### Step 5: Publish & Authorize Users
1. Commit the updated `modules/<module_id>/manifest.json` and `modules/<module_id>/module.dex.bin` to the server.
2. In the Admin Console (`/admin`), authorize `<module_id>` for the target device.
3. Within 15 minutes (or immediately on app open), `ModuleSyncWorker` queries `GET /api/v1/modules/pending`, posts the system notification **"New Module Available: <Name>"**, and mounts the module into the Dashboard without reinstalling the Core APK.

---

## 5. Hook Module Guide (`HollowHookEntry` & `HollowHookBridge`)

Core APK contains only `com.example.isolation.hook.HollowHookBridge` (referenced by `assets/xposed_init`). Feature hook code lives inside modules implementing `HollowHookEntry`.

### Implementing `HollowHookEntry`
```kotlin
package com.example.isolation.module.clipboardguard

import com.example.isolation.sdk.hook.HollowHookEntry
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object ClipboardGuardModule : HollowModule, HollowHookEntry {
    override val hookTargets: Set<String> = setOf("*")

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == "android" || lpparam.packageName == "com.android.systemui") return
        // Always wrap hook installations in defensive checks
        XposedHelpers.findAndHookMethod(
            "android.content.ClipboardManager",
            lpparam.classLoader,
            "getPrimaryClip",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    // Return null clip to untrusted background callers
                    param.result = null
                }
            }
        )
    }
}
```

### Cross-Process Staged DEX Loading & Reboot Rules
- Target apps (`com.snapchat.android`, `system_server`, `com.android.systemui`) cannot read `/data/data/com.aistudio.isovault.xrvf` directly.
- When a module with `hookTargets` is installed, Core stages a verified read-only copy in `/data/local/tmp/.hollow_hooks/<moduleId>/` (or `/data/adb/.hollow_vault/staged_hooks/`).
- `HollowHookBridge` re-verifies both the **SHA-256 digest** and **Ed25519 signature** inside the target process before loading any bytecode.
- **Restart Rules:**
  - Third-party app hooks (`"*"` or app package names): Take effect immediately upon force-stopping/restarting the target app.
  - SystemUI hooks (`"com.android.systemui"`): Require restarting SystemUI (`killall com.android.systemui`) or a soft reboot.
  - Framework hooks (`"android"` / `"system_server"`): Require a device reboot.
- **Emergency Kill-Switch:** Creating `/data/adb/.hollow_vault/DISABLE_HOOKS` immediately disables all dynamic hooks across every process on boot.

---

## 6. Versioning Rules & Frozen SDK Policy

- **`HOLLOW_SDK_VERSION = 1`** (`CORE_ENGINE_VERSION = 1`):
  The `:hollow-sdk` module is **append-only and binary-frozen**.
  - **NEVER** delete, rename, or change parameter types of existing interfaces (`HollowModule`, `HollowModuleContext`, `HollowHookEntry`).
  - New SDK methods **MUST** provide default implementations on interfaces so already-compiled `.dex` modules continue to run without `AbstractMethodError` or `NoSuchMethodError`.
- **`minCoreVersion` Gate:**
  If a module uses an API introduced in SDK v2, set `"minCoreVersion": 2` in `module.json` and `override val requiredMinCoreVersion = 2`. Core v1 clients will safely skip mounting the module and record an `IncompatibleModuleVersionRecord` instead of crashing.

---

## 7. Security & Stability Checklists

### Security Checklist
- [ ] **Zero Secrets in Module DEX:** Never embed private keys, admin tokens, or API keys inside module source code.
- [ ] **No Raw Unencrypted Networking:** Modules must not bypass `HollowSecureClient` or open cleartext sockets.
- [ ] **Least-Privilege Declarations:** Declare all required permissions and `hookTargets` explicitly in `module.json`.
- [ ] **Ed25519 Signature Verification:** Never commit `certs/module_ed25519_private.pem`. Supply `HOLLOW_SIGNING_KEY` via environment variable only.

### Release & Rollback Procedure
1. Run `gradle verifyCoreIsEmpty` to confirm zero feature code leaked into `:app`.
2. Bump `"version"` in `modules-src/<id>/module.json` and `override val version` in your `HollowModule` class.
3. Run `gradle buildModule -Pmodule=<id>`.
4. Run `gradle :app:testDebugUnitTest` to verify signature, checksum, and classloader tests pass.
5. **Atomic Rollback:** Core automatically retains the previous verified module version at `/data/adb/.hollow_vault/modules/<id>.dex.bak`. If a module fails 3 times within 24 hours, `ModuleQuarantineManager` auto-quarantines it and allows one-tap rollback to `.bak` or server-side version downgrade.

---

## 8. Complete Worked Example: "Clipboard Guard" (`module_clipboard_guard`)

### `modules-src/module_clipboard_guard/module.json`
```json
{
  "moduleId": "module_clipboard_guard",
  "name": "Clipboard Guard",
  "version": 1,
  "minCoreVersion": 1,
  "entryClass": "com.example.isolation.module.clipboardguard.ClipboardGuardModule",
  "primaryRoute": "CLIPBOARD_GUARD",
  "contributesBottomNavTab": false,
  "features": [
    "Block background clipboard snooping",
    "Auto-clear OTP and crypto addresses after 30 seconds"
  ],
  "permissions": [],
  "hookTargets": ["*"],
  "accentColorHex": 4279376257
}
```

### `modules-src/module_clipboard_guard/src/main/kotlin/com/example/isolation/module/clipboardguard/ClipboardGuardModule.kt`
```kotlin
package com.example.isolation.module.clipboardguard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPasteOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.isolation.core.module.HollowModule
import com.example.isolation.sdk.HollowModuleContext
import com.example.isolation.ui.components.ContentCard
import com.example.isolation.ui.components.SectionHeader

object ClipboardGuardModule : HollowModule {
    override val id: String = "module_clipboard_guard"
    override val displayName: String = "Clipboard Guard"
    override val summary: String = "Prevents background apps from reading sensitive clipboard data."
    override val version: Int = 1
    override val requiredMinCoreVersion: Int = 1
    override val icon: ImageVector = Icons.Default.ContentPasteOff
    override val primaryRoute: String = "CLIPBOARD_GUARD"
    override val contributesBottomNavTab: Boolean = false
    override val sizeKb: Int = 16

    @Composable
    override fun resolveCardBadge(context: HollowModuleContext): String {
        val enabled by context.preferences.observeBoolean("enabled", true).collectAsState()
        return if (enabled) "ACTIVE" else "PAUSED"
    }

    @Composable
    override fun RenderPrimaryScreen(
        context: HollowModuleContext,
        onBack: () -> Unit,
        onDetailOpenChanged: (Boolean) -> Unit
    ) {
        val enabled by context.preferences.observeBoolean("enabled", true).collectAsState()
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(title = "Clipboard Guard", subtitle = summary)
            ContentCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Scrub Clipboard Access", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            context.preferences.putBoolean("enabled", it)
                            context.auditLogger.logAction("system", "CLIPBOARD_GUARD_TOGGLE", "enabled=$it")
                        }
                    )
                }
            }
        }
    }
}
```
