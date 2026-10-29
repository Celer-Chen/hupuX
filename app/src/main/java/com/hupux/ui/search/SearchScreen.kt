package com.hupux.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hupux.data.model.SearchPost
import com.hupux.ui.components.HupuPullToRefresh
import com.hupux.ui.home.PillButton
import com.hupux.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onPostClick: (String) -> Unit,
    onBack: () -> Unit,
    vm: SearchViewModel = koinViewModel()
) {
    val state by vm.state.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val keyboard       = LocalSoftwareKeyboardController.current

    // 进页面自动聚焦输入框
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(Modifier.fillMaxSize().background(AppBg)) {
        // ── 搜索栏 ────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().background(HeaderBg).statusBarsPadding()
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextPrimary)
            }
            OutlinedTextField(
                value         = state.query,
                onValueChange = vm::updateQuery,
                singleLine    = true,
                placeholder   = { Text("搜索帖子", fontSize = 14.sp, color = TextTertiary) },
                trailingIcon  = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = vm::clearQuery) {
                            Icon(Icons.Default.Close, contentDescription = "清空", tint = TextTertiary)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    vm.search()
                }),
                shape    = RoundedCornerShape(18.dp),
                colors   = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = HupuRed,
                    unfocusedBorderColor = DividerColor
                ),
                modifier = Modifier.weight(1f).focusRequester(focusRequester)
            )
            TextButton(onClick = { keyboard?.hide(); vm.search() }) {
                Text("搜索", color = HupuRed, fontWeight = FontWeight.Bold)
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = DividerColor)

        Box(Modifier.fillMaxSize()) {
            when {
                state.submittedQuery.isEmpty() -> SearchLanding(
                    state           = state,
                    onSearch        = { q -> keyboard?.hide(); vm.search(q) },
                    onClearHistory  = vm::clearHistory,
                    onRemoveHistory = vm::removeHistory
                )

                state.isLoading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center), color = HupuRed)

                state.error != null && state.results.isEmpty() -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.error!!, color = HupuRed, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    PillButton("重试", onClick = { vm.search() })
                }

                state.results.isEmpty() -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🔍", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "没有找到「${state.submittedQuery}」相关的帖子",
                        color = TextSecondary, fontSize = 14.sp
                    )
                }

                else -> SearchResults(
                    state        = state,
                    onPostClick  = onPostClick,
                    onLoadMore   = vm::loadMore,
                    onRefresh    = vm::refresh,
                    onSelectSort = vm::selectSort
                )
            }
        }
    }
}

// ─── 搜索首页：热搜词 + 搜索历史 ─────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchLanding(
    state: SearchUiState,
    onSearch: (String) -> Unit,
    onClearHistory: () -> Unit,
    onRemoveHistory: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        if (state.hotWords.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "热搜",
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.hotWords.forEach { word ->
                    SuggestionChip(
                        onClick = { onSearch(word) },
                        label = { Text(word, fontSize = 13.sp) }
                    )
                }
            }
        }

        if (state.history.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("搜索历史", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClearHistory, contentPadding = PaddingValues(0.dp)) {
                    Text("清空", fontSize = 12.sp, color = TextTertiary)
                }
            }
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.history.forEach { query ->
                    HistoryChip(
                        query   = query,
                        onClick = { onSearch(query) },
                        onRemove = { onRemoveHistory(query) }
                    )
                }
            }
        }

        if (state.hotWords.isEmpty() && state.history.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 80.dp), Alignment.Center) {
                Text("输入关键词搜索帖子", color = TextTertiary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun HistoryChip(query: String, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BgGray)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(query, fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Default.Close, contentDescription = "删除", tint = TextTertiary,
                modifier = Modifier.size(12.dp))
        }
    }
}

// ─── 搜索结果 ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchResults(
    state: SearchUiState,
    onPostClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onSelectSort: (SearchSort) -> Unit
) {
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3 && state.hasMore && !state.isLoadingMore && !state.isRefreshing
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) onLoadMore() }

    HupuPullToRefresh(
        isRefreshing = state.isRefreshing,
        onRefresh    = onRefresh,
        modifier     = Modifier.fillMaxSize()
    ) {
        LazyColumn(state = listState) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "共 ${state.totalCount} 个结果",
                        fontSize = 12.sp, color = TextTertiary, modifier = Modifier.weight(1f)
                    )
                    SearchSort.entries.forEach { sort ->
                        TextButton(
                            onClick = { onSelectSort(sort) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text(
                                sort.label,
                                fontSize = 13.sp,
                                fontWeight = if (state.sort == sort) FontWeight.Bold else FontWeight.Normal,
                                color = if (state.sort == sort) HupuRed else TextTertiary
                            )
                        }
                    }
                }
            }
            items(state.displayedResults, key = { it.tid }) { post ->
                SearchPostCard(post = post, keyword = state.submittedQuery, onClick = { onPostClick(post.tid) })
            }
            if (state.isLoadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = HupuRed)
                    }
                }
            }
            if (!state.hasMore && state.results.isNotEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                        Text("没有更多了", fontSize = 12.sp, color = TextTertiary)
                    }
                }
            }
        }
    }
}

// ─── 结果卡片 ────────────────────────────────────────────────────────────────

@Composable
private fun SearchPostCard(post: SearchPost, keyword: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp), color = CardBg, shadowElevation = 1.dp
    ) {
        Row(Modifier.padding(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    highlight(post.title, keyword),
                    fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
                    lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (post.summary.isNotEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        highlight(post.summary, keyword),
                        fontSize = 12.sp, color = TextSecondary,
                        lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    buildString {
                        append(post.username)
                        append("  💬 ${post.replies}")
                        if (post.lights > 0)       append("  👍 ${post.lights}")
                        if (post.recommendNum > 0) append("  🔥 ${post.recommendNum}")
                        if (post.lastPostTime > 0) append("  ${formatLastReply(post.lastPostTime)}")
                    },
                    fontSize = 11.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (post.image.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                Box {
                    AsyncImage(
                        model              = post.image,
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.size(84.dp).clip(RoundedCornerShape(8.dp)).background(BgGray)
                    )
                    if (post.isVideo) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                        ) {
                            Text("视频", fontSize = 9.sp, color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }
            }
        }
    }
}

/** 关键词高亮（不区分大小写），无关键词时原样返回 */
@Composable
private fun highlight(text: String, keyword: String): AnnotatedString {
    val key = keyword.trim()
    if (key.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        val lower = text.lowercase()
        val lowerKey = key.lowercase()
        var start = 0
        while (true) {
            val idx = lower.indexOf(lowerKey, start)
            if (idx < 0) {
                append(text.substring(start))
                break
            }
            append(text.substring(start, idx))
            withStyle(SpanStyle(color = HupuRed, fontWeight = FontWeight.Bold)) {
                append(text.substring(idx, idx + key.length))
            }
            start = idx + key.length
        }
    }
}

private val LAST_REPLY_FMT = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

private fun formatLastReply(epochSeconds: Long): String =
    LAST_REPLY_FMT.format(Date(epochSeconds * 1000))
