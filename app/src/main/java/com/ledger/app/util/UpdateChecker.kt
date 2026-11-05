package com.ledger.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.coroutineContext

/** GitHub Releases 上的一个版本（只取更新需要的字段） */
data class ReleaseInfo(
    /** tag_name：数字字符串 = versionCode */
    val versionCode: Long,
    /** 展示用版本名（release name 去掉前导 v，如 "1.3"） */
    val versionName: String,
    /** release notes */
    val changelog: String,
    val apkUrl: String,
    /** ledger.apk 资产字节数 */
    val apkSize: Long,
)

/** 下载完成但文件大小与 asset size 不符（文件已删除） */
class IncompleteDownloadException(msg: String) : IOException(msg)

/**
 * 应用内更新：唯一更新源 = GitHub Releases（公开仓库，匿名访问，不带任何 token / Authorization）。
 * 全部走 HTTPS；不引入 OkHttp，用 HttpURLConnection + 协程（Dispatchers.IO）。
 */
object UpdateChecker {
    const val REPO_API_LATEST = "https://api.github.com/repos/wbw5780/ledger-app/releases/latest"
    const val ASSET_NAME = "ledger.apk"
    const val APK_MIME = "application/vnd.android-package-archive"
    private const val TIMEOUT_MS = 10_000
    private const val MAX_REDIRECTS = 5

    // ------------------------------------------------------------ 解析（纯函数，可单测）

    /**
     * 解析 releases/latest 的 JSON。tag_name 不是数字、或没有名为 ledger.apk 的资产时返回 null。
     */
    fun parseRelease(json: String): ReleaseInfo? {
        val o = JSONObject(json)
        val code = o.optString("tag_name").trim().removePrefix("v").toLongOrNull() ?: return null
        val assets = o.optJSONArray("assets") ?: return null
        var url: String? = null
        var size = -1L
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            if (a.optString("name") == ASSET_NAME) {
                url = a.optString("browser_download_url").takeIf { it.isNotBlank() }
                size = a.optLong("size", -1L)
                break
            }
        }
        if (url == null || size <= 0) return null
        val name = o.optString("name").trim().ifEmpty { code.toString() }
        val versionName = name.removePrefix("v").removePrefix("V").trim()
        val body = if (o.isNull("body")) "" else o.optString("body").trim()
        return ReleaseInfo(code, versionName, body, url, size)
    }

    /** 远端 tag 数字严格大于本地 versionCode 才算新版本 */
    fun isNewer(release: ReleaseInfo, localVersionCode: Long): Boolean = release.versionCode > localVersionCode

    /** 字节数 → "3.2"（MB，一位小数） */
    fun mb(bytes: Long): String = String.format(Locale.US, "%.1f", bytes / 1024.0 / 1024.0)

    // ------------------------------------------------------------ 本地版本

    fun localVersionCode(ctx: Context): Long {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            ctx.packageManager.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        }
        return info.longVersionCode
    }

    // ------------------------------------------------------------ 网络

    /** 拉取最新 release；网络/HTTP/解析错误一律抛异常，由调用方决定静默还是提示 */
    suspend fun fetchLatest(): ReleaseInfo = withContext(Dispatchers.IO) {
        val conn = (URL(REPO_API_LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            // GitHub API 要求带 User-Agent
            setRequestProperty("User-Agent", "LedgerApp-Updater")
        }
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("GitHub API HTTP $code")
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseRelease(text) ?: throw IOException("release 中没有可用的 $ASSET_NAME")
        } finally {
            conn.disconnect()
        }
    }

    fun updateFile(ctx: Context): File = File(File(ctx.cacheDir, "updates"), ASSET_NAME)

    /**
     * 流式下载到 cacheDir/updates/ledger.apk。
     * github.com → objects.githubusercontent.com 的 https 跨主机 302 由 HttpURLConnection 自动跟随
     * （同协议重定向）；保险起见这里也手动处理 3xx。不带任何 Authorization 头。
     * 协程取消、网络错误、大小不符时都会删除临时文件。
     * @param onProgress (已下载字节, 总字节)，在 IO 线程回调
     */
    suspend fun download(ctx: Context, release: ReleaseInfo, onProgress: (Long, Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            val file = updateFile(ctx)
            file.parentFile?.mkdirs()
            file.delete()
            try {
                val conn = openFollowingRedirects(release.apkUrl)
                try {
                    val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
                    var done = 0L
                    var lastReport = -1L
                    onProgress(0, total)
                    conn.inputStream.use { input ->
                        file.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                coroutineContext.ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                // 约每 1% 回调一次，避免刷 UI 太频繁
                                if (done - lastReport >= total / 100 || done == total) {
                                    lastReport = done
                                    onProgress(done, total)
                                }
                            }
                        }
                    }
                    onProgress(done, total)
                } finally {
                    conn.disconnect()
                }
                coroutineContext.ensureActive()
                if (file.length() != release.apkSize) {
                    throw IncompleteDownloadException("下载不完整：${file.length()} / ${release.apkSize} 字节")
                }
                file
            } catch (t: Throwable) {
                file.delete()
                throw t
            }
        }

    private fun openFollowingRedirects(start: String): HttpURLConnection {
        var url = URL(start)
        repeat(MAX_REDIRECTS + 1) {
            if (url.protocol != "https") throw IOException("拒绝非 HTTPS 地址")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "LedgerApp-Updater")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) throw IOException("重定向缺少 Location")
                url = URL(url, loc)
                return@repeat
            }
            if (code != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                throw IOException("下载 HTTP $code")
            }
            return conn
        }
        throw IOException("重定向次数过多")
    }

    // ------------------------------------------------------------ 安装

    /** 下载的 APK 是否确实是本应用（包名一致）且版本号符合 release；防止拿到错误文件去安装 */
    fun verifyArchive(ctx: Context, file: File, release: ReleaseInfo): Boolean {
        @Suppress("DEPRECATION")
        val info = ctx.packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: return false
        return info.packageName == ctx.packageName && info.longVersionCode == release.versionCode
    }

    /** Android 8+ 需要用户授予「安装未知应用」 */
    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))

    fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }
}
