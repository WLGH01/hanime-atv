package me.hanime.atv.data

import android.content.Context
import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

class HanimeRepository(private val client: HanimeClient, private val store: LocalStore) {

    private val url: HttpUrl get() = HanimeClient.BASE_URL.toHttpUrl()

    suspend fun loadHome(): HomePage {
        val page = HtmlParser.parseHome(client.get("/"))
        page.user?.let { store.user = it }
        return page
    }

    suspend fun search(
        query: String?,
        page: Int,
        genre: String? = null,
        sort: String? = null,
    ): SearchPage {
        val params = linkedMapOf("page" to page.toString())
        if (!query.isNullOrBlank()) params["query"] = query
        if (!genre.isNullOrBlank()) params["genre"] = genre
        if (!sort.isNullOrBlank()) params["sort"] = sort
        return HtmlParser.parseSearch(client.get("/search", params), page)
    }

    suspend fun loadVideo(code: String): VideoDetail =
        HtmlParser.parseVideo(client.get("/watch", mapOf("v" to code)), code)

    suspend fun loadFavorites(userId: String, page: Int): FavoritePage =
        HtmlParser.parseCardList(client.get("/user/$userId/likes", mapOf("page" to page.toString())), page)

    suspend fun loadWatchLater(userId: String, page: Int): FavoritePage =
        HtmlParser.parseCardList(client.get("/user/$userId/saves", mapOf("page" to page.toString())), page)

    /** 把 WebView 里的 cookie 同步到 OkHttp，完成登录态迁移 */
    fun importWebViewCookies(): Boolean {
        val raw = CookieManager.getInstance().getCookie(HanimeClient.BASE_URL) ?: return false
        val cookies = raw.split(";")
            .mapNotNull { part ->
                val trimmed = part.trim()
                if (trimmed.isEmpty()) null else Cookie.parse(url, trimmed)
            }
        if (cookies.isEmpty()) return false
        client.cookieJar.saveFromResponse(url, cookies)
        return true
    }

    /** 用首页的账号入口验证登录状态 */
    suspend fun verifyLogin(): UserInfo? {
        val home = HtmlParser.parseHome(client.get("/"))
        val user = home.user
        if (user != null) {
            store.user = user
        }
        return user
    }

    fun logout() {
        client.cookieJar.clear()
        store.user = null
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }

    fun hasCookies(): Boolean = !client.cookieJar.isEmpty()

    /** 收藏 / 取消收藏（点赞） */
    suspend fun like(code: String, csrf: String?, userId: String?, positive: Boolean) {
        client.postForm(
            "/like",
            mapOf(
                "like-foreign-id" to code,
                "like-status" to if (positive) "" else "1",
                "unlike-status" to if (positive) "1" else "",
                "_token" to (csrf ?: ""),
                "like-user-id" to (userId ?: ""),
                "like-is-positive" to "1",
            ),
        )
    }

    /** 加入稍后再看 */
    suspend fun saveToWatchLater(code: String, csrf: String?, userId: String?, checked: Boolean) {
        client.postForm(
            "/save",
            mapOf(
                "_token" to (csrf ?: ""),
                "input_id" to "0",
                "video_id" to code,
                "is_checked" to checked.toString(),
                "user_id" to (userId ?: ""),
            ),
        )
    }

    fun cachedUser(): UserInfo? = store.user

    /** 邮箱 + 密码登录：先取 CSRF token，再提交表单，最后用首页验证会话 */
    suspend fun loginWithPassword(email: String, password: String): Boolean {
        val token = HtmlParser.extractToken(client.get("/login"))
        client.postForm(
            "/login",
            mapOf(
                "_token" to token,
                "email" to email,
                "password" to password,
            ),
        )
        return verifyLogin() != null
    }

    /** 手动粘贴 cookie（例如从电脑浏览器复制），适用于电视上无法完成 Google 登录的情况 */
    suspend fun loginWithCookieString(raw: String): Boolean {
        val cookies = raw.split(";", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains("=") }
            .mapNotNull { Cookie.parse(url, it) }
        if (cookies.isEmpty()) return false
        client.cookieJar.saveFromResponse(url, cookies)
        return verifyLogin() != null
    }
}


