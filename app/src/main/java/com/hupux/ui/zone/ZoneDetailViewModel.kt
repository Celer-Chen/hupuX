package com.hupux.ui.zone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.local.CookiePreferences
import com.hupux.data.model.Post
import com.hupux.data.model.Zone
import com.hupux.data.model.ZoneDetail
import com.hupux.data.repository.FollowedZonesRepository
import com.hupux.data.repository.ZoneRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val JINGHUA_MIN_RECOMMEND = 20   // 精华 = 推荐数 ≥ 20

data class ZoneDetailUiState(
    val zoneDetail:    ZoneDetail? = null,
    val allPosts:      List<Post>  = emptyList(),   // 全量，供两个 tab 共用
    val selectedTab:   Int         = 0,              // 0=全部  1=精华
    val isLoading:     Boolean     = false,
    val isRefreshing:  Boolean     = false,
    val isLoadingMore: Boolean     = false,
    val error:         String?     = null,
    val nextCursor:    String?     = null,
    val isLoggedIn:    Boolean     = false,
    val isFollowed:    Boolean     = false
) {
    // 精华 = recommendNum ≥ 阈值
    val posts: List<Post> get() = if (selectedTab == 0) allPosts
                                  else allPosts.filter { it.recommendNum >= JINGHUA_MIN_RECOMMEND }
}

class ZoneDetailViewModel constructor(
    private val repo: ZoneRepository,
    private val followedRepo: FollowedZonesRepository,
    private val cookiePrefs: CookiePreferences
) : ViewModel() {

    private val _state = MutableStateFlow(ZoneDetailUiState(isLoading = true, isLoggedIn = cookiePrefs.isLoggedIn))
    val state = _state.asStateFlow()

    private var topicId: Int = 0

    fun init(id: Int) {
        if (topicId == id && _state.value.allPosts.isNotEmpty()) return
        topicId = id
        viewModelScope.launch {
            // 关注状态实时跟随本地库（发现页徽标切换后回来也要是最新状态）
            runCatching { followedRepo.isFollowed(id) }
                .onSuccess { followed -> _state.value = _state.value.copy(isFollowed = followed) }
        }
        load()
    }

    /** 关注/取关当前专区（本地保存，与发现页徽标同一份数据） */
    fun toggleFollow() {
        val z = _state.value.zoneDetail ?: return
        val zone = Zone(
            topicId    = z.topicId,
            topicName  = z.name,
            topicLogo  = z.logo,
            count      = z.allThreadNum
        )
        viewModelScope.launch {
            followedRepo.toggle(zone)
            _state.value = _state.value.copy(isFollowed = followedRepo.isFollowed(z.topicId))
        }
    }

    fun load() = loadInternal(isRefresh = false)

    fun refresh() = loadInternal(isRefresh = true)

    private fun loadInternal(isRefresh: Boolean) {
        viewModelScope.launch {
            val snapshot = _state.value
            _state.value = if (isRefresh && snapshot.allPosts.isNotEmpty()) {
                snapshot.copy(isRefreshing = true, error = null)
            } else {
                snapshot.copy(
                    isLoading = true, allPosts = emptyList(),
                    nextCursor = null, error = null
                )
            }
            runCatching { repo.getZonePosts(topicId) }
                .onSuccess { page ->
                    _state.value = _state.value.copy(
                        zoneDetail   = page.zoneDetail,
                        allPosts     = page.posts,
                        nextCursor   = page.nextCursor,
                        isLoading    = false,
                        isRefreshing = false
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoading    = false,
                        isRefreshing = false,
                        error        = e.message ?: "加载失败"
                    )
                }
        }
    }

    fun selectTab(index: Int) {
        if (_state.value.selectedTab == index) return
        _state.value = _state.value.copy(selectedTab = index)
        // 如果切到精华且当前精华列表为空，自动加载更多
        if (index == 1 && _state.value.posts.isEmpty() && _state.value.nextCursor != null) {
            loadMore()
        }
    }

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.isLoadingMore) return
        // 同步置位，防止协程启动前多次调用都通过检查
        _state.value = _state.value.copy(isLoadingMore = true)
        viewModelScope.launch {
            runCatching { repo.getZonePosts(topicId, cursor) }
                .onSuccess { page ->
                    val existing = _state.value.allPosts.map { it.tid }.toHashSet()
                    val newPosts = page.posts.filter { it.tid !in existing }
                    _state.value = _state.value.copy(
                        allPosts      = _state.value.allPosts + newPosts,
                        nextCursor    = page.nextCursor,
                        isLoadingMore = false
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(isLoadingMore = false)
                }
        }
    }
}
