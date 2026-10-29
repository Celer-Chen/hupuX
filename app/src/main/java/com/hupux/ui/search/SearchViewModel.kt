package com.hupux.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.model.SearchPost
import com.hupux.data.repository.SearchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 结果排序。服务端 search2 不接受排序参数（实测多种写法均被忽略），
 * 所以「最新 / 最热」对已加载条目本地重排。
 */
enum class SearchSort(val label: String) {
    DEFAULT("全部"), NEWEST("最新"), HOTTEST("最热")
}

data class SearchUiState(
    val query: String = "",
    /** 已提交搜索的关键词；空串表示停在搜索首页（热搜 + 历史） */
    val submittedQuery: String = "",
    val hotWords: List<String> = emptyList(),
    val history: List<String> = emptyList(),
    val results: List<SearchPost> = emptyList(),
    val sort: SearchSort = SearchSort.DEFAULT,
    val page: Int = 0,
    val totalPage: Int = 0,
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
) {
    val displayedResults: List<SearchPost> get() = when (sort) {
        SearchSort.DEFAULT -> results
        SearchSort.NEWEST  -> results.sortedByDescending { it.lastPostTime }
        SearchSort.HOTTEST -> results.sortedByDescending { it.replies }
    }
}

class SearchViewModel constructor(
    private val repo: SearchRepository,
    private val historyStore: SearchHistory
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        _state.value = _state.value.copy(history = historyStore.items)
        viewModelScope.launch {
            runCatching { repo.hotSearchWords() }
                .onSuccess { words -> _state.value = _state.value.copy(hotWords = words) }
        }
    }

    fun updateQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    /** 回车或点热搜词：从第 1 页重新搜索 */
    fun search(raw: String = _state.value.query) {
        val query = raw.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        historyStore.add(query)
        _state.value = _state.value.copy(
            query         = query,
            submittedQuery = query,
            results       = emptyList(),
            page          = 0,
            totalPage     = 0,
            totalCount    = 0,
            hasMore       = false,
            sort          = SearchSort.DEFAULT,
            error         = null,
            isLoading     = true,
            isRefreshing  = false,
            isLoadingMore = false,
            history       = historyStore.items
        )
        loadPage(1)
    }

    /** 下拉刷新：重拉第 1 页，保留已有结果 */
    fun refresh() {
        if (_state.value.submittedQuery.isEmpty() || _state.value.isRefreshing) return
        _state.value = _state.value.copy(isRefreshing = true, error = null)
        loadPage(1)
    }

    fun loadMore() {
        val s = _state.value
        if (s.submittedQuery.isEmpty() || !s.hasMore || s.isLoading || s.isLoadingMore || s.isRefreshing) return
        _state.value = s.copy(isLoadingMore = true)
        loadPage(s.page + 1)
    }

    fun selectSort(sort: SearchSort) {
        _state.value = _state.value.copy(sort = sort)
    }

    /** 清空输入框并回到搜索首页 */
    fun clearQuery() {
        searchJob?.cancel()
        _state.value = _state.value.copy(
            query          = "",
            submittedQuery = "",
            results        = emptyList(),
            page           = 0,
            totalPage      = 0,
            totalCount     = 0,
            hasMore        = false,
            sort           = SearchSort.DEFAULT,
            error          = null,
            isLoading      = false,
            isRefreshing   = false,
            isLoadingMore  = false
        )
    }

    fun clearHistory() {
        historyStore.clear()
        _state.value = _state.value.copy(history = emptyList())
    }

    fun removeHistory(query: String) {
        historyStore.remove(query)
        _state.value = _state.value.copy(history = historyStore.items)
    }

    private fun loadPage(page: Int) {
        val query = _state.value.submittedQuery
        searchJob = viewModelScope.launch {
            runCatching { repo.search(query, page) }
                .onSuccess { data ->
                    val cur = _state.value
                    _state.value = cur.copy(
                        results       = if (page == 1) data.posts else merge(cur.results, data.posts),
                        page          = data.currentPage,
                        totalPage     = data.totalPage,
                        totalCount    = data.totalCount,
                        hasMore       = data.hasMore,
                        isLoading     = false,
                        isLoadingMore = false,
                        isRefreshing  = false,
                        error         = null
                    )
                }
                .onFailure { e ->
                    val cur = _state.value
                    _state.value = cur.copy(
                        isLoading     = false,
                        isLoadingMore = false,
                        isRefreshing  = false,
                        // 已有结果时刷新/翻页失败不弹错误覆盖列表
                        error         = if (cur.results.isEmpty()) e.message ?: "搜索失败" else null
                    )
                }
        }
    }

    private fun merge(old: List<SearchPost>, new: List<SearchPost>): List<SearchPost> {
        val seen = old.map { it.tid }.toHashSet()
        return old + new.filter { it.tid !in seen }
    }
}
