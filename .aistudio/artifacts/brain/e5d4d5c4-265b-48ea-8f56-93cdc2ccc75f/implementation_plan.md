# New Standalone Module Plan: App Compatibility & Sign-In Guard (`module_app_compatibility`)

## 1. Scope & Safety Boundary
Instead of modifying existing modules directly, we will create a **new standalone downloadable module** (`module_app_compatibility`) packaged with its own DEX UI via `ModuleHostBridge`.

> **Note on Platform Safety Policies:** I cannot include code designed to evade third-party anti-tampering, root detection, or memory/stack inspection. Instead, this new module resolves sign-in failures by **preventing our own hooks from breaking authentication flows** (such as fixing unfiltered `gservices` queries, isolating cross-app IPC calls, and disabling conflicting hooks on protected apps during sign-in).

---

## 2. New Module: `module_app_compatibility` (Sign-In & Compatibility Guard)

### Features Included in the New Module
1. **Per-App Sign-In Safe Profile**
   - Allows selecting specific installed apps (e.g., social or messaging apps that fail to log in when privacy modules are active) and applying a **Sign-In Compatibility Profile**.
2. **`gservices` & Auth Token Passthrough**
   - Ensures Google Play Services (`com.google.android.gsf.gservices`), Firebase Auth, and KeyStore queries pass through uncorrupted when `DeviceIdentitySpoofHook` is active, preventing the broken single-row `android_id` cursor bug from interrupting login handshakes.
3. **Selective Hook Isolation**
   - Prevents unnecessary hot-path hooks (`Socket.connect`, `ProxySelector`, `Resources.getDisplayMetrics`, `WebView` script injection, and cross-package `ContentResolver.call` lookups) from attaching to apps added to the Compatibility Guard list while keeping safe privacy controls (like Clipboard protection or Ad-ID anonymization) active.
4. **Compatibility Diagnostics View**
   - Displays which active module hooks are currently targeting a selected app and flags any settings that conflict with standard OAuth / Play Services sign-in flows.

---

## 3. Standalone DEX UI Packaging (`modules-src/module_app_compatibility`)

1. **`ModuleHostBridge` Contract (`com.example.isolation.module.ModuleHostBridge`)**
   - Introduce `ModuleHostBridge` so the new module's screen is rendered directly from its compiled DEX bundle without hardcoding a new screen file in the core APK's `ui/screens/` directory.
2. **Build & Sign Integration**
   - Add `modules-src/module_app_compatibility` to `compileFeatureModules` in `app/build.gradle.kts` so it compiles into `module.dex.bin`, is signed with Ed25519 in debug assets, and appears in the Dashboard & Admin Console alongside the existing 5 modules.
