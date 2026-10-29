package com.example.isolation.core.module

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.isolation.core.root.RootLicenseVaultManager
import com.example.isolation.core.security.HollowSecureClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Module Sync Worker (`ModuleSyncWorker`).
 *
 * Implements Phase 4 install and notification flow:
 * 1. Runs on app open and periodically every 15 minutes.
 * 2. Queries GET `/api/v1/modules/pending` on the Hollow cloud server using hardware-tethered identity.
 * 3. When an admin authorizes a new module for the device, posts a system notification:
 *    "New module available: <Name>". Tapping opens the install flow in Core.
 * 4. When an admin revokes an existing module, unmounts and deletes it from the Root Vault.
 * 5. Operates with zero Google Play Services dependency.
 */
object ModuleSyncWorker {

    private const val TAG = "ModuleSyncWorker"
    const val CHANNEL_ID = "hollow_module_sync"
    const val EXTRA_INSTALL_MODULE_ID = "extra_install_module_id"
    private const val SYNC_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes

    private var syncJob: Job? = null

    data class SyncResult(
        val pendingCount: Int,
        val newModules: List<HollowSecureClient.PendingModuleInfo>,
        val revokedModules: List<String>
    )

    fun startPeriodicSync(context: Context, scope: CoroutineScope, serverUrlProvider: () -> String) {
        syncJob?.cancel()
        createNotificationChannel(context)

        syncJob = scope.launch(Dispatchers.IO) {
            Log.i(TAG, "Starting periodic module sync worker (interval: 15 min)")
            while (isActive) {
                try {
                    val serverUrl = serverUrlProvider()
                    syncNow(context, serverUrl)
                } catch (t: Throwable) {
                    Log.w(TAG, "Periodic sync encountered error: ${t.message}")
                }
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    suspend fun syncNow(context: Context, serverUrl: String): SyncResult {
        val vault = RootLicenseVaultManager.readVaultRecord(context)
        val downloaded = vault.downloadedModules
        val pendingList = HollowSecureClient.fetchPendingModules(context, serverUrl)

        val newModules = mutableListOf<HollowSecureClient.PendingModuleInfo>()
        for (item in pendingList) {
            if (!downloaded.contains(item.moduleId)) {
                newModules.add(item)
            }
        }

        // Post notifications for newly available modules
        for (item in newModules) {
            postModuleAvailableNotification(context, item)
        }

        // Detect server-side revocations
        val allowedFromServer = pendingList.map { it.moduleId }.toSet()
        val revoked = mutableListOf<String>()
        if (pendingList.isNotEmpty()) {
            for (modId in downloaded) {
                if (!allowedFromServer.contains(modId) && !vault.allowedModules.contains(modId)) {
                    Log.w(TAG, "Module '$modId' was revoked by server admin. Purging from Root Vault.")
                    RootLicenseVaultManager.deleteDownloadedModuleFromVault(context, modId)
                    revoked.add(modId)
                }
            }
        }

        Log.i(TAG, "Module sync complete: ${newModules.size} new, ${revoked.size} revoked.")
        return SyncResult(
            pendingCount = pendingList.size,
            newModules = newModules,
            revokedModules = revoked
        )
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hollow Module Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for newly authorized security modules"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun postModuleAvailableNotification(context: Context, moduleInfo: HollowSecureClient.PendingModuleInfo) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_INSTALL_MODULE_ID, moduleInfo.moduleId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            moduleInfo.moduleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("New Module Available: ${moduleInfo.name}")
            .setContentText("Tap to install ${moduleInfo.name} into the Root Vault.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "${moduleInfo.name} (v${moduleInfo.version}) is now authorized for your device.\n${moduleInfo.changelog}"
            ))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(moduleInfo.moduleId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Missing POST_NOTIFICATIONS permission
        }
    }
}
