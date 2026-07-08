package com.example

import android.content.Context

object AppConfig {
    private const val PREF_NAME = "AppConfigPrefs"
    private const val KEY_BOT_TOKEN = "telegram_bot_token"
    private const val KEY_CHAT_ID = "telegram_chat_id"

    fun saveConfig(context: Context, token: String, chatId: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_BOT_TOKEN, token.trim())
            .putString(KEY_CHAT_ID, chatId.trim())
            .apply()
    }

    fun getTelegramBotToken(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_BOT_TOKEN, "") ?: ""
        return if (token.isEmpty()) BuildConfig.TELEGRAM_BOT_TOKEN else token
    }

    fun getTelegramChatId(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val chatId = prefs.getString(KEY_CHAT_ID, "") ?: ""
        return if (chatId.isEmpty()) BuildConfig.TELEGRAM_CHAT_ID else chatId
    }
}
