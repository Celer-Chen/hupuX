package com.hupux.ui.profile

import androidx.lifecycle.SavedStateHandle
import com.hupux.data.model.UserRecommendPost
import com.hupux.data.scraper.HupuDesktopScraper
import com.hupux.ui.components.PagingViewModel
import com.hupux.ui.components.PageResult

class UserRecommendListViewModel constructor(
    private val scraper: HupuDesktopScraper,
    savedState: SavedStateHandle
) : PagingViewModel<UserRecommendPost>() {

    private val uid: String = savedState.get<String>("uid") ?: ""

    override suspend fun fetchPage(page: Int): PageResult<UserRecommendPost> {
        val items = scraper.fetchRecommendList(uid, page)
        // 该接口没有 hasMore 字段，按「返回满页即可能还有更多」推断
        return PageResult(items, items.size >= 30)
    }

    override fun keyOf(item: UserRecommendPost): Any = item.tid
}
