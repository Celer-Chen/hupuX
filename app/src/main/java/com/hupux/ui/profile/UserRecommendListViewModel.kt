package com.hupux.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.model.UserRecommendPost
import com.hupux.data.scraper.HupuDesktopScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserRecommendListViewModel constructor(
    private val scraper: HupuDesktopScraper,
    savedState: SavedStateHandle
) : ViewModel() {

    private val uid: String = savedState.get<String>("uid") ?: ""

    data class State(
        val items: List<UserRecommendPost> = emptyList(),
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val hasMore: Boolean = true,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var page = 1

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
                val items = scraper.fetchRecommendList(uid, page)
                page++
                _state.value = _state.value.copy(
                    items        = if (isRefresh) items else _state.value.items + items,
                    hasMore      = items.size >= 30,
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
        page = 1
        loadInternal(isRefresh = true)
    }
}
