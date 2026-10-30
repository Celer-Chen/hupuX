package com.hupux.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hupux.data.model.PostDetail
import com.hupux.ui.theme.*

// ─── Post body card ───────────────────────────────────────────────────────────

@Composable
internal fun PostBodyCard(
    post: PostDetail,
    isRecommended: Boolean = false,
    isCollected: Boolean = false,
    onCollect: (() -> Unit)? = null,
    onRecommend: (() -> Unit)? = null,
    onReplyMain: (() -> Unit)? = null
) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        shape = RoundedCornerShape(16.dp), color = CardBg, shadowElevation = 4.dp) {
        Column(Modifier.padding(16.dp)) {
            if (post.topicName.isNotEmpty()) {
                Surface(color = HupuRed.copy(0.08f), shape = RoundedCornerShape(4.dp)) {
                    Text(post.topicName, fontSize = 11.sp, color = HupuRed,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Spacer(Modifier.height(10.dp))
            }
            Text(post.title, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                lineHeight = 26.sp, color = TextPrimary)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = post.authorAvatar, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(BgGray))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(post.author, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(2.dp))
                    Row {
                        Text(post.time, fontSize = 11.sp, color = TextTertiary)
                        if (post.location.isNotEmpty())
                            Text("  ·  ${post.location}", fontSize = 11.sp, color = TextTertiary)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${post.views}", fontSize = 12.sp, color = TextTertiary)
                    Text("浏览", fontSize = 10.sp, color = TextTertiary)
                }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
            Spacer(Modifier.height(12.dp))
            PostBodyWebView(html = post.content)
            if (onCollect != null || onRecommend != null || onReplyMain != null) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (onCollect != null) {
                        TextButton(onClick = onCollect) {
                            Text(
                                if (isCollected) "已收藏" else "收藏",
                                fontSize = 13.sp,
                                color = if (isCollected) TextTertiary else HupuRed,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    if (onRecommend != null) {
                        TextButton(onClick = onRecommend) {
                            Text(
                                if (isRecommended) "已推荐" else "推荐",
                                fontSize = 13.sp,
                                color = if (isRecommended) TextTertiary else HupuRed,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    if (onReplyMain != null) {
                        TextButton(onClick = onReplyMain) {
                            Text("回复主贴", fontSize = 13.sp, color = HupuRed, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
