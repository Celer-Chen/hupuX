package com.hupux.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.model.HomeMatch
import com.hupux.data.model.HotItem
import com.hupux.data.model.Post
import com.hupux.data.repository.FollowedZonesRepository
import com.hupux.data.repository.HomeRepository
import com.hupux.data.repository.ZoneRepository
import com.hupux.data.scraper.HupuMatchScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 「今日比分」的刷新间隔。
 *
 * 这条数据是从 www.hupu.com 首页 HTML 里抠出来的（约 580KB，没有独立接口），
 * 抓一次不便宜，所以回到前台时按这个间隔节流，而不是每次都真的去抓。
 */
private const val MATCH_REFRESH_INTERVAL_MS = 2 * 60 * 1000L

/**
 * 关注流抓取的并发上限。关注 N 个专区就并发 N 个请求会把虎扑打毛，
 * 也容易在低端机上抢带宽；这里按批次限流（实际吞吐约为 3 路并发）。
 */
private const val FOLLOW_FETCH_CONCURRENCY = 3

data class HomeUiState(
    val recommendPosts:      List<Post>       = emptyList(),
    val bannerPosts:         List<Post>       = emptyList(),
    val hotItems:            List<HotItem>    = emptyList(),
    val followedPool:        List<Post>       = emptyList(),
    val followDisplayCount:  Int              = 10,
    val followZoneCursors:   Map<Int, String> = emptyMap(),
    val selectedTab:         Int              = 0,
    val isLoading:           Boolean          = true,
    val isRefreshing:        Boolean          = false,
    val isLoadingHot:        Boolean          = false,
    val hotError:            String?          = null,
    val isLoadingFollow:     Boolean          = false,
    val isLoadingMoreFollow: Boolean          = false,
    val error:               String?          = null
) {
    val followedPosts: List<Post> get() = followedPool.take(followDisplayCount)
    val followHasMore: Boolean    get() = followDisplayCount < followedPool.size || followZoneCursors.isNotEmpty()
}

class HomeViewModel constructor(
    private val homeRepo:     HomeRepository,
    private val zoneRepo:     ZoneRepository,
    private val followedRepo: FollowedZonesRepository,
    private val matchScraper: HupuMatchScraper
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()

    // Expose followed zones count so the tab can show a badge
    val followedCount = followedRepo.getAll()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 首页顶部「今日比分」横条。独立于 HomeUiState，避免 loadRecommend 整体重置时被清空。 */
    private val _homeMatches = MutableStateFlow<List<HomeMatch>>(emptyList())
    val homeMatches = _homeMatches.asStateFlow()

    /** 上次成功抓到比分的时刻，用来做节流 */
    private var lastMatchFetch = 0L
    private var matchJob: Job? = null

    init {
        loadRecommend()
        refreshHomeMatches()
    }

    /**
     * 刷新「今日比分」横条。首页每次回到前台、以及重新进入首页时都会调用，
     * 由 [MATCH_REFRESH_INTERVAL_MS] 节流；已有请求在飞时直接跳过。
     *
     * 比分条属于锦上添花，抓取失败就静默留空，不影响首页其余内容；
     * 失败时也不记时间，这样下次回到前台还能立刻重试。
     */
    fun refreshHomeMatches() {
        if (matchJob?.isActive == true) return
        if (System.currentTimeMillis() - lastMatchFetch < MATCH_REFRESH_INTERVAL_MS) return
        matchJob = viewModelScope.launch {
            runCatching { matchScraper.fetchHomeMatches() }
                .onSuccess {
                    _homeMatches.value = it
                    lastMatchFetch = System.currentTimeMillis()
                }
        }
    }

    fun loadRecommend(isRefresh: Boolean = false) {
        viewModelScope.launch {
            val current = _state.value
            _state.value = if (isRefresh && current.recommendPosts.isNotEmpty()) {
                current.copy(isRefreshing = true, error = null)
            } else {
                HomeUiState(isLoading = true)
            }
            runCatching { homeRepo.getPosts() }
                .onSuccess { posts ->
                    val banner = posts.filter { it.images.isNotEmpty() }.shuffled().take(5)
                    _state.value = _state.value.copy(
                        recommendPosts = posts,
                        bannerPosts    = banner,
                        isLoading      = false,
                        isRefreshing   = false,
                        error          = null
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

    /** 下拉刷新当前 Tab：推荐 / 热榜 / 关注 */
    fun refreshSelectedTab() {
        when (_state.value.selectedTab) {
            0 -> loadRecommend(isRefresh = true)
            1 -> loadHot(isRefresh = true)
            2 -> loadFollowedFeed(isRefresh = true)
        }
    }

    fun selectTab(index: Int) {
        _state.value = _state.value.copy(selectedTab = index)
        when (index) {
            1 -> if (_state.value.hotItems.isEmpty()) loadHot()
            2 -> if (_state.value.followedPool.isEmpty()) loadFollowedFeed()
        }
    }

    fun loadHot(isRefresh: Boolean = false) {
        viewModelScope.launch {
            val current = _state.value
            _state.value = if (isRefresh && current.hotItems.isNotEmpty()) {
                current.copy(isRefreshing = true, hotError = null)
            } else {
                current.copy(isLoadingHot = true, hotError = null)
            }
            runCatching { homeRepo.getHotItems() }
                .onSuccess { items ->
                    _state.value = _state.value.copy(
                        hotItems    = items,
                        isLoadingHot = false,
                        isRefreshing = false,
                        hotError     = null
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoadingHot = false,
                        isRefreshing = false,
                        hotError     = e.message ?: "加载失败"
                    )
                }
        }
    }

    fun loadFollowedFeed(isRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoadingFollow = !isRefresh,
                isRefreshing    = isRefresh
            )
            val zones = followedRepo.getAll().first()
            if (zones.isEmpty()) {
                _state.value = _state.value.copy(
                    followedPool = emptyList(), followZoneCursors = emptyMap(),
                    followDisplayCount = 10, isLoadingFollow = false, isRefreshing = false
                )
                return@launch
            }
            val sem = Semaphore(FOLLOW_FETCH_CONCURRENCY)
            val results = zones.map { zone ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        runCatching { zoneRepo.getZonePosts(zone.topicId) }.getOrNull()
                            ?.let { page -> Triple(zone.topicId, page.posts.map { it.copy(label = zone.topicName) }, page.nextCursor) }
                    }
                }
            }.mapNotNull { it.await() }

            if (isRefresh && results.isEmpty()) {
                _state.value = _state.value.copy(isLoadingFollow = false, isRefreshing = false)
                return@launch
            }

            val pool    = results.flatMap { it.second }.sortedBy { parseHupuTime(it.time) }
            val cursors = results.mapNotNull { (id, _, c) -> c?.let { id to it } }.toMap()
            _state.value = _state.value.copy(
                followedPool = pool, followZoneCursors = cursors,
                followDisplayCount = 10, isLoadingFollow = false, isRefreshing = false
            )
        }
    }

    fun loadMoreFollowed() {
        val s = _state.value
        if (s.isLoadingMoreFollow || s.isRefreshing) return

        if (s.followDisplayCount < s.followedPool.size) {
            _state.value = s.copy(followDisplayCount = s.followDisplayCount + 10)
            return
        }
        if (s.followZoneCursors.isEmpty()) return

        _state.value = s.copy(isLoadingMoreFollow = true)
        viewModelScope.launch {
            val zones   = followedRepo.getAll().first().associateBy { it.topicId }
            val cursors = _state.value.followZoneCursors

            val sem = Semaphore(FOLLOW_FETCH_CONCURRENCY)
            val results = cursors.entries.map { (topicId, cursor) ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        val name = zones[topicId]?.topicName ?: ""
                        runCatching { zoneRepo.getZonePosts(topicId, cursor) }.getOrNull()
                            ?.let { page -> Triple(topicId, page.posts.map { it.copy(label = name) }, page.nextCursor) }
                    }
                }
            }.mapNotNull { it.await() }

            val cur     = _state.value
            val seen    = cur.followedPool.map { it.tid }.toHashSet()
            val newPool = (cur.followedPool + results.flatMap { it.second }.filter { it.tid !in seen })
                .sortedBy { parseHupuTime(it.time) }
            val newCursors = results.mapNotNull { (id, _, c) -> c?.let { id to it } }.toMap()
            _state.value = cur.copy(
                followedPool        = newPool,
                followDisplayCount  = cur.followDisplayCount + 10,
                followZoneCursors   = newCursors,
                isLoadingMoreFollow = false
            )
        }
    }
}

// 解析虎扑相对时间为秒数（越小越新）
private fun parseHupuTime(time: String): Long = when {
    time == "刚刚"         -> 0L
    time.endsWith("分钟前") -> (time.removeSuffix("分钟前").trim().toLongOrNull() ?: 999L) * 60
    time.endsWith("小时前") -> (time.removeSuffix("小时前").trim().toLongOrNull() ?: 999L) * 3600
    time.endsWith("天前")  -> (time.removeSuffix("天前").trim().toLongOrNull() ?: 999L) * 86400
    else                  -> Long.MAX_VALUE  // 具体日期（很早的帖子）排最后
}
