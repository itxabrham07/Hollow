package com.example.isolation.sdk

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import java.io.File

/**
 * Current frozen SDK version. Modules specify requiredMinCoreVersion matching this semantics.
 */
const val HOLLOW_SDK_VERSION: Int = 1

/**
 * Standard runtime context provided to dynamic Hollow feature modules.
 * Replaces direct IsoVaultViewModel and Room dependencies.
 */
interface HollowModuleContext {
    val context: Context
    val moduleId: String
    val preferences: ModulePreferences
    val databaseFactory: ModuleDatabaseFactory
    val eventBus: ModuleEventBus
    val rootShell: ModuleRootShell
    val auditLogger: ModuleAuditLogger
    val permissionHelper: ModulePermissionHelper
    val coroutineScope: CoroutineScope
    val logger: ModuleLogger
    val manifestFlags: Map<String, Any?>
    val isHookActive: Boolean

    fun notifyStateChanged() {}
    fun getModulePrivateDir(): File {
        val dir = File(context.filesDir, "modules_data/$moduleId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}

interface ModulePreferences {
    fun getString(key: String, defValue: String = ""): String
    fun putString(key: String, value: String)
    fun getBoolean(key: String, defValue: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, defValue: Int = 0): Int
    fun putInt(key: String, value: Int)
    fun getLong(key: String, defValue: Long = 0L): Long
    fun putLong(key: String, value: Long)
    fun getAll(): Map<String, *>
    fun remove(key: String)
    fun clear()
}

interface ModuleDatabaseFactory {
    fun openOrCreateDatabase(dbName: String, version: Int = 1): ModuleDatabase
}

interface ModuleDatabase {
    fun execSQL(sql: String, bindArgs: Array<Any?>? = null)
    fun query(sql: String, selectionArgs: Array<String>? = null): ModuleCursor
    fun close()
}

interface ModuleCursor {
    val count: Int
    fun moveToFirst(): Boolean
    fun moveToNext(): Boolean
    fun isAfterLast(): Boolean
    fun getColumnIndex(columnName: String): Int
    fun getString(columnIndex: Int): String?
    fun getInt(columnIndex: Int): Int
    fun getLong(columnIndex: Int): Long
    fun getBlob(columnIndex: Int): ByteArray?
    fun close()
}

interface ModuleEventBus {
    fun publish(topic: String, payload: Any? = null)
    fun subscribe(topic: String, observer: (Any?) -> Unit): ModuleSubscription
}

fun interface ModuleSubscription {
    fun unsubscribe()
}

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

interface ModuleRootShell {
    fun isRootAvailable(): Boolean
    fun execute(command: String): ShellResult
    fun executeAsync(command: String, onResult: (ShellResult) -> Unit)
}

data class ModuleAuditEntry(
    val timestampMs: Long = System.currentTimeMillis(),
    val action: String,
    val target: String,
    val details: String,
    val isSevere: Boolean = false
)

interface ModuleAuditLogger {
    fun log(action: String, target: String, details: String, isSevere: Boolean = false)
    fun getRecentLogs(limit: Int = 50): List<ModuleAuditEntry>
}

interface ModulePermissionHelper {
    fun hasPermission(permission: String): Boolean
    fun checkRootAccess(): Boolean
}

interface ModuleLogger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
