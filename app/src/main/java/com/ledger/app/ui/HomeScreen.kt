package com.ledger.app.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledger.app.data.AppDatabase
import com.ledger.app.data.Project
import com.ledger.app.data.ProjectSummary
import com.ledger.app.data.Record
import com.ledger.app.util.Dates
import com.ledger.app.util.Series
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 一级页面：底部导航 3 个 Tab（总览 / 明细 / 我的） */
@Composable
fun MainScreen(db: AppDatabase, nav: Navigator, tab: Int, onTab: (Int) -> Unit) {
    val ctx = LocalContext.current
    val projects by remember { db.projectDao().observeAll() }.collectAsState(initial = null)
    val summaries by remember { db.recordDao().observeSummaries() }.collectAsState(initial = emptyList())
    val allRecords by remember { db.recordDao().observeAll() }.collectAsState(initial = emptyList())

    val quickRecord: () -> Unit = {
        if (projects.isNullOrEmpty()) {
            Toast.makeText(ctx, "请先新建一个项目", Toast.LENGTH_SHORT).show()
        } else nav.push(Screen.RecordEdit(null, null))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { LedgerBottomBar(tab, onTab) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> OverviewTab(projects, summaries, allRecords, nav, quickRecord)
                1 -> RecordsTab(projects.orEmpty(), allRecords, nav, quickRecord)
                else -> MeTab(projects.orEmpty(), allRecords.size, nav, quickRecord)
            }
        }
    }
}

// ================================================================ 总览

@Composable
private fun OverviewTab(
    projects: List<Project>?,
    summaries: List<ProjectSummary>,
    allRecords: List<Record>,
    nav: Navigator,
    quickRecord: () -> Unit,
) {
    val series = remember(allRecords) { Series.cumulative(allRecords) }
    val sumMap = remember(summaries) { summaries.associateBy { it.projectId } }
    val net = summaries.sumOf { it.income } - summaries.sumOf { it.expense }
    val today = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))
    }

    Column(Modifier.fillMaxSize()) {
        LargeHeader("账本", today) { QuickRecordButton(quickRecord) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            item { HeroProfitCard("总盈亏", net, series) }
            item {
                SectionHeader("我的项目", badge = projects?.takeIf { it.isNotEmpty() }?.size?.toString()) {
                    RoundIconButton(
                        LedgerIcons.Plus, "新建项目", { nav.push(Screen.ProjectEdit(null)) },
                        tint = MaterialTheme.colorScheme.onPrimary,
                        container = MaterialTheme.colorScheme.primary,
                        size = 36.dp,
                    )
                }
            }
            val list = projects
            if (list != null && list.isEmpty()) {
                item { EmptyState("还没有项目，点「＋」新建一个", onClick = { nav.push(Screen.ProjectEdit(null)) }) }
            }
            items(list.orEmpty(), key = { it.id }) { p ->
                val s = sumMap[p.id]
                ProjectCard(p, (s?.income ?: 0L) - (s?.expense ?: 0L)) { nav.push(Screen.ProjectDetail(p.id)) }
            }
        }
    }
}

/** 项目卡片：图标 + 名称 + 净盈亏 */
@Composable
private fun ProjectCard(p: Project, net: Long, onClick: () -> Unit) {
    LedgerCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProjectIcon(p.iconFile, p.name, 50.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                p.name,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            AmountText(net, 18.sp, MoneyColors.of(net))
            Spacer(Modifier.width(4.dp))
            Icon(
                LedgerIcons.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ================================================================ 明细

@Composable
private fun RecordsTab(projects: List<Project>, allRecords: List<Record>, nav: Navigator, quickRecord: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf<Long?>(null) }
    // 筛选的项目被删除后回到「全部」
    LaunchedEffect(projects) {
        if (filter != null && projects.none { it.id == filter }) filter = null
    }
    val projectMap = remember(projects) { projects.associateBy { it.id } }
    // observeAll 为时间正序，这里倒序
    val groups = remember(allRecords, filter) {
        val f = filter
        val map = LinkedHashMap<Int, MutableList<Record>>()
        for (i in allRecords.indices.reversed()) {
            val r = allRecords[i]
            if (f != null && r.projectId != f) continue
            map.getOrPut(Dates.monthKey(r.timestamp)) { ArrayList() }.add(r)
        }
        map.values.toList()
    }

    Column(Modifier.fillMaxSize()) {
        LargeHeader("明细") { QuickRecordButton(quickRecord) }
        LazyRow(
            contentPadding = PaddingValues(horizontal = PagePadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            item { PillChip("全部", filter == null) { filter = null } }
            items(projects, key = { it.id }) { p -> PillChip(p.name, filter == p.id) { filter = p.id } }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 4.dp, bottom = 24.dp),
        ) {
            if (groups.isEmpty()) {
                item { EmptyState("暂无流水", Modifier.padding(top = 8.dp)) }
            }
            for (g in groups) {
                val first = g.first()
                item(key = "m" + Dates.monthKey(first.timestamp)) {
                    MonthHeader(Dates.monthLabel(first.timestamp), g.sumOf { it.signedCents })
                }
                items(g.size, key = { g[it].id }) { i ->
                    val r = g[i]
                    val p = projectMap[r.projectId]
                    RecordRow(
                        r,
                        first = i == 0,
                        last = i == g.lastIndex,
                        subtitle = if (filter == null) p?.name ?: "" else sourceLabel(r.source),
                        leading = {
                            if (filter == null) ProjectIcon(p?.iconFile, p?.name ?: "?", 40.dp) else SourceBadge(r)
                        },
                    ) { nav.push(Screen.RecordEdit(r.id, r.projectId)) }
                }
            }
        }
    }
}

/** 月份标题行：月份 + 当月小计（标题样式） */
@Composable
fun MonthHeader(label: String, monthNet: Long) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), modifier = Modifier.weight(1f))
        AmountText(monthNet, 16.sp, MoneyColors.of(monthNet))
    }
}

/** 流水行（分组卡片样式） */
@Composable
fun RecordRow(
    r: Record,
    first: Boolean,
    last: Boolean,
    subtitle: String,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val title = r.counterparty.ifBlank { r.note.ifBlank { if (r.signedCents > 0) "收入" else "支出" } }
    GroupedRow(first, last, onClick) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                Dates.monthDayTime(r.timestamp) + if (subtitle.isNotBlank()) " · $subtitle" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        AmountText(r.signedCents, 16.sp, MoneyColors.of(r.signedCents))
    }
}

// ================================================================ 我的

@Composable
private fun MeTab(projects: List<Project>, recordCount: Int, nav: Navigator, quickRecord: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember { appVersion(ctx) }
    Column(Modifier.fillMaxSize()) {
        LargeHeader("我的")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = PagePadding, end = PagePadding, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            BrandCard(projects.size, recordCount)
            PrimaryButton(
                "新建项目", { nav.push(Screen.ProjectEdit(null)) },
                Modifier.fillMaxWidth(), icon = LedgerIcons.Plus, height = 58.dp,
            )
            TonalButton("记一笔", quickRecord, Modifier.fillMaxWidth(), icon = LedgerIcons.Pen)
            Spacer(Modifier.height(4.dp))
            LedgerCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                Text("账单导出", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(14.dp))
                GuideStep(1, "微信：我 → 服务 → 钱包 → 账单 → 常见问题 → 下载账单")
                Spacer(Modifier.height(12.dp))
                GuideStep(2, "支付宝：我的 → 账单 → ··· → 开具交易流水明细")
                Spacer(Modifier.height(12.dp))
                GuideStep(3, "收到的 PDF / CSV 传到手机，在项目页「导入账单」")
            }
            Text(
                "账本 v$version",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

/** 品牌卡：深绿渐变 + 项目数 / 流水数 */
@Composable
private fun BrandCard(projectCount: Int, recordCount: Int) {
    val shape = CardShape
    Box(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Brand.Emerald, Brand.EmeraldDeep)))
            .padding(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(LedgerIcons.Book, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                Spacer(Modifier.width(12.dp))
                Text("账本", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Box(Modifier.clip(CircleShape).background(Brand.Gold).padding(horizontal = 10.dp, vertical = 3.dp)) {
                    Text("离线", color = Color(0xFF3A2A00), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row {
                BrandStat("项目", projectCount, Modifier.weight(1f))
                BrandStat("流水", recordCount, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BrandStat(label: String, value: Int, modifier: Modifier) {
    Column(modifier) {
        Text(value.toString(), color = Color.White, style = AmountStyle.copy(fontSize = 28.sp))
        Text(label, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun GuideStep(n: Int, text: String) {
    val p = LocalLedger.current
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(p.gold.copy(alpha = if (p.dark) 0.24f else 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(n.toString(), color = if (p.dark) p.gold else Color(0xFF8A6111), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
    }
}

private fun appVersion(ctx: Context): String = runCatching {
    @Suppress("DEPRECATION")
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
}.getOrDefault("")
