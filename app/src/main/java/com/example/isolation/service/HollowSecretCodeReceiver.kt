package com.example.isolation.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.MainActivity

/**
 * Allows opening Hollow when hidden from all launchers by dialing
 * `*#*#465569#*#*` ("HOLLOW" on the phone keypad).
 */
class HollowSecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == "android.provider.Telephony.SECRET_CODE" ||
            action == "android.telephony.action.SECRET_CODE"
        ) {
            try {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(launchIntent)
            } catch (_: Throwable) {}
        }
    }
}
