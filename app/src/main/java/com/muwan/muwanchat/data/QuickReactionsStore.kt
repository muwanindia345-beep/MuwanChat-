package com.muwan.muwanchat.data

import android.content.Context
import org.json.JSONArray

object QuickReactionsStore {

    private const val PREFS_NAME = "quick_reactions"
    private const val KEY_PREFIX = "reactions_"

    val DEFAULT = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(context: Context, uid: String): List<String> {
        val raw = prefs(context).getString(KEY_PREFIX + uid, null) ?: return DEFAULT
        return try {
            val arr = JSONArray(raw)
            val list = (0 until arr.length()).map { arr.getString(it) }
            if (list.size == 6) list else DEFAULT
        } catch (_: Exception) {
            DEFAULT
        }
    }

    fun save(context: Context, uid: String, reactions: List<String>) {
        val arr = JSONArray()
        reactions.forEach { arr.put(it) }
        prefs(context).edit().putString(KEY_PREFIX + uid, arr.toString()).apply()
    }
}
