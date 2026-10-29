package com.example.isolation.core.module

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.IsoVaultApp
import com.example.MainActivity
import com.example.isolation.core.root.RootLicenseVaultManager
import com.example.isolation.sdk.HollowModuleContext
import com.example.isolation.sdk.ModuleAuditEntry
import com.example.isolation.sdk.ModuleAuditLogger
import com.example.isolation.sdk.ModuleCursor
import com.example.isolation.sdk.ModuleDatabase
import com.example.isolation.sdk.ModuleDatabaseFactory
import com.example.isolation.sdk.ModuleEventBus
import com.example.isolation.sdk.ModuleLogger
import com.example.isolation.sdk.ModulePermissionHelper
import com.example.isolation.sdk.ModulePreferences
import com.example.isolation.sdk.ModuleRootShell
import com.example.isolation.sdk.ModuleSubscription
import com.example.isolation.sdk.ShellResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Concrete implementation of HollowModuleContext provided to each mounted module.
 * Completely isolates module data into module-scoped storage.
 */
class HollowModuleContextImpl(
    override val context: Context,
    override val moduleId: String,
    override val manifestFlags: Map<String, Any?> = emptyMap(),
    override val coroutineScope: CoroutineScope = IsoVaultApp.instanceOrNull()?.applicationScope
        ?: CoroutineScope(Dispatchers.Default)
) : HollowModuleContext {

    override val isHookActive: Boolean
        get() = MainActivity.isLsposedModuleActiveReal()

    override val preferences: ModulePreferences by lazy {
        SharedPrefsModulePreferences(
            context.getSharedPreferences("hollow_mod_$moduleId", Context.MODE_PRIVATE)
        )
    }

    override val databaseFactory: ModuleDatabaseFactory by lazy {
        SqliteModuleDatabaseFactory(context, moduleId)
    }

    override val eventBus: ModuleEventBus
        get() = GlobalModuleEventBus

    override val rootShell: ModuleRootShell by lazy {
        RootToolsModuleShell(context)
    }

    override val auditLogger: ModuleAuditLogger by lazy {
        FileAuditLogger(getModulePrivateDir(), moduleId)
    }

    override val permissionHelper: ModulePermissionHelper by lazy {
        AppModulePermissionHelper(context)
    }

    override val logger: ModuleLogger by lazy {
        AndroidModuleLogger(moduleId)
    }

    private class SharedPrefsModulePreferences(
        private val prefs: SharedPreferences
    ) : ModulePreferences {
        override fun getString(key: String, defValue: String): String =
            prefs.getString(key, defValue) ?: defValue

        override fun putString(key: String, value: String) {
            prefs.edit().putString(key, value).apply()
        }

        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            prefs.getBoolean(key, defValue)

        override fun putBoolean(key: String, value: Boolean) {
            prefs.edit().putBoolean(key, value).apply()
        }

        override fun getInt(key: String, defValue: Int): Int =
            prefs.getInt(key, defValue)

        override fun putInt(key: String, value: Int) {
            prefs.edit().putInt(key, value).apply()
        }

        override fun getLong(key: String, defValue: Long): Long =
            prefs.getLong(key, defValue)

        override fun putLong(key: String, value: Long) {
            prefs.edit().putLong(key, value).apply()
        }

        override fun getAll(): Map<String, *> = prefs.all

        override fun remove(key: String) {
            prefs.edit().remove(key).apply()
        }

        override fun clear() {
            prefs.edit().clear().apply()
        }
    }

    private class SqliteModuleDatabaseFactory(
        private val context: Context,
        private val moduleId: String
    ) : ModuleDatabaseFactory {
        override fun openOrCreateDatabase(dbName: String, version: Int): ModuleDatabase {
            val dbFile = File(context.filesDir, "modules_data/$moduleId/$dbName.db").apply {
                parentFile?.mkdirs()
            }
            val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            db.version = version
            return SqliteModuleDatabase(db)
        }
    }

    private class SqliteModuleDatabase(
        private val db: SQLiteDatabase
    ) : ModuleDatabase {
        override fun execSQL(sql: String, bindArgs: Array<Any?>?) {
            if (bindArgs == null) {
                db.execSQL(sql)
            } else {
                db.execSQL(sql, bindArgs)
            }
        }

        override fun query(sql: String, selectionArgs: Array<String>?): ModuleCursor {
            val cursor = db.rawQuery(sql, selectionArgs)
            return SqliteModuleCursor(cursor)
        }

        override fun close() {
            try { db.close() } catch (_: Throwable) {}
        }
    }

    private class SqliteModuleCursor(private val cursor: Cursor) : ModuleCursor {
        override val count: Int get() = cursor.count
        override fun moveToFirst(): Boolean = cursor.moveToFirst()
        override fun moveToNext(): Boolean = cursor.moveToNext()
        override fun isAfterLast(): Boolean = cursor.isAfterLast
        override fun getColumnIndex(columnName: String): Int = cursor.getColumnIndex(columnName)
        override fun getString(columnIndex: Int): String? =
            if (columnIndex >= 0 && !cursor.isNull(columnIndex)) cursor.getString(columnIndex) else null
        override fun getInt(columnIndex: Int): Int =
            if (columnIndex >= 0) cursor.getInt(columnIndex) else 0
        override fun getLong(columnIndex: Int): Long =
            if (columnIndex >= 0) cursor.getLong(columnIndex) else 0L
        override fun getBlob(columnIndex: Int): ByteArray? =
            if (columnIndex >= 0 && !cursor.isNull(columnIndex)) cursor.getBlob(columnIndex) else null
        override fun close() {
            try { cursor.close() } catch (_: Throwable) {}
        }
    }

    object GlobalModuleEventBus : ModuleEventBus {
        private val subscribers = ConcurrentHashMap<String, CopyOnWriteArrayList<(Any?) -> Unit>>()

        override fun publish(topic: String, payload: Any?) {
            subscribers[topic]?.forEach { observer ->
                try {
                    observer(payload)
                } catch (t: Throwable) {
                    Log.e("ModuleEventBus", "Error in observer for topic '$topic': ${t.message}")
                }
            }
        }

        override fun subscribe(topic: String, observer: (Any?) -> Unit): ModuleSubscription {
            val list = subscribers.getOrPut(topic) { CopyOnWriteArrayList() }
            list.add(observer)
            return ModuleSubscription {
                list.remove(observer)
            }
        }
    }

    private class RootToolsModuleShell(private val context: Context) : ModuleRootShell {
        override fun isRootAvailable(): Boolean {
            return RootLicenseVaultManager.isRootAccessGranted(context)
        }

        override fun execute(command: String): ShellResult {
            return try {
                val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                val stdout = proc.inputStream.bufferedReader().readText()
                val stderr = proc.errorStream.bufferedReader().readText()
                val exitCode = proc.waitFor()
                ShellResult(exitCode, stdout, stderr)
            } catch (t: Throwable) {
                ShellResult(-1, "", t.message ?: "Failed to execute root command")
            }
        }

        override fun executeAsync(command: String, onResult: (ShellResult) -> Unit) {
            Thread {
                val res = execute(command)
                onResult(res)
            }.start()
        }
    }

    private class FileAuditLogger(
        private val moduleDir: File,
        private val moduleId: String
    ) : ModuleAuditLogger {
        private val logs = CopyOnWriteArrayList<ModuleAuditEntry>()
        private val logFile = File(moduleDir, "audit.log")

        init {
            loadInitialLogs()
        }

        private fun loadInitialLogs() {
            try {
                if (logFile.exists()) {
                    logFile.readLines().takeLast(100).forEach { line ->
                        val parts = line.split("\t")
                        if (parts.size >= 5) {
                            val time = parts[0].toLongOrNull() ?: System.currentTimeMillis()
                            val act = parts[1]
                            val tgt = parts[2]
                            val det = parts[3]
                            val sev = parts[4].toBoolean()
                            logs.add(ModuleAuditEntry(time, act, tgt, det, sev))
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        override fun log(action: String, target: String, details: String, isSevere: Boolean) {
            val entry = ModuleAuditEntry(
                timestampMs = System.currentTimeMillis(),
                action = action,
                target = target,
                details = details,
                isSevere = isSevere
            )
            logs.add(0, entry)
            while (logs.size > 200) {
                logs.removeAt(logs.size - 1)
            }
            try {
                logFile.appendText("${entry.timestampMs}\t$action\t$target\t$details\t$isSevere\n")
            } catch (_: Throwable) {}
        }

        override fun getRecentLogs(limit: Int): List<ModuleAuditEntry> {
            return logs.take(limit)
        }
    }

    private class AppModulePermissionHelper(private val context: Context) : ModulePermissionHelper {
        override fun hasPermission(permission: String): Boolean {
            return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

        override fun checkRootAccess(): Boolean {
            return RootLicenseVaultManager.isRootAccessGranted(context)
        }
    }

    private class AndroidModuleLogger(private val moduleId: String) : ModuleLogger {
        override fun d(tag: String, message: String) {
            Log.d("Hollow:$moduleId:$tag", message)
        }

        override fun i(tag: String, message: String) {
            Log.i("Hollow:$moduleId:$tag", message)
        }

        override fun w(tag: String, message: String, throwable: Throwable?) {
            Log.w("Hollow:$moduleId:$tag", message, throwable)
        }

        override fun e(tag: String, message: String, throwable: Throwable?) {
            Log.e("Hollow:$moduleId:$tag", message, throwable)
        }
    }
}
