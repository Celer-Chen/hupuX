package com.hupux.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hupux.ui.theme.HupuRed

/**
 * 统一下拉刷新容器。
 *
 * 悬浮顶栏的页面要把 [topInset] 设为顶栏高度，否则刷新指示器会被顶栏盖住。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HupuPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh    = onRefresh,
        modifier     = modifier,
        state        = state,
        indicator    = {
            PullToRefreshDefaults.Indicator(
                state        = state,
                isRefreshing = isRefreshing,
                modifier     = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topInset),
                containerColor = MaterialTheme.colorScheme.surface,
                color          = HupuRed
            )
        },
        content = content
    )
}
