package com.hupux.data.repository

import android.net.Uri
import com.hupux.data.local.CookiePreferences
import com.hupux.data.model.CommentPage
import com.hupux.data.model.PostDetail
import com.hupux.data.scraper.HupuDesktopScraper
import com.hupux.data.scraper.HupuImageUploader
import com.hupux.data.scraper.HupuScraper
import com.hupux.data.scraper.VideoUploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PostRepository constructor(
    private val scraper: HupuScraper,
    private val desktopScraper: HupuDesktopScraper,
    private val cookiePrefs: CookiePreferences,
    private val imageUploader: HupuImageUploader
) {
    suspend fun getPost(tid: String): PostDetail = withContext(Dispatchers.IO) {
        val post = scraper.fetchPost(tid)
        // 桌面版 SSR 分页信息最完整（current/total），且匿名也可访问。
        // 匿名短贴继续用移动端 SSR（更快）；登录用户或明显是多页盖楼贴才抓桌面版。
        val needsDesktop = cookiePrefs.isLoggedIn ||
            post.hasMoreComments ||
            post.replies > post.comments.size
        if (!needsDesktop) return@withContext post
        try {
            val desktop = desktopScraper.fetchPostReplies(tid)
            post.copy(
                comments          = desktop.comments,
                hasMoreComments   = desktop.currentPage < desktop.totalPages,
                commentTotalPages = desktop.totalPages,
                desktopBaseUrl    = desktop.baseUrl,
                fid               = desktop.fid,
                topicId           = desktop.topicId,
                isRecommended     = desktop.isRecommended
            )
        } catch (_: Exception) {
            post
        }
    }

    suspend fun loadCommentsPage(tid: String, page: Int): CommentPage =
        withContext(Dispatchers.IO) {
            val desktop = runCatching { desktopScraper.fetchPostReplies(tid, page) }.getOrNull()
            if (desktop != null) {
                CommentPage(
                    comments    = desktop.comments,
                    currentPage = desktop.currentPage,
                    totalPages  = desktop.totalPages,
                    hasMore     = desktop.currentPage < desktop.totalPages
                )
            } else {
                scraper.fetchReplyList(tid, page)
            }
        }

    suspend fun createThread(topicId: Int, title: String, content: String): Long =
        withContext(Dispatchers.IO) {
            desktopScraper.createThread(topicId, title, content)
        }

    suspend fun uploadImage(uri: Uri): String =
        withContext(Dispatchers.IO) { imageUploader.upload(uri) }

    suspend fun uploadImageForReply(uri: Uri): String =
        withContext(Dispatchers.IO) { imageUploader.upload(uri, "reply-oss", "/reply") }

    /** 上传视频（module=editor-video-oss），返回 videoUrl + objectKey。[onProgress] 回调来自上传线程池。 */
    suspend fun uploadVideo(
        uri: Uri,
        onProgress: ((uploaded: Long, total: Long) -> Unit)? = null
    ): VideoUploadResult =
        withContext(Dispatchers.IO) { imageUploader.uploadVideo(uri, onProgress) }

    /** 用 videoUrl 换取封面地址 */
    suspend fun getVideoCover(videoUrl: String): String =
        withContext(Dispatchers.IO) { desktopScraper.getVideoCover(videoUrl) }

    /** 发视频帖。format.videoInfo.key = base64(objectKey + 毫秒时间戳) 在此算好 */
    suspend fun createVideoThread(
        topicId: Int, title: String, desc: String,
        videoUrl: String, coverUrl: String, objectKey: String,
        creationType: String, containsAi: Int
    ): Long = withContext(Dispatchers.IO) {
        val key = android.util.Base64.encodeToString(
            (objectKey + System.currentTimeMillis()).toByteArray(), android.util.Base64.NO_WRAP)
        desktopScraper.createVideoThread(
            topicId, title, desc, videoUrl, coverUrl, key, creationType, containsAi)
    }

    suspend fun submitReply(
        tid: String, fid: String, topicId: String,
        quoteId: String, content: String
    ) = withContext(Dispatchers.IO) {
        desktopScraper.createReply(tid, fid, topicId, quoteId, content)
    }

    suspend fun toggleLikeComment(
        pid: String, tid: String, fid: String, isCurrentlyLiked: Boolean
    ) = withContext(Dispatchers.IO) {
        val puid = cookiePrefs.extractUid()?.toLongOrNull() ?: 0L
        val pidL = pid.toLongOrNull() ?: 0L
        val tidL = tid.toLongOrNull() ?: 0L
        val fidL = fid.toLongOrNull() ?: 0L
        if (isCurrentlyLiked) {
            desktopScraper.cancelLightReply(pidL, tidL, puid, fidL)
        } else {
            desktopScraper.lightReply(pidL, tidL, puid, fidL)
        }
    }

    suspend fun collectPost(tid: String, isCurrentlyCollected: Boolean) = withContext(Dispatchers.IO) {
        val tidL = tid.toLongOrNull() ?: 0L
        if (isCurrentlyCollected) desktopScraper.uncollectThread(tidL)
        else desktopScraper.collectThread(tidL)
    }

    suspend fun recommendPost(tid: String, fid: String, isCurrentlyRecommended: Boolean) =
        withContext(Dispatchers.IO) {
            val tidL = tid.toLongOrNull() ?: 0L
            val fidL = fid.toLongOrNull() ?: 0L
            val status = if (isCurrentlyRecommended) 0 else 1
            desktopScraper.recommendThread(tidL, fidL, status)
        }

    suspend fun getSubReplies(tid: String, parentPid: String) = withContext(Dispatchers.IO) {
        val desktop = runCatching { desktopScraper.fetchDesktopSubReplies(tid, parentPid) }
            .getOrDefault(emptyList())
        if (desktop.isNotEmpty()) desktop else scraper.fetchSubReplies(tid, parentPid)
    }
}
