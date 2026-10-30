package com.example.isolation.core.data.repository

import android.content.Context
import com.example.isolation.core.result.HollowResult
import com.example.isolation.core.result.runSuspendCatchingHollow
import com.example.isolation.model.PrivacyProfile
import com.example.isolation.model.PrivacyProfileStore
import com.example.isolation.model.RealDeviceHardware
import com.example.isolation.model.RealHardwareSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

interface DeviceSpoofRepository {
    val privacyProfiles: Flow<List<PrivacyProfile>>
    val realHardware: RealHardwareSnapshot
    suspend fun savePrivacyProfile(profile: PrivacyProfile): HollowResult<Unit>
    suspend fun deletePrivacyProfile(profileId: String): HollowResult<Unit>
    suspend fun resetPrivacyProfilesToDefaults(): HollowResult<List<PrivacyProfile>>
    fun getProfileById(id: String): PrivacyProfile
}

class DefaultDeviceSpoofRepository(
    private val context: Context
) : DeviceSpoofRepository {

    private val _privacyProfiles = MutableStateFlow(PrivacyProfileStore.loadProfiles(context))
    override val privacyProfiles: Flow<List<PrivacyProfile>> = _privacyProfiles.asStateFlow()

    override val realHardware: RealHardwareSnapshot by lazy {
        RealDeviceHardware.read(context)
    }

    override suspend fun savePrivacyProfile(profile: PrivacyProfile): HollowResult<Unit> =
        runSuspendCatchingHollow(tag = "DeviceSpoofRepo", errorMessage = "Failed to save privacy profile: ${profile.name}") {
            withContext(Dispatchers.IO) {
                val current = PrivacyProfileStore.loadProfiles(context).toMutableList()
                val idx = current.indexOfFirst { it.id == profile.id }
                if (idx >= 0) {
                    current[idx] = profile
                } else {
                    current.add(profile)
                }
                PrivacyProfileStore.saveProfiles(context, current)
                _privacyProfiles.value = current
            }
        }

    override suspend fun deletePrivacyProfile(profileId: String): HollowResult<Unit> =
        runSuspendCatchingHollow(tag = "DeviceSpoofRepo", errorMessage = "Failed to delete privacy profile $profileId") {
            withContext(Dispatchers.IO) {
                val current = PrivacyProfileStore.loadProfiles(context).filterNot { it.id == profileId }
                PrivacyProfileStore.saveProfiles(context, current)
                _privacyProfiles.value = current
            }
        }

    override suspend fun resetPrivacyProfilesToDefaults(): HollowResult<List<PrivacyProfile>> =
        runSuspendCatchingHollow(tag = "DeviceSpoofRepo", errorMessage = "Failed to reset privacy profiles") {
            withContext(Dispatchers.IO) {
                val defaults = PrivacyProfileStore.resetToDefaults(context)
                _privacyProfiles.value = defaults
                defaults
            }
        }

    override fun getProfileById(id: String): PrivacyProfile =
        PrivacyProfileStore.getProfileById(context, id)
}
