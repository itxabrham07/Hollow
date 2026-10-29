# App Hider & SystemUI Stealth Module (`module_hider`)

System-wide launcher app hiding, Calculator/Notes/Calendar disguise presets with dynamic icon swapping, and SystemUI Recents task card suppression.

## Architecture
- **Contract**: Implements `HollowModule` and `HollowHookEntry` from `:hollow-sdk`.
- **Target Hooks**: `ApplicationPackageManager`, `LauncherApps`, `com.android.systemui`.
- **Storage**: Independent scoped storage (`HollowModuleContext.preferences`).
