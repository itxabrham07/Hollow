package com.example.isolation.pkg

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.model.DeviceProfilePreset
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
    val hideInSystemUiRecents: Boolean = true,
    val hideFromPlayStore: Boolean = false,
    val spoofInSettings: Boolean = true,
    val customIconBase64: String = "",
    val customIconDonorPkg: String = "",
    // Independent Per-App Device Identity & Privacy Spoofing fields
    val isIdentitySpoofed: Boolean = false,
    val deviceProfilePreset: DeviceProfilePreset = DeviceProfilePreset.PIXEL_9_PRO,
    val spoofBrand: String = "",
    val spoofModel: String = "",
    val spoofDevice: String = "",
    val spoofAndroidId: String = "",
    val spoofImei: String = "",
    val spoofAdvertisingId: String = "",
    val spoofMacAddress: String = "",
    val spoofCarrierName: String = "",
    val spoofSimOperator: String = "",
    val spoofSerial: String = "",
    val hideRootAndXposed: Boolean = true,
    val hideAdbAndDevOptions: Boolean = true,
    val hideVpnAndProxy: Boolean = true,
    val blockClipboardRead: Boolean = false
)

class AppPackageRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    private fun buildInstalledAppInfo(
        pkg: String,
        appName: String,
        isSystem: Boolean,
        targetSdk: Int,
        icon: Drawable?,
        config: AppIsolationConfig?
    ): InstalledAppInfo {
        return InstalledAppInfo(
            packageName = pkg,
            appName = appName,
            isSystemApp = isSystem,
            targetSdkVersion = targetSdk,
            isIsolated = config?.isIsolated ?: false,
            isolationMode = config?.isolationMode ?: IsolationMode.ENCRYPTED_VAULT,
            hasStoragePermission = true,
            icon = icon,
            isHiddenOrDisguised = config?.isHiddenOrDisguised ?: false,
            launcherVisibilityMode = config?.launcherVisibilityMode ?: LauncherVisibilityMode.VISIBLE,
            customLauncherLabel = config?.customLauncherLabel ?: "",
            disguiseIconPreset = config?.disguiseIconPreset ?: DisguiseIconPreset.CALCULATOR,
            hideInSystemUiRecents = config?.hideInSystemUiRecents ?: true,
            hideFromPlayStore = config?.hideFromPlayStore ?: false,
            spoofInSettings = config?.spoofInSettings ?: true,
            customIconBase64 = config?.customIconBase64 ?: "",
            customIconDonorPkg = config?.customIconDonorPkg ?: "",
            isIdentitySpoofed = config?.isIdentitySpoofed ?: false,
            deviceProfilePreset = config?.deviceProfilePreset ?: DeviceProfilePreset.PIXEL_9_PRO,
            spoofBrand = config?.spoofBrand ?: "",
            spoofModel = config?.spoofModel ?: "",
            spoofDevice = config?.spoofDevice ?: "",
            spoofAndroidId = config?.spoofAndroidId ?: "",
            spoofImei = config?.spoofImei ?: "",
            spoofAdvertisingId = config?.spoofAdvertisingId ?: "",
            spoofMacAddress = config?.spoofMacAddress ?: "",
            spoofCarrierName = config?.spoofCarrierName ?: "",
            spoofSimOperator = config?.spoofSimOperator ?: "",
            spoofSerial = config?.spoofSerial ?: "",
            hideRootAndXposed = config?.hideRootAndXposed ?: true,
            hideAdbAndDevOptions = config?.hideAdbAndDevOptions ?: true,
            hideVpnAndProxy = config?.hideVpnAndProxy ?: true,
            blockClipboardRead = config?.blockClipboardRead ?: false
        )
    }

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

                resultMap[pkg] = buildInstalledAppInfo(
                    pkg = pkg,
                    appName = appName,
                    isSystem = isSystem,
                    targetSdk = appInfo.targetSdkVersion,
                    icon = icon,
                    config = configMap[pkg]
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

                resultMap[pkg] = buildInstalledAppInfo(
                    pkg = pkg,
                    appName = appName,
                    isSystem = isSystem,
                    targetSdk = appInfo.targetSdkVersion,
                    icon = icon,
                    config = configMap[pkg]
                )
            }
        } catch (_: Exception) {}

        // Always ensure any configured app is present in the list even if hidden from PM queries
        configs.forEach { cfg ->
            if (!resultMap.containsKey(cfg.packageName)) {
                resultMap[cfg.packageName] = buildInstalledAppInfo(
                    pkg = cfg.packageName,
                    appName = cfg.appName,
                    isSystem = false,
                    targetSdk = 35,
                    icon = ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon),
                    config = cfg
                )
            }
        }

        resultMap.values.sortedWith(
            compareByDescending<InstalledAppInfo> { it.isIsolated || it.isHiddenOrDisguised || it.isIdentitySpoofed }
                .thenBy { it.appName.lowercase() }
        )
    }
}
