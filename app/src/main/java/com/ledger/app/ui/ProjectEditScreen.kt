package com.ledger.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ledger.app.data.AppDatabase
import com.ledger.app.data.Project
import com.ledger.app.util.IconStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjectEditScreen(db: AppDatabase, nav: Navigator, projectId: Long?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(projectId == null) }
    var original by remember { mutableStateOf<Project?>(null) }
    var name by remember { mutableStateOf("") }
    val keywords = remember { mutableStateListOf<String>() }
    var keywordInput by remember { mutableStateOf("") }
    /** 新选择的图标（尚未保存） */
    var newIcon by remember { mutableStateOf<Bitmap?>(null) }
    var removeIcon by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(projectId) {
        if (projectId != null) {
            val p = db.projectDao().get(projectId)
            if (p != null) {
                original = p
                name = p.name
                keywords.clear()
                keywords.addAll(p.keywords.split('\n').map { it.trim() }.filter { it.isNotEmpty() })
            }
            loaded = true
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { runCatching { IconStore.fromUri(ctx, uri) }.getOrNull() }
            if (bmp == null) Toast.makeText(ctx, "无法读取该图片", Toast.LENGTH_SHORT).show()
            else { newIcon = bmp; removeIcon = false }
        }
    }

    fun addKeyword() {
        val k = keywordInput.trim()
        if (k.isNotEmpty() && k !in keywords) keywords.add(k)
        keywordInput = ""
    }

    fun save() {
        val n = name.trim()
        if (n.isEmpty()) {
            Toast.makeText(ctx, "请填写项目名称", Toast.LENGTH_SHORT).show(); return
        }
        if (keywordInput.isNotBlank()) addKeyword()
        saving = true
        scope.launch {
            val iconName: String? = withContext(Dispatchers.IO) {
                when {
                    newIcon != null -> IconStore.save(ctx, newIcon!!)
                    removeIcon -> null
                    else -> original?.iconFile
                }
            }
            // 默认关键词 = 项目名
            val kw = keywords.toList().ifEmpty { listOf(n) }.joinToString("\n")
            val o = original
            if (o == null) {
                db.projectDao().insert(Project(name = n, keywords = kw, iconFile = iconName))
            } else {
                db.projectDao().update(o.copy(name = n, keywords = kw, iconFile = iconName))
                if (o.iconFile != iconName) withContext(Dispatchers.IO) { IconStore.delete(ctx, o.iconFile) }
            }
            saving = false
            nav.pop()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SubTopBar(if (projectId == null) "新建项目" else "编辑项目", onBack = { nav.pop() }) {
                RoundIconButton(
                    LedgerIcons.Check, "保存", { save() },
                    tint = MaterialTheme.colorScheme.onPrimary,
                    container = MaterialTheme.colorScheme.primary,
                    enabled = loaded && !saving,
                )
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(start = PagePadding, end = PagePadding, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            // 图标
            LedgerCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val preview: ImageBitmap? = remember(newIcon) { newIcon?.asImageBitmap() }
                    if (preview != null) BitmapOrLetter(preview, name, 76.dp)
                    else ProjectIcon(if (removeIcon) null else original?.iconFile, name.ifBlank { "?" }, 76.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TonalButton("选择已装 App", { showAppPicker = true }, Modifier.fillMaxWidth(), icon = LedgerIcons.Apps, height = 44.dp)
                        TonalButton("上传图片", { imagePicker.launch("image/*") }, Modifier.fillMaxWidth(), icon = LedgerIcons.Image, height = 44.dp)
                    }
                }
                if (newIcon != null || (!removeIcon && original?.iconFile != null)) {
                    TextButton(onClick = { newIcon = null; removeIcon = true }, modifier = Modifier.align(Alignment.End)) {
                        Text("移除图标", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            // 名称 + 关键词
            LedgerCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                Text("项目名称", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("例如：噜噜王国") },
                    singleLine = true,
                    shape = FieldShape,
                    colors = ledgerFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                Text("账单匹配关键词", style = MaterialTheme.typography.titleMedium)
                if (keywords.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        keywords.forEach { k -> KeywordChip(k) { keywords.remove(k) } }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = keywordInput,
                        onValueChange = { keywordInput = it },
                        placeholder = { Text("不填默认用项目名") },
                        singleLine = true,
                        shape = FieldShape,
                        colors = ledgerFieldColors(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addKeyword() }),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    RoundIconButton(
                        LedgerIcons.Plus, "添加", { addKeyword() },
                        tint = LocalLedger.current.onTonal, container = LocalLedger.current.tonal, size = 48.dp,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            PrimaryButton("保存", { save() }, Modifier.fillMaxWidth(), enabled = loaded && !saving)
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onPicked = { app ->
                showAppPicker = false
                scope.launch {
                    val bmp = withContext(Dispatchers.IO) {
                        runCatching { IconStore.drawableToBitmap(ctx.packageManager.getApplicationIcon(app.packageName)) }.getOrNull()
                    }
                    if (bmp != null) { newIcon = bmp; removeIcon = false }
                    name = app.label
                    if (keywords.isEmpty()) keywords.add(app.label)
                }
            },
        )
    }
}

@Composable
private fun KeywordChip(text: String, onRemove: () -> Unit) {
    val p = LocalLedger.current
    Row(
        Modifier.height(34.dp).clip(CircleShape).background(p.tonal).clickable(onClick = onRemove)
            .padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = p.onTonal, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp))
        Spacer(Modifier.width(6.dp))
        Icon(LedgerIcons.Close, "删除关键词", tint = p.onTonal, modifier = Modifier.size(14.dp))
    }
}

data class InstalledApp(val packageName: String, val label: String, val launchable: Boolean)

private fun loadInstalledApps(ctx: Context): List<InstalledApp> {
    val pm = ctx.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val launchable: Set<String> = if (Build.VERSION.SDK_INT >= 33) {
        pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION") pm.queryIntentActivities(launcherIntent, 0)
    }.map { it.activityInfo.packageName }.toSet()
    val apps: List<ApplicationInfo> = if (Build.VERSION.SDK_INT >= 33) {
        pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION") pm.getInstalledApplications(0)
    }
    return apps.filter { it.packageName != ctx.packageName }
        .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString(), it.packageName in launchable) }
        .sortedWith(compareBy({ !it.launchable }, { it.label }))
}

@Composable
private fun AppPickerDialog(onDismiss: () -> Unit, onPicked: (InstalledApp) -> Unit) {
    val ctx = LocalContext.current
    val apps by produceState<List<InstalledApp>?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { loadInstalledApps(ctx) }.getOrDefault(emptyList()) }
    }
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    val filtered = remember(apps, query, showAll) {
        apps.orEmpty().filter { (showAll || it.launchable) }
            .filter { query.isBlank() || it.label.contains(query.trim(), true) || it.packageName.contains(query.trim(), true) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.padding(16.dp).imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)) {
                    Text("选择应用", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    RoundIconButton(LedgerIcons.Close, "关闭", onDismiss, size = 38.dp)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(LedgerIcons.Search, null, Modifier.size(20.dp)) },
                    placeholder = { Text("搜索应用") },
                    singleLine = true,
                    shape = FieldShape,
                    colors = ledgerFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp).clickable { showAll = !showAll },
                ) {
                    Checkbox(
                        checked = showAll,
                        onCheckedChange = { showAll = it },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                    )
                    Text("显示系统组件", style = MaterialTheme.typography.bodyMedium)
                }
                if (apps == null) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (filtered.isEmpty()) {
                    EmptyState("没有找到应用", Modifier.padding(top = 8.dp))
                } else {
                    LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                        items(filtered.size, key = { filtered[it].packageName }) { i ->
                            val app = filtered[i]
                            GroupedRow(first = i == 0, last = i == filtered.lastIndex, onClick = { onPicked(app) }) {
                                AppIcon(app.packageName, app.label)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    app.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(packageName: String, label: String) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                IconStore.drawableToBitmap(ctx.packageManager.getApplicationIcon(packageName)).asImageBitmap()
            }.getOrNull()
        }
    }
    BitmapOrLetter(bmp, label, 40.dp)
}
