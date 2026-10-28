# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-keepattributes SourceFile,LineNumberTable,Signature

# Preserve all Core classes, ViewModels, UI components, and data models
# referenced at runtime by standalone dynamically loaded .dex modules via DexClassLoader parent delegation.
-keep class com.example.isolation.core.module.** { *; }
-keep class com.example.isolation.core.root.** { *; }
-keep class com.example.isolation.core.license.** { *; }
-keep class com.example.isolation.core.security.** { *; }
-keep class com.example.isolation.ui.viewmodel.** { *; }
-keep class com.example.isolation.ui.screens.** { *; }
-keep class com.example.isolation.ui.theme.** { *; }
-keep class com.example.isolation.model.** { *; }
-keep class com.example.isolation.data.entity.** { *; }
-keep class com.example.isolation.pkg.** { *; }
-keep class com.example.isolation.mount.** { *; }
-keep class com.example.isolation.service.** { *; }
