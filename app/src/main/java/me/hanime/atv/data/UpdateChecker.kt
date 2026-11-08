package me.hanime.atv.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * 通过 GitHub Releases 做应用内更新：
 * 1. 拉取 `releases/latest`（CI 以 `v<versionName>` 作为 tag）。
 * 2. 与当前 BuildConfig.VERSION_NAME 做版本比较。
 * 3. 下载 release 资产里的 APK，交给系统安装器打开。
 */
class UpdateChecker(private val httpClient: OkHttpClient) {

    private val client: OkHttpClient by lazy {
        httpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    suspend fun latest(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        val response = client.newCall(request).execute()
        response.use { res ->
            if (!res.isSuccessful) return@withContext null
            val text = res.body?.string().orEmpty()
            if (text.isBlank()) return@withContext null
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return@withContext null
            val tag = json.optString("tag_name").trim()
            val version = tag.removePrefix("v").removePrefix("V")
            if (version.isBlank() || compareVersions(version, currentVersion) <= 0) return@withContext null

            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            var apkSize = 0L
            if (assets != null) {
                val candidates = (0 until assets.length()).mapNotNull { i ->
                    assets.optJSONObject(i)?.takeIf {
                        it.optString("name").endsWith(".apk", ignoreCase = true)
                    }
                }
                // 优先 release 包（debug 包 applicationId 带 .debug，不应覆盖安装）
                val chosen = candidates.firstOrNull {
                    it.optString("name").contains("release", ignoreCase = true)
                } ?: candidates.firstOrNull()
                chosen?.let {
                    apkUrl = it.optString("browser_download_url").ifBlank { null }
                    apkSize = it.optLong("size", 0L)
                }
            }

            UpdateInfo(
                version = version,
                title = json.optString("name").ifBlank { "Hanime ATV v$version" },
                notes = json.optString("body").take(1200).trim(),
                pageUrl = json.optString("html_url"),
                apkUrl = apkUrl,
                apkSize = apkSize,
                publishedAt = json.optString("published_at"),
            )
        }
    }

    companion object {
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/WLGH01/hanime-atv/releases/latest"
        private const val USER_AGENT = "HanimeATV-Update"

        /** 语义化版本比较：返回 >0 表示 a 比 b 新 */
        fun compareVersions(a: String, b: String): Int {
            fun parts(v: String): List<Int> = v.trim().removePrefix("v").removePrefix("V")
                .split('.', '-', '+')
                .map { token -> token.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
            val pa = parts(a)
            val pb = parts(b)
            val size = maxOf(pa.size, pb.size)
            for (i in 0 until size) {
                val x = pa.getOrElse(i) { 0 }
                val y = pb.getOrElse(i) { 0 }
                if (x != y) return x.compareTo(y)
            }
            return 0
        }
    }
}

/** 生成 release APK 的本地文件名 */
fun apkFileName(info: UpdateInfo): String {
    val raw = info.apkUrl?.substringAfterLast('/').orEmpty()
    if (raw.isNotBlank() && raw.endsWith(".apk", ignoreCase = true)) return raw
    return "HanimeATV-v${info.version}-release.apk"
}

object ApkInstaller {

    private fun fileProviderAuthority(context: Context) = "${context.packageName}.fileprovider"

    /** Android 8+ 需要用户允许"安装未知来源应用"，没有权限时先引导去设置 */
    fun canRequestInstall(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        runCatching {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun install(context: Context, apk: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, fileProviderAuthority(context), apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    fun openReleasePage(context: Context, url: String) {
        if (url.isBlank()) return
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        }
    }

    @Suppress("unused")
    private fun hostOf(url: String): String = runCatching { URI(url).host.orEmpty() }.getOrDefault("")
}
