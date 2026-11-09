package me.hanime.atv.ui.screen

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
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
import androidx.compose.ui.text.style.TextAlign
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
import me.hanime.atv.ui.login.QrCodeImage
import me.hanime.atv.ui.theme.Palette

// 登录页是全屏独立页面（没有左侧导航栏）。
// 主路径是"手机扫码"：电视只负责显示二维码，账号密码/Google 授权都在手机上完成，
// 彻底绕开电视屏幕键盘打不出大写符号、WebView 被 Google 判"浏览器不安全"这两个坑。
private const val GOOGLE_LOGIN_URL = "https://hanime1.me/auth/google"

private enum class LoginTab(val label: String) {
    Scan("扫码登录"),
    Email("邮箱登录"),
    Cookie("Cookie 导入"),
    Google("Google (TV)"),
}

/** 当前正在用屏幕键盘编辑哪个输入框 */
private enum class EditTarget { Email, Password, Cookie }

@Composable
fun LoginScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var tab by remember { mutableStateOf(LoginTab.Scan) }
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
    val scanActionFocus = remember { FocusRequester() }

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

    // 进入登录页即启动局域网配对服务；VM 内部会判断是否已启动，重复调用无副作用
    LaunchedEffect(Unit) {
        vm.ensurePhoneServer()
    }

    // 默认焦点落在“扫码登录”Tab，符合遥控器从顶部开始操作的习惯
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
                .padding(horizontal = 36.dp, vertical = 16.dp),
        ) {
            LoginHeader(vm = vm, backFocus = backFocus, onBack = { vm.goBack() })
            Spacer(Modifier.height(10.dp))
            LoginTabs(selected = tab, tabFocus = tabFocus, onSelect = { tab = it })
            Spacer(Modifier.height(12.dp))

            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (tab) {
                    LoginTab.Scan -> ScanLoginTab(vm, scanActionFocus)

                    LoginTab.Email -> EmailLoginTab(
                        vm = vm,
                        email = email,
                        password = password,
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
                        onClear = {
                            email = ""
                            password = ""
                        },
                    )

                    LoginTab.Cookie -> CookieImportTab(
                        vm = vm,
                        cookie = cookie,
                        cookieFocus = cookieFocus,
                        onEdit = {
                            draft = cookie
                            editing = EditTarget.Cookie
                        },
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

                    LoginTab.Google -> GoogleLoginTab(vm, onUseScan = { tab = LoginTab.Scan })
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
                    EditTarget.Email -> "Shift 切换大写，?123 切到符号键盘"
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
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "推荐手机扫码登录：手机上输入，电视无需打字",
                color = Palette.TextTertiary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            Modifier
                .size(8.dp)
                .background(
                    if (vm.isLoggedIn) Palette.Success else Palette.TextTertiary,
                    RoundedCornerShape(50),
                ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (vm.isLoggedIn) (vm.user?.name ?: "已登录") else "未登录",
            color = if (vm.isLoggedIn) Palette.TextPrimary else Palette.TextTertiary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp),
        )

        if (vm.isLoggedIn) {
            Spacer(Modifier.width(14.dp))
            TvButton("退出登录", { vm.logout() })
        }

        Spacer(Modifier.width(14.dp))
        TvFocusable(
            onClick = onBack,
            focusRequester = backFocus,
            shape = RoundedCornerShape(999.dp),
            focusedScale = 1.06f,
        ) { focused ->
            Text(
                "返回",
                color = if (focused) Palette.TextPrimary else Palette.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .background(
                        if (focused) Palette.SurfaceHigh else Palette.Surface,
                        RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 20.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun LoginTabs(selected: LoginTab, tabFocus: FocusRequester, onSelect: (LoginTab) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LoginTab.entries.forEachIndexed { index, item ->
            val isSelected = item == selected
            TvFocusable(
                onClick = { onSelect(item) },
                shape = RoundedCornerShape(10.dp),
                focusedScale = 1.03f,
                focusRequester = if (index == 0) tabFocus else null,
            ) { focused ->
                Column(
                    modifier = Modifier
                        .background(
                            when {
                                isSelected -> Color(0x33FF5C8A)
                                focused -> Palette.SurfaceHigh
                                else -> Color(0x14FFFFFF)
                            },
                            RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        item.label,
                        color = when {
                            isSelected -> Palette.Accent
                            focused -> Palette.TextPrimary
                            else -> Palette.TextSecondary
                        },
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
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

// ------------------------------------------------------------------ 扫码登录（主路径）

@Composable
private fun ScanLoginTab(vm: AppViewModel, actionFocus: FocusRequester) {
    val state = vm.phoneLogin
    val url = state.url
    val statusColor = when {
        state.finished -> Palette.Success
        state.error != null -> Palette.Danger
        state.active -> Palette.Accent
        else -> Palette.TextTertiary
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        Column(
            Modifier.width(264.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(212.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Palette.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (url != null) {
                    QrCodeImage(content = url, size = 200.dp)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (state.error == null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Palette.Accent,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            text = if (state.error == null) "正在启动…" else "服务未启动",
                            color = Palette.TextTertiary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            if (url != null) {
                Text(
                    "配对码 ${pairingCodeOf(url)}",
                    color = Palette.Accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                "手机与电视需在同一 Wi-Fi",
                color = Palette.TextTertiary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
            if (url != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    hostOf(url),
                    color = Palette.TextTertiary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(Modifier.weight(1f)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Palette.Surface.copy(alpha = 0.8f))
                    .padding(horizontal = 13.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(statusColor, RoundedCornerShape(50)))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = state.status,
                    color = statusColor,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(10.dp))
            Text("操作步骤", color = Palette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            StepLine("1", "用手机相机或微信扫描左侧二维码")
            StepLine("2", "在手机上输入站点账号密码（Google 账号请按页内说明）")
            StepLine("3", "电视自动同步登录状态，无需在电视上打字")
            Spacer(Modifier.height(4.dp))

            val errorText = state.error ?: vm.loginError
            if (errorText != null) {
                Text(
                    errorText,
                    color = Palette.Danger,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    "二维码和配对码只在本机有效，登录成功后服务自动关闭。",
                    color = Palette.TextTertiary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                )
            }

            Spacer(Modifier.weight(1f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvButton(
                    text = if (state.active) "刷新二维码" else "重试启动",
                    onClick = {
                        // 重新扫码必须换一次配对码：先关旧服务，再启动新服务
                        vm.cancelPhoneLogin()
                        vm.startPhoneLogin()
                    },
                    primary = true,
                    focusRequester = actionFocus,
                )
                if (state.active) {
                    TvButton("停止服务", onClick = { vm.cancelPhoneLogin() })
                }
            }
        }
    }
}

@Composable
private fun StepLine(index: String, text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(18.dp)
                .background(Palette.SurfaceHigh, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Text(index, color = Palette.Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = Palette.TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.height(6.dp))
}

// ------------------------------------------------------------------ 邮箱登录

@Composable
private fun EmailLoginTab(
    vm: AppViewModel,
    email: String,
    password: String,
    emailFocus: FocusRequester,
    passwordFocus: FocusRequester,
    onEditEmail: () -> Unit,
    onEditPassword: () -> Unit,
    onClear: () -> Unit,
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        Column(Modifier.weight(1.25f)) {
            LoginInputField(
                label = "邮箱",
                value = email,
                placeholder = "点击后用屏幕键盘输入邮箱地址",
                masked = false,
                focusRequester = emailFocus,
                onClick = onEditEmail,
            )
            Spacer(Modifier.height(8.dp))
            LoginInputField(
                label = "密码",
                value = password,
                placeholder = "点击后用屏幕键盘输入密码",
                masked = true,
                focusRequester = passwordFocus,
                onClick = onEditPassword,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PrimaryAction(
                    label = if (vm.loginBusy) "登录中…" else "登录",
                    busy = vm.loginBusy,
                    enabled = !vm.loginBusy,
                    onClick = { vm.loginWithPassword(email, password) },
                )
                TvButton("清空", onClear)
                val error = vm.loginError
                if (error != null) {
                    Text(
                        error,
                        color = Palette.Danger,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "提示：屏幕键盘已支持大写字母与符号（大写 / ?123），密码以圆点掩码显示；登录成功后自动返回。",
                color = Palette.TextTertiary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }

        Column(Modifier.weight(0.85f)) {
            InfoCard(
                title = "邮箱登录",
                lines = listOf(
                    "选择“邮箱”或“密码”输入框，屏幕键盘会覆盖在页面上方",
                    "Shift 切换大小写，?123 切到符号键盘",
                    "输入完成后按键盘上的“完成”返回表单",
                    "若站点触发 Cloudflare 验证，建议改用扫码登录",
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
    val shape = RoundedCornerShape(12.dp)
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
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(label, color = Palette.TextSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            val shown = when {
                value.isEmpty() -> placeholder
                masked -> "●".repeat(value.length.coerceAtMost(16))
                else -> value
            }
            Text(
                text = shown,
                color = if (value.isEmpty()) Palette.TextTertiary else Palette.TextPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ------------------------------------------------------------------ Cookie 导入

@Composable
private fun CookieImportTab(
    vm: AppViewModel,
    cookie: String,
    cookieFocus: FocusRequester,
    onEdit: () -> Unit,
    onClear: () -> Unit,
    onPaste: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        Column(Modifier.weight(1.25f)) {
            TvFocusable(
                onClick = onEdit,
                focusRequester = cookieFocus,
                shape = shape,
                focusedScale = 1.01f,
            ) { focused ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 140.dp)
                        .background(if (focused) Palette.SurfaceHigh else Palette.Surface, shape)
                        .padding(14.dp),
                ) {
                    Text("Cookie 内容", color = Palette.TextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = cookie.ifBlank {
                            "点击这里用遥控器输入，或先在电脑上复制 Cookie 再点“从剪贴板粘贴”"
                        },
                        color = if (cookie.isBlank()) Palette.TextTertiary else Palette.TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "已输入 ${cookie.length} 个字符",
                        color = Palette.TextTertiary,
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PrimaryAction(
                    label = if (vm.loginBusy) "导入中…" else "导入登录",
                    busy = vm.loginBusy,
                    enabled = !vm.loginBusy,
                    onClick = { vm.loginWithCookieString(normalizeCookieInput(cookie)) },
                )
                TvButton("从剪贴板粘贴", onPaste)
                TvButton("清空", onClear)
                val error = vm.loginError
                if (error != null) {
                    Text(
                        error,
                        color = Palette.Danger,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "说明：在电脑浏览器登录 hanime1.me，按 F12 → Network → 任意请求，复制请求头里的 Cookie 值（形如 key=value; key=value），粘贴到这里即可。",
                color = Palette.TextTertiary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(Modifier.weight(0.85f)) {
            InfoCard(
                title = "Cookie 导入",
                lines = listOf(
                    "适合电视上不方便完成 Google 授权的场景",
                    "Cookie 等同于账号凭证，请勿分享给他人",
                    "导入后 App 会校验登录态，成功即自动返回",
                    "提示无效时请重新登录电脑端后复制最新 Cookie",
                ),
            )
        }
    }
}

// ------------------------------------------------------------------ Google 登录（TV 内置 WebView）

@Composable
private fun GoogleLoginTab(vm: AppViewModel, onUseScan: () -> Unit) {
    val context = LocalContext.current

    var pageUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    // 已停在 hanime1.me 的非登录页，说明站点会话 Cookie 已经写进 WebView
    var detected by remember { mutableStateOf(false) }
    // 自动导入只触发一次；导入结束后由 vm.loginBusy 复位，允许下次页面加载重试
    var importing by remember { mutableStateOf(false) }
    // Google/站点返回的 HTTP 状态，用来解释"404/403"到底是哪一步
    var httpProblem by remember { mutableStateOf<String?>(null) }

    // WebView 只在 Google 登录 Tab 内创建，离开该 Tab 时销毁，避免内存泄漏。
    // 注意：不要自动加载 /auth/google —— Google 会拒绝 WebView，直接打开只会让
    // 用户看到错误页；这里等用户明确点击后再加载，并显示真实 HTTP 状态。
    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#171726"))
            settings.javaScriptEnabled = true
            // Google 的授权流程依赖 localStorage/sessionStorage、数据库和弹窗式 JS
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            // 站点资源混用 http/https 时不拦截，否则登录后跳转可能白屏
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // 必须是桌面 Chrome UA，否则 Google 会以 disallow_useragent 拒绝授权
            settings.userAgentString = HanimeClient.USER_AGENT
            isFocusable = true
            isFocusableInTouchMode = true
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)
            // Google 的登录页会用到 JS 对话框/多窗口，没有 WebChromeClient 会卡死在空白页
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    pageUrl = url.orEmpty()
                    loading = true
                    httpProblem = null
                    // 又回到登录页/授权页，说明用户开始了新一轮登录，允许再次自动导入
                    if (!isLoggedInHanimeUrl(url)) {
                        detected = false
                        importing = false
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    pageUrl = url.orEmpty()
                    loading = false
                    // 只有"hanime1.me 且不是 /login、/auth"才算登录成功
                    if (!isHanimeHost(url) || !isLoggedInHanimeUrl(url)) {
                        detected = false
                        return
                    }
                    detected = true
                    // 只自动导入一次；vm.loginBusy 覆盖用户在别处同时触发的导入
                    if (importing || vm.loginBusy) return
                    importing = true
                    // 先把 CookieManager 落盘，再让 OkHttp 侧读取，否则可能读到旧值
                    runCatching { CookieManager.getInstance().flush() }
                    view?.postDelayed({
                        if (vm.loginBusy) {
                            importing = false
                        } else {
                            vm.onWebViewLoginFinished(close = true)
                        }
                    }, 300)
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                    errorResponse: android.webkit.WebResourceResponse?,
                ) {
                    // 只看主文档，避免 favicon/统计脚本的 4xx 干扰状态显示
                    if (request?.isForMainFrame != true) return
                    val code = errorResponse?.statusCode ?: return
                    if (code >= 400) {
                        httpProblem = "HTTP $code：${request?.url}"
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true) {
                        httpProblem = "加载失败：${error?.description ?: "未知错误"}"
                    }
                }
            }
        }
    }

    // 导入结束（无论成功还是失败）后允许重试；成功时页面会被 VM 关闭
    LaunchedEffect(vm.loginBusy, vm.isLoggedIn) {
        if (!vm.loginBusy && importing) importing = false
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
        Text(
            "在电视的 WebView 里完成 Google 授权：本页用桌面 Chrome UA 打开站点登录页，回到 hanime1.me 首页后会自动读取 Cookie 并同步回 App。若 Google 提示“浏览器不安全”，请改用手机扫码（账号密码）或 Cookie 导入。",
            color = Palette.AccentSoft,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            maxLines = 3,
        )
        Spacer(Modifier.height(6.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val googleRejected = looksLikeGoogleRejection(pageUrl, httpProblem)
            val status = when {
                importing -> "正在导入登录态…"
                loading -> "正在加载…"
                googleRejected -> "Google 不允许在 WebView 中授权，请改用手机扫码或 Cookie 导入"
                httpProblem != null -> httpProblem.orEmpty()
                vm.isLoggedIn -> "已登录"
                detected -> "检测到登录态，正在导入…（若没有反应请点“我已完成登录”）"
                pageUrl.isBlank() -> "尚未加载任何页面"
                else -> "在网页中完成 Google 授权；回到站点后会自动导入"
            }
            Text(
                text = status,
                color = when {
                    googleRejected || httpProblem != null -> Palette.Danger
                    importing -> Palette.Accent
                    detected || vm.isLoggedIn -> Palette.Success
                    else -> Palette.TextSecondary
                },
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (pageUrl.isNotBlank()) {
                Text(
                    text = hostOf(pageUrl),
                    color = Palette.TextTertiary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 240.dp),
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // weight(1f) 吃掉剩余高度，再兜一个 300dp 上限：矮屏时按钮始终可见
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(max = 300.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Palette.SurfaceHigh),
        ) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
        }

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TvButton(
                text = "进入登录页",
                onClick = {
                    httpProblem = null
                    detected = false
                    importing = false
                    webView.loadUrl(GOOGLE_LOGIN_URL)
                    // 让 WebView 拿到真实焦点，遥控器/键盘才能操作网页
                    runCatching { webView.requestFocus() }
                },
                primary = true,
            )
            TvButton(
                text = "用手机扫码登录",
                onClick = {
                    onUseScan()
                    vm.showToast("用手机扫码后输入站点账号密码；Google 授权请在本页完成")
                },
                primary = true,
            )
            TvButton(
                text = when {
                    vm.loginBusy -> "同步中…"
                    importing -> "正在导入…"
                    else -> "我已完成登录"
                },
                onClick = {
                    if (!vm.loginBusy) {
                        importing = true
                        // 手动兜底也要先把 WebView 的 Cookie 落盘再导入
                        runCatching { CookieManager.getInstance().flush() }
                        vm.onWebViewLoginFinished(close = true)
                    }
                },
                primary = detected || vm.isLoggedIn,
            )
            TvButton(
                text = "刷新",
                onClick = {
                    httpProblem = null
                    if (pageUrl.isNotBlank()) webView.reload()
                },
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "推荐流程：本页点“进入登录页”→ 完成 Google 授权 → 回到 hanime1.me 首页即自动导入并关闭登录页。若 Google 拒绝 WebView（403/错误页），请用「扫码登录」输入账号密码，或用「Cookie 导入」。",
            color = Palette.TextTertiary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            maxLines = 3,
        )
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

    Box(Modifier.fillMaxSize().background(Color(0xF2080910))) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                Modifier.fillMaxWidth(0.9f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, color = Palette.TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TvButton("取消", onCancel)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    value.isEmpty() -> "等待输入…"
                    masked -> "●".repeat(value.length.coerceAtMost(20))
                    else -> value
                },
                color = if (value.isEmpty()) Palette.TextTertiary else Palette.TextPrimary,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(Palette.Surface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                hint,
                color = Palette.TextTertiary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            TvKeyboard(
                onKey = onKey,
                onBackspace = onBackspace,
                onDone = onDone,
                doneLabel = doneLabel,
                firstFocusRequester = keyboardFocus,
            )
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
                .padding(horizontal = 24.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                color = if (enabled) Color.White else Palette.TextSecondary,
                fontSize = 14.sp,
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
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Surface.copy(alpha = 0.7f))
            .padding(16.dp),
    ) {
        Text(title, color = Palette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        lines.forEach { line ->
            Text("· $line", color = Palette.TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(6.dp))
        }
    }
}

// ------------------------------------------------------------------ 工具函数

/** 从配对地址 http://ip:port/?t=XXXXXX 里取出 6 位配对码 */
private fun pairingCodeOf(url: String?): String {
    if (url.isNullOrBlank()) return "------"
    val token = runCatching { Uri.parse(url).getQueryParameter("t") }.getOrNull()
    return token.orEmpty().ifBlank { "------" }
}

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

/**
 * 是否属于"Google 拒绝在 WebView 里授权"：
 * - 主文档 403 且停在非 hanime1.me 的页面（Cloudflare 的 403 会停在站点自身域）；
 * - 被重定向到 accounts.google.com 的错误/拒绝页（含 disallowed_useragent）。
 */
private fun looksLikeGoogleRejection(url: String?, problem: String?): Boolean {
    val lowerUrl = url.orEmpty().lowercase()
    val lowerProblem = problem.orEmpty().lowercase()
    if (lowerUrl.contains("disallowed_useragent")) return true
    if (lowerUrl.contains("accounts.google.com") &&
        (lowerUrl.contains("error") || lowerUrl.contains("denied"))
    ) {
        return true
    }
    return lowerProblem.contains("403") && !lowerUrl.contains("hanime1.me")
}

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
