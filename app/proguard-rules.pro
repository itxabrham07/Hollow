# Hollow - ProGuard / R8 Rules for Release Minification

# Xposed entry class and hook classes (CRITICAL: prevents stripping during R8 minification)
-keep class com.example.isolation.hook.StorageIsolationHook { *; }
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage { *; }
-keep class * implements de.robv.android.xposed.IXposedHookZygoteInit { *; }
-keep class * implements de.robv.android.xposed.IXposedHookInitPackageResources { *; }
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# Keep ContentProvider and Service entry points
-keep class com.example.isolation.provider.IsoVaultConfigProvider { *; }
-keep class com.example.IsoVaultApp { *; }
-keep class com.example.MainActivity { *; }

# Keep Room database and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Keep data models, crypto engine, and entities
-keep class com.example.isolation.data.entity.** { *; }
-keep class com.example.isolation.model.** { *; }
-keep class com.example.isolation.crypto.** { *; }
-keep class com.example.isolation.mount.** { *; }

# Keep ViewModels and Compose state classes
-keep class com.example.isolation.ui.viewmodel.** { *; }

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
