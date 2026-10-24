package com.example.isolation.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.example.IsoVaultApp
import kotlinx.coroutines.runBlocking

/**
 * World-accessible ContentProvider enabling hooked target apps (e.g. Snapchat, WhatsApp)
 * to query their isolation rules in real time across Linux sandboxes.
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
        val cursor = MatrixCursor(arrayOf("packageName", "isIsolated", "mode", "whitelistedFolders", "blockPollution"))
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
                            arrayOf(
                                config.packageName,
                                if (config.isIsolated) 1 else 0,
                                config.isolationMode.name,
                                config.whitelistedFolders,
                                if (config.blockRootPollution) 1 else 0
                            )
                        )
                        return cursor
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Query error: ${e.message}")
            }
        }

        // Fallback: check shared prefs
        val prefs = context?.getSharedPreferences("isovault_rules", Context.MODE_PRIVATE)
        val isIsolated = prefs?.getBoolean("${pkg}_isolated", false) ?: false
        val mode = prefs?.getString("${pkg}_mode", "ENCRYPTED_VAULT") ?: "ENCRYPTED_VAULT"
        val whitelist = prefs?.getString("${pkg}_whitelist", "Download") ?: "Download"
        val pollution = prefs?.getBoolean("${pkg}_pollution", true) ?: true

        cursor.addRow(arrayOf(pkg, if (isIsolated) 1 else 0, mode, whitelist, if (pollution) 1 else 0))
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

            val isIsolated = config?.isIsolated ?: (context?.getSharedPreferences("isovault_rules", Context.MODE_PRIVATE)?.getBoolean("${pkg}_isolated", false) ?: false)
            val mode = config?.isolationMode?.name ?: "ENCRYPTED_VAULT"
            val whitelist = config?.whitelistedFolders ?: "Download"

            bundle.putBoolean("isIsolated", isIsolated)
            bundle.putString("mode", mode)
            bundle.putString("whitelistedFolders", whitelist)
        }
        return bundle
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.$AUTHORITY.rule"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
