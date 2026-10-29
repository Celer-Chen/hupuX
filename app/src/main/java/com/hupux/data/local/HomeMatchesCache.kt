package com.hupux.data.local

import android.content.Context
import com.hupux.data.model.HomeMatch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 「今日比分」横条的本地缓存。
 *
 * 这条数据要抓 www.hupu.com 首页约 580KB 的 HTML，冷启动现场抓太慢；
 * 这里把上次成功的结果连同抓取时间一起缓存，启动时先展示缓存、后台再刷新。
 *
 * 手动拼 JSON（而不是给 HomeMatch 加 @Serializable）：模型是纯展示层，
 * 不值得为一个缓存把 kotlinx-serialization 插件引到 model 里。
 */
class HomeMatchesCache(ctx: Context) {

    private val prefs = ctx.getSharedPreferences("hupu_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /** 上次成功抓取的时间戳（毫秒），0 表示从未抓到 */
    val savedAt: Long get() = prefs.getLong(KEY_TIME, 0L)

    fun load(): List<HomeMatch> {
        val raw = prefs.getString(KEY_DATA, null) ?: return emptyList()
        return runCatching {
            json.parseToJsonElement(raw).let { it as? JsonArray ?: return emptyList() }
                .mapNotNull { el ->
                    val o = el as? JsonObject ?: return@mapNotNull null
                    val home = o.str("homeTeamName") ?: return@mapNotNull null
                    HomeMatch(
                        leagueType = o.str("leagueType") ?: "",
                        status     = o.str("status") ?: "",
                        desc       = o.str("desc") ?: "",
                        matchTime  = o.str("matchTime") ?: "",
                        homeName   = home,
                        awayName   = o.str("awayTeamName") ?: "",
                        homeLogo   = o.str("homeTeamLogo") ?: "",
                        awayLogo   = o.str("awayTeamLogo") ?: "",
                        homeScore  = o.str("homeScore") ?: "",
                        awayScore  = o.str("awayScore") ?: ""
                    )
                }
        }.getOrDefault(emptyList())
    }

    fun save(matches: List<HomeMatch>, atMillis: Long) {
        val arr = buildJsonArray {
            matches.forEach { m ->
                add(buildJsonObject {
                    put("leagueType", m.leagueType)
                    put("status", m.status)
                    put("desc", m.desc)
                    put("matchTime", m.matchTime)
                    put("homeTeamName", m.homeName)
                    put("awayTeamName", m.awayName)
                    put("homeTeamLogo", m.homeLogo)
                    put("awayTeamLogo", m.awayLogo)
                    put("homeScore", m.homeScore)
                    put("awayScore", m.awayScore)
                })
            }
        }
        prefs.edit()
            .putString(KEY_DATA, arr.toString())
            .putLong(KEY_TIME, atMillis)
            .apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_DATA).remove(KEY_TIME).apply()
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)
            ?.takeIf { it !is kotlinx.serialization.json.JsonNull }
            ?.contentOrNull

    companion object {
        private const val KEY_DATA = "home_matches_cache"
        private const val KEY_TIME = "home_matches_saved_at"
    }
}
