# Storage Shield & Virtual Vault Module (`module_isolation`)

Dynamic filesystem redirection, per-app encrypted volume sandboxing, zero-trace media cloaking, and granular folder whitelisting.

## Architecture
- **Contract**: Implements `HollowModule` and `HollowHookEntry` from `:hollow-sdk`.
- **Target Hooks**: `android`, `*` (File, FileInputStream, FileOutputStream, MediaProvider).
- **Storage**: Independent scoped storage (`HollowModuleContext.databaseFactory`).
