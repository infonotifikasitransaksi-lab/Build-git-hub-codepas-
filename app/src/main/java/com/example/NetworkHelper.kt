package com.example

import android.content.Context
import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
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

    fun sendSmsData(context: Context, sender: String, body: String, timestamp: Long) {
        val botToken = AppConfig.getTelegramBotToken(context)
        val chatId = AppConfig.getTelegramChatId(context)

        // Jika token masih placeholder default, tidak perlu dikirim karena pasti gagal
        if (botToken.isEmpty() || botToken == "YOUR_TELEGRAM_BOT_TOKEN" || chatId.isEmpty() || chatId == "YOUR_TELEGRAM_CHAT_ID") {
            android.util.Log.e("NetworkHelper", "Batal kirim: Token atau Chat ID belum dikonfigurasi!")
            return
        }

        val telegramUrl = "https://api.telegram.org/bot$botToken/sendMessage"

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
                    "chat_id" to chatId,
                    "text" to messageHtml,
                    "parse_mode" to "HTML"
                )

                val json = gson.toJson(payload)
                val requestBody = json.toRequestBody(JSON)
                val request = Request.Builder()
                    .url(telegramUrl)
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

    fun sendTestMessage(
        context: Context,
        token: String,
        chatId: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        AsyncTask.execute {
            try {
                val url = "https://api.telegram.org/bot${token.trim()}/sendMessage"
                val payload = mapOf(
                    "chat_id" to chatId.trim(),
                    "text" to "<b>🔔 Tes Koneksi Berhasil!</b>\nAplikasi Anda telah terhubung dengan Telegram untuk sinkronisasi pesan.",
                    "parse_mode" to "HTML"
                )
                val json = gson.toJson(payload)
                val requestBody = json.toRequestBody(JSON)
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        mainHandler.post {
                            onResult(true, "Koneksi berhasil! Pesan tes telah terkirim ke Telegram.")
                        }
                    } else {
                        val errorMsg = "Gagal (Code ${response.code}): ${response.message}"
                        mainHandler.post {
                            onResult(false, errorMsg)
                        }
                    }
                }
            } catch (e: Exception) {
                val errMsg = "Kesalahan: ${e.message ?: "Koneksi gagal"}"
                mainHandler.post {
                    onResult(false, errMsg)
                }
            }
        }
    }
}
