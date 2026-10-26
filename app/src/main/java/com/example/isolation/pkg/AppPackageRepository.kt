package com.example.isolation.pkg

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.model.DisguiseIconPreset
import com.example.isolation.model.IsolationMode
import com.example.isolation.model.LauncherVisibilityMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val targetSdkVersion: Int,
    val isIsolated: Boolean = false,
    val isolationMode: IsolationMode = IsolationMode.ENCRYPTED_VAULT,
    val hasStoragePermission: Boolean = true,
    val icon: Drawable? = null,
    // Independent App Hider & Disguise fields (separate from Storage Protection)
    val isHiddenOrDisguised: Boolean = false,
    val launcherVisibilityMode: LauncherVisibilityMode = LauncherVisibilityMode.VISIBLE,
    val customLauncherLabel: String = "",
    val disguiseIconPreset: DisguiseIconPreset = DisguiseIconPreset.CALCULATOR,
    val hideInSystemUiRecents: Boolean = true
)

class AppPackageRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    suspend fun getInstalledApps(
        configs: List<AppIsolationConfig>,
        includeSystemApps: Boolean = false
    ): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val configMap = configs.associateBy { it.packageName }
        val resultMap = mutableMapOf<String, InstalledAppInfo>()

        // 1. Query launcher apps (user-visible installed apps like Snapchat, WhatsApp, etc.)
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)
            for (resolveInfo in resolveInfos) {
                val appInfo = resolveInfo.activityInfo?.applicationInfo ?: continue
                val pkg = appInfo.packageName
                if (pkg == context.packageName) continue

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (!includeSystemApps && isSystem) continue

                val appName = try {
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    pkg
                }

                val icon = try {
                    packageManager.getApplicationIcon(appInfo)
                } catch (_: Exception) {
                    ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
                }

                val config = configMap[pkg]
                resultMap[pkg] = InstalledAppInfo(
                    packageName = pkg,
                    appName = appName,
                    isSystemApp = isSystem,
                    targetSdkVersion = appInfo.targetSdkVersion,
                    isIsolated = config?.isIsolated ?: false,
                    isolationMode = config?.isolationMode ?: IsolationMode.ENCRYPTED_VAULT,
                    hasStoragePermission = true,
                    icon = icon,
                    isHiddenOrDisguised = config?.isHiddenOrDisguised ?: false,
                    launcherVisibilityMode = config?.launcherVisibilityMode ?: LauncherVisibilityMode.VISIBLE,
                    customLauncherLabel = config?.customLauncherLabel ?: "",
                    disguiseIconPreset = config?.disguiseIconPreset ?: DisguiseIconPreset.CALCULATOR,
                    hideInSystemUiRecents = config?.hideInSystemUiRecents ?: true
                )
            }
        } catch (_: Exception) {}

        // 2. Query all installed packages
        try {
            val installedPackages = packageManager.getInstalledPackages(0)
            for (pkgInfo in installedPackages) {
                val appInfo = pkgInfo.applicationInfo ?: continue
                val pkg = pkgInfo.packageName
                if (pkg == context.packageName || resultMap.containsKey(pkg)) continue

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (!includeSystemApps && isSystem) continue

                val appName = try {
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    pkg
                }

                val icon = try {
                    packageManager.getApplicationIcon(appInfo)
                } catch (_: Exception) {
                    ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
                }

                val config = configMap[pkg]
                resultMap[pkg] = InstalledAppInfo(
                    packageName = pkg,
                    appName = appName,
                    isSystemApp = isSystem,
                    targetSdkVersion = appInfo.targetSdkVersion,
                    isIsolated = config?.isIsolated ?: false,
                    isolationMode = config?.isolationMode ?: IsolationMode.ENCRYPTED_VAULT,
                    hasStoragePermission = true,
                    icon = icon,
                    isHiddenOrDisguised = config?.isHiddenOrDisguised ?: false,
                    launcherVisibilityMode = config?.launcherVisibilityMode ?: LauncherVisibilityMode.VISIBLE,
                    customLauncherLabel = config?.customLauncherLabel ?: "",
                    disguiseIconPreset = config?.disguiseIconPreset ?: DisguiseIconPreset.CALCULATOR,
                    hideInSystemUiRecents = config?.hideInSystemUiRecents ?: true
                )
            }
        } catch (_: Exception) {}

        // Always ensure any configured app is present in the list even if hidden from PM queries
        configs.forEach { cfg ->
            if (!resultMap.containsKey(cfg.packageName)) {
                resultMap[cfg.packageName] = InstalledAppInfo(
                    packageName = cfg.packageName,
                    appName = cfg.appName,
                    isSystemApp = false,
                    targetSdkVersion = 35,
                    isIsolated = cfg.isIsolated,
                    isolationMode = cfg.isolationMode,
                    hasStoragePermission = true,
                    icon = ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon),
                    isHiddenOrDisguised = cfg.isHiddenOrDisguised,
                    launcherVisibilityMode = cfg.launcherVisibilityMode,
                    customLauncherLabel = cfg.customLauncherLabel,
                    disguiseIconPreset = cfg.disguiseIconPreset,
                    hideInSystemUiRecents = cfg.hideInSystemUiRecents
                )
            }
        }

        resultMap.values.sortedWith(
            compareByDescending<InstalledAppInfo> { it.isIsolated || it.isHiddenOrDisguised }
                .thenBy { it.appName.lowercase() }
        )
    }
}
