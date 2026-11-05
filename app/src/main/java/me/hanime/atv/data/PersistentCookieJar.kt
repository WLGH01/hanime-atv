package me.hanime.atv.data

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject

/**
 * 把 Cookie 持久化到 SharedPreferences，保证 App 重启后仍然处于登录状态，
 * 同时供 WebView 登录完成后把 cookie 注入到网络层。
 */
class PersistentCookieJar(context: Context) : CookieJar {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("hanime_cookie_store", Context.MODE_PRIVATE)

    private val lock = Any()
    private val cookies = LinkedHashMap<String, Cookie>()

    init {
        load()
    }

    private fun keyOf(cookie: Cookie): String = "${cookie.name}@${cookie.domain}@${cookie.path}"

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val builder = Cookie.Builder()
                    .name(o.getString("name"))
                    .value(o.getString("value"))
                    .domain(o.getString("domain"))
                    .path(o.getString("path"))
                if (o.optBoolean("secure")) builder.secure()
                if (o.optBoolean("httpOnly")) builder.httpOnly()
                if (o.optBoolean("hostOnly", true)) builder.hostOnlyDomain(o.getString("domain"))
                val c = builder.build()
                cookies[keyOf(c)] = c
            }
        }
    }

    private fun persist() {
        val arr = JSONArray()
        synchronized(lock) {
            val now = System.currentTimeMillis()
            cookies.values.filter { it.expiresAt > now }.forEach { c ->
                arr.put(
                    JSONObject()
                        .put("name", c.name)
                        .put("value", c.value)
                        .put("domain", c.domain)
                        .put("path", c.path)
                        .put("secure", c.secure)
                        .put("httpOnly", c.httpOnly)
                        .put("hostOnly", c.hostOnly)
                        .put("expiresAt", c.expiresAt),
                )
            }
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            cookies.forEach { c ->
                this.cookies[keyOf(c)] = c
                if (c.expiresAt in 1..now) this.cookies.remove(keyOf(c))
            }
        }
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return synchronized(lock) {
            cookies.values.filter { it.matches(url) && it.expiresAt > now }
        }
    }

    fun isEmpty(): Boolean = synchronized(lock) { cookies.isEmpty() }

    fun all(): List<Cookie> = synchronized(lock) { cookies.values.toList() }

    fun clear() {
        synchronized(lock) { cookies.clear() }
        prefs.edit().remove(KEY).apply()
    }

    /** 供 WebView CookieManager 注入使用 */
    fun cookieString(url: HttpUrl): String =
        loadForRequest(url).joinToString("; ") { "${it.name}=${it.value}" }

    fun containsSession(): Boolean = synchronized(lock) {
        cookies.values.any { it.name.contains("session", ignoreCase = true) || it.name == "XSRF-TOKEN" }
    }

    companion object {
        private const val KEY = "cookies_v1"
    }
}
