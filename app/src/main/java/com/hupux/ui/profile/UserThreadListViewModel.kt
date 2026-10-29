package com.hupux.ui.profile

import androidx.lifecycle.SavedStateHandle
import com.hupux.data.model.UserThread
import com.hupux.data.scraper.HupuDesktopScraper
import com.hupux.ui.components.PagingViewModel
import com.hupux.ui.components.PageResult

class UserThreadListViewModel constructor(
    private val scraper: HupuDesktopScraper,
    savedState: SavedStateHandle
) : PagingViewModel<UserThread>() {

    private val uid: String = savedState.get<String>("uid") ?: ""
    private var nextMaxTime = 0L

    override suspend fun fetchPage(page: Int): PageResult<UserThread> {
        if (page == 1) nextMaxTime = 0L
        val data = scraper.fetchThreadList(uid, nextMaxTime)
        nextMaxTime = data.nextMaxTime
        return PageResult(data.threads, data.hasMore)
    }

    override fun keyOf(item: UserThread): Any = item.tid
}
