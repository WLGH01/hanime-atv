package me.hanime.atv.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import me.hanime.atv.HanimeApp
import me.hanime.atv.data.CloudflareError
import me.hanime.atv.data.FavoritePage
import me.hanime.atv.data.HanimeCard
import me.hanime.atv.data.HanimeRepository
import me.hanime.atv.data.HistoryItem
import me.hanime.atv.data.HomePage
import me.hanime.atv.data.HttpError
import me.hanime.atv.data.LocalStore
import me.hanime.atv.data.SearchPage
import me.hanime.atv.data.UserInfo
import me.hanime.atv.data.VideoDetail
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Favorites : Screen
    data object History : Screen
    data object Settings : Screen
    data object Login : Screen
    data class Detail(val code: String) : Screen
    data class Player(val code: String) : Screen
}

sealed interface Loadable<out T> {
    data object Idle : Loadable<Nothing>
    data object Loading : Loadable<Nothing>
    data class Success<T>(val data: T) : Loadable<T>
    data class Failure(val message: String) : Loadable<Nothing>
}

fun Throwable.toUserMessage(): String = when (this) {
    is UnknownHostException -> "网络无法解析域名，请检查电视网络或 DNS 设置"
    is SocketTimeoutException -> "连接超时，请检查网络后重试"
    is CloudflareError -> "触发了站点的 Cloudflare 验证，请稍后重试或切换网络"
    is HttpError -> when (code) {
        403 -> "访问被拒绝（403），可能被 Cloudflare 拦截"
        404 -> "内容不存在（404）"
        in 500..599 -> "服务器异常（$code），请稍后再试"
        else -> "请求失败（$code）"
    }
    is IOException -> "网络异常：${message ?: "连接失败"}"
    else -> message ?: "未知错误"
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as HanimeApp
    private val repository: HanimeRepository = app.repository
    private val store: LocalStore = app.store

    // ------------------------------------------------------------ 导航

    var backStack by mutableStateOf(listOf<Screen>(Screen.Home))
        private set

    val screen: Screen get() = backStack.last()

    fun navigate(target: Screen) {
        if (target == screen) return
        backStack = backStack + target
    }

    fun goBack() {
        if (backStack.size > 1) {
            backStack = backStack.dropLast(1)
            if (screen is Screen.Home && homeState !is Loadable.Success) loadHome()
        }
    }

    fun navigateToTopLevel(target: Screen) {
        backStack = if (target == Screen.Home) listOf(Screen.Home) else listOf(Screen.Home, target)
    }

    fun goHome() {
        backStack = listOf(Screen.Home)
        if (homeState !is Loadable.Success) loadHome()
    }

    // ------------------------------------------------------------ 首页

    var homeState by mutableStateOf<Loadable<HomePage>>(Loadable.Idle)
        private set

    fun loadHome(force: Boolean = false) {
        if (!force && homeState is Loadable.Loading) return
        if (!force && homeState is Loadable.Success) {
            refreshUserSilently()
            return
        }
        homeState = Loadable.Loading
        viewModelScope.launch {
            runCatching { repository.loadHome() }
                .onSuccess { page ->
                    homeState = Loadable.Success(page)
                    page.user?.let { user = it }
                }
                .onFailure { homeState = Loadable.Failure(it.toUserMessage()) }
        }
    }

    private fun refreshUserSilently() {
        viewModelScope.launch {
            runCatching { repository.verifyLogin() }.onSuccess { user = it ?: user }
        }
    }

    // ------------------------------------------------------------ 搜索

    var searchQuery by mutableStateOf("")
        private set
    var searchState by mutableStateOf<Loadable<SearchPage>>(Loadable.Idle)
        private set
    var searchPage by mutableStateOf(0)
        private set
    var searchLoadingMore by mutableStateOf(false)
        private set
    var currentGenre by mutableStateOf<String?>(null)
        private set
    var searchHistory by mutableStateOf(store.searchHistory)
        private set

    fun onSearchInput(text: String) {
        searchQuery = text
    }

    fun search(query: String = searchQuery, genre: String? = currentGenre, reset: Boolean = true) {
        val q = query.trim()
        if (q.isBlank() && genre.isNullOrBlank()) return
        searchQuery = q
        currentGenre = genre
        if (q.isNotBlank()) {
            store.addSearchHistory(q)
            searchHistory = store.searchHistory
        }
        if (reset) {
            searchPage = 0
            searchState = Loadable.Loading
        }
        viewModelScope.launch {
            runCatching { repository.search(q.ifBlank { null }, 1, genre) }
                .onSuccess { page ->
                    searchPage = 1
                    searchState = Loadable.Success(page)
                }
                .onFailure { searchState = Loadable.Failure(it.toUserMessage()) }
        }
    }

    fun searchMore() {
        val current = (searchState as? Loadable.Success)?.data ?: return
        if (!current.hasNext || searchLoadingMore) return
        searchLoadingMore = true
        viewModelScope.launch {
            val next = searchPage + 1
            runCatching { repository.search(searchQuery.ifBlank { null }, next, currentGenre) }
                .onSuccess { page ->
                    searchPage = next
                    searchState = Loadable.Success(
                        page.copy(items = (current.items + page.items).distinctBy { it.code }),
                    )
                }
                .onFailure { showToast(it.toUserMessage()) }
            searchLoadingMore = false
        }
    }

    // ------------------------------------------------------------ 详情

    var detailState by mutableStateOf<Loadable<VideoDetail>>(Loadable.Idle)
        private set
    var detailCode by mutableStateOf<String?>(null)
        private set

    fun openDetail(code: String) {
        navigate(Screen.Detail(code))
        loadDetail(code)
    }

    fun loadDetail(code: String, force: Boolean = false) {
        if (!force && detailCode == code && detailState is Loadable.Success) return
        detailCode = code
        detailState = Loadable.Loading
        viewModelScope.launch {
            runCatching { repository.loadVideo(code) }
                .onSuccess { detailState = Loadable.Success(it) }
                .onFailure { detailState = Loadable.Failure(it.toUserMessage()) }
        }
    }

    fun retryDetail() {
        detailCode?.let { loadDetail(it, force = true) }
    }

    // ------------------------------------------------------------ 播放

    var playingVideo by mutableStateOf<VideoDetail?>(null)
        private set

    fun play(video: VideoDetail) {
        if (video.sources.isEmpty()) {
            showToast("没有解析到可播放的视频地址")
            return
        }
        playingVideo = video
        navigate(Screen.Player(video.code))
    }

    fun playCode(code: String) {
        val cached = (detailState as? Loadable.Success)?.data?.takeIf { it.code == code }
        if (cached != null) {
            play(cached)
        } else {
            viewModelScope.launch {
                runCatching { repository.loadVideo(code) }
                    .onSuccess { play(it) }
                    .onFailure { showToast(it.toUserMessage()) }
            }
        }
    }

    fun saveProgress(video: VideoDetail, positionMs: Long, durationMs: Long) {
        if (positionMs <= 5_000L) return
        store.upsertHistory(
            HistoryItem(
                code = video.code,
                title = video.title,
                cover = video.cover,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        history = store.history()
    }

    fun resumePosition(code: String): Long = store.historyFor(code)?.positionMs ?: 0L

    // ------------------------------------------------------------ 账号

    var user by mutableStateOf<UserInfo?>(store.user)
        private set
    var loginBusy by mutableStateOf(false)
        private set

    val isLoggedIn: Boolean get() = user != null

    fun openLogin() = navigate(Screen.Login)

    fun loginWithPassword(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            showToast("请输入邮箱和密码")
            return
        }
        loginBusy = true
        viewModelScope.launch {
            val ok = runCatching { repository.loginWithPassword(email.trim(), password) }
                .onFailure { showToast(it.toUserMessage()) }
                .getOrDefault(false)
            loginBusy = false
            if (ok) {
                user = repository.cachedUser()
                showToast("登录成功")
                homeState = Loadable.Idle
                loadHome()
                goBack()
            } else {
                showToast("登录失败，请检查账号密码")
            }
        }
    }

    fun loginWithCookieString(raw: String) {
        if (raw.isBlank()) {
            showToast("请粘贴 Cookie")
            return
        }
        loginBusy = true
        viewModelScope.launch {
            val ok = runCatching { repository.loginWithCookieString(raw) }
                .onFailure { showToast(it.toUserMessage()) }
                .getOrDefault(false)
            loginBusy = false
            if (ok) {
                user = repository.cachedUser()
                showToast("登录成功")
                homeState = Loadable.Idle
                loadHome()
                goBack()
            } else {
                showToast("Cookie 无效或已过期")
            }
        }
    }

    /** WebView 登录完成后调用：把 cookie 同步到网络层并验证 */
    fun onWebViewLoginFinished(close: Boolean = false) {
        loginBusy = true
        viewModelScope.launch {
            repository.importWebViewCookies()
            val info = runCatching { repository.verifyLogin() }.getOrNull()
            user = info ?: repository.cachedUser()
            loginBusy = false
            if (user != null) {
                showToast("登录成功：${user?.name ?: ""}")
                homeState = Loadable.Idle
                loadHome()
                if (close) goBack()
            } else {
                showToast("还没有检测到登录状态，请完成登录后再点此按钮")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            user = null
            favorites = emptyList()
            showToast("已退出登录")
        }
    }


    /** 收藏（点赞）当前视频 */
    fun toggleFavorite(video: VideoDetail) {
        if (!isLoggedIn) {
            openLogin()
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.like(video.code, video.csrf, user?.id, positive = true)
            }
                .onSuccess { showToast("已收藏") }
                .onFailure { showToast(it.toUserMessage()) }
        }
    }

    /** 加入稍后再看 */
    fun addToWatchLater(video: VideoDetail) {
        if (!isLoggedIn) {
            openLogin()
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.saveToWatchLater(video.code, video.csrf, user?.id, checked = true)
            }
                .onSuccess { showToast("已加入稍后再看") }
                .onFailure { showToast(it.toUserMessage()) }
        }
    }
    // ------------------------------------------------------------ 收藏 / 列表

    var favoritesState by mutableStateOf<Loadable<FavoritePage>>(Loadable.Idle)
        private set
    var favorites by mutableStateOf<List<HanimeCard>>(emptyList())
        private set
    var favoritePage by mutableStateOf(0)
        private set

    fun loadFavorites(force: Boolean = false) {
        val id = user?.id ?: run { openLogin(); return }
        if (!force && favoritesState is Loadable.Success) return
        favoritesState = Loadable.Loading
        viewModelScope.launch {
            runCatching { repository.loadFavorites(id, 1) }
                .onSuccess {
                    favoritePage = 1
                    favorites = it.items
                    favoritesState = Loadable.Success(it)
                }
                .onFailure { favoritesState = Loadable.Failure(it.toUserMessage()) }
        }
    }

    fun favoritesMore() {
        val data = (favoritesState as? Loadable.Success)?.data ?: return
        if (!data.hasNext) return
        val id = user?.id ?: return
        viewModelScope.launch {
            runCatching { repository.loadFavorites(id, favoritePage + 1) }
                .onSuccess {
                    favoritePage += 1
                    favorites = (favorites + it.items).distinctBy { c -> c.code }
                    favoritesState = Loadable.Success(it.copy(items = favorites))
                }
        }
    }

    // ------------------------------------------------------------ 历史

    var history by mutableStateOf(store.history())
        private set

    fun removeHistory(code: String) {
        store.removeHistory(code)
        history = store.history()
    }

    fun clearHistory() {
        store.clearHistory()
        history = emptyList()
    }

    // ------------------------------------------------------------ 提示

    var toast by mutableStateOf<String?>(null)
        private set

    fun showToast(message: String) {
        toast = message
    }

    fun consumeToast() {
        toast = null
    }
}



