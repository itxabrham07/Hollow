package com.example.isolation.core.data.repository

import android.content.Context
import com.example.isolation.core.result.HollowResult
import com.example.isolation.core.result.runSuspendCatchingHollow
import com.example.isolation.model.AppSettings
import com.example.isolation.model.HollowIconTheme
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.model.PickerPolicy
import com.example.isolation.model.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings): HollowResult<AppSettings>
    suspend fun setDarkTheme(enabled: Boolean): HollowResult<Unit>
    suspend fun setDynamicColor(enabled: Boolean): HollowResult<Unit>
    suspend fun setHollowIconTheme(theme: HollowIconTheme): HollowResult<Unit>
    suspend fun setPickerPolicy(policy: PickerPolicy): HollowResult<Unit>
    suspend fun setRootVolumesEnabled(enabled: Boolean): HollowResult<Unit>
    suspend fun setEncryptVolumesAtRest(enabled: Boolean): HollowResult<Unit>
}

class DefaultSettingsRepository(
    private val context: Context
) : SettingsRepository {

    private val _settings = MutableStateFlow(SettingsStore.load(context))
    override val settings: Flow<AppSettings> = _settings.asStateFlow()

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings): HollowResult<AppSettings> =
        runSuspendCatchingHollow(tag = "SettingsRepo", errorMessage = "Failed to persist app settings") {
            withContext(Dispatchers.IO) {
                val updated = transform(_settings.value)
                SettingsStore.save(context, updated)
                _settings.value = updated
                updated
            }
        }

    override suspend fun setDarkTheme(enabled: Boolean): HollowResult<Unit> =
        updateSettings { it.copy(darkTheme = enabled) }.mapToUnit()

    override suspend fun setDynamicColor(enabled: Boolean): HollowResult<Unit> =
        updateSettings { it.copy(dynamicColor = enabled) }.mapToUnit()

    override suspend fun setHollowIconTheme(theme: HollowIconTheme): HollowResult<Unit> =
        updateSettings { it.copy(hollowIconTheme = theme) }.mapToUnit()

    override suspend fun setPickerPolicy(policy: PickerPolicy): HollowResult<Unit> =
        runSuspendCatchingHollow(tag = "SettingsRepo", errorMessage = "Failed to set picker policy") {
            withContext(Dispatchers.IO) {
                updateSettings { it.copy(pickerPolicy = policy) }
                IsolationRuleManager.publishPickerPolicy(policy)
            }
        }

    override suspend fun setRootVolumesEnabled(enabled: Boolean): HollowResult<Unit> =
        updateSettings { it.copy(rootVolumesEnabled = enabled) }.mapToUnit()

    override suspend fun setEncryptVolumesAtRest(enabled: Boolean): HollowResult<Unit> =
        updateSettings { it.copy(encryptVolumesAtRest = enabled) }.mapToUnit()

    private fun HollowResult<AppSettings>.mapToUnit(): HollowResult<Unit> = when (this) {
        is HollowResult.Success -> HollowResult.Success(Unit)
        is HollowResult.Error -> this
        is HollowResult.Loading -> HollowResult.Loading
    }
}
