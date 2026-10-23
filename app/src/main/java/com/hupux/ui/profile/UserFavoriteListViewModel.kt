package com.hupux.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.model.UserThread
import com.hupux.data.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserFavoriteListViewModel(
    private val profileRepo: ProfileRepository
) : ViewModel() {

    data class State(
        val items: List<UserThread> = emptyList(),
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val hasMore: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var nextMaxTime = 0L

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
                val page = profileRepo.fetchFavoriteList(nextMaxTime)
                nextMaxTime = page.nextMaxTime
                _state.value = _state.value.copy(
                    items        = if (isRefresh) page.threads else _state.value.items + page.threads,
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
        nextMaxTime = 0L
        loadInternal(isRefresh = true)
    }
}
