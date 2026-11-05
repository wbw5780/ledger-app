package com.ledger.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledger.app.csv.BillParser
import com.ledger.app.csv.ImportRow
import com.ledger.app.csv.PdfBillParser
import com.ledger.app.data.AppDatabase
import com.ledger.app.data.Record
import com.ledger.app.data.RecordType
import com.ledger.app.util.Dates
import com.ledger.app.util.Money
import kotlinx.coroutines.launch

/** 命中关键词：对方+商品名 contains 任一关键词（忽略大小写） */
fun matchesKeywords(row: ImportRow, keywords: List<String>): Boolean {
    val hay = row.counterparty + " " + row.goods
    return keywords.any { it.isNotBlank() && hay.contains(it, ignoreCase = true) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewScreen(db: AppDatabase, nav: Navigator, screen: Screen.ImportPreview) {
    val scope = rememberCoroutineScope()
    val rows = screen.result.rows
    val checked = remember { mutableStateListOf<Boolean>().apply { repeat(rows.size) { add(false) } } }
    var keywords by remember { mutableStateOf<List<String>>(emptyList()) }
    var projectName by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var resultMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(screen.projectId) {
        val p = db.projectDao().get(screen.projectId)
        if (p != null) {
            projectName = p.name
            keywords = p.keywordList()
            rows.forEachIndexed { i, r -> checked[i] = matchesKeywords(r, keywords) }
        }
    }

    var selCount = 0
    var selIncome = 0L
    var selExpense = 0L
    rows.forEachIndexed { i, r ->
        if (checked.getOrElse(i) { false }) {
            selCount++
            if (r.isIncome) selIncome += r.amountCents else selExpense += r.amountCents
        }
    }

    val sourceName = if (screen.result.source == BillParser.ALIPAY) "支付宝" else "微信"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SubTopBar("导入预览", onBack = { nav.pop() }) },
        bottomBar = {
            val p = LocalLedger.current
            Box(
                Modifier.fillMaxWidth().background(p.navBar)
                    .drawBehind { drawLine(p.hairline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                    .navigationBarsPadding().padding(horizontal = PagePadding, vertical = 12.dp),
            ) {
                PrimaryButton(
                    text = if (projectName.isBlank()) "确认导入（$selCount）" else "导入到「$projectName」（$selCount）",
                    enabled = selCount > 0 && !importing,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        importing = true
                        val toInsert = rows.filterIndexed { i, _ -> checked[i] }.map { r ->
                            Record(
                                projectId = screen.projectId,
                                type = if (r.isIncome) RecordType.INCOME else RecordType.EXPENSE,
                                amountCents = r.amountCents,
                                timestamp = r.timestamp,
                                counterparty = r.title,
                                note = r.note,
                                source = r.source,
                                sourceId = r.sourceId,
                            )
                        }
                        scope.launch {
                            val (added, skipped) = db.importRecords(toInsert)
                            importing = false
                            resultMsg = "新增 $added 条，跳过重复 $skipped 条"
                        }
                    },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 4.dp, bottom = 24.dp),
        ) {
            item {
                LedgerCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            screen.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        GoldBadge(sourceName + if (screen.result.encoding == PdfBillParser.ENCODING_LABEL) " PDF" else " CSV")
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "共 ${rows.size} 笔 · 跳过 ${screen.result.skippedLines}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("已选 $selCount 笔", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(2.dp))
                            AmountText(selIncome - selExpense, 30.sp, MoneyColors.of(selIncome - selExpense), fitChars = 13)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiniStat("收入", selIncome, MoneyColors.income(), Modifier.weight(1f))
                        MiniStat("支出", selExpense, MoneyColors.expense(), Modifier.weight(1f))
                    }
                }
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp),
                ) {
                    PillChip("全选", false) { for (i in checked.indices) checked[i] = true }
                    PillChip("全不选", false) { for (i in checked.indices) checked[i] = false }
                    PillChip("按关键词", false) {
                        rows.forEachIndexed { i, r -> checked[i] = matchesKeywords(r, keywords) }
                    }
                }
            }
            items(rows.size) { i ->
                val r = rows[i]
                val on = checked.getOrElse(i) { false }
                GroupedRow(first = i == 0, last = i == rows.lastIndex, onClick = { checked[i] = !checked[i] }) {
                    CheckMark(on)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            r.title.ifBlank { "（无名称）" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            Dates.dateTime(r.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    val signed = if (r.isIncome) r.amountCents else -r.amountCents
                    AmountText(
                        signed, 16.sp,
                        if (on) MoneyColors.of(signed) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }

    resultMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("导入完成") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { resultMsg = null; nav.pop() }) { Text("好的") } },
        )
    }
}

@Composable
private fun MiniStat(label: String, cents: Long, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.09f)).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
        AmountText(cents, 17.sp, color, signed = false, fitChars = 11)
    }
}

/** 圆形勾选标记：选中品牌绿底白勾 */
@Composable
private fun CheckMark(on: Boolean) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (on) cs.primary else Color.Transparent, label = "check")
    Box(
        Modifier.size(26.dp).clip(CircleShape).background(bg)
            .then(if (on) Modifier else Modifier.border(2.dp, cs.outline, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(LedgerIcons.Check, null, tint = cs.onPrimary, modifier = Modifier.size(16.dp))
    }
}
