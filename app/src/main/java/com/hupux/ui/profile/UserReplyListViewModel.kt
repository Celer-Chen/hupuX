package com.hupux.ui.profile

import androidx.lifecycle.SavedStateHandle
import com.hupux.data.model.UserReply
import com.hupux.data.scraper.HupuDesktopScraper
import com.hupux.ui.components.PagingViewModel
import com.hupux.ui.components.PageResult

class UserReplyListViewModel constructor(
    private val scraper: HupuDesktopScraper,
    savedState: SavedStateHandle
) : PagingViewModel<UserReply>() {

    private val uid: String = savedState.get<String>("uid") ?: ""
    private var maxTime = 0L

    override suspend fun fetchPage(page: Int): PageResult<UserReply> {
        if (page == 1) maxTime = 0L
        val data = scraper.fetchReplyList(uid, maxTime)
        maxTime = data.maxTime
        return PageResult(data.replies, data.hasMore)
    }

    override fun keyOf(item: UserReply): Any = item.pid
}
