package com.hupux.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hupux.data.model.StandingGroup
import com.hupux.data.model.StandingRow
import com.hupux.ui.components.HupuPullToRefresh
import com.hupux.ui.home.PillButton
import com.hupux.ui.theme.*
import org.koin.androidx.compose.koinViewModel

private val LEAGUES = listOf("NBA", "CBA")

private val COLUMNS = listOf(
    "排名", "队名", "胜", "负", "胜率", "胜场差",
    "主场", "客场", "赛区", "分区", "得分", "失分", "净胜", "连胜/负"
)
private val COL_WIDTHS = listOf(38.dp, 88.dp, 30.dp, 30.dp, 48.dp, 48.dp, 50.dp, 50.dp, 50.dp, 50.dp, 48.dp, 48.dp, 48.dp, 58.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandingsScreen(
    onBack: () -> Unit,
    vm: StandingsViewModel = koinViewModel()
) {
    val state by vm.state.collectAsState()
    val tableScroll = rememberScrollState()

    Column(Modifier.fillMaxSize().background(AppBg)) {
        // ── 顶栏 + 联赛切换 ──────────────────────────────────────
        Column(Modifier.background(HeaderBg).statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                }
                Text("战绩排行", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                Spacer(Modifier.weight(1f))
                LEAGUES.forEach { league ->
                    val sel = state.league == league
                    TextButton(onClick = { vm.selectLeague(league) }) {
                        Text(
                            league, fontSize = 14.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            color = if (sel) HupuRed else TextTertiary
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
        }

        Box(Modifier.fillMaxSize()) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center), color = HupuRed)

                state.error != null -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.error!!, color = HupuRed, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    PillButton("重试", onClick = vm::refresh)
                }

                state.standings == null -> Text(
                    "暂无数据", color = TextSecondary, fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center))

                else -> HupuPullToRefresh(
                    isRefreshing = state.isRefreshing,
                    onRefresh    = vm::refresh,
                    modifier     = Modifier.fillMaxSize()
                ) {
                    LazyColumn {
                        state.standings!!.groups.forEach { group ->
                            item(key = "head-${group.name}") {
                                GroupHeader(group, tableScroll.value)
                            }
                            item(key = "table-${group.name}") {
                                StandingTable(group, tableScroll)
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(group: StandingGroup, scrollX: Int) {
    // 分区标题随表格水平滚动，保证始终可见
    Row(
        Modifier.fillMaxWidth().background(BgGray)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            group.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
            modifier = Modifier.offset(x = (-scrollX).dp.coerceAtMost(0.dp))
        )
    }
}

/** 战绩表格：首两列固定，其余列横向滚动 */
@Composable
private fun StandingTable(group: StandingGroup, scrollState: ScrollState) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp), color = CardBg, shadowElevation = 1.dp
    ) {
        Column {
            // 表头
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderCell(COLUMNS[0], COL_WIDTHS[0])
                HeaderCell(COLUMNS[1], COL_WIDTHS[1])
                Row(Modifier.horizontalScroll(scrollState)) {
                    COLUMNS.drop(2).forEachIndexed { i, title ->
                        HeaderCell(title, COL_WIDTHS[i + 2])
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
            group.rows.forEachIndexed { idx, row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FixedCell(row.rank, COL_WIDTHS[0], if (idx < 3) HupuRed else TextSecondary,
                        if (idx < 3) FontWeight.Bold else FontWeight.Normal)
                    FixedCell(row.teamName, COL_WIDTHS[1], TextPrimary, FontWeight.Medium,
                        TextAlign.Start)
                    Row(Modifier.horizontalScroll(scrollState)) {
                        ScrollCell(row.wins, COL_WIDTHS[2])
                        ScrollCell(row.losses, COL_WIDTHS[3])
                        ScrollCell(row.winPct, COL_WIDTHS[4])
                        ScrollCell(row.gamesBehind, COL_WIDTHS[5])
                        ScrollCell(row.homeRecord, COL_WIDTHS[6])
                        ScrollCell(row.awayRecord, COL_WIDTHS[7])
                        ScrollCell(row.divisionRecord, COL_WIDTHS[8])
                        ScrollCell(row.conferenceRecord, COL_WIDTHS[9])
                        ScrollCell(row.pointsFor, COL_WIDTHS[10])
                        ScrollCell(row.pointsAgainst, COL_WIDTHS[11])
                        ScrollCell(row.pointDiff, COL_WIDTHS[12],
                            color = diffColor(row.pointDiff))
                        ScrollCell(row.streak, COL_WIDTHS[13])
                    }
                }
                if (idx != group.rows.lastIndex) {
                    HorizontalDivider(thickness = 0.5.dp, color = DividerColor.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun diffColor(diff: String): androidx.compose.ui.graphics.Color = when {
    diff.startsWith("+") -> HupuRed
    diff.startsWith("-") -> Color(0xFF1E88E5)
    else -> TextSecondary
}

@Composable
private fun HeaderCell(title: String, width: androidx.compose.ui.unit.Dp) {
    Box(Modifier.width(width).padding(vertical = 8.dp), Alignment.Center) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextTertiary,
            textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun FixedCell(
    text: String, width: androidx.compose.ui.unit.Dp,
    color: androidx.compose.ui.graphics.Color,
    weight: FontWeight, align: TextAlign = TextAlign.Center
) {
    Box(Modifier.width(width).padding(vertical = 9.dp, horizontal = 2.dp)) {
        Text(text, fontSize = 12.sp, color = color, fontWeight = weight,
            textAlign = align, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ScrollCell(
    text: String, width: androidx.compose.ui.unit.Dp,
    color: androidx.compose.ui.graphics.Color = TextSecondary
) {
    Box(Modifier.width(width).padding(vertical = 9.dp), Alignment.Center) {
        Text(text, fontSize = 12.sp, color = color, textAlign = TextAlign.Center, maxLines = 1)
    }
}
