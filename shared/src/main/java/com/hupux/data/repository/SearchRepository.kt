package com.hupux.data.repository

import com.hupux.data.ioDispatcher
import com.hupux.data.model.SearchPage
import com.hupux.data.scraper.HupuScraper
import kotlinx.coroutines.withContext

class SearchRepository(private val scraper: HupuScraper) {

    /** 搜索帖子（20 条/页） */
    suspend fun search(query: String, page: Int = 1): SearchPage = withContext(ioDispatcher) {
        scraper.fetchSearchPosts(query, page)
    }

    /** 搜索页热搜词；拿不到返回空列表 */
    suspend fun hotSearchWords(): List<String> = withContext(ioDispatcher) {
        scraper.fetchHotSearchWords()
    }
}
