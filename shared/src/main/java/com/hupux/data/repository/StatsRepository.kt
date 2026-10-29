package com.hupux.data.repository

import com.hupux.data.ioDispatcher
import com.hupux.data.model.MatchBoxScore
import com.hupux.data.model.Standings
import com.hupux.data.scraper.HupuStatsScraper
import kotlinx.coroutines.withContext

class StatsRepository(private val scraper: HupuStatsScraper) {

    /** 战绩排行（league: "NBA" / "CBA"） */
    suspend fun standings(league: String): Standings = withContext(ioDispatcher) {
        scraper.fetchStandings(league)
    }

    /** 比赛技术统计 */
    suspend fun boxScore(boxScoreId: String, league: String = "NBA"): MatchBoxScore =
        withContext(ioDispatcher) { scraper.fetchBoxScore(boxScoreId, league) }

    /** 按日期 + 两队名反查 PC 端 boxscoreId（用于从评分详情跳技术统计） */
    suspend fun findBoxScoreId(date: String, teamA: String, teamB: String, league: String = "NBA"): String? =
        withContext(ioDispatcher) { scraper.findBoxScoreId(date, teamA, teamB, league) }
}
