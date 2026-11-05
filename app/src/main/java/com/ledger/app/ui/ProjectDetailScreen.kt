package com.ledger.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledger.app.csv.BillImporter
import com.ledger.app.csv.ParseResult
import com.ledger.app.data.AppDatabase
import com.ledger.app.data.Record
import com.ledger.app.data.RecordSource
import com.ledger.app.data.RecordType
import com.ledger.app.util.Dates
import com.ledger.app.util.IconStore
import com.ledger.app.util.Money
import com.ledger.app.util.ChartMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_FILE_BYTES = 30L * 1024 * 1024

/** 文件选择器接受的类型：PDF（微信新版）、CSV/TXT（微信旧版、支付宝）；部分文件管理器把 csv 标成 octet-stream / ms-excel */
private val BILL_MIME_TYPES = arrayOf(
    "application/pdf",
    "text/*",
    "text/csv",
    "text/comma-separated-values",
    "application/csv",
    "application/vnd.ms-excel",
    "application/octet-stream",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(db: AppDatabase, nav: Navigator, projectId: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val project by remember(projectId) { db.projectDao().observe(projectId) }.collectAsState(initial = null)
    val records by remember(projectId) { db.recordDao().observeByProject(projectId) }.collectAsState(initial = emptyList())
    val series = remember(records) { ChartMath.chartPoints(records) }
    val income = remember(records) { records.filter { it.type == RecordType.INCOME }.sumOf { it.amountCents } }
    val expense = remember(records) { records.filter { it.type == RecordType.EXPENSE }.sumOf { it.amountCents } }
    // 按月倒序分组（records 已按时间倒序）
    val groups = remember(records) {
        val map = LinkedHashMap<Int, MutableList<Record>>()
        for (r in records) map.getOrPut(Dates.monthKey(r.timestamp)) { ArrayList() }.add(r)
        map.values.toList()
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var parsing by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // 不看扩展名：由 BillImporter 按文件头魔数判断 PDF / CSV
        val name = displayName(ctx, uri) ?: uri.lastPathSegment ?: "账单"
        parsing = true
        scope.launch {
            val result: ParseResult = withContext(Dispatchers.IO) {
                try {
                    val size = runCatching {
                        ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                    }.getOrNull() ?: -1L
                    if (size > MAX_FILE_BYTES) {
                        ParseResult(null, emptyList(), 0, "?", "文件过大（超过 30MB）")
                    } else {
                        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (bytes == null) ParseResult(null, emptyList(), 0, "?", "无法读取文件")
                        else BillImporter.parse(bytes, name)
                    }
                } catch (e: Throwable) {
                    ParseResult(null, emptyList(), 0, "?", "读取失败：${e.message}")
                }
            }
            parsing = false
            if (result.error != null) {
                Toast.makeText(ctx, result.error, Toast.LENGTH_LONG).show()
            } else if (result.rows.isEmpty()) {
                Toast.makeText(ctx, "文件中没有可导入的收入/支出记录", Toast.LENGTH_LONG).show()
            } else {
                nav.push(Screen.ImportPreview(projectId, name, result))
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SubTopBar("", onBack = { nav.pop() }) {
                RoundIconButton(LedgerIcons.Edit, "编辑项目", { nav.push(Screen.ProjectEdit(projectId)) })
                RoundIconButton(LedgerIcons.Trash, "删除项目", { confirmDelete = true }, tint = MaterialTheme.colorScheme.error)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 4.dp, bottom = 32.dp),
            ) {
                item {
                    Row(Modifier.padding(start = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProjectIcon(project?.iconFile, project?.name ?: "", 56.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            project?.name ?: "",
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                item { HeroProfitCard("本项目盈亏", income - expense, series, chartHeight = ChartHeightDetail) }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(CardGap),
                        modifier = Modifier.fillMaxWidth().padding(top = CardGap),
                    ) {
                        StatTile("收入", income, MoneyColors.income(), Modifier.weight(1f))
                        StatTile("支出", expense, MoneyColors.expense(), Modifier.weight(1f))
                    }
                }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(CardGap),
                        modifier = Modifier.fillMaxWidth().padding(top = CardGap),
                    ) {
                        TonalButton(
                            "导入账单", { picker.launch(BILL_MIME_TYPES) },
                            Modifier.weight(1f), icon = LedgerIcons.Import, enabled = !parsing,
                        )
                        PrimaryButton(
                            "记一笔", { nav.push(Screen.RecordEdit(null, projectId)) },
                            Modifier.weight(1f), icon = LedgerIcons.Pen,
                        )
                    }
                }
                if (records.isEmpty()) {
                    item { EmptyState("暂无流水", Modifier.padding(top = 20.dp)) }
                }
                for (g in groups) {
                    val first = g.first()
                    item(key = "m" + Dates.monthKey(first.timestamp)) {
                        MonthHeader(Dates.monthLabel(first.timestamp), g.sumOf { it.signedCents })
                    }
                    items(g.size, key = { g[it].id }) { i ->
                        val r = g[i]
                        RecordRow(
                            r,
                            first = i == 0,
                            last = i == g.lastIndex,
                            subtitle = sourceLabel(r.source),
                            leading = { SourceBadge(r) },
                        ) { nav.push(Screen.RecordEdit(r.id, projectId)) }
                    }
                }
            }
            if (parsing) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除项目") },
            text = { Text("确定删除「${project?.name ?: ""}」吗？该项目下的 ${records.size} 条记录将一并删除，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val p = project ?: return@TextButton
                    scope.launch {
                        db.deleteProject(p)
                        IconStore.delete(ctx, p.iconFile)
                        nav.popToHome()
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

private fun displayName(ctx: Context, uri: Uri): String? = runCatching {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()
