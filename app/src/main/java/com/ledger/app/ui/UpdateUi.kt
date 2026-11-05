package com.ledger.app.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.ledger.app.util.IncompleteDownloadException
import com.ledger.app.util.ReleaseInfo
import com.ledger.app.util.UpdateChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 「我的」页检查更新入口的四态（+ 初始态） */
sealed interface CheckState {
    data object Idle : CheckState
    data object Checking : CheckState
    data object UpToDate : CheckState
    data class Found(val release: ReleaseInfo) : CheckState
    data object Failed : CheckState
}

/** 当前弹窗 */
sealed interface UpdateDialog {
    data class Prompt(val release: ReleaseInfo) : UpdateDialog
    data class Downloading(val release: ReleaseInfo, val done: Long, val total: Long) : UpdateDialog
    data class NeedPermission(val release: ReleaseInfo, val file: File) : UpdateDialog
}

/**
 * 更新流程状态机（Activity 级，进程内只持有一个）。
 * 静默检查：任何错误吞掉、无新版不打扰；手动检查：失败 toast + 可重试。
 */
class UpdateController(private val appCtx: Context, private val scope: CoroutineScope) {
    var check by mutableStateOf<CheckState>(CheckState.Idle)
        private set
    var dialog by mutableStateOf<UpdateDialog?>(null)
        private set

    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var startupChecked = false
    /** 跳去「安装未知应用」设置后待安装的文件，onResume 时重试 */
    private var pendingInstall: Pair<ReleaseInfo, File>? = null

    private fun toast(msg: String) = Toast.makeText(appCtx, msg, Toast.LENGTH_SHORT).show()

    /** 启动后静默检查，只做一次 */
    fun startupCheck() {
        if (startupChecked) return
        startupChecked = true
        check(silent = true)
    }

    fun check(silent: Boolean) {
        if (checkJob?.isActive == true) return
        if (!silent) check = CheckState.Checking
        checkJob = scope.launch {
            try {
                val release = UpdateChecker.fetchLatest()
                val local = UpdateChecker.localVersionCode(appCtx)
                if (UpdateChecker.isNewer(release, local)) {
                    check = CheckState.Found(release)
                    if (dialog == null) dialog = UpdateDialog.Prompt(release)
                } else if (!silent) {
                    check = CheckState.UpToDate
                    toast("已是最新版本")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                if (!silent) {
                    check = CheckState.Failed
                    toast("检查更新失败，请检查网络（需能访问 GitHub）")
                }
            }
        }
    }

    /** 「我的」页入口点击 */
    fun onEntryClick() {
        when (val s = check) {
            is CheckState.Found -> if (dialog == null) dialog = UpdateDialog.Prompt(s.release)
            CheckState.Checking -> Unit
            else -> check(silent = false)
        }
    }

    fun dismiss() {
        dialog = null
    }

    fun startDownload(ctx: Context, release: ReleaseInfo) {
        downloadJob?.cancel()
        dialog = UpdateDialog.Downloading(release, 0, release.apkSize)
        downloadJob = scope.launch {
            try {
                val file = UpdateChecker.download(appCtx, release) { done, total ->
                    // IO 线程回调；Compose snapshot 状态可跨线程写
                    dialog = UpdateDialog.Downloading(release, done, total)
                }
                val ok = withContext(Dispatchers.IO) { UpdateChecker.verifyArchive(appCtx, file, release) }
                if (!ok) {
                    file.delete()
                    dialog = null
                    toast("安装包校验失败，请重试")
                    return@launch
                }
                dialog = null
                tryInstall(ctx, release, file)
            } catch (e: CancellationException) {
                UpdateChecker.updateFile(appCtx).delete()
                throw e
            } catch (_: IncompleteDownloadException) {
                dialog = null
                toast("下载不完整，请重试")
            } catch (_: Throwable) {
                dialog = null
                toast("下载失败，请检查网络（需能访问 GitHub）")
            }
        }
    }

    /** 取消：终止下载 + 删除临时文件 */
    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        UpdateChecker.updateFile(appCtx).delete()
        dialog = null
    }

    private fun tryInstall(ctx: Context, release: ReleaseInfo, file: File) {
        if (!UpdateChecker.canInstall(appCtx)) {
            dialog = UpdateDialog.NeedPermission(release, file)
            return
        }
        pendingInstall = null
        runCatching { UpdateChecker.install(ctx, file) }
            .onFailure { toast("无法打开系统安装器") }
    }

    fun openInstallSettings(ctx: Context, release: ReleaseInfo, file: File) {
        pendingInstall = release to file
        dialog = null
        runCatching { ctx.startActivity(UpdateChecker.unknownSourcesSettingsIntent(ctx)) }
            .onFailure { pendingInstall = null; toast("无法打开设置，请手动允许本应用安装未知应用") }
    }

    /** Activity onResume：从设置页返回后重试安装（仍未授权则再次弹说明） */
    fun onResume(ctx: Context) {
        val (release, file) = pendingInstall ?: return
        pendingInstall = null
        if (file.exists()) tryInstall(ctx, release, file)
    }
}

val LocalUpdater = staticCompositionLocalOf<UpdateController?> { null }

// ================================================================ 弹窗

@Composable
fun UpdateDialogs(updater: UpdateController) {
    val ctx = LocalContext.current
    when (val d = updater.dialog) {
        null -> Unit
        is UpdateDialog.Prompt -> AlertDialog(
            onDismissRequest = { updater.dismiss() },
            shape = RoundedCornerShape(28.dp),
            containerColor = LocalLedger.current.card,
            title = { Text("发现新版本 v${d.release.versionName}") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GoldBadge("${UpdateChecker.mb(d.release.apkSize)} MB")
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        d.release.changelog.ifBlank { "（无更新说明）" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            dismissButton = { TextButton(onClick = { updater.dismiss() }) { Text("以后再说") } },
            confirmButton = { TextButton(onClick = { updater.startDownload(ctx, d.release) }) { Text("立即更新") } },
        )
        is UpdateDialog.Downloading -> {
            val frac = if (d.total > 0) (d.done.toFloat() / d.total).coerceIn(0f, 1f) else 0f
            AlertDialog(
                onDismissRequest = { },
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                shape = RoundedCornerShape(28.dp),
                containerColor = LocalLedger.current.card,
                title = { Text("正在下载 v${d.release.versionName}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LinearProgressIndicator(
                            progress = { frac },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = LocalLedger.current.tonal,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                "${(frac * 100).toInt()}%",
                                style = AmountStyle.copy(fontSize = 15.sp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${UpdateChecker.mb(d.done)} / ${UpdateChecker.mb(d.total)} MB",
                                style = AmountStyle.copy(fontSize = 14.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                confirmButton = { },
                dismissButton = { TextButton(onClick = { updater.cancelDownload() }) { Text("取消") } },
            )
        }
        is UpdateDialog.NeedPermission -> AlertDialog(
            onDismissRequest = { updater.dismiss() },
            shape = RoundedCornerShape(28.dp),
            containerColor = LocalLedger.current.card,
            title = { Text("需要安装权限") },
            text = {
                Text(
                    "新版本已下载完成。安装前需要允许「账本」安装未知应用：点「去设置」打开开关后返回，会自动继续安装。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            dismissButton = { TextButton(onClick = { updater.dismiss() }) { Text("取消") } },
            confirmButton = {
                TextButton(onClick = { updater.openInstallSettings(ctx, d.release, d.file) }) { Text("去设置") }
            },
        )
    }
}

// ================================================================ 「我的」页入口

/** 分组卡片：当前版本 + 检查更新（检查中 / 已是最新 / 发现新版 / 失败可重试） */
@Composable
fun UpdateEntryCard(version: String) {
    val updater = LocalUpdater.current
    val p = LocalLedger.current
    val cs = MaterialTheme.colorScheme
    Column {
        GroupedRow(first = true, last = updater == null, onClick = { }) {
            EntryIcon(LedgerIcons.Book)
            Spacer(Modifier.width(14.dp))
            Text("当前版本", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("v$version", style = AmountStyle.copy(fontSize = 15.sp), color = cs.onSurfaceVariant)
        }
        if (updater == null) return@Column
        val state = updater.check
        GroupedRow(first = false, last = true, onClick = { updater.onEntryClick() }) {
            EntryIcon(LedgerIcons.Download)
            Spacer(Modifier.width(14.dp))
            Text("检查更新", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            when (state) {
                CheckState.Idle -> Unit
                CheckState.Checking -> {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = cs.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("检查中…", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                CheckState.UpToDate ->
                    Text("已是最新", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                is CheckState.Found -> GoldBadge("新版 v${state.release.versionName}")
                CheckState.Failed ->
                    Text(
                        "检查失败，点击重试", style = MaterialTheme.typography.bodyMedium, color = p.expense,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
            }
            if (state != CheckState.Checking) {
                Spacer(Modifier.width(6.dp))
                Icon(LedgerIcons.ChevronRight, null, tint = cs.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EntryIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val p = LocalLedger.current
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(p.tonal),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = p.onTonal, modifier = Modifier.size(20.dp)) }
}
