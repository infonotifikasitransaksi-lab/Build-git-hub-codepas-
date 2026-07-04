package com.example

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object SmsStorage {

    private const val FILE_NAME = "sms_log.txt"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun saveSms(context: Context, sender: String, body: String, timestamp: Long) {
        val file = File(context.filesDir, FILE_NAME)
        val date = dateFormat.format(Date(timestamp))
        val line = "[$date] Dari: $sender | Isi: $body\n"
        try {
            file.appendText(line)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Fungsi untuk membaca seluruh log (bisa digunakan untuk debugging)
    fun readAllSms(context: Context): String {
        val file = File(context.filesDir, FILE_NAME)
        return if (file.exists()) file.readText() else ""
    }

    fun clearLogs(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        try {
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
