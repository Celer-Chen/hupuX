package com.hupux.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.hupux.data.model.PlayerBoxScore
import com.hupux.data.model.TeamBoxScore
import com.hupux.ui.components.HupuPullToRefresh
import com.hupux.ui.home.PillButton
import com.hupux.ui.theme.*
import org.koin.androidx.compose.koinViewModel

private val PLAYER_COLUMNS = listOf(
    "球员", "位置", "时间", "投篮", "3分", "罚球",
    "前场", "后场", "篮板", "助攻", "犯规", "抢断", "失误", "封盖", "得分", "±"
)
private val PLAYER_COL_WIDTHS = listOf(
    100.dp, 34.dp, 40.dp, 52.dp, 48.dp, 48.dp,
    38.dp, 38.dp, 38.dp, 40.dp, 38.dp, 38.dp, 38.dp, 38.dp, 44.dp, 44.dp
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScoreScreen(
    boxScoreId: String,
    league: String,
    onBack: () -> Unit,
    vm: BoxScoreViewModel = koinViewModel()
) {
    LaunchedEffect(boxScoreId) { vm.load(boxScoreId, league) }
    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxSize().background(AppBg)) {
        Column(Modifier.background(HeaderBg).statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                }
                Text("比赛技术统计", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
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
                    PillButton("重试", onClick = { vm.refresh(boxScoreId, league) })
                }

                state.boxScore == null || !state.boxScore!!.available -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📊", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("比赛尚未开始，暂无技术统计", color = TextSecondary, fontSize = 14.sp)
                }

                else -> HupuPullToRefresh(
                    isRefreshing = state.isRefreshing,
                    onRefresh    = { vm.refresh(boxScoreId, league) },
                    modifier     = Modifier.fillMaxSize()
                ) {
                    val box = state.boxScore!!
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        item { QuarterTable(box.away, box.home) }
                        item { TeamSection(box.away) }
                        item { TeamSection(box.home) }
                    }
                }
            }
        }
    }
}

/** 分节比分表 */
@Composable
private fun QuarterTable(away: TeamBoxScore, home: TeamBoxScore) {
    val quarters = maxOf(away.quarterScores.size, home.quarterScores.size)
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        shape = RoundedCornerShape(12.dp), color = CardBg, shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Box(Modifier.weight(1f))
                repeat(quarters) { i ->
                    Box(Modifier.width(38.dp), Alignment.Center) {
                        Text(quarterLabel(i), fontSize = 12.sp, color = TextTertiary)
                    }
                }
                Box(Modifier.width(46.dp), Alignment.Center) {
                    Text("总分", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
            listOf(away, home).forEach { team ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(team.teamName, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                        if (team.isHome) {
                            Spacer(Modifier.width(5.dp))
                            Surface(color = TagBg, shape = RoundedCornerShape(4.dp)) {
                                Text("主", fontSize = 9.sp, color = HupuRed,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                    }
                    repeat(quarters) { i ->
                        Box(Modifier.width(38.dp), Alignment.Center) {
                            Text(team.quarterScores.getOrNull(i) ?: "-", fontSize = 13.sp, color = TextSecondary)
                        }
                    }
                    Box(Modifier.width(46.dp), Alignment.Center) {
                        Text(
                            team.totalScore.toString(),
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = if (team.totalScore > (if (team.isHome) away.totalScore else home.totalScore))
                                HupuRed else TextPrimary
                        )
                    }
                }
            }
        }
    }
}

private fun quarterLabel(index: Int): String = when (index) {
    0 -> "一"; 1 -> "二"; 2 -> "三"; 3 -> "四"
    else -> "加${index - 3}"
}

/** 一方球队的球员技术统计 */
@Composable
private fun TeamSection(team: TeamBoxScore) {
    if (team.starters.isEmpty() && team.bench.isEmpty()) return
    val scroll = rememberScrollState()

    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${team.teamName}${if (team.isHome) "（主队）" else "（客队）"}",
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary
            )
            Spacer(Modifier.width(8.dp))
            Text("${team.totalScore} 分", fontSize = 13.sp, color = HupuRed, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(6.dp))
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp), color = CardBg, shadowElevation = 1.dp
        ) {
            Column {
                // 表头（首列固定，其余横向滚动）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PHeaderCell(PLAYER_COLUMNS[0], PLAYER_COL_WIDTHS[0])
                    Row(Modifier.horizontalScroll(scroll)) {
                        PLAYER_COLUMNS.drop(1).forEachIndexed { i, t ->
                            PHeaderCell(t, PLAYER_COL_WIDTHS[i + 1])
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = DividerColor)

                if (team.starters.isNotEmpty()) {
                    GroupLabel("首发")
                    team.starters.forEachIndexed { i, p ->
                        PlayerRow(p, scroll, i != team.starters.lastIndex)
                    }
                }
                if (team.bench.isNotEmpty()) {
                    GroupLabel("替补")
                    team.bench.forEachIndexed { i, p ->
                        PlayerRow(p, scroll, i != team.bench.lastIndex)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Box(
        Modifier.fillMaxWidth().background(BgGray.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextTertiary)
    }
}

@Composable
private fun PlayerRow(p: PlayerBoxScore, scroll: ScrollState, showDivider: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 首列固定
        Box(Modifier.width(PLAYER_COL_WIDTHS[0]).padding(vertical = 8.dp, horizontal = 8.dp)) {
            Text(p.name, fontSize = 12.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.horizontalScroll(scroll)) {
            PCell(p.position, PLAYER_COL_WIDTHS[1], TextTertiary)
            PCell(p.minutes, PLAYER_COL_WIDTHS[2])
            PCell(p.fieldGoals, PLAYER_COL_WIDTHS[3])
            PCell(p.threePointers, PLAYER_COL_WIDTHS[4])
            PCell(p.freeThrows, PLAYER_COL_WIDTHS[5])
            PCell(p.offRebounds, PLAYER_COL_WIDTHS[6])
            PCell(p.defRebounds, PLAYER_COL_WIDTHS[7])
            PCell(p.rebounds, PLAYER_COL_WIDTHS[8])
            PCell(p.assists, PLAYER_COL_WIDTHS[9])
            PCell(p.fouls, PLAYER_COL_WIDTHS[10])
            PCell(p.steals, PLAYER_COL_WIDTHS[11])
            PCell(p.turnovers, PLAYER_COL_WIDTHS[12])
            PCell(p.blocks, PLAYER_COL_WIDTHS[13])
            PCell(p.points, PLAYER_COL_WIDTHS[14], HupuRed, FontWeight.Bold)
            PCell(p.plusMinus, PLAYER_COL_WIDTHS[15], plusMinusColor(p.plusMinus))
        }
    }
    if (showDivider) HorizontalDivider(thickness = 0.5.dp, color = DividerColor.copy(alpha = 0.5f))
}

@Composable
private fun plusMinusColor(v: String) = when {
    v.startsWith("+") -> HupuRed
    v.startsWith("-") -> Color(0xFF1E88E5)
    else -> TextSecondary
}

@Composable
private fun PHeaderCell(title: String, width: androidx.compose.ui.unit.Dp) {
    Box(Modifier.width(width).padding(vertical = 8.dp), Alignment.Center) {
        Text(title, fontSize = 10.sp, color = TextTertiary, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun PCell(
    text: String, width: androidx.compose.ui.unit.Dp,
    color: androidx.compose.ui.graphics.Color = TextSecondary,
    weight: FontWeight = FontWeight.Normal
) {
    Box(Modifier.width(width).padding(vertical = 8.dp), Alignment.Center) {
        Text(text, fontSize = 11.sp, color = color, fontWeight = weight,
            textAlign = TextAlign.Center, maxLines = 1)
    }
}
