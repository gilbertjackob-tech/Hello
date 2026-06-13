package com.glassbox.hello.chat

import android.content.Context

object ChatInboxPrefs {
    private const val PREFS_NAME = "hello_chat_inbox"
    private const val KEY_PINNED_CHAT_IDS = "pinned_chat_ids"
    private const val KEY_MUTED_CHAT_IDS = "muted_chat_ids"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun pinnedChatIds(context: Context): Set<String> = readIds(context, KEY_PINNED_CHAT_IDS)

    fun mutedChatIds(context: Context): Set<String> = readIds(context, KEY_MUTED_CHAT_IDS)

    fun isPinned(context: Context, chatId: String): Boolean = chatId in pinnedChatIds(context)

    fun isMuted(context: Context, chatId: String): Boolean = chatId in mutedChatIds(context)

    fun setPinned(context: Context, chatId: String, pinned: Boolean) {
        updateIds(context, KEY_PINNED_CHAT_IDS) { ids ->
            if (pinned) ids.add(chatId) else ids.remove(chatId)
        }
    }

    fun setMuted(context: Context, chatId: String, muted: Boolean) {
        updateIds(context, KEY_MUTED_CHAT_IDS) { ids ->
            if (muted) ids.add(chatId) else ids.remove(chatId)
        }
    }

    private fun readIds(context: Context, key: String): Set<String> {
        return prefs(context).getStringSet(key, emptySet()).orEmpty().mapNotNull { it.trim().takeIf(String::isNotBlank) }.toSet()
    }

    private fun updateIds(context: Context, key: String, mutator: (MutableSet<String>) -> Unit) {
        val next = readIds(context, key).toMutableSet()
        mutator(next)
        prefs(context).edit().putStringSet(key, next).apply()
    }
}
