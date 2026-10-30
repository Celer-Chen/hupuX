package com.hupux.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hupux.data.model.Comment
import com.hupux.data.model.PostDetail
import com.hupux.ui.theme.*

// ─── Comment card ─────────────────────────────────────────────────────────────

@Composable
internal fun CommentCard(
    comment: Comment,
    isLiked: Boolean = false,
    showQuote: Boolean = true,
    floor: Int? = null,
    onLike: (() -> Unit)? = null,
    onReplyClick: (() -> Unit)? = null,
    onReplyToComment: (() -> Unit)? = null,
    onUserClick: (() -> Unit)? = null
) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp), color = CardBg, shadowElevation = 2.dp) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                AsyncImage(model = comment.avatar, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(BgGray)
                        .then(if (onUserClick != null) Modifier.clickable(onClick = onUserClick) else Modifier))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(comment.username, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            modifier = if (onUserClick != null) Modifier.clickable(onClick = onUserClick) else Modifier)
                        if (comment.isAuthor) {
                            Spacer(Modifier.width(5.dp))
                            Surface(color = HupuRed, shape = RoundedCornerShape(3.dp)) {
                                Text("楼主", fontSize = 10.sp, color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        val likeCount = comment.lights + if (isLiked) 1 else 0
                        val likeColor = if (isLiked) HupuRed else TextTertiary
                        val likeMod = if (onLike != null)
                            Modifier.clickable(onClick = onLike).padding(4.dp)
                        else
                            Modifier.padding(4.dp)
                        Text("👍 $likeCount", fontSize = 12.sp, color = likeColor,
                            modifier = likeMod)
                    }
                    Spacer(Modifier.height(5.dp))
                    if (showQuote) {
                        comment.quoteContent?.let { quote ->
                            Surface(color = BgGray, shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                                    Text("@${comment.quoteUsername}", fontSize = 12.sp,
                                        color = HupuRed, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.height(2.dp))
                                    HtmlText(html = quote, imageScale = 0.125f)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    HtmlText(html = comment.content, imageScale = 0.125f)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(comment.time, fontSize = 11.sp, color = TextTertiary)
                        if (comment.location.isNotEmpty())
                            Text("  ·  ${comment.location}", fontSize = 11.sp, color = TextTertiary)
                        Spacer(Modifier.weight(1f))
                        if (onReplyToComment != null) {
                            val dark = isSystemInDarkTheme()
                            val pillBg = if (dark)
                                Brush.verticalGradient(listOf(Color(0xFF2A2D3A), Color(0xFF1D2028)))
                            else
                                Brush.verticalGradient(listOf(Color.White, Color(0xFFE0E0E0)))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .shadow(2.dp, RoundedCornerShape(14.dp),
                                        ambientColor = Color.Black.copy(.12f),
                                        spotColor    = Color.Black.copy(.12f))
                                    .background(pillBg, RoundedCornerShape(14.dp))
                                    .clickable { onReplyToComment() }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("回复", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    color = TextSecondary)
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        if (comment.replyCount > 0) {
                            val dark = isSystemInDarkTheme()
                            val pillBg = if (dark)
                                Brush.verticalGradient(listOf(Color(0xFF2A2D3A), Color(0xFF1D2028)))
                            else
                                Brush.verticalGradient(listOf(Color.White, Color(0xFFE0E0E0)))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .shadow(2.dp, RoundedCornerShape(14.dp),
                                        ambientColor = Color.Black.copy(.12f),
                                        spotColor    = Color.Black.copy(.12f))
                                    .background(pillBg, RoundedCornerShape(14.dp))
                                    .clickable { onReplyClick?.invoke() }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "${comment.replyCount} 条回复",
                                    fontSize   = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Sub-replies bottom sheet ─────────────────────────────────────────────────

@Composable
internal fun SubRepliesSheet(
    subReplies: List<Comment>,
    totalCount: Int,
    isLoading: Boolean,
    canGoBack: Boolean,
    likedPids: Set<String> = emptySet(),
    onLike: ((Comment) -> Unit)? = null,
    onBack: () -> Unit,
    onReplyClick: (String) -> Unit,
    onReplyToComment: ((Comment) -> Unit)? = null,
    onOpenUser: (String) -> Unit = {}
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canGoBack) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回上层",
                        tint = TextSecondary)
                }
                Spacer(Modifier.width(4.dp))
            }
            Text("回复", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.width(6.dp))
            Text("$totalCount 条", fontSize = 13.sp, color = TextTertiary)
        }
        HorizontalDivider(color = DividerColor)

        if (isLoading) {
            Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                CircularProgressIndicator(color = HupuRed, modifier = Modifier.size(28.dp))
            }
        } else if (subReplies.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(100.dp), Alignment.Center) {
                Text("暂未找到回复内容", color = TextTertiary, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier       = Modifier.fillMaxWidth().heightIn(max = 480.dp)
            ) {
                items(subReplies) { reply ->
                    CommentCard(
                        comment          = reply,
                        isLiked          = reply.pid in likedPids,
                        showQuote        = false,
                        onLike           = onLike?.let { { it(reply) } },
                        onReplyClick     = if (reply.replyCount > 0) ({ onReplyClick(reply.pid) }) else null,
                        onReplyToComment = if (reply.desktopPage > 0) onReplyToComment?.let { { it(reply) } } else null,
                        onUserClick      = if (reply.authorPuid.isNotEmpty() && reply.desktopPage > 0) ({ onOpenUser(reply.authorPuid) }) else null
                    )
                }
                if (subReplies.size < totalCount) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) {
                            Text(
                                "还有 ${totalCount - subReplies.size} 条回复，请在帖子中查看",
                                fontSize = 11.sp, color = TextTertiary
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
