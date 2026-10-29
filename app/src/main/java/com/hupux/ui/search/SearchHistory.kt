package com.hupux.ui.search

import android.content.Context

/**
 * 搜索历史。存 "hupu_prefs" 的独立 key，按「最新在前」去重保存，最多 [MAX] 条。
 */
class SearchHistory(ctx: Context) {

    private val prefs = ctx.getSharedPreferences("hupu_prefs", Context.MODE_PRIVATE)

    var items: List<String>
        get() = prefs.getString(KEY, "")
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        set(value) {
            prefs.edit().putString(KEY, value.joinToString("\n")).apply()
        }

    fun add(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        items = (listOf(q) + items.filter { it != q }).take(MAX)
    }

    fun remove(query: String) {
        items = items.filter { it != query }
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        private const val KEY = "search_history"
        private const val MAX = 20
    }
}
