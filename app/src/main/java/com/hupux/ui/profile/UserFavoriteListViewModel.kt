package com.hupux.ui.profile

import com.hupux.data.model.UserThread
import com.hupux.data.repository.ProfileRepository
import com.hupux.ui.components.PagingViewModel
import com.hupux.ui.components.PageResult

class UserFavoriteListViewModel(
    private val profileRepo: ProfileRepository
) : PagingViewModel<UserThread>() {

    private var nextMaxTime = 0L

    override suspend fun fetchPage(page: Int): PageResult<UserThread> {
        if (page == 1) nextMaxTime = 0L
        val data = profileRepo.fetchFavoriteList(nextMaxTime)
        nextMaxTime = data.nextMaxTime
        return PageResult(data.threads, data.hasMore)
    }

    override fun keyOf(item: UserThread): Any = item.tid
}
