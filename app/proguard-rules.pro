# ProGuard / R8 rules for Vault-LSPosed

# Keep Xposed / LSPosed entry point class and all its methods
-keep class com.example.isolation.hook.StorageIsolationHook {
    public *;
    private *;
}
-keep class com.example.isolation.hook.** { *; }

# Keep MainActivity self-hook status method called by StorageIsolationHook
-keepclassmembers class com.example.MainActivity {
    public static boolean isLsposedModuleActiveReal();
}
-keepclassmembers class com.example.MainActivity$Companion {
    public boolean isLsposedModuleActiveReal();
}

# Keep Xposed API interfaces and callbacks
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# Keep Room entities & database migrations
-keep class com.example.isolation.data.entity.** { *; }
-keep class com.example.isolation.data.IsoVaultDatabase* { *; }
