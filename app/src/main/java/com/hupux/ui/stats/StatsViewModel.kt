package com.hupux.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.model.MatchBoxScore
import com.hupux.data.model.Standings
import com.hupux.data.repository.StatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ─── 战绩排行 ────────────────────────────────────────────────────────────────

data class StandingsUiState(
    val league: String = "NBA",
    val standings: Standings? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

class StandingsViewModel(private val repo: StatsRepository) : ViewModel() {

    private val _state = MutableStateFlow(StandingsUiState())
    val state: StateFlow<StandingsUiState> = _state.asStateFlow()

    init { load("NBA") }

    fun selectLeague(league: String) {
        if (_state.value.league == league && _state.value.standings != null) return
        load(league)
    }

    fun refresh() {
        if (_state.value.isRefreshing) return
        load(_state.value.league, isRefresh = true)
    }

    private fun load(league: String, isRefresh: Boolean = false) {
        val hasData = _state.value.standings != null && _state.value.league == league
        _state.value = _state.value.copy(
            league       = league,
            isLoading    = !isRefresh && !hasData,
            isRefreshing = isRefresh,
            error        = null
        )
        viewModelScope.launch {
            runCatching { repo.standings(league) }
                .onSuccess { data ->
                    _state.value = _state.value.copy(
                        standings = data, isLoading = false, isRefreshing = false, error = null
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoading = false, isRefreshing = false,
                        error = if (_state.value.standings == null) e.message ?: "加载失败" else null
                    )
                }
        }
    }
}

// ─── 比赛技术统计 ────────────────────────────────────────────────────────────

data class BoxScoreUiState(
    val boxScore: MatchBoxScore? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

class BoxScoreViewModel(private val repo: StatsRepository) : ViewModel() {

    private val _state = MutableStateFlow(BoxScoreUiState())
    val state: StateFlow<BoxScoreUiState> = _state.asStateFlow()

    private var loadedId = ""

    fun load(boxScoreId: String, league: String) {
        if (loadedId == boxScoreId && _state.value.boxScore != null) return
        loadedId = boxScoreId
        _state.value = BoxScoreUiState(isLoading = true)
        viewModelScope.launch {
            runCatching { repo.boxScore(boxScoreId, league) }
                .onSuccess { data ->
                    _state.value = BoxScoreUiState(boxScore = data, isLoading = false)
                }
                .onFailure { e ->
                    _state.value = BoxScoreUiState(isLoading = false, error = e.message ?: "加载失败")
                }
        }
    }

    fun refresh(boxScoreId: String, league: String) {
        if (_state.value.isRefreshing) return
        _state.value = _state.value.copy(isRefreshing = true, error = null)
        viewModelScope.launch {
            runCatching { repo.boxScore(boxScoreId, league) }
                .onSuccess { data ->
                    _state.value = BoxScoreUiState(boxScore = data, isLoading = false)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isRefreshing = false,
                        error = if (_state.value.boxScore == null) e.message ?: "加载失败" else null
                    )
                }
        }
    }
}
