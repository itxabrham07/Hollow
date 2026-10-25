package com.example.isolation.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.example.IsoVaultApp
import com.example.isolation.data.entity.AccessAuditLog
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.model.AuditAction
import com.example.isolation.model.IsolationMode
import com.example.isolation.model.IsolationRuleManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * World-accessible ContentProvider enabling hooked target apps (e.g. Snapchat, WhatsApp)
 * to query their isolation rules and report real-time interceptions back to IsoVault.
 */
class IsoVaultConfigProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.aistudio.isovault.xrvf.provider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/rules")
        private const val TAG = "IsoVaultProvider"

        fun notifyRuleChanged(context: Context, packageName: String) {
            try {
                val uri = Uri.withAppendedPath(CONTENT_URI, packageName)
                context.contentResolver.notifyChange(uri, null)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to notify provider change: ${e.message}")
            }
        }
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(arrayOf("packageName", "isIsolated", "mode", "whitelistedFolders", "blockPollution", "explicitlyDisabled"))
        val pkg = uri.lastPathSegment ?: selectionArgs?.firstOrNull() ?: ""

        if (pkg.isNotBlank()) {
            try {
                val app = context?.applicationContext as? IsoVaultApp
                if (app != null) {
                    val config = runBlocking {
                        app.database.isolationDao().getConfigForPackage(pkg)
                    }
                    if (config != null) {
                        cursor.addRow(
                            arrayOf<Any>(
                                config.packageName,
                                if (config.isIsolated) 1 else 0,
                                config.isolationMode.name,
                                config.whitelistedFolders,
                                if (config.blockRootPollution) 1 else 0,
                                if (!config.isIsolated) 1 else 0
                            )
                        )
                        return cursor
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Query error: ${e.message}")
            }
        }

        val ctx = context
        val prefs = if (ctx != null) IsolationRuleManager.getReadableSharedPrefs(ctx) else null
        val isConfigured = prefs?.getBoolean("${pkg}_configured", false) ?: false
        val isIsolated = if (isConfigured) (prefs?.getBoolean("${pkg}_isolated", true) ?: true) else true
        val explicitlyDisabled = prefs?.getBoolean("${pkg}_explicitly_disabled", false) ?: false
        val mode = prefs?.getString("${pkg}_mode", "ENCRYPTED_VAULT") ?: "ENCRYPTED_VAULT"
        val whitelist = prefs?.getString("${pkg}_whitelist", "Download") ?: "Download"
        val pollution = prefs?.getBoolean("${pkg}_pollution", true) ?: true

        cursor.addRow(
            arrayOf<Any>(
                pkg,
                if (isIsolated) 1 else 0,
                mode,
                whitelist,
                if (pollution) 1 else 0,
                if (explicitlyDisabled) 1 else 0
            )
        )
        return cursor
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val bundle = Bundle()
        val pkg = arg ?: ""

        if (method == "check_isolated" && pkg.isNotBlank()) {
            val app = context?.applicationContext as? IsoVaultApp
            val config = if (app != null) {
                runBlocking { app.database.isolationDao().getConfigForPackage(pkg) }
            } else null

            val ctx = context
            val prefs = if (ctx != null) IsolationRuleManager.getReadableSharedPrefs(ctx) else null
            val isConfigured = config != null || (prefs?.getBoolean("${pkg}_configured", false) ?: false)
            val explicitlyDisabled = if (config != null) !config.isIsolated else (prefs?.getBoolean("${pkg}_explicitly_disabled", false) ?: false)
            val isIsolated = config?.isIsolated ?: if (isConfigured) (prefs?.getBoolean("${pkg}_isolated", true) ?: true) else true
            val mode = config?.isolationMode?.name ?: (prefs?.getString("${pkg}_mode", "ENCRYPTED_VAULT") ?: "ENCRYPTED_VAULT")
            val whitelist = config?.whitelistedFolders ?: (prefs?.getString("${pkg}_whitelist", "Download") ?: "Download")
            val blockPollution = config?.blockRootPollution ?: (prefs?.getBoolean("${pkg}_pollution", true) ?: true)

            bundle.putBoolean("isConfigured", isConfigured)
            bundle.putBoolean("isIsolated", isIsolated)
            bundle.putBoolean("explicitlyDisabled", explicitlyDisabled)
            bundle.putString("mode", mode)
            bundle.putString("whitelistedFolders", whitelist)
            bundle.putBoolean("blockPollution", blockPollution)
            return bundle
        }

        if (method == "record_audit" && pkg.isNotBlank() && extras != null) {
            val app = context?.applicationContext as? IsoVaultApp
            if (app != null) {
                val requestedPath = extras.getString("requestedPath") ?: ""
                val resolvedPath = extras.getString("resolvedPath") ?: ""
                val actionStr = extras.getString("action") ?: "REDIRECTED"
                val operation = extras.getString("operation") ?: "REDIRECT"
                val details = extras.getString("details") ?: ""
                val action = try { AuditAction.valueOf(actionStr) } catch (_: Exception) { AuditAction.REDIRECTED }

                app.applicationScope.launch {
                    try {
                        val dao = app.database.isolationDao()
                        var cfg = dao.getConfigForPackage(pkg)
                        if (cfg == null) {
                            cfg = AppIsolationConfig(
                                packageName = pkg,
                                appName = pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() },
                                isIsolated = true,
                                isolationMode = IsolationMode.ENCRYPTED_VAULT,
                                blockRootPollution = true,
                                whitelistedFolders = "Download",
                                totalFilesRedirected = 1,
                                lastAccessedTimestamp = System.currentTimeMillis()
                            )
                            dao.insertOrUpdateConfig(cfg)
                        } else {
                            dao.incrementRedirectCount(pkg, System.currentTimeMillis())
                        }

                        dao.insertAuditLog(
                            AccessAuditLog(
                                packageName = pkg,
                                appName = cfg.appName,
                                requestedPath = requestedPath,
                                targetResolvedPath = resolvedPath,
                                action = action,
                                operation = operation,
                                details = details
                            )
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to record audit log: ${e.message}")
                    }
                }
                bundle.putBoolean("recorded", true)
            }
            return bundle
        }

        return bundle
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.$AUTHORITY.rule"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
