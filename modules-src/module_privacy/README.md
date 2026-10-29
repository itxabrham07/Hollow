# Hardware Spoofer & Privacy Profiles Module (`module_privacy`)

Hardware identity cloaking, Android ID & serial virtualization, OpenGL GPU vendor/renderer spoofing, and per-app telemetry masking.

## Architecture
- **Contract**: Implements `HollowModule` and `HollowHookEntry` from `:hollow-sdk`.
- **Target Hooks**: `android`, `com.google.android.gms`, `*`.
- **Storage**: Independent scoped storage (`HollowModuleContext.preferences`).
