package com.example.isolation.pkg

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.model.IsolationMode
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
    val icon: Drawable? = null
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
                val isIsolated = config?.isIsolated ?: false
                val mode = config?.isolationMode ?: IsolationMode.ENCRYPTED_VAULT

                resultMap[pkg] = InstalledAppInfo(
                    packageName = pkg,
                    appName = appName,
                    isSystemApp = isSystem,
                    targetSdkVersion = appInfo.targetSdkVersion,
                    isIsolated = isIsolated,
                    isolationMode = mode,
                    hasStoragePermission = true,
                    icon = icon
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
                val isIsolated = config?.isIsolated ?: false
                val mode = config?.isolationMode ?: IsolationMode.ENCRYPTED_VAULT

                resultMap[pkg] = InstalledAppInfo(
                    packageName = pkg,
                    appName = appName,
                    isSystemApp = isSystem,
                    targetSdkVersion = appInfo.targetSdkVersion,
                    isIsolated = isIsolated,
                    isolationMode = mode,
                    hasStoragePermission = true,
                    icon = icon
                )
            }
        } catch (_: Exception) {}

        // Always ensure Snapchat is present in the list if configured in database
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
                    icon = ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
                )
            }
        }

        resultMap.values.sortedWith(
            compareByDescending<InstalledAppInfo> { it.isIsolated }
                .thenBy { it.appName.lowercase() }
        )
    }
}
