package com.hupux.data.scraper

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.hupux.data.model.MatchBoxScore
import com.hupux.data.model.PlayerBoxScore
import com.hupux.data.model.StandingGroup
import com.hupux.data.model.StandingRow
import com.hupux.data.model.Standings
import com.hupux.data.model.TeamBoxScore
import io.ktor.client.HttpClient

/**
 * 虎扑 PC 端（nba.hupu.com）的数据抓取：战绩排行 + 比赛技术统计。
 *
 * 这两块数据 PC 端是服务端渲染的 HTML 表格，和移动端 API 无关：
 * - 战绩排行：`/standings`，`table.players_table`，「东部/西部」两张表
 * - 技术统计：`/games/boxscore/{id}`，分节比分表 + 两队球员技术统计表
 *
 * 适配联赛：NBA（nba.hupu.com）、CBA（cba.hupu.com）。
 */
class HupuStatsScraper(private val client: HttpClient) {

    /**
     * 战绩排行。
     * @param league "NBA" 或 "CBA"
     */
    suspend fun fetchStandings(league: String): Standings {
        val base = hostOf(league)
        val html = HupuHttp.getString(client, "$base/standings", ua = HupuHttp.DESKTOP_UA, referer = "$base/")
        return parseStandings(html, league)
    }

    /**
     * 比赛技术统计（分节比分 + 两队球员数据）。
     * @param boxScoreId 是 nba.hupu.com/games/boxscore/{id} 里的 id
     * @param league "NBA" 或 "CBA"
     */
    suspend fun fetchBoxScore(boxScoreId: String, league: String = "NBA"): MatchBoxScore {
        val base = hostOf(league)
        val html = HupuHttp.getString(
            client, "$base/games/boxscore/$boxScoreId",
            ua = HupuHttp.DESKTOP_UA, referer = "$base/schedule"
        )
        return parseBoxScore(html)
    }


    /**
     * 按日期 + 两队队名反查 PC 端 boxscoreId。
     *
     * 移动端赛程接口的 matchId 和 PC 端 games/boxscore/{id} 是两套编号，没有直接映射；
     * PC 端 schedule/{date} 页里每场比赛旁边就有 boxscore 链接，按队名匹配即可。
     * @param date 形如 "2026-10-23"
     * @return boxscoreId，找不到返回 null
     */
    suspend fun findBoxScoreId(date: String, teamA: String, teamB: String, league: String = "NBA"): String? {
        val base = hostOf(league)
        val html = HupuHttp.getString(client, "$base/schedule/$date", ua = HupuHttp.DESKTOP_UA, referer = "$base/schedule")
        val doc = Ksoup.parse(html)
        // 每个 boxscore 链接所在的行里找队名
        for (link in doc.select("a[href*=/games/boxscore/]")) {
            val href = link.attr("href")
            val id = href.substringAfterLast("/").takeIf { it.isNotBlank() } ?: continue
            // 往上找最近的行容器（tr 或外层 div），看里面是否同时出现两队名
            val rowText = generateSequence(link.parent()) { it.parent() }
                .take(4)
                .map { it.text() }
                .firstOrNull { it.contains(teamA) && it.contains(teamB) }
                ?: continue
            return id
        }
        return null
    }

    private fun hostOf(league: String): String =
        if (league.equals("CBA", ignoreCase = true)) "https://cba.hupu.com" else "https://nba.hupu.com"

    // ── 战绩排行解析 ────────────────────────────────────────────────────────

    private fun parseStandings(html: String, league: String): Standings {
        val doc = Ksoup.parse(html)
        val groups = doc.select("table.players_table").mapNotNull { table ->
            // 表前的分区标题（如「东部」「西部」）在 table 前的兄弟元素里
            val title = groupNameBefore(table)
            val rows = table.select("tr").mapNotNull { parseStandingRow(it) }
            if (rows.isEmpty()) null else StandingGroup(title, rows)
        }
        return Standings(league, groups)
    }

    /** 往前找最近的分区标题文本（<h3>/<div class="title">/纯文本节点） */
    private fun groupNameBefore(table: Element): String {
        var el: Element? = table.previousElementSibling()
        var guard = 0
        while (el != null && guard++ < 5) {
            val text = el.text().trim()
            if (text.isNotEmpty() && text.length <= 12) return text
            el = el.previousElementSibling()
        }
        return ""
    }

    private fun parseStandingRow(tr: Element): StandingRow? {
        val cells = tr.select("td").map { it.text().trim() }
        // 表头行（th）或列数不足的行跳过；标准表 14 列
        if (cells.size < 14) return null
        return StandingRow(
            rank            = cells[0],
            teamName        = cells[1],
            wins            = cells[2],
            losses          = cells[3],
            winPct          = cells[4],
            gamesBehind     = cells[5],
            homeRecord      = cells[6],
            awayRecord      = cells[7],
            divisionRecord  = cells[8],
            conferenceRecord = cells[9],
            pointsFor       = cells[10],
            pointsAgainst   = cells[11],
            pointDiff       = cells[12],
            streak          = cells[13]
        )
    }

    // ── 技术统计解析 ────────────────────────────────────────────────────────

    private fun parseBoxScore(html: String): MatchBoxScore {
        val doc = Ksoup.parse(html)
        val tables = doc.select("table")
        if (tables.size < 3) return MatchBoxScore("", emptyTeam(false), emptyTeam(true), available = false)

        // 表 0：分节比分（行 0 = 表头「一/二/三/四/总分」，行 1/2 = 两队）
        val quarterRows = tables[0].select("tr").map { tr ->
            tr.select("td,th").map { it.text().trim() }
        }
        val awayLine = quarterRows.getOrNull(1) ?: return MatchBoxScore("", emptyTeam(false), emptyTeam(true), available = false)
        val homeLine = quarterRows.getOrNull(2) ?: return MatchBoxScore("", emptyTeam(false), emptyTeam(true), available = false)

        // 表 1/2：两队球员技术统计（行 0 = 表头，行 1+ = 球员）
        // 「首发/替补」是单元格里的分组标记：第一列非球员名的行（"首发"/"替补"）作为分隔
        val awayPlayers = parsePlayers(tables[1])
        val homePlayers = parsePlayers(tables[2])

        val away = TeamBoxScore(
            teamName      = awayLine.firstOrNull { it.isNotEmpty() } ?: "客队",
            isHome        = false,
            quarterScores = awayLine.drop(1).dropLast(1).map { it.ifEmpty { "0" } },
            starters      = awayPlayers.first,
            bench         = awayPlayers.second
        )
        val home = TeamBoxScore(
            teamName      = homeLine.firstOrNull { it.isNotEmpty() } ?: "主队",
            isHome        = true,
            quarterScores = homeLine.drop(1).dropLast(1).map { it.ifEmpty { "0" } },
            starters      = homePlayers.first,
            bench         = homePlayers.second
        )

        // 标题：页面 h1/h2 里的比分，退回到「客队 x-y 主队」
        val title = doc.select("h1,h2,h3").firstOrNull { it.text().contains("-") }?.text()?.trim()
            ?: "${away.teamName} ${away.totalScore}-${home.totalScore} ${home.teamName}"

        return MatchBoxScore(title = title, home = home, away = away)
    }

    /** 解析球员表，返回 (首发, 替补)。表内以「首发」「替补」文本行分组。 */
    private fun parsePlayers(table: Element): Pair<List<PlayerBoxScore>, List<PlayerBoxScore>> {
        val starters = mutableListOf<PlayerBoxScore>()
        val bench = mutableListOf<PlayerBoxScore>()
        var isStarter = true
        for (tr in table.select("tr")) {
            val cells = tr.select("td,th").map { it.text().trim() }
            if (cells.isEmpty()) continue
            val first = cells[0]
            // 分组标记行：第一列是「首发」「替补」
            if (first == "首发") { isStarter = true; continue }
            if (first == "替补") { isStarter = false; continue }
            // 表头行
            if (first == "" || first == "球员" || first == "首发" || cells.size < 16) continue
            val player = PlayerBoxScore(
                name         = first,
                position     = cells.getOrNull(1) ?: "",
                minutes      = cells.getOrNull(2) ?: "",
                fieldGoals   = cells.getOrNull(3) ?: "",
                threePointers = cells.getOrNull(4) ?: "",
                freeThrows   = cells.getOrNull(5) ?: "",
                offRebounds  = cells.getOrNull(6) ?: "",
                defRebounds  = cells.getOrNull(7) ?: "",
                rebounds     = cells.getOrNull(8) ?: "",
                assists      = cells.getOrNull(9) ?: "",
                fouls        = cells.getOrNull(10) ?: "",
                steals       = cells.getOrNull(11) ?: "",
                turnovers    = cells.getOrNull(12) ?: "",
                blocks       = cells.getOrNull(13) ?: "",
                points       = cells.getOrNull(14) ?: "",
                plusMinus    = cells.getOrNull(15) ?: "",
                starter      = isStarter
            )
            (if (isStarter) starters else bench).add(player)
        }
        return starters to bench
    }

    private fun emptyTeam(isHome: Boolean) = TeamBoxScore("", isHome, emptyList(), emptyList(), emptyList())
}
