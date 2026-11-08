package me.hanime.atv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 站点 HTTP 客户端。所有请求都带浏览器 UA / Referer，避免被 Cloudflare 或防盗链拦截。
 */
class HanimeClient(context: Context) {

    val cookieJar = PersistentCookieJar(context)

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
                .header("Referer", BASE_URL + "/")
                .build()
            chain.proceed(req)
        }
        .build()

    suspend fun get(path: String, params: Map<String, String> = emptyMap()): String =
        getMulti(path, params.map { it.key to it.value })

    /** 支持同名多值参数（站点的标签筛选是 `?tags[]=` 数组形式） */
    suspend fun getMulti(path: String, params: List<Pair<String, String>>): String {
        val builder = if (path.startsWith("http")) {
            path.toHttpUrl().newBuilder()
        } else {
            (BASE_URL + "/" + path.trimStart('/')).toHttpUrl().newBuilder()
        }
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return execute(Request.Builder().url(builder.build()).get().build())
    }

    suspend fun postForm(path: String, fields: Map<String, String>): String {
        val builder = FormBody.Builder()
        fields.forEach { (k, v) -> builder.add(k, v) }
        val url = buildUrl(path)
        return execute(
            Request.Builder()
                .url(url)
                .post(builder.build())
                .header("X-Requested-With", "XMLHttpRequest")
                .build(),
        )
    }

    fun buildUrl(path: String, params: Map<String, String> = emptyMap()): HttpUrl {
        val builder = if (path.startsWith("http")) {
            path.toHttpUrl().newBuilder()
        } else {
            (BASE_URL + "/" + path.trimStart('/')).toHttpUrl().newBuilder()
        }
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build()
    }

    /** 直接下载二进制（GitHub Release 的 APK），回调进度 */
    suspend fun download(url: String, target: java.io.File, onProgress: (Long, Long) -> Unit): java.io.File =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).await()
            response.use { res ->
                if (!res.isSuccessful) throw HttpError(res.code, null, "")
                val body = res.body ?: throw IOException("empty body")
                val total = body.contentLength()
                target.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            onProgress(written, if (total > 0) total else written)
                        }
                    }
                }
            }
            target
        }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        val call = httpClient.newCall(request)
        val response = call.await()
        response.use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw HttpError(res.code, res.header("cf-mitigated"), body)
            }
            if (looksLikeCloudflare(body)) {
                throw CloudflareError()
            }
            body
        }
    }

    private fun looksLikeCloudflare(body: String): Boolean =
        body.contains("Just a moment...") && body.contains("cf-challenge")

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
        cont.invokeOnCancellation { runCatching { cancel() } }
    }

    companion object {
        const val BASE_URL = "https://hanime1.me"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Safari/537.36"
    }
}

class HttpError(val code: Int, val cfMitigated: String?, val body: String) :
    IOException("HTTP $code")

class CloudflareError : IOException("Cloudflare challenge")


