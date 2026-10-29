package com.hupux.data.scraper

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText

/**
 * 虎扑接口请求的公共部分：User-Agent 常量 + 带重试的 GET。
 *
 * 三个 scraper（HupuScraper / HupuDesktopScraper / HupuMatchScraper）此前
 * 各自维护一份 UA 字符串和请求头，这里收敛到一处；网络层统一做「瞬时失败重试」
 * （连接抖动 / 网关 5xx / 超时），业务错误不重试。
 */
object HupuHttp {

    /** 移动端 UA：m.hupu.com 系接口 */
    const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"

    /** 桌面 UA：www.hupu.com / my.hupu.com 系接口 */
    const val DESKTOP_UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /**
     * bbs.hupu.com 的 WAF 对 Mac UA 会返回 405；
     * Windows Chrome UA + 浏览器导航头才能拿到 SSR
     */
    const val BBS_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /** 瞬时失败重试次数（总共尝试 1 + [RETRIES] 次） */
    private const val RETRIES = 2

    /**
     * 带重试的 GET。
     *
     * @param ua     User-Agent，见 [MOBILE_UA] / [DESKTOP_UA] / [BBS_UA]
     * @param referer 可选 Referer
     * @param cookie  可选 Cookie（登录态）
     * @param headers 附加请求头（如 bbs 的 Sec-Fetch-* 导航头）
     */
    suspend fun getString(
        client: HttpClient,
        url: String,
        ua: String = MOBILE_UA,
        referer: String? = null,
        cookie: String? = null,
        headers: Map<String, String> = emptyMap()
    ): String {
        var lastError: Throwable? = null
        repeat(RETRIES + 1) { attempt ->
            try {
                return client.get(url) {
                    header("User-Agent", ua)
                    header("Accept-Language", "zh-CN,zh;q=0.9")
                    referer?.let { header("Referer", it) }
                    cookie?.takeIf { it.isNotEmpty() }?.let { header("Cookie", it) }
                    headers.forEach { (k, v) -> header(k, v) }
                }.bodyAsText()
            } catch (e: Throwable) {
                lastError = e
                // 网络抖动类异常才重试；业务层解析异常直接抛给上层
                if (attempt == RETRIES) throw e
            }
        }
        throw lastError ?: IllegalStateException("unreachable")
    }
}
