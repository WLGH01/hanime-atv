package me.hanime.atv.data

import android.content.Context
import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class HanimeRepository(private val client: HanimeClient, private val store: LocalStore) {

    private val url: HttpUrl get() = HanimeClient.BASE_URL.toHttpUrl()

    suspend fun loadHome(): HomePage {
        val page = HtmlParser.parseHome(client.get("/"))
        page.user?.let { store.user = it }
        return page
    }

    /** 按站点 `/search` 的筛选参数拉取列表：query / genre / tags[] / sort / page */
    suspend fun search(filter: SearchFilter, page: Int): SearchPage {
        val params = mutableListOf<Pair<String, String>>()
        if (filter.query.isNotBlank()) params += "query" to filter.query.trim()
        if (!filter.genre.isNullOrBlank()) params += "genre" to filter.genre
        filter.tags.forEach { params += "tags[]" to it }
        params += "sort" to filter.sort.value
        if (page > 1) params += "page" to page.toString()
        return HtmlParser.parseSearch(client.getMulti("/search", params), page)
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

    /**
     * 邮箱 + 密码登录：先取 CSRF token，再提交表单，最后用首页验证会话。
     * 失败时会把站点返回的提示文字带出来，避免只显示"登录失败"。
     */
    suspend fun loginWithPassword(email: String, password: String): LoginResult {
        val loginHtml = runCatching { client.get("/login") }.getOrElse {
            return LoginResult.Failure("无法打开登录页：${it.message ?: "网络异常"}")
        }
        val token = HtmlParser.extractToken(loginHtml)
        val body = runCatching {
            client.postForm(
                "/login",
                linkedMapOf(
                    "_token" to token,
                    "email" to email,
                    "password" to password,
                ),
            )
        }.getOrElse {
            return LoginResult.Failure("提交登录失败：${it.message ?: "网络异常"}")
        }
        val error = HtmlParser.extractErrorMessage(body)
        val user = verifyLogin()
        return if (user != null) {
            LoginResult.Success(user)
        } else {
            LoginResult.Failure(error ?: "登录失败：账号或密码不正确，或站点要求人机验证")
        }
    }

    /** 手动粘贴 cookie（例如从电脑浏览器复制），适合电视上无法完成 Google 授权的情况 */
    suspend fun loginWithCookieString(raw: String): LoginResult {
        val cookies = raw.split(";", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains("=") }
            .mapNotNull { Cookie.parse(url, it) }
        if (cookies.isEmpty()) return LoginResult.Failure("没有解析到有效的 Cookie 键值对")
        client.cookieJar.saveFromResponse(url, cookies)
        val user = verifyLogin()
        return if (user != null) LoginResult.Success(user) else LoginResult.Failure("Cookie 无效或已过期")
    }

    /** 供局域网扫码登录服务使用：登录成功后写回本地用户缓存 */
    fun rememberUser(user: UserInfo) {
        store.user = user
    }

    fun localStore(): LocalStore = store

    fun okHttpClient() = client.httpClient

    companion object {
        fun fromContext(context: Context): HanimeRepository {
            val app = context.applicationContext as me.hanime.atv.HanimeApp
            return app.repository
        }
    }
}
