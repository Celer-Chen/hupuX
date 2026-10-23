package com.hupux.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.model.UserReply
import com.hupux.data.scraper.HupuDesktopScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserReplyListViewModel constructor(
    private val scraper: HupuDesktopScraper,
    savedState: SavedStateHandle
) : ViewModel() {

    private val uid: String = savedState.get<String>("uid") ?: ""

    data class State(
        val items: List<UserReply> = emptyList(),
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val hasMore: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var maxTime = 0L

    init { loadInternal() }

    fun load() {
        if (_state.value.isLoading || _state.value.isRefreshing) return
        loadInternal(isRefresh = false)
    }

    private fun loadInternal(isRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val hasItems = _state.value.items.isNotEmpty()
            _state.value = _state.value.copy(
                isLoading    = !isRefresh || !hasItems,
                isRefreshing = isRefresh,
                error        = null
            )
            try {
                val page = scraper.fetchReplyList(uid, maxTime)
                maxTime = page.maxTime
                _state.value = _state.value.copy(
                    items        = if (isRefresh) page.replies else _state.value.items + page.replies,
                    hasMore      = page.hasMore,
                    isLoading    = false,
                    isRefreshing = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false, isRefreshing = false, error = e.message)
            }
        }
    }

    fun refresh() {
        if (_state.value.isRefreshing) return
        maxTime = 0L
        loadInternal(isRefresh = true)
    }
}
