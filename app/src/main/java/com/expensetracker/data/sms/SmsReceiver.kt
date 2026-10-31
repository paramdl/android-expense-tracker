package com.expensetracker.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.expensetracker.ExpenseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Listens for incoming SMS and hands bank alerts to [SmsIngest]. */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val app = context.applicationContext as? ExpenseApp ?: return
        if (!app.settings.smsEnabled) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // Multipart messages arrive as several PDUs that must be stitched together.
        val sender = messages[0].displayOriginatingAddress ?: return
        val body = messages.joinToString("") { it.displayMessageBody ?: "" }
        val ts = messages[0].timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SmsIngest.ingest(app.repo, app.settings, sender, body, ts)
            } catch (_: Exception) {
                // A malformed alert must never crash the receiver.
            } finally {
                pending.finish()
            }
        }
    }
}
