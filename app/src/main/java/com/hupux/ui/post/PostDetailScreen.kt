package com.hupux.ui.post

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hupux.data.model.Comment
import com.hupux.data.model.PostDetail
import com.hupux.ui.home.PillButton
import com.hupux.ui.profile.ImageItem
import com.hupux.ui.theme.*
import org.koin.androidx.compose.koinViewModel

// 图片点击回调，由 PostDetailScreen 提供，HtmlText 和 PostBodyWebView 消费
internal val LocalImageClick = staticCompositionLocalOf<(String) -> Unit> { {} }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    tid: String,
    onBack: () -> Unit,
    onOpenUser: (puid: String) -> Unit = {},
    onLoginClick: () -> Unit = {},
    vm: PostDetailViewModel = koinViewModel()
) {
    LaunchedEffect(tid) { vm.load(tid) }
    val state by vm.state.collectAsState()
    var showLoginDialog by remember { mutableStateOf(false) }
    val s = state as? PostDetailUiState.Success

    var viewerUrl by remember { mutableStateOf<String?>(null) }

    CompositionLocalProvider(LocalImageClick provides { url ->
        val full = if (url.startsWith("//")) "https:$url" else url
        viewerUrl = full
    }) {

    HazeTopBarScaffold(
        modifier = Modifier.background(AppBg),
        // 正文是 WebView，战报帖里几十张动图在持续重绘，套进 haze 源会一直闪
        hazed = false,
        topBar = { barModifier ->
        // ── Top bar ───────────────────────────────────────────────
        Box(barModifier.statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary)
                }
                Text(s?.post?.topicName ?: "", fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                if (s != null) {
                    val context = LocalContext.current
                    IconButton(onClick = {
                        val url = "https://m.hupu.com/bbs/${s.post.tid}.html"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TITLE, s.post.title)
                            putExtra(Intent.EXTRA_TEXT, "${s.post.title}\n$url")
                        }
                        context.startActivity(Intent.createChooser(intent, "分享到"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "分享", tint = TextPrimary)
                    }
                }
            }
            HorizontalDivider(
                Modifier.align(Alignment.BottomCenter),
                thickness = 0.5.dp, color = DividerColor
            )
        }
        }
    ) { topPadding ->
        Box(Modifier.fillMaxSize()) {
            when (val st = state) {
                is PostDetailUiState.Loading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center), color = HupuRed)
                is PostDetailUiState.Error   -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(st.message, color = HupuRed)
                    Spacer(Modifier.height(12.dp))
                    PillButton("重试", onClick = { vm.load(tid) })
                }
                is PostDetailUiState.Success -> PostContent(st, vm, onOpenUser, { showLoginDialog = true }, topPadding)
            }
        }
    }

    // ── 子回复 ModalBottomSheet ───────────────────────────────────
    val success = state as? PostDetailUiState.Success
    if (success?.expandedPid != null) {
        val pid        = success.expandedPid
        val subReplies = success.subRepliesMap[pid] ?: emptyList()
        // 在帖子顶层评论和已加载的所有子回复里查找父评论
        val parent     = success.post.comments.find { it.pid == pid }
            ?: success.subRepliesMap.values.flatten().find { it.pid == pid }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = vm::dismissReplies,
            sheetState       = sheetState
        ) {
            SubRepliesSheet(
                subReplies       = subReplies,
                totalCount       = parent?.replyCount ?: subReplies.size,
                isLoading        = success.isLoadingSubReplies,
                canGoBack        = success.replyStack.size > 1,
                likedPids        = success.likedPids,
                onLike           = if (success.post.fid.isNotEmpty()) {{
                    if (success.isLoggedIn) vm::toggleLike else { c: Comment -> showLoginDialog = true }
                }} else null,
                onBack           = vm::popReplies,
                onReplyClick     = vm::showReplies,
                onReplyToComment = { c: Comment ->
                    if (success.isLoggedIn) vm.startReply(c) else showLoginDialog = true
                },
                onOpenUser       = { puid: String ->
                    if (success.isLoggedIn) onOpenUser(puid) else showLoginDialog = true
                }
            )
        }
    }

    // ── 原生回复 BottomSheet ──────────────────────────────────────
    val successState = state as? PostDetailUiState.Success
    if (successState?.showReplySheet == true) {
        ModalBottomSheet(
            onDismissRequest = vm::dismissReply,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ReplySheet(
                replyingTo      = successState.replyingTo,
                content         = successState.replyContent,
                images          = successState.replyImages,
                isSubmitting    = successState.isSubmittingReply,
                error           = successState.replyError,
                success         = successState.replySuccess,
                onContentChange = vm::updateReplyContent,
                onAddImage      = vm::addReplyImage,
                onRemoveImage   = vm::removeReplyImage,
                onSubmit        = vm::submitReply,
                onDismiss       = vm::dismissReply
            )
        }
    }

    } // end CompositionLocalProvider

    // ── 全屏图片查看器 ────────────────────────────────────────────
    viewerUrl?.let { url ->
        ImageViewerDialog(url = url, onDismiss = { viewerUrl = null })
    }

    // ── 登录引导对话框 ────────────────────────────────────────────
    if (showLoginDialog) {
        AlertDialog(
            onDismissRequest = { showLoginDialog = false },
            title = { Text("需要登录") },
            text  = { Text("点赞、回复、收藏、推荐等功能需要登录虎扑账号后使用。") },
            confirmButton = {
                TextButton(onClick = {
                    showLoginDialog = false
                    onLoginClick()
                }) { Text("去登录", color = HupuRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showLoginDialog = false }) { Text("取消") }
            }
        )
    }
}

// ─── Post content list ────────────────────────────────────────────────────────

@Composable
private fun PostContent(
    s: PostDetailUiState.Success,
    vm: PostDetailViewModel,
    onOpenUser: (puid: String) -> Unit = {},
    onRequireLogin: () -> Unit = {},
    /** 顶栏浮在内容之上，顶部留白按量到的栏高补 */
    contentTopPadding: Dp = 0.dp
) {
    val listState = rememberLazyListState()
    var scrollToComments by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpText by remember { mutableStateOf("") }

    fun goToPage(page: Int) {
        scrollToComments = true
        vm.goToCommentPage(page)
    }

    LaunchedEffect(s.pageMode, s.commentPage) {
        if (scrollToComments) {
            listState.animateScrollToItem(1)
            scrollToComments = false
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = contentTopPadding + 8.dp, bottom = 24.dp)
    ) {
        item { PostBodyCard(
            post          = s.post,
            isRecommended = s.isRecommended,
            isCollected   = s.isCollected,
            onCollect     = if (s.post.fid.isNotEmpty()) {{
                if (s.isLoggedIn) vm.toggleCollect() else onRequireLogin()
            }} else null,
            onRecommend   = if (s.post.fid.isNotEmpty()) {{
                if (s.isLoggedIn) vm.recommendPost() else onRequireLogin()
            }} else null,
            onReplyMain   = if (s.post.fid.isNotEmpty()) {{
                if (s.isLoggedIn) vm.startMainPostReply() else onRequireLogin()
            }} else null
        ) }
        item {
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                shape    = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color    = CardBg, shadowElevation = 4.dp
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("全部评论", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text("${s.post.replies}", fontSize = 14.sp, color = TextTertiary)
                    Spacer(Modifier.weight(1f))
                    if (s.isLoadingSort) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = HupuRed, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    } else {
                        SortChip("正序", s.sortMode == CommentSort.DEFAULT)  { vm.setSort(CommentSort.DEFAULT) }
                        SortChip("倒序", s.sortMode == CommentSort.REVERSED) { vm.setSort(CommentSort.REVERSED) }
                        SortChip("最热", s.sortMode == CommentSort.BY_LIKES) { vm.setSort(CommentSort.BY_LIKES) }
                    }
                }
            }
        }
        if (s.post.commentTotalPages > 1 || s.pageMode) {
            item {
                CommentPager(
                    page         = s.commentPage,
                    totalPages   = s.post.commentTotalPages,
                    pageMode     = s.pageMode,
                    canPrev      = s.canGoPrevPage,
                    canNext      = s.canGoNextPage,
                    isLoading    = s.isPageLoading,
                    onPrev       = { scrollToComments = true; vm.prevCommentPage() },
                    onNext       = { scrollToComments = true; vm.nextCommentPage() },
                    onJump       = { jumpText = ""; showJumpDialog = true },
                    onPageMode   = { scrollToComments = true; vm.enablePageMode() },
                    onContinuous = vm::enableContinuousMode
                )
            }
        }
        val displayedComments = s.displayedComments
        // 楼层号：分页模式按「(页码-1)*20 + 页内序号」，连续模式按全局序号
        val floorOffset = if (s.pageMode) (s.commentPage - 1) * 20 else 0
        itemsIndexed(displayedComments, key = { _, c -> c.pid }) { index, comment ->
            // 连续模式：靠近底部时自动加载下一页；分页模式不自动叠加
            if (!s.pageMode && s.sortMode == CommentSort.DEFAULT &&
                index == displayedComments.size - 3 && s.post.hasMoreComments
            ) {
                LaunchedEffect(s.post.comments.size) { vm.loadMoreComments() }
            }
            CommentCard(
                comment          = comment,
                floor            = floorOffset + index + 1,
                isLiked          = comment.pid in s.likedPids,
                onLike           = if (s.post.fid.isNotEmpty()) {{
                    if (s.isLoggedIn) vm.toggleLike(comment) else onRequireLogin()
                }} else null,
                onReplyClick     = { vm.showReplies(comment.pid) },
                onReplyToComment = if (comment.desktopPage > 0) {{
                    if (s.isLoggedIn) vm.startReply(comment) else onRequireLogin()
                }} else null,
                onUserClick      = if (comment.authorPuid.isNotEmpty() && comment.desktopPage > 0) {{
                    if (s.isLoggedIn) onOpenUser(comment.authorPuid) else onRequireLogin()
                }} else null
            )
        }
        // 非默认排序：数据已全部在内存，「加载更多」只是本地增量显示，无需网络
        if (s.sortMode != CommentSort.DEFAULT && s.hasMoreDisplayed) {
            item {
                Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) {
                    OutlinedButton(
                        onClick = vm::loadMoreSorted,
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = HupuRed)
                    ) {
                        Text("加载更多（还有 ${s.sortedComments.size - s.sortedDisplayCount} 条）")
                    }
                }
            }
        }
        if (s.isLoadingMoreComments && s.sortMode == CommentSort.DEFAULT) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = HupuRed)
                }
            }
        }
        if (s.pageMode) {
            item {
                CommentPager(
                    page         = s.commentPage,
                    totalPages   = s.post.commentTotalPages,
                    pageMode     = true,
                    canPrev      = s.canGoPrevPage,
                    canNext      = s.canGoNextPage,
                    isLoading    = s.isPageLoading,
                    onPrev       = { scrollToComments = true; vm.prevCommentPage() },
                    onNext       = { scrollToComments = true; vm.nextCommentPage() },
                    onJump       = { jumpText = ""; showJumpDialog = true },
                    onPageMode   = { scrollToComments = true; vm.enablePageMode() },
                    onContinuous = vm::enableContinuousMode,
                    showContinuous = false
                )
            }
        }
    }

    if (showJumpDialog) {
        val maxPage = s.post.commentTotalPages.takeIf { it > 0 } ?: Int.MAX_VALUE
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("跳到评论页") },
            text = {
                Column {
                    OutlinedTextField(
                        value         = jumpText,
                        onValueChange = { jumpText = it.filter(Char::isDigit).take(6) },
                        singleLine    = true,
                        label         = { Text("页码") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier      = Modifier.fillMaxWidth()
                    )
                    if (s.post.commentTotalPages > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text("共 ${s.post.commentTotalPages} 页", fontSize = 12.sp, color = TextTertiary)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = jumpText.toIntOrNull()
                        if (target != null && target in 1..maxPage) {
                            showJumpDialog = false
                            goToPage(target)
                        }
                    }
                ) { Text("确定", color = HupuRed) }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CommentPager(
    page: Int,
    totalPages: Int,
    pageMode: Boolean,
    canPrev: Boolean,
    canNext: Boolean,
    isLoading: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJump: () -> Unit,
    onPageMode: () -> Unit,
    onContinuous: () -> Unit,
    showContinuous: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
        color    = CardBg
    ) {
        if (!pageMode) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (totalPages > 0) "共 $totalPages 页" else "分页浏览",
                    fontSize = 12.sp, color = TextTertiary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onPageMode,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) { Text("分页浏览", fontSize = 12.sp, color = HupuRed) }
            }
            return@Surface
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onPrev,
                enabled = canPrev && !isLoading,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) { Text("上一页", fontSize = 12.sp) }
            Text(
                if (totalPages > 0) "第 $page / $totalPages 页" else "第 $page 页",
                fontSize = 12.sp, color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onNext,
                enabled = canNext && !isLoading,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) { Text("下一页", fontSize = 12.sp) }
            TextButton(
                onClick = onJump,
                enabled = !isLoading,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) { Text("跳页", fontSize = 12.sp) }
            if (showContinuous) {
                TextButton(
                    onClick = onContinuous,
                    enabled = !isLoading,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) { Text("连续", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) HupuRed else TextTertiary
        )
    }
}

@Composable
private fun ReplySheet(
    replyingTo: Comment?,   // null = 回复主贴
    content: String,
    images: List<ImageItem>,
    isSubmitting: Boolean,
    error: String?,
    success: Boolean,
    onContentChange: (String) -> Unit,
    onAddImage: (Uri) -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(success) {
        if (success) {
            android.widget.Toast.makeText(context, "回复成功", android.widget.Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        uri?.let { onAddImage(it) }
    }
    val hasUploading = images.any { it.status == ImageItem.Status.Uploading }
    val canSubmit = !isSubmitting && !hasUploading &&
        (content.trim().isNotEmpty() || images.any { it.status == ImageItem.Status.Done })

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            // enableEdgeToEdge 下窗口不再为键盘 resize，键盘高度只通过
            // WindowInsets.ime 汇报，不加这句输入框会被键盘盖住
            .imePadding()
            .navigationBarsPadding()
    ) {
        Text(
            if (replyingTo != null) "回复 @${replyingTo.username}" else "回复主贴",
            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        // 引用原评论（回复主贴时不显示）
        if (replyingTo != null) {
            Surface(color = BgGray, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    android.text.Html.fromHtml(replyingTo.content, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim(),
                    fontSize = 13.sp, color = TextSecondary,
                    maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        OutlinedTextField(
            value = content,
            onValueChange = onContentChange,
            placeholder = { Text("输入回复内容...", color = TextTertiary, fontSize = 14.sp) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
            shape = RoundedCornerShape(10.dp),
            minLines = 3,
            enabled = !isSubmitting,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = HupuRed,
                unfocusedBorderColor = DividerColor
            )
        )
        // 图片缩略图条
        if (images.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(images, key = { it.uri }) { item ->
                    ReplyImageThumbnail(item = item, onRemove = { onRemoveImage(item.uri) })
                }
            }
        }
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(error, fontSize = 12.sp, color = HupuRed)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                enabled = !isSubmitting
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "添加图片", tint = TextSecondary)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("取消", color = TextSecondary)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSubmit,
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = HupuRed),
                shape = RoundedCornerShape(20.dp)
            ) {
                if (isSubmitting || hasUploading) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("提交", color = Color.White)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ReplyImageThumbnail(item: ImageItem, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
        AsyncImage(
            model              = item.uri,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize()
        )
        when (item.status) {
            ImageItem.Status.Uploading -> Box(
                modifier         = Modifier.fillMaxSize().background(Color.Black.copy(0.45f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            }
            ImageItem.Status.Error -> Box(
                modifier         = Modifier.fillMaxSize().background(Color(0xAAFF0000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Warning, contentDescription = item.errorMsg, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            ImageItem.Status.Done -> Unit
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(18.dp)
                .background(Color.Black.copy(0.55f), CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, contentDescription = "删除", tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

private fun buildDesktopReplyUrl(post: PostDetail, comment: Comment): String {
    val page = comment.desktopPage
    val pid  = comment.pid
    return if (page > 0 && post.desktopBaseUrl.isNotEmpty()) {
        if (page <= 1) {
            "https://bbs.hupu.com${post.desktopBaseUrl}#$pid"
        } else {
            val slug = post.desktopBaseUrl.removePrefix("/").removeSuffix(".html")
            "https://bbs.hupu.com/post-$slug-$page.html#$pid"
        }
    } else {
        "https://bbs.hupu.com/post-${post.tid}.html"
    }
}
