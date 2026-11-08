package me.hanime.atv.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.hanime.atv.BuildConfig
import me.hanime.atv.HanimeApp
import me.hanime.atv.data.ApkInstaller
import me.hanime.atv.data.CloudflareError
import me.hanime.atv.data.FavoritePage
import me.hanime.atv.data.HanimeCard
import me.hanime.atv.data.HanimeRepository
import me.hanime.atv.data.HistoryItem
import me.hanime.atv.data.HomePage
import me.hanime.atv.data.HttpError
import me.hanime.atv.data.LocalStore
import me.hanime.atv.data.LoginResult
import me.hanime.atv.data.PhoneLoginServer
import me.hanime.atv.data.PhoneLoginState
import me.hanime.atv.data.SearchFilter
import me.hanime.atv.data.SearchPage
import me.hanime.atv.data.SortOption
import me.hanime.atv.data.UpdateChecker
import me.hanime.atv.data.UpdateInfo
import me.hanime.atv.data.UpdateState
import me.hanime.atv.data.UserInfo
import me.hanime.atv.data.VideoDetail
import me.hanime.atv.data.apkFileName
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed interface Screen {
    data object Home : Screen
    data object Discover : Screen
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
        419 -> "登录页已过期（419），请重试"
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
    private val updateChecker = UpdateChecker(app.client.httpClient)

    val versionName: String = BuildConfig.VERSION_NAME

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

    // ------------------------------------------------------------ 浏览 / 筛选

    var filter by mutableStateOf(SearchFilter())
        private set

    var browseState by mutableStateOf<Loadable<SearchPage>>(Loadable.Idle)
        private set

    var browsePage by mutableStateOf(0)
        private set

    var browseLoadingMore by mutableStateOf(false)
        private set

    var searchQuery by mutableStateOf("")
        private set

    var searchHistory by mutableStateOf(store.searchHistory)
        private set

    fun onSearchInput(text: String) {
        searchQuery = text
    }

    fun clearSearchHistory() {
        store.searchHistory = emptyList()
        searchHistory = store.searchHistory
    }

    fun removeSearchHistory(keyword: String) {
        store.searchHistory = store.searchHistory.filterNot { it == keyword }
        searchHistory = store.searchHistory
    }

    /** 直接按关键词搜索，并跳到"分类/结果"页 */
    fun openQuery(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        applyFilter(filter.copy(query = q, genre = null, tags = emptyList()))
        if (screen != Screen.Discover) navigate(Screen.Discover)
    }

    /** 详情页点击标签：切到分类页并只筛这个标签 */
    fun openTag(tag: String) {
        val value = tag.removePrefix("#").trim()
        if (value.isBlank()) return
        applyFilter(filter.copy(query = "", genre = null, tags = listOf(value)))
        if (screen != Screen.Discover) navigate(Screen.Discover)
    }

    /** 首页/搜索里的类型入口 */
    fun openGenre(genre: String?) {
        applyFilter(filter.copy(query = "", tags = emptyList(), genre = genre))
        if (screen != Screen.Discover) navigate(Screen.Discover)
    }

    /**
     * 应用筛选条件。reset=true 表示从第一页重新拉取。
     * 关键词搜索会写入搜索历史。
     */
    fun applyFilter(newFilter: SearchFilter, reset: Boolean = true) {
        val changed = newFilter != filter
        filter = newFilter
        if (newFilter.query.isNotBlank()) {
            store.addSearchHistory(newFilter.query)
            searchHistory = store.searchHistory
            searchQuery = newFilter.query
        }
        if (!reset && !changed && browseState is Loadable.Success) return
        if (newFilter.isDefault && newFilter.query.isBlank() && newFilter.tags.isEmpty()) {
            // 什么都没有选时，默认展示"最新上传"
        }
        browsePage = 0
        browseState = Loadable.Loading
        viewModelScope.launch {
            val page = runCatching { repository.search(newFilter, 1) }
            page.onSuccess {
                browsePage = 1
                browseState = Loadable.Success(it)
            }.onFailure { browseState = Loadable.Failure(it.toUserMessage()) }
        }
    }

    /** 在当前筛选上做增量修改（例如只换排序） */
    fun quickFilter(transform: (SearchFilter) -> SearchFilter) {
        applyFilter(transform(filter))
    }

    fun setSort(sort: SortOption) = quickFilter { it.copy(sort = sort) }

    fun toggleTagFilter(tag: String) {
        val tags = filter.tags.toMutableList()
        if (tags.contains(tag)) tags.remove(tag) else tags.add(tag)
        applyFilter(filter.copy(tags = tags))
    }

    fun clearFilter() = applyFilter(SearchFilter())

    fun browseMore() {
        val current = (browseState as? Loadable.Success)?.data ?: return
        if (!current.hasNext || browseLoadingMore) return
        browseLoadingMore = true
        val nextFilter = filter
        viewModelScope.launch {
            val next = browsePage + 1
            runCatching { repository.search(nextFilter, next) }
                .onSuccess { page ->
                    browsePage = next
                    browseState = Loadable.Success(
                        page.copy(items = (current.items + page.items).distinctBy { it.code }),
                    )
                }
                .onFailure { showToast(it.toUserMessage()) }
            browseLoadingMore = false
        }
    }

    // 兼容旧搜索页的只读别名（新界面直接使用 browseState / filter）
    val searchState: Loadable<SearchPage> get() = browseState
    val searchPage: Int get() = browsePage
    val searchLoadingMore: Boolean get() = browseLoadingMore
    val currentGenre: String? get() = filter.genre

    fun search(query: String = searchQuery, genre: String? = currentGenre, reset: Boolean = true) {
        applyFilter(filter.copy(query = query.trim(), genre = genre), reset = reset)
    }

    fun searchMore() = browseMore()

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
    var loginError by mutableStateOf<String?>(null)
        private set

    val isLoggedIn: Boolean get() = user != null

    fun openLogin() {
        loginError = null
        navigate(Screen.Login)
    }

    /** 登录页打开时自动开始手机扫码配对 */
    fun ensurePhoneServer() {
        if (phoneLogin.active) return
        startPhoneLogin()
    }

    private fun onLoginSuccess(result: LoginResult.Success, close: Boolean = true) {
        user = result.user
        loginError = null
        showToast("登录成功：${result.user.name}")
        homeState = Loadable.Idle
        loadHome()
        if (close) {
            val target = screen
            if (target is Screen.Login) goBack()
        }
        favoritesState = Loadable.Idle
    }

    fun loginWithPassword(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            loginError = "请输入邮箱和密码"
            return
        }
        if (loginBusy) return
        loginBusy = true
        loginError = null
        viewModelScope.launch {
            val result = runCatching { repository.loginWithPassword(email.trim(), password) }
                .getOrElse { LoginResult.Failure(it.toUserMessage()) }
            loginBusy = false
            when (result) {
                is LoginResult.Success -> onLoginSuccess(result)
                is LoginResult.Failure -> loginError = result.message
            }
        }
    }

    fun loginWithCookieString(raw: String) {
        if (raw.isBlank()) {
            loginError = "请粘贴 Cookie"
            return
        }
        if (loginBusy) return
        loginBusy = true
        loginError = null
        viewModelScope.launch {
            val result = runCatching { repository.loginWithCookieString(raw) }
                .getOrElse { LoginResult.Failure(it.toUserMessage()) }
            loginBusy = false
            when (result) {
                is LoginResult.Success -> onLoginSuccess(result)
                is LoginResult.Failure -> loginError = result.message
            }
        }
    }

    /** WebView 登录完成后调用：把 cookie 同步到网络层并验证 */
    fun onWebViewLoginFinished(close: Boolean = false) {
        if (loginBusy) return
        loginBusy = true
        loginError = null
        viewModelScope.launch {
            repository.importWebViewCookies()
            val info = runCatching { repository.verifyLogin() }.getOrNull()
            loginBusy = false
            val cached = info ?: repository.cachedUser()
            if (cached != null) {
                onLoginSuccess(LoginResult.Success(cached), close = close)
            } else {
                loginError = "还没有检测到登录状态，请完成登录后再点此按钮"
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            user = null
            favorites = emptyList()
            favoritesState = Loadable.Idle
            showToast("已退出登录")
        }
    }

    // ------------------------------------------------------------ 手机扫码登录

    var phoneLogin by mutableStateOf(PhoneLoginState())
        private set

    private var phoneServer: PhoneLoginServer? = null

    fun startPhoneLogin() {
        if (phoneLogin.active) return
        phoneLogin = PhoneLoginState(active = true, status = "正在启动局域网服务…")
        viewModelScope.launch {
            val server = withContext(Dispatchers.IO) {
                runCatching { PhoneLoginServer.create(repository) }.getOrNull()
            }
            if (server == null) {
                phoneLogin = PhoneLoginState(
                    active = false,
                    error = "无法启动局域网服务，请检查电视网络后重试",
                    status = "启动失败",
                )
                return@launch
            }
            val url = server.pairingUrl
            if (url == null) {
                runCatching { server.stop() }
                phoneLogin = PhoneLoginState(
                    active = false,
                    error = "没有检测到局域网 IP，请确认电视已连接 Wi-Fi 或网线",
                    status = "未联网",
                )
                return@launch
            }
            server.onStatus = { text ->
                viewModelScope.launch { phoneLogin = phoneLogin.copy(status = text) }
            }
            server.onSuccess = { info ->
                viewModelScope.launch {
                    phoneLogin = phoneLogin.copy(finished = true, status = "登录成功：${info.name}")
                    stopPhoneServer(keepState = true)
                    onLoginSuccess(LoginResult.Success(info))
                }
            }
            phoneServer = server
            phoneLogin = PhoneLoginState(
                active = true,
                url = url,
                status = "请用手机扫描二维码",
            )
        }
    }

    fun cancelPhoneLogin() {
        stopPhoneServer(keepState = false)
        phoneLogin = PhoneLoginState()
    }

    private fun stopPhoneServer(keepState: Boolean) {
        val server = phoneServer
        phoneServer = null
        if (server != null) {
            viewModelScope.launch(Dispatchers.IO) { runCatching { server.stop() } }
        }
        if (!keepState) phoneLogin = PhoneLoginState()
    }

    override fun onCleared() {
        stopPhoneServer(keepState = true)
        super.onCleared()
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
        val id = user?.id ?: run {
            favoritesState = Loadable.Failure("登录后可查看收藏")
            return
        }
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

    // ------------------------------------------------------------ 界面缩放

    var uiScale by mutableStateOf(store.uiScale)
        private set

    fun applyUiScale(value: Float) {
        val clamped = value.coerceIn(0.8f, 1.25f)
        uiScale = clamped
        store.uiScale = clamped
    }

    // ------------------------------------------------------------ 应用内更新

    var updateState by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set
    var showUpdateDialog by mutableStateOf(false)
        private set

    fun maybeAutoCheckUpdate() {
        val last = store.lastUpdateCheck
        if (System.currentTimeMillis() - last < 6 * 60 * 60 * 1000L) return
        if (updateState !is UpdateState.Idle) return
        checkUpdate(silent = true)
    }

    fun checkUpdate(silent: Boolean = false) {
        if (updateState is UpdateState.Checking || updateState is UpdateState.Downloading) return
        updateState = UpdateState.Checking
        viewModelScope.launch {
            val info = runCatching { updateChecker.latest(versionName) }.getOrNull()
            store.lastUpdateCheck = System.currentTimeMillis()
            updateState = when {
                info == null -> {
                    if (!silent) showToast("已是最新版本（$versionName）")
                    UpdateState.Idle
                }
                info.version == store.skippedVersion -> UpdateState.Idle
                else -> UpdateState.Available(info)
            }
            if (info != null && info.version != store.skippedVersion) showUpdateDialog = true
        }
    }

    fun dismissUpdateDialog() {
        showUpdateDialog = false
    }

    fun skipUpdate() {
        val info = (updateState as? UpdateState.Available)?.info
        if (info != null) store.skippedVersion = info.version
        showUpdateDialog = false
        updateState = UpdateState.Idle
    }

    fun openUpdateDialog() {
        if (updateState is UpdateState.Available || updateState is UpdateState.Downloading ||
            updateState is UpdateState.Ready
        ) {
            showUpdateDialog = true
        }
    }

    fun downloadUpdate() {
        val available = updateState as? UpdateState.Available ?: return
        val info = available.info
        val url = info.apkUrl
        if (url.isNullOrBlank()) {
            ApkInstaller.openReleasePage(app, info.pageUrl)
            showUpdateDialog = false
            return
        }
        val target = File(app.cacheDir.resolve("updates"), apkFileName(info))
        viewModelScope.launch {
            updateState = UpdateState.Downloading(info, 0f, 0L)
            var lastPercent = -1
            val result = runCatching {
                app.client.download(url, target) { read, total ->
                    val fraction = if (total > 0) read.toFloat() / total else 0f
                    val percent = (fraction * 100).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        updateState = UpdateState.Downloading(info, fraction.coerceIn(0f, 1f), read)
                    }
                }
            }
            result.onSuccess { file ->
                updateState = UpdateState.Ready(info, file)
                installUpdate(file)
            }.onFailure {
                updateState = UpdateState.Failed(it.toUserMessage())
                showToast("下载失败：${it.toUserMessage()}")
            }
        }
    }

    /** 已有下载好的 APK 时直接拉起系统安装器 */
    fun installUpdate(file: File? = null) {
        val apk = file ?: (updateState as? UpdateState.Ready)?.file
        if (apk == null || !apk.exists()) {
            showToast("安装包不存在，请重新下载")
            return
        }
        if (!ApkInstaller.canRequestInstall(app)) {
            showToast("请先允许 Hanime ATV 安装应用，然后返回重试")
            ApkInstaller.openInstallPermissionSettings(app)
            return
        }
        showUpdateDialog = false
        if (!ApkInstaller.install(app, apk)) {
            showToast("无法启动系统安装器，请到设置→应用→允许安装未知来源后重试")
        }
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

    /** 供只读场景使用 */
    fun updateInfoOrNull(): UpdateInfo? = when (val state = updateState) {
        is UpdateState.Available -> state.info
        is UpdateState.Downloading -> state.info
        is UpdateState.Ready -> state.info
        else -> null
    }
}
