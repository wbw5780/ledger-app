package com.ledger.app.ui

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ledger.app.data.AppDatabase
import com.ledger.app.data.Record
import com.ledger.app.data.RecordSource
import com.ledger.app.data.RecordType
import com.ledger.app.util.Dates
import com.ledger.app.util.Money
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordEditScreen(db: AppDatabase, nav: Navigator, recordId: Long?, presetProjectId: Long?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val projects by remember { db.projectDao().observeAll() }.collectAsState(initial = emptyList())

    var original by remember { mutableStateOf<Record?>(null) }
    var loaded by remember { mutableStateOf(recordId == null) }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(RecordType.EXPENSE) }
    var timestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var counterparty by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var projectId by remember { mutableStateOf(presetProjectId) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(recordId) {
        if (recordId != null) {
            db.recordDao().get(recordId)?.let { r ->
                original = r
                amount = Money.toInput(r.amountCents)
                type = r.type
                timestamp = r.timestamp
                counterparty = r.counterparty
                note = r.note
                projectId = r.projectId
            }
            loaded = true
        }
    }
    LaunchedEffect(projects) {
        if (projectId == null && projects.isNotEmpty()) projectId = projects.first().id
    }

    fun save() {
        val cents = Money.parseInput(amount)
        if (cents == null || cents <= 0) {
            Toast.makeText(ctx, "请输入正确的金额（最多两位小数）", Toast.LENGTH_SHORT).show(); return
        }
        val pid = projectId
        if (pid == null || projects.none { it.id == pid }) {
            Toast.makeText(ctx, "请选择所属项目", Toast.LENGTH_SHORT).show(); return
        }
        scope.launch {
            val o = original
            if (o == null) {
                db.recordDao().insert(
                    Record(
                        projectId = pid, type = type, amountCents = cents, timestamp = timestamp,
                        counterparty = counterparty.trim(), note = note.trim(), source = RecordSource.MANUAL,
                    )
                )
            } else {
                db.recordDao().update(
                    o.copy(
                        projectId = pid, type = type, amountCents = cents, timestamp = timestamp,
                        counterparty = counterparty.trim(), note = note.trim(),
                    )
                )
            }
            nav.pop()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SubTopBar(if (recordId == null) "记一笔" else "编辑记录", onBack = { nav.pop() }) {
                if (recordId != null) {
                    RoundIconButton(LedgerIcons.Trash, "删除记录", { confirmDelete = true }, tint = MaterialTheme.colorScheme.error)
                }
                RoundIconButton(
                    LedgerIcons.Check, "保存", { save() },
                    tint = MaterialTheme.colorScheme.onPrimary,
                    container = MaterialTheme.colorScheme.primary,
                    enabled = loaded,
                )
            }
        },
    ) { padding ->
        val typeColor = if (type == RecordType.INCOME) MoneyColors.income() else MoneyColors.expense()
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(start = PagePadding, end = PagePadding, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            // 收支切换 + 金额
            LedgerCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                TypeToggle(type) { type = it }
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("¥", color = typeColor, style = AmountStyle.copy(fontSize = 26.sp), modifier = Modifier.padding(bottom = 4.dp))
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value = amount,
                        onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = AmountStyle.copy(fontSize = 40.sp, color = typeColor),
                        cursorBrush = SolidColor(typeColor),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box {
                                if (amount.isEmpty()) {
                                    Text("0.00", style = AmountStyle.copy(fontSize = 40.sp), color = typeColor.copy(alpha = 0.3f))
                                }
                                inner()
                            }
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(typeColor.copy(alpha = 0.25f)))
                original?.let { o ->
                    if (o.source != RecordSource.MANUAL) {
                        Spacer(Modifier.height(14.dp))
                        GoldBadge(if (o.source == RecordSource.WECHAT) "微信账单导入" else "支付宝账单导入")
                    }
                }
            }
            // 项目 / 时间 / 对方 / 备注
            LedgerCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { menuOpen = it }) {
                    OutlinedTextField(
                        value = projects.firstOrNull { it.id == projectId }?.name ?: "请选择",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("所属项目") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen) },
                        shape = FieldShape,
                        colors = ledgerFieldColors(),
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = LocalLedger.current.card,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        projects.forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, onClick = { projectId = p.id; menuOpen = false })
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PickerPill(LedgerIcons.Calendar, Dates.date(timestamp), Modifier.weight(1.2f)) { showDate = true }
                    PickerPill(LedgerIcons.Clock, Dates.dateTime(timestamp).substringAfter(' '), Modifier.weight(1f)) { showTime = true }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = counterparty,
                    onValueChange = { counterparty = it },
                    label = { Text("对方 / 商品名") },
                    singleLine = true,
                    shape = FieldShape,
                    colors = ledgerFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    shape = FieldShape,
                    colors = ledgerFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(4.dp))
            PrimaryButton("保存", { save() }, Modifier.fillMaxWidth(), enabled = loaded)
        }
    }

    val current = Instant.ofEpochMilli(timestamp).atZone(Dates.zone).toLocalDateTime()
    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = current.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d: LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        timestamp = LocalDateTime.of(d, current.toLocalTime()).atZone(Dates.zone).toInstant().toEpochMilli()
                    }
                    showDate = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("取消") } },
        ) { DatePicker(state = state) }
    }
    if (showTime) {
        val state = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("选择时间") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    timestamp = LocalDateTime.of(current.toLocalDate(), LocalTime.of(state.hour, state.minute))
                        .atZone(Dates.zone).toInstant().toEpochMilli()
                    showTime = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("取消") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除记录") },
            text = { Text("确定删除这条记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val o = original ?: return@TextButton
                    scope.launch { db.recordDao().delete(o); nav.pop() }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

/** 支出 / 收入 切换：药丸分段，选中段用对应收支色 */
@Composable
private fun TypeToggle(type: Int, onChange: (Int) -> Unit) {
    val p = LocalLedger.current
    Row(
        Modifier.fillMaxWidth().height(46.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
    ) {
        listOf(RecordType.EXPENSE to "支出", RecordType.INCOME to "收入").forEach { (t, label) ->
            val sel = type == t
            val c = if (t == RecordType.INCOME) p.income else p.expense
            val bg by animateColorAsState(if (sel) c else Color.Transparent, label = "seg")
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(bg).clickable { onChange(t) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

/** 日期 / 时间选择胶囊 */
@Composable
private fun PickerPill(icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalLedger.current
    Row(
        modifier.height(50.dp).clip(FieldShape).background(p.tonal).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = p.onTonal, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = p.onTonal, style = AmountStyle.copy(fontSize = 15.sp), maxLines = 1)
    }
}
