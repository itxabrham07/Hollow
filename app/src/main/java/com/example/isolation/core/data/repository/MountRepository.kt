package com.example.isolation.core.data.repository

import android.content.Context
import com.example.isolation.core.result.HollowResult
import com.example.isolation.core.result.runSuspendCatchingHollow
import com.example.isolation.mount.MountEntry
import com.example.isolation.mount.MountExecutionResult
import com.example.isolation.mount.MountManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface MountRepository {
    suspend fun readSystemMounts(): HollowResult<List<MountEntry>>
    suspend fun getSelinuxStatus(): HollowResult<String>
    suspend fun attachVolume(packageName: String): HollowResult<MountExecutionResult>
    suspend fun detachVolume(packageName: String): HollowResult<MountExecutionResult>
    suspend fun isRootAvailable(): HollowResult<Boolean>
    suspend fun generateBootScript(): String
}

class DefaultMountRepository(
    private val context: Context
) : MountRepository {

    override suspend fun readSystemMounts(): HollowResult<List<MountEntry>> =
        runSuspendCatchingHollow(tag = "MountRepo", errorMessage = "Failed to read system mounts") {
            withContext(Dispatchers.IO) {
                MountManager.readSystemMounts()
            }
        }

    override suspend fun getSelinuxStatus(): HollowResult<String> =
        runSuspendCatchingHollow(tag = "MountRepo", errorMessage = "Failed to determine SELinux status") {
            withContext(Dispatchers.IO) {
                MountManager.getSelinuxStatus()
            }
        }

    override suspend fun attachVolume(packageName: String): HollowResult<MountExecutionResult> =
        runSuspendCatchingHollow(tag = "MountRepo", errorMessage = "Failed to attach volume for $packageName") {
            withContext(Dispatchers.IO) {
                MountManager.attachVolume(context, packageName)
            }
        }

    override suspend fun detachVolume(packageName: String): HollowResult<MountExecutionResult> =
        runSuspendCatchingHollow(tag = "MountRepo", errorMessage = "Failed to detach volume for $packageName") {
            withContext(Dispatchers.IO) {
                MountManager.detachVolume(context, packageName)
            }
        }

    override suspend fun isRootAvailable(): HollowResult<Boolean> =
        runSuspendCatchingHollow(tag = "MountRepo", errorMessage = "Root check failed") {
            withContext(Dispatchers.IO) {
                MountManager.isRootAvailable()
            }
        }

    override suspend fun generateBootScript(): String = withContext(Dispatchers.IO) {
        MountManager.generateBootService(context, emptyList())
    }
}
