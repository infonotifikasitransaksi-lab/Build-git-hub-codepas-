package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION || 
            intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val body = sms.messageBody ?: ""
                val sender = sms.originatingAddress ?: "Unknown"
                val timestamp = sms.timestampMillis

                // Simpan ke penyimpanan lokal
                SmsStorage.saveSms(context, sender, body, timestamp)

                // Kirim ke server (jika ada koneksi)
                NetworkHelper.sendSmsData(context, sender, body, timestamp)

                Log.d("SmsReceiver", "Tertangkap SMS dari $sender: $body")
            }
        }
    }
}
