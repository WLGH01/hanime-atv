package me.hanime.atv.ui.screen

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import me.hanime.atv.data.HanimeClient
import me.hanime.atv.ui.AppViewModel
import me.hanime.atv.ui.components.TvButton
import me.hanime.atv.ui.components.TvFocusable
import me.hanime.atv.ui.components.TvKeyboard
import me.hanime.atv.ui.theme.Palette

// 登录页是全屏独立页面（没有左侧导航栏），三种登录方式并列成 Tab。
private const val GOOGLE_LOGIN_URL = "https://hanime1.me/auth/google"
private const val EMAIL_LOGIN_URL = "https://hanime1.me/login"

private enum class LoginTab(val label: String) {
    Email("邮箱登录"),
    Google("Google 登录"),
    Cookie("Cookie 导入"),
}

/** 当前正在用屏幕键盘编辑哪个输入框 */
private enum class EditTarget { Email, Password, Cookie }

@Composable
fun LoginScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var tab by remember { mutableStateOf(LoginTab.Email) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var cookie by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    // 键盘输入中的草稿：点“完成”才写回对应字段
    var draft by remember { mutableStateOf("") }

    val tabFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val cookieFocus = remember { FocusRequester() }

    fun focusFor(target: EditTarget): FocusRequester = when (target) {
        EditTarget.Email -> emailFocus
        EditTarget.Password -> passwordFocus
        EditTarget.Cookie -> cookieFocus
    }

    fun closeEditor(target: EditTarget, commit: Boolean) {
        if (commit) {
            when (target) {
                EditTarget.Email -> email = draft
                EditTarget.Password -> password = draft
                EditTarget.Cookie -> cookie = draft
            }
        }
        editing = null
        // 输入框一直在覆盖层下面，关闭后可以立刻把焦点还回去
        runCatching { focusFor(target).requestFocus() }
    }

    // 默认焦点落在“邮箱登录”Tab，符合遥控器从顶部开始操作的习惯
    LaunchedEffect(Unit) {
        delay(220)
        runCatching { tabFocus.requestFocus() }
    }

    BackHandler(enabled = editing != null) {
        editing?.let { closeEditor(it, commit = false) }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 44.dp, vertical = 20.dp),
        ) {
            LoginHeader(vm = vm, backFocus = backFocus, onBack = { vm.goBack() })
            Spacer(Modifier.height(14.dp))
            LoginTabs(selected = tab, tabFocus = tabFocus, onSelect = { tab = it })
            Spacer(Modifier.height(16.dp))

            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (tab) {
                    LoginTab.Email -> EmailLoginTab(
                        email = email,
                        password = password,
                        loginBusy = vm.loginBusy,
                        emailFocus = emailFocus,
                        passwordFocus = passwordFocus,
                        onEditEmail = {
                            draft = email
                            editing = EditTarget.Email
                        },
                        onEditPassword = {
                            draft = password
                            editing = EditTarget.Password
                        },
                        onSubmit = { vm.loginWithPassword(email, password) },
                        onClear = {
                            email = ""
                            password = ""
                        },
                    )

                    LoginTab.Google -> GoogleLoginTab(vm)

                    LoginTab.Cookie -> CookieImportTab(
                        busy = vm.loginBusy,
                        cookie = cookie,
                        cookieFocus = cookieFocus,
                        onEdit = {
                            draft = cookie
                            editing = EditTarget.Cookie
                        },
                        onSubmit = { vm.loginWithCookieString(normalizeCookieInput(cookie)) },
                        onClear = { cookie = "" },
                        onPaste = {
                            val text = readClipboard(context)
                            if (text.isNullOrBlank()) {
                                vm.showToast("剪贴板为空")
                            } else {
                                cookie = text.trim()
                                vm.showToast("已从剪贴板导入 Cookie")
                            }
                        },
                    )
                }
            }
        }

        val current = editing
        if (current != null) {
            val target = current
            KeyboardOverlay(
                title = when (target) {
                    EditTarget.Email -> "输入邮箱地址"
                    EditTarget.Password -> "输入密码"
                    EditTarget.Cookie -> "输入 Cookie"
                },
                value = draft,
                masked = target == EditTarget.Password,
                doneLabel = if (target == EditTarget.Cookie) "保存" else "完成",
                hint = when (target) {
                    EditTarget.Email -> "屏幕键盘支持 A-Z、0-9 与 @ . - _"
                    EditTarget.Password -> "密码以圆点显示，不会明文出现在屏幕上"
                    EditTarget.Cookie -> "Cookie 很长时，建议在电脑上复制后使用“从剪贴板粘贴”"
                },
                onKey = { draft += it },
                onBackspace = { if (draft.isNotEmpty()) draft = draft.dropLast(1) },
                onDone = { closeEditor(target, commit = true) },
                onCancel = { closeEditor(target, commit = false) },
            )
        }
    }
}

// ------------------------------------------------------------------ 顶部：标题 + 登录状态 + 返回按钮

@Composable
private fun LoginHeader(vm: AppViewModel, backFocus: FocusRequester, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "登录 Hanime",
                color = Palette.TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "登录后可同步收藏、稍后再看与观看记录",
                color = Palette.TextTertiary,
                fontSize = 15.sp,
            )
        }

        if (vm.isLoggedIn) {
            Box(
                Modifier
                    .size(9.dp)
                    .background(Palette.Success, RoundedCornerShape(50)),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = vm.user?.name ?: "已登录",
                color = Palette.TextPrimary,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
            Spacer(Modifier.width(16.dp))
            TvButton("退出登录", { vm.logout() })
        } else {
            Text(
                "未登录",
                color = Palette.TextTertiary,
                fontSize = 16.sp,
                modifier = Modifier
                    .background(Palette.Surface, RoundedCornerShape(999.dp))
                    .padding(horizontal = 20.dp, vertical = 9.dp),
            )
        }

        Spacer(Modifier.width(20.dp))
        TvFocusable(
            onClick = onBack,
            focusRequester = backFocus,
            shape = RoundedCornerShape(999.dp),
            focusedScale = 1.06f,
        ) { focused ->
            Text(
                "返回",
                color = if (focused) Palette.TextPrimary else Palette.TextSecondary,
                fontSize = 16.sp,
                modifier = Modifier
                    .background(
                        if (focused) Palette.SurfaceHigh else Palette.Surface,
                        RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 24.dp, vertical = 11.dp),
            )
        }
    }
}

@Composable
private fun LoginTabs(selected: LoginTab, tabFocus: FocusRequester, onSelect: (LoginTab) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LoginTab.entries.forEach { item ->
            val isSelected = item == selected
            TvFocusable(
                onClick = { onSelect(item) },
                shape = RoundedCornerShape(12.dp),
                focusedScale = 1.04f,
                focusRequester = if (item == LoginTab.Email) tabFocus else null,
            ) { focused ->
                Column(
                    modifier = Modifier
                        .background(
                            when {
                                isSelected -> Color(0x33FF5C8A)
                                focused -> Palette.SurfaceHigh
                                else -> Color(0x14FFFFFF)
                            },
                            RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 26.dp, vertical = 11.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        item.label,
                        color = when {
                            isSelected -> Palette.Accent
                            focused -> Palette.TextPrimary
                            else -> Palette.TextSecondary
                        },
                        fontSize = 17.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(
                                if (isSelected) Palette.Accent else Color.Transparent,
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 邮箱登录

@Composable
private fun EmailLoginTab(
    email: String,
    password: String,
    loginBusy: Boolean,
    emailFocus: FocusRequester,
    passwordFocus: FocusRequester,
    onEditEmail: () -> Unit,
    onEditPassword: () -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
        Column(Modifier.weight(1.35f)) {
            LoginInputField(
                label = "邮箱",
                value = email,
                placeholder = "点击后输入邮箱地址",
                masked = false,
                focusRequester = emailFocus,
                onClick = onEditEmail,
            )
            Spacer(Modifier.height(12.dp))
            LoginInputField(
                label = "密码",
                value = password,
                placeholder = "点击后输入密码",
                masked = true,
                focusRequester = passwordFocus,
                onClick = onEditPassword,
            )
            Spacer(Modifier.height(18.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PrimaryAction(
                    label = if (loginBusy) "登录中…" else "登录",
                    busy = loginBusy,
                    enabled = !loginBusy,
                    onClick = onSubmit,
                )
                TvButton("清空", onClear)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "提示：使用电视遥控器或外接键盘输入。密码仅用于向 hanime1.me 提交登录表单，不会保存在本地。",
                color = Palette.TextTertiary,
                fontSize = 15.sp,
                lineHeight = 23.sp,
            )
        }

        Column(Modifier.weight(1f)) {
            InfoCard(
                title = "邮箱登录",
                lines = listOf(
                    "选择“邮箱”或“密码”输入框，屏幕键盘会覆盖在页面上方",
                    "输入完成后按键盘上的“完成”返回表单",
                    "确认无误后选择“登录”，等待站点返回结果",
                    "如果站点触发 Cloudflare 验证，登录可能失败，可改用 Google 或 Cookie 登录",
                ),
            )
        }
    }
}

@Composable
private fun LoginInputField(
    label: String,
    value: String,
    placeholder: String,
    masked: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    TvFocusable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        focusRequester = focusRequester,
        shape = shape,
        focusedScale = 1.02f,
    ) { focused ->
        Column(
            Modifier
                .fillMaxWidth()
                .background(if (focused) Palette.SurfaceHigh else Palette.Surface, shape)
                .padding(horizontal = 20.dp, vertical = 13.dp),
        ) {
            Text(label, color = Palette.TextSecondary, fontSize = 15.sp)
            Spacer(Modifier.height(5.dp))
            val shown = when {
                value.isEmpty() -> placeholder
                masked -> "●".repeat(value.length.coerceAtMost(16))
                else -> value
            }
            Text(
                text = shown,
                color = if (value.isEmpty()) Palette.TextTertiary else Palette.TextPrimary,
                fontSize = 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ------------------------------------------------------------------ Google 登录

@Composable
private fun GoogleLoginTab(vm: AppViewModel) {
    val context = LocalContext.current

    var pageUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var detected by remember { mutableStateOf(false) }

    // WebView 只在 Google 登录 Tab 内创建，离开该 Tab 时销毁，避免内存泄漏
    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#171726"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // 必须是桌面 Chrome UA，否则 Google 会以 disallow_useragent 拒绝授权
            settings.userAgentString = HanimeClient.USER_AGENT
            isFocusable = true
            isFocusableInTouchMode = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    pageUrl = url.orEmpty()
                    loading = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    pageUrl = url.orEmpty()
                    loading = false
                    // 回到 hanime1.me 且不是登录页，说明 Cookie 里已经有会话了
                    if (isHanimeHost(url)) detected = isLoggedInHanimeUrl(url)
                }
            }
            loadUrl(GOOGLE_LOGIN_URL)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val status = when {
                loading -> "正在加载 Google 登录页…"
                detected -> "检测到已登录，请点击下方按钮完成同步"
                else -> "请在网页中完成 Google 授权，然后点击“我已完成登录”"
            }
            Text(
                text = status,
                color = if (!loading && detected) Palette.Success else Palette.TextSecondary,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (pageUrl.isNotBlank()) {
                Text(
                    text = hostOf(pageUrl),
                    color = Palette.TextTertiary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 280.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 全宽 WebView：占满本 Tab 的剩余空间（1080p 电视上约 380dp），
        // 保证屏幕更矮时下方“我已完成登录”等按钮也始终可见（Google 页面本身可滚动）。
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(max = 420.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Palette.SurfaceHigh),
        ) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
        }

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvButton(
                text = "进入登录页",
                onClick = {
                    webView.loadUrl(GOOGLE_LOGIN_URL)
                    // 让 WebView 拿到真实焦点，遥控器/键盘才能操作网页
                    runCatching { webView.requestFocus() }
                },
                primary = true,
            )
            TvButton(
                text = if (vm.loginBusy) "同步中…" else "我已完成登录",
                onClick = { if (!vm.loginBusy) vm.onWebViewLoginFinished(close = true) },
                primary = detected,
            )
            TvButton(
                text = "刷新",
                onClick = { webView.reload() },
            )
            TvButton(
                text = "打开邮箱登录页",
                onClick = { webView.loadUrl(EMAIL_LOGIN_URL) },
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "提示：电视上建议使用外接 USB / 蓝牙键盘完成 Google 授权；如果 Google 以“浏览器不安全”拒绝，请改用邮箱登录或 Cookie 导入。",
            color = Palette.TextTertiary,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )
    }
}

// ------------------------------------------------------------------ Cookie 导入

@Composable
private fun CookieImportTab(
    busy: Boolean,
    cookie: String,
    cookieFocus: FocusRequester,
    onEdit: () -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onPaste: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
        Column(Modifier.weight(1.35f)) {
            TvFocusable(
                onClick = onEdit,
                focusRequester = cookieFocus,
                shape = shape,
                focusedScale = 1.01f,
            ) { focused ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp, max = 210.dp)
                        .background(if (focused) Palette.SurfaceHigh else Palette.Surface, shape)
                        .padding(18.dp),
                ) {
                    Text("Cookie 内容", color = Palette.TextSecondary, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = cookie.ifBlank {
                            "点击这里用遥控器输入，或先在电脑上复制 Cookie 再点“从剪贴板粘贴”"
                        },
                        color = if (cookie.isBlank()) Palette.TextTertiary else Palette.TextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "已输入 ${cookie.length} 个字符",
                        color = Palette.TextTertiary,
                        fontSize = 15.sp,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PrimaryAction(
                    label = if (busy) "导入中…" else "导入登录",
                    busy = busy,
                    enabled = !busy,
                    onClick = onSubmit,
                )
                TvButton("清空", onClear)
                TvButton("从剪贴板粘贴", onPaste)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "说明：在电脑浏览器登录 hanime1.me，打开开发者工具 → Network，选择任意请求，复制请求头里的 Cookie 值（形如 key=value; key=value），粘贴到这里即可。",
                color = Palette.TextTertiary,
                fontSize = 15.sp,
                lineHeight = 23.sp,
            )
        }

        Column(Modifier.weight(1f)) {
            InfoCard(
                title = "Cookie 导入",
                lines = listOf(
                    "适合电视上不方便完成 Google 授权的场景",
                    "Cookie 等同于账号凭证，请勿分享给他人",
                    "导入后 App 会校验首页登录态，成功即自动返回",
                    "如果提示无效，请重新登录电脑端浏览器后复制最新 Cookie",
                ),
            )
        }
    }
}

// ------------------------------------------------------------------ 覆盖层 / 小组件

@Composable
private fun KeyboardOverlay(
    title: String,
    value: String,
    masked: Boolean,
    doneLabel: String,
    hint: String,
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val keyboardFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { keyboardFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(Color(0xE6080910))) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 60.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = Palette.TextSecondary, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    value.isEmpty() -> "等待输入…"
                    masked -> "●".repeat(value.length.coerceAtMost(20))
                    else -> value
                },
                color = if (value.isEmpty()) Palette.TextTertiary else Palette.TextPrimary,
                fontSize = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .background(Palette.Surface, RoundedCornerShape(14.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                hint,
                color = Palette.TextTertiary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            TvKeyboard(
                onKey = onKey,
                onBackspace = onBackspace,
                onDone = onDone,
                doneLabel = doneLabel,
                firstFocusRequester = keyboardFocus,
            )
            Spacer(Modifier.height(14.dp))
            TvButton("取消", onCancel)
        }
    }
}

@Composable
private fun PrimaryAction(
    label: String,
    busy: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    TvFocusable(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        focusedScale = 1.04f,
        enabled = enabled,
    ) { _ ->
        val backgroundModifier = if (busy) {
            Modifier.background(Palette.SurfaceHigh, shape)
        } else {
            Modifier.background(Palette.accentGradient, shape)
        }
        Row(
            Modifier
                .then(backgroundModifier)
                .padding(horizontal = 34.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(17.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                label,
                color = if (enabled) Color.White else Palette.TextSecondary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, lines: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Surface.copy(alpha = 0.7f))
            .padding(22.dp),
    ) {
        Text(title, color = Palette.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        lines.forEach { line ->
            Text("· $line", color = Palette.TextSecondary, fontSize = 15.sp, lineHeight = 23.sp)
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ------------------------------------------------------------------ 工具函数

/** 站点主机判断：hanime1.me 及其子域 */
private fun isHanimeHost(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val host = runCatching { Uri.parse(url).host }.getOrNull() ?: return false
    return host == "hanime1.me" || host.endsWith(".hanime1.me")
}

/**
 * 是否已经回到站点的“非登录页”。
 * 登录成功后 WebView 会从 Google 跳回 hanime1.me 的首页/视频页，
 * 此时 URL 不再包含 /login 或 /auth，说明会话 Cookie 已经写入。
 */
private fun isLoggedInHanimeUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    val host = uri.host ?: return false
    if (host != "hanime1.me" && !host.endsWith(".hanime1.me")) return false
    val path = uri.path.orEmpty()
    return !path.contains("/login") && !path.contains("/auth")
}

private fun hostOf(url: String): String =
    runCatching { Uri.parse(url).host }.getOrNull() ?: url

/** 允许用户直接粘贴整行请求头（Cookie: a=b; c=d），去掉前缀后再交给仓库解析 */
private fun normalizeCookieInput(raw: String): String {
    var text = raw.trim()
    if (text.startsWith("cookie:", ignoreCase = true)) {
        text = text.substring("cookie:".length).trim()
    }
    return text
}

private fun readClipboard(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}
