# Hollow — System-Level App Hider, Disguise & SystemUI Injection Plan

A system-wide **App Hider, Disguise & SystemUI Injection** architecture for Hollow. Instead of hooking individual launcher apps (which could be bypassed if a user installs a new launcher), Hollow hooks **Android System Framework (`android` / `PackageManagerService`), global `ApplicationPackageManager` / `LauncherApps`, and `SystemUI` (`com.android.systemui`)** directly—so **every launcher (including newly installed ones), Recents, Share sheets, and SystemUI** automatically hide or disguise selected apps, and Hollow itself can be opened directly from SystemUI even when completely hidden.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Revised Architecture Based on Your Feedback:**
> 1. **System Framework & Global PackageManager Hooking (Works in Any New Launcher):** Rather than targeting specific launcher packages, hooks are injected at the **Android System Server (`android`) / global `ApplicationPackageManager` & `LauncherApps` level**. Any existing or newly installed launcher querying `LauncherApps` or `PackageManager` automatically receives the filtered app list or the spoofed custom name and decoy icon.
> 2. **Direct SystemUI Injection (`com.android.systemui`):**
>    - **Secret SystemUI Entry Point for Hollow:** When Hollow itself is completely hidden from all launchers, users can launch Hollow directly via an injected **SystemUI trigger** (e.g., long-pressing the Quick Settings header/clock or a disguised Quick Settings tile) without needing any launcher icon at all.
>    - **SystemUI Recents & Notification Masking:** Hooking `com.android.systemui` also disguises or hides the app's title/icon in the **Recents (Overview) task switcher** and status bar/notifications.
> 3. **Two Independent Protection Lists:** **Protected Apps (Storage Isolation)** and **Hide & Disguise Apps** remain completely separate so users can select different apps for storage sandboxing vs. system-wide hiding/disguise.

---

## 1. Overview & Core Concept

- **Why System Framework + SystemUI Instead of Per-Launcher Hooks:**
  - If you only hook specific launchers, installing a new third-party launcher (Nova, Lawnchair, Niagara, etc.) or opening the system Share sheet / Settings / Recents would expose the real app name, icon, or presence.
  - By intercepting `PackageManagerService` (`android`), `LauncherApps`, `ApplicationPackageManager`, and `PackageItemInfo` globally across processes—plus injecting into `com.android.systemui`—**every app drawer, any newly installed launcher, Recents screen, and SystemUI surface** sees only the spoofed name/icon or hides the app completely.
  - Furthermore, injecting a stealth trigger directly into **SystemUI** allows Hollow to be 100% invisible in every launcher while remaining accessible anytime from the Quick Settings / notification shade.

---

## 2. User Experience & Visual Design

- **Aesthetic & Theme Polarity:** Intentional Dark Mode (`#0B0F17` obsidian canvas, `#131924` elevated cards, `#8AB4F8` primary accent, and `#A8DAB5` / `#FDD663` status badges).
- **Navigation & Screen Flow:**
  1. **Home Dashboard Entry:** Below the full-width **Protected Apps** card, a dedicated **Hide & Disguise Apps** card displays the live count of hidden and disguised apps plus SystemUI injection status.
  2. **Hide & Disguise Apps Screen:**
     - **Hollow Stealth & SystemUI Injection Card (Top):**
       - Lets the user **Hide Hollow Completely** from all launchers or **Disguise Hollow** with a custom name and decoy icon.
       - Configures the **SystemUI Secret Trigger** (e.g., *Long-press Quick Settings Date/Clock in SystemUI* or *Quick Settings Stealth Tile*) to open Hollow directly from SystemUI anytime.
     - **Active Hidden & Disguised Apps List (Default View):**
       - Displays only the apps currently hidden or disguised, with a top-right **+ Add Apps** button to select from all installed apps.
       - Each app card includes a one-tap **Launch** button (to open hidden apps directly from Hollow), a **Configure Disguise** button, and a status pill (**Hidden System-Wide** or **Disguised as "<Custom Name>"**).
  3. **Disguise Configuration Modal:**
     - **Mode Selector:** **Visible (Normal)**, **Disguise Name & Icon System-Wide**, or **Hide Completely**.
     - **Custom App Name Field:** Free-text input for any user-typed name (with quick presets like *Calculator*, *Notes*, *System Update*, *Calendar*).
     - **Decoy Icon Picker:** Clean vector utility icons (Calculator, Notes, Clock, Weather, Calendar, Settings Gear) that replace the real icon across all launchers and SystemUI.
     - **Mask in SystemUI Recents Toggle:** Optional toggle to also apply the disguise/blur in SystemUI Recents & Task Switcher.

---

## 3. Key Product Decisions & Trade-Offs

- **Global Framework + SystemUI Hooking vs. Launcher-Only Hooking:**
  - *Global `PackageManager` / `LauncherApps` Hooks:* Runs in `android` (System Server) and client processes (`ApplicationPackageManager` / `PackageItemInfo`), guaranteeing that **any new launcher** installed in the future automatically displays the spoofed name/icon or omits hidden apps—while exempting Hollow's own package (`com.example` / `com.aistudio.isovault.mpxqzr`) so Hollow itself can still see and launch every app.
  - *SystemUI Injection (`com.android.systemui`):* Hooks SystemUI views (Quick Settings clock/date header long-press listener) to launch Hollow via an explicit component intent, and hooks SystemUI task/notification labels so disguised apps stay camouflaged in Recents.
- **Root `pm` Deep-Hide Option:** Alongside LSPosed framework filtering, users with root can optionally toggle OS-level component state while still launching apps from inside Hollow.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         HOLLOW ANDROID APP (UI)                         │
│                                                                         │
│  ┌──────────────────────────┐         ┌──────────────────────────────┐  │
│  │   Protected Apps Screen  │         │  Hide & Disguise Apps Screen │  │
│  │ (Storage Isolation List) │         │ (System-Wide Hide & Spoof +  │  │
│  └────────────┬─────────────┘         │  SystemUI Trigger Settings)  │  │
│               │                       └──────────────┬───────────────┘  │
│               └──────────────────┬───────────────────┘                  │
│                                  ▼                                      │
│                  ┌───────────────────────────────┐                      │
│                  │   Room Database & Rule Sync   │                      │
│                  │  (XSharedPreferences + IPC)   │                      │
│                  └───────────────┬───────────────┘                      │
└──────────────────────────────────┼──────────────────────────────────────┘
                                   │
          ┌────────────────────────┴────────────────────────┐
          ▼                                                 ▼
┌───────────────────────────────────────┐ ┌───────────────────────────────┐
│   GLOBAL SYSTEM & PM HOOKS (LSPosed)  │ │   SYSTEMUI INJECTION HOOKS    │
│   (`android` + All Client Processes)  │ │     (`com.android.systemui`)  │
│                                       │ │                               │
│ • Hooks `LauncherApps.getActivityList`│ │ • Injects Long-Press Trigger  │
│   & `PackageManager.queryIntent...`   │ │   on Quick Settings Clock /   │
│   globally -> any new launcher hides  │ │   Header to open Hollow even  │
│   apps automatically                  │ │   when completely hidden      │
│ • Hooks `PackageItemInfo.loadLabel`   │ │ • Spoofs App Label & Icon in  │
│   & `getApplicationLabel` globally -> │ │   SystemUI Recents / Overview │
│   serves custom typed name everywhere │ │   and Status Bar surfaces     │
│ • Hooks `PackageItemInfo.loadIcon` -> │ │ • Native Quick Settings Tile  │
│   serves decoy vector icon everywhere │ │   for instant disguised entry │
└───────────────────────────────────────┘ └───────────────────────────────┘
```

### Data Model & Cross-Process Delivery
- **Per-App Hide & Disguise Fields (Independent of `isIsolated`):**
  - `launcherVisibilityMode: LauncherVisibilityMode` (`VISIBLE`, `DISGUISED`, `HIDDEN`)
  - `customLauncherLabel: String` (user-typed custom app name)
  - `disguiseIconPreset: DisguiseIconPreset` (`DEFAULT`, `CALCULATOR`, `NOTES`, `CALENDAR`, `CLOCK`, `WEATHER`, `SYSTEM_UTILITY`)
  - `hideInSystemUiRecents: Boolean` (masks/spoofs the app in SystemUI Recents)
- **Hollow Stealth & SystemUI Settings (`AppSettings`):**
  - `hollowVisibilityMode: LauncherVisibilityMode` (Visible, Disguised, or Completely Hidden)
  - `hollowCustomName: String` and `hollowIconPreset: DisguiseIconPreset`
  - `systemUiTriggerEnabled: Boolean` (enables opening Hollow by long-pressing the SystemUI Quick Settings header/clock)

### Interactive Component Mapping

| UI Element / Control | Backing Action & System Behavior |
| :--- | :--- |
| **Dashboard "Hide & Disguise Apps" Card** | Opens `AppHiderScreen`; shows active hidden/disguised app counts and SystemUI trigger badge. |
| **SystemUI Stealth Trigger Toggle** | Injects a long-press listener into `com.android.systemui` Quick Settings header/clock (plus optional Quick Settings Tile) that launches Hollow directly even when its launcher component is disabled/hidden. |
| **Hollow Self-Visibility Mode (`Visible` / `Disguise` / `Hide`)** | Toggles Hollow's launcher `<activity-alias>` and applies global label/icon spoofing so Hollow is hidden or disguised across all launchers. |
| **Per-App Mode (`Visible` / `Disguise` / `Hide`)** | Updates global `PackageManager` / `LauncherApps` filter rules so **every existing or newly installed launcher** hides or disguises the app immediately. |
| **Custom Name Input & Decoy Icon Picker** | Overrides `PackageItemInfo.loadLabel()`, `ApplicationPackageManager.getApplicationLabel()`, and `loadIcon()` across all external callers and `com.android.systemui`. |
| **"Launch App" Button Inside Hollow** | Resolves the target package's entry activity and starts it directly from inside Hollow (Hollow's own process is exempted from hiding rules). |
