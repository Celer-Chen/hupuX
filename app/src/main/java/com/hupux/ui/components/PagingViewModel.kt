package com.hupux.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 通用分页列表状态。
 *
 * [T] 是单条数据类型；[items] 已加载条目，[page] 是已加载到的页码（从 1 开始），
 * [hasMore] 为 false 表示到底了。
 */
data class PagingUiState<T>(
    val items: List<T> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val isLoading: Boolean = true,       // 首屏加载（无内容时显示骨架/转圈）
    val isRefreshing: Boolean = false,   // 下拉刷新（保留旧内容）
    val isLoadingMore: Boolean = false,  // 触底加载下一页
    val error: String? = null
)

/**
 * 通用分页 ViewModel 基类：统一「首屏加载 / 下拉刷新 / 触底加载更多 / 去重合并」。
 *
 * 子类只需实现 [fetchPage]（拉一页数据），返回 [PageResult]。
 * 典型用法是 UserThreadList / UserReplyList / UserRecommendList / UserFavoriteList
 * 这类「按页拉列表」的页面——此前四个页面各自复制了一遍这套逻辑。
 */
abstract class PagingViewModel<T> : ViewModel() {

    private val _state = MutableStateFlow(PagingUiState<T>())
    val state: StateFlow<PagingUiState<T>> = _state.asStateFlow()

    private var loadJob: Job? = null

    /** 拉一页数据。[page] 从 1 开始；返回条目 + 是否还有更多。 */
    protected abstract suspend fun fetchPage(page: Int): PageResult<T>

    /** 合并去重的 key，默认用条目本身；条目类型不同时子类覆盖。 */
    protected open fun keyOf(item: T): Any = item as Any

    fun load() = loadInternal(refresh = false)

    fun refresh() = loadInternal(refresh = true)

    fun loadMore() {
        val s = _state.value
        if (!s.hasMore || s.isLoading || s.isRefreshing || s.isLoadingMore) return
        loadInternal(refresh = false, page = s.page + 1)
    }

    /** 只刷新第 1 页（发布成功后重置列表等场景用） */
    fun reset() {
        loadJob?.cancel()
        _state.value = PagingUiState()
        loadInternal(refresh = true, page = 1)
    }

    private fun loadInternal(refresh: Boolean, page: Int = 1) {
        if (loadJob?.isActive == true) return
        val hasItems = _state.value.items.isNotEmpty()
        _state.value = _state.value.copy(
            isLoading    = !refresh && !hasItems && page == 1,
            isRefreshing = refresh,
            isLoadingMore = page > 1,
            error        = null
        )
        loadJob = viewModelScope.launch {
            runCatching { fetchPage(page) }
                .onSuccess { result ->
                    _state.update { cur ->
                        val merged = if (page == 1) result.items
                                     else (cur.items + result.items).distinctBy { keyOf(it) }
                        cur.copy(
                            items        = merged,
                            page         = page,
                            hasMore      = result.hasMore,
                            isLoading    = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            error        = null
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { cur ->
                        cur.copy(
                            isLoading     = false,
                            isRefreshing  = false,
                            isLoadingMore = false,
                            // 已有内容时刷新/翻页失败不弹错误覆盖列表
                            error         = if (cur.items.isEmpty()) e.message ?: "加载失败" else null
                        )
                    }
                }
        }
    }

    private inline fun MutableStateFlow<PagingUiState<T>>.update(block: (PagingUiState<T>) -> PagingUiState<T>) {
        value = block(value)
    }
}

/** [PagingViewModel.fetchPage] 的返回值 */
data class PageResult<T>(
    val items: List<T>,
    val hasMore: Boolean
)
