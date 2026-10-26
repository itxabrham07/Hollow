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

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { IsoVaultDatabase.getDatabase(this) }
    val packageRepository by lazy { AppPackageRepository(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Re-publish every stored rule so hooked apps see the current state after a reboot or update.
        applicationScope.launch { republishRules() }
    }

    private suspend fun republishRules() {
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
    }

    companion object {
        lateinit var instance: IsoVaultApp
            private set

        fun instanceOrNull(): IsoVaultApp? = if (::instance.isInitialized) instance else null
    }
}
