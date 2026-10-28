package com.example

import android.app.Application
import com.example.isolation.data.IsoVaultDatabase
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.model.SettingsStore
import com.example.isolation.pkg.AppPackageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class IsoVaultApp : Application() {

    val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> }
    )

    val database by lazy { IsoVaultDatabase.getDatabase(this) }
    val packageRepository by lazy { AppPackageRepository(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                android.util.Log.e("IsoVaultCrash", "Uncaught exception on ${thread.name}", throwable)
                val crashFile = java.io.File(filesDir, "last_crash.txt")
                crashFile.writeText("${java.util.Date()}\n${throwable.stackTraceToString()}")
            } catch (_: Throwable) {
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            com.example.isolation.service.RootDeadmanWatchdog.init(this)
        } catch (_: Throwable) {
        }

        // Re-publish every stored rule so hooked apps see the current state after a reboot or update.
        applicationScope.launch {
            try {
                republishRules()
            } catch (_: Throwable) {
            }
        }
    }

    private suspend fun republishRules() {
        try {
            IsolationRuleManager.publishPickerPolicy(SettingsStore.load(this).pickerPolicy)
            for (cfg in database.isolationDao().getAllConfigs().first()) {
                val whitelist = cfg.whitelistedFolders.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
                if (cfg.isIsolated) {
                    IsolationRuleManager.updateRule(
                        packageName = cfg.packageName,
                        mode = cfg.isolationMode,
                        blockRootPollution = cfg.blockRootPollution,
                        whitelistedFolders = whitelist
                    )
                } else {
                    IsolationRuleManager.disableRule(
                        packageName = cfg.packageName,
                        mode = cfg.isolationMode,
                        blockRootPollution = cfg.blockRootPollution,
                        whitelistedFolders = whitelist
                    )
                }
            }
        } catch (_: Throwable) {
        }
    }

    companion object {
        lateinit var instance: IsoVaultApp
            private set

        fun instanceOrNull(): IsoVaultApp? = if (::instance.isInitialized) instance else null
    }
}
