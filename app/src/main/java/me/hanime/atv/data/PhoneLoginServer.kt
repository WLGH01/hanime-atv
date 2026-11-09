package me.hanime.atv.data

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLDecoder

/**
 * 电视端局域网配对服务：电视显示二维码，手机扫码后打开这个服务提供的页面。
 *
 * 为什么要有它：电视遥控器/屏幕键盘输入长密码几乎不可用。把账号密码登录搬到
 * 手机键盘上完成，电视只负责收结果，是最稳的方案。
 *
 * 页面能力：
 * - 手机输入账号密码 → 电视用站点表单登录（Cookie 落在电视的 CookieJar）
 * - Google 登录引导：说明改在电视「Google (TV)」Tab 里完成（WebView 才能拿到 HttpOnly 会话）
 * - Google-only 账号：先在手机上给账号设一个密码，再回本页用邮箱密码登录
 * - 高级：把电脑上登录后的 Cookie 粘贴回电视
 *
 * 注意：站点会话 Cookie `hanime1_session` 带 HttpOnly，网页 JS 的 document.cookie
 * 读不到，所以不要再提供"书签把 Cookie 带回电视"这类方案（且浏览器保存书签时会
 * 去掉 javascript: 前缀，点开只会变成访问站点上不存在的路径，返回 404）。
 */
class PhoneLoginServer(
    private val repository: HanimeRepository,
    port: Int = DEFAULT_PORT,
) : NanoHTTPD("0.0.0.0", port) {

    val token: String = newToken()

    @Volatile
    var statusText: String = "等待手机连接…"
        private set

    @Volatile
    var phoneConnected: Boolean = false
        private set

    @Volatile
    var finished: Boolean = false
        private set

    var onStatus: ((String) -> Unit)? = null
    var onSuccess: ((UserInfo) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    val pairingUrl: String?
        get() = lanAddress()?.let { "http://$it:$listeningPort/?t=$token" }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.orEmpty()
        return try {
            when {
                session.method == Method.GET && (uri == "/" || uri == "/index.html") -> {
                    if (!tokenMatches(tokenFrom(session))) return forbidden()
                    phoneConnected = true
                    pushStatus("手机已连接，等待登录…")
                    htmlPage()
                }

                session.method == Method.GET && uri == "/api/status" -> {
                    if (!tokenMatches(tokenFrom(session))) return forbidden()
                    json(
                        """{"ok":true,"finished":$finished,"connected":$phoneConnected,"status":${quote(statusText)}}""",
                    )
                }

                session.method == Method.POST && uri == "/api/login" -> handleLogin(session)
                session.method == Method.POST && uri == "/api/cookie" -> handleCookie(session)
                session.method == Method.GET && uri == "/api/ping" -> json("""{"ok":true}""")
                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "text/plain; charset=utf-8",
                    "not found",
                )
            }
        } catch (t: Throwable) {
            pushStatus("处理失败：${t.message ?: "未知错误"}")
            json("""{"ok":false,"message":${quote(t.message ?: "未知错误")}}""")
        }
    }

    // ------------------------------------------------------------ 接口实现

    private fun handleLogin(session: IHTTPSession): Response {
        val params = postParams(session)
        if (!tokenMatches(params["t"])) return forbidden()
        val email = params["email"].orEmpty().trim()
        val password = params["password"].orEmpty()
        if (email.isBlank() || password.isBlank()) {
            return json("""{"ok":false,"message":"请输入账号与密码"}""")
        }
        phoneConnected = true
        pushStatus("正在用电视登录 $email …")
        val result = runBlocking { repository.loginWithPassword(email, password) }
        return when (result) {
            is LoginResult.Success -> {
                completeLogin(result.user)
                json(
                    """{"ok":true,"message":"登录成功","user":${quote(result.user.name)}}""",
                )
            }

            is LoginResult.Failure -> {
                pushStatus("登录失败：${result.message}")
                json("""{"ok":false,"message":${quote(result.message)}}""")
            }
        }
    }

    private fun handleCookie(session: IHTTPSession): Response {
        val params = postParams(session)
        if (!tokenMatches(params["t"])) return forbidden()
        val cookie = params["cookie"].orEmpty().trim()
        if (cookie.isBlank()) return json("""{"ok":false,"message":"Cookie 不能为空"}""")
        pushStatus("正在校验 Cookie …")
        val result = runBlocking { repository.loginWithCookieString(cookie) }
        return when (result) {
            is LoginResult.Success -> {
                completeLogin(result.user)
                json("""{"ok":true,"message":"Cookie 同步成功","user":${quote(result.user.name)}}""")
            }

            is LoginResult.Failure -> {
                pushStatus("Cookie 校验失败：${result.message}")
                json("""{"ok":false,"message":${quote(result.message)}}""")
            }
        }
    }

    private fun completeLogin(user: UserInfo) {
        if (finished) return
        finished = true
        repository.rememberUser(user)
        pushStatus("登录成功：${user.name}")
        onSuccess?.invoke(user)
        // 再给手机端留一点时间展示成功状态，然后自动关闭服务
        Thread {
            runCatching { Thread.sleep(12_000) }
            runCatching { stop() }
        }.apply { isDaemon = true }.start()
    }

    // ------------------------------------------------------------ 工具

    private fun pushStatus(text: String) {
        statusText = text
        onStatus?.invoke(text)
    }

    private fun tokenMatches(given: String?): Boolean =
        !given.isNullOrBlank() && given == token

    private fun forbidden(): Response =
        newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain; charset=utf-8", "invalid token")

    private fun json(payload: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", payload)

    private fun htmlPage(): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", PAGE_HTML.replace("__TOKEN__", token))

    private fun tokenFrom(session: IHTTPSession): String? {
        val query: Map<String, String> = session.parms
        return query["t"]
    }

    private fun postParams(session: IHTTPSession): Map<String, String> {
        val map = HashMap<String, String>()
        runCatching { session.parseBody(map) }
        val body = map["postData"].orEmpty()
        val fromBody = body.split("&").mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) null else {
                URLDecoder.decode(part.substring(0, index), "UTF-8") to
                    URLDecoder.decode(part.substring(index + 1), "UTF-8")
            }
        }.toMap()
        val merged = HashMap<String, String>()
        val query: Map<String, String> = session.parms
        query.forEach { (key, value) -> merged[key] = value.orEmpty() }
        merged.putAll(fromBody)
        return merged
    }

    private fun quote(value: String): String {
        val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "")
        return "\"$escaped\""
    }

    companion object {
        const val DEFAULT_PORT = 8765
        private const val SOCKET_READ_TIMEOUT = 20_000

        /** 逐个端口尝试，避免 8765 被别的应用占用 */
        fun create(repository: HanimeRepository): PhoneLoginServer {
            var last: IOException? = null
            for (port in DEFAULT_PORT until DEFAULT_PORT + 12) {
                val server = PhoneLoginServer(repository, port)
                try {
                    server.start(SOCKET_READ_TIMEOUT, false)
                    return server
                } catch (e: IOException) {
                    last = e
                    runCatching { server.stop() }
                }
            }
            throw last ?: IOException("无法在 8765-8776 上启动配对服务")
        }

        /** 取本机局域网 IPv4，优先 wlan/eth 接口 */
        fun lanAddress(): String? {
            val candidates = mutableListOf<Pair<Int, String>>()
            val interfaces = runCatching {
                java.util.Collections.list(NetworkInterface.getNetworkInterfaces())
            }.getOrDefault(emptyList<NetworkInterface>())
            interfaces.forEach { nif ->
                if (!nif.isUp || nif.isLoopback) return@forEach
                val name = nif.name.orEmpty().lowercase()
                if (name.startsWith("dummy") || name.startsWith("rmnet") || name.startsWith("tun")) return@forEach
                val score = when {
                    name.startsWith("wlan") -> 0
                    name.startsWith("eth") -> 1
                    name.startsWith("en") -> 2
                    else -> 5
                }
                val addresses = runCatching {
                    java.util.Collections.list(nif.inetAddresses)
                }.getOrDefault(emptyList())
                addresses.forEach { address ->
                    if (address is Inet4Address && !address.isLoopbackAddress && address.isSiteLocalAddress) {
                        candidates += score to address.hostAddress.orEmpty()
                    }
                }
            }
            return candidates.minByOrNull { it.first }?.second
        }

        private fun newToken(): String {
            val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            return (1..6).map { chars.random() }.joinToString("")
        }

        private val PAGE_HTML: String = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>Hanime ATV 手机登录</title>
<style>
:root{--bg:#0b0b13;--card:#171726;--hi:#202033;--pink:#ff5c8a;--violet:#7c4dff;--text:#f5f5fa;--dim:#a8a8bc;--ok:#4ade80}
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
body{margin:0;background:radial-gradient(900px 500px at 80% -10%,rgba(124,77,255,.28),transparent),var(--bg);color:var(--text);font:16px/1.6 -apple-system,BlinkMacSystemFont,"PingFang SC","Microsoft YaHei",sans-serif;padding:22px 18px 60px}
.wrap{max-width:520px;margin:0 auto}
.logo{display:flex;align-items:center;gap:10px;margin:6px 0 18px}
.mark{width:38px;height:38px;border-radius:12px;background:linear-gradient(135deg,var(--pink),var(--violet));display:flex;align-items:center;justify-content:center;font-weight:900;font-size:20px}
h1{font-size:20px;margin:0}
.sub{color:var(--dim);font-size:13px;margin-top:4px}
.card{background:var(--card);border:1px solid rgba(255,255,255,.06);border-radius:18px;padding:18px;margin-bottom:16px}
.status{display:flex;align-items:center;gap:9px;font-size:14px;color:var(--dim)}
.dot{width:9px;height:9px;border-radius:50%;background:var(--pink);box-shadow:0 0 12px var(--pink)}
.dot.ok{background:var(--ok);box-shadow:0 0 12px var(--ok)}
label{display:block;color:var(--dim);font-size:13px;margin:14px 0 6px}
input,textarea{width:100%;background:var(--hi);border:1px solid rgba(255,255,255,.08);border-radius:12px;color:var(--text);padding:14px;font-size:16px;outline:none}
input:focus,textarea:focus{border-color:var(--pink)}
button{width:100%;margin-top:16px;border:0;border-radius:12px;padding:15px;font-size:16px;font-weight:700;color:#fff;background:linear-gradient(135deg,var(--pink),var(--violet));cursor:pointer}
button:disabled{opacity:.55}
button.ghost{background:var(--hi);color:var(--text);font-weight:600;border:1px solid rgba(255,255,255,.08)}
.tip{color:#6f6f85;font-size:12.5px;line-height:1.7;margin-top:10px}
.msg{margin-top:12px;font-size:14px;border-radius:12px;padding:11px 13px;display:none}
.msg.ok{display:block;background:rgba(74,222,128,.12);color:#86efac;border:1px solid rgba(74,222,128,.3)}
.msg.err{display:block;background:rgba(255,107,107,.12);color:#ffb4b4;border:1px solid rgba(255,107,107,.3)}
.hr{height:1px;background:rgba(255,255,255,.07);margin:18px 0}
details summary{cursor:pointer;color:var(--dim);font-size:14px;outline:none}
.badge{display:inline-block;font-size:12px;color:var(--dim);background:rgba(255,255,255,.06);border-radius:999px;padding:3px 10px;margin-left:6px}
.done{display:none;text-align:center;padding:26px 10px}
.done .big{font-size:46px}
</style>
</head>
<body>
<div class="wrap">
  <div class="logo"><div class="mark">H</div><div><h1>Hanime ATV 手机登录</h1><div class="sub">配对码 <span class="badge" id="tok">__TOKEN__</span></div></div></div>

  <div class="card">
    <div class="status"><span class="dot" id="dot"></span><span id="status">正在连接电视…</span></div>
  </div>

  <div id="forms">
    <div class="card">
      <div style="font-weight:700">用账号密码登录</div>
      <div class="tip">在手机上输入更省事，电视会自动同步登录状态，无需在电视上打字。</div>
      <form id="loginForm" autocomplete="on">
        <label>邮箱 / 账号</label>
        <input id="email" name="email" type="email" inputmode="email" autocomplete="username" placeholder="you@example.com">
        <label>密码</label>
        <input id="password" name="password" type="password" autocomplete="current-password" placeholder="站点密码">
        <button id="submitBtn" type="submit">登录</button>
      </form>
      <div class="msg" id="msg"></div>
      <div class="hr"></div>
      <div style="font-weight:700">Google 登录怎么走（重要）</div>
      <div class="tip">
        <b>推荐：在电视上完成 Google 登录</b><br>
        回到电视，切到「Google (TV)」标签页 → 点“进入登录页” → 在电视的网页里用 Google 登录。电视的 WebView 能读取站点的 HttpOnly 登录 Cookie，登录成功后 App 会自动导入并返回，手机上不需要再做任何操作。
      </div>
      <div class="tip">
        <b>账号只能用 Google 登录？</b><br>
        1. 用手机浏览器打开 <a href="https://hanime1.me/login" target="_blank" rel="noopener" style="color:#ff8fb0">hanime1.me/login</a>，先用 Google 登录一次（完成账号建立 / 确认账号可用）。<br>
        2. 在站点点「忘記密碼」，用该账号的邮箱重置一次密码。<br>
        3. 回到本页，用上面的「邮箱 + 新密码」登录，电视会自动同步登录态。
      </div>
      <div class="tip">
        <b>已经在电脑上登录过？</b><br>
        展开下面的「高级：手动粘贴 Cookie」，把电脑浏览器的 Cookie 粘贴进来即可同步到电视；也可以在电视的「Cookie 导入」Tab 里粘贴。
      </div>
    </div>

    <div class="card">
      <details>
        <summary>高级：手动粘贴 Cookie</summary>
        <div class="tip">在电脑浏览器登录 hanime1.me → F12 → Network → 任意请求 → 复制请求头里的 Cookie 值，粘贴到下面。</div>
        <textarea id="cookie" rows="4" placeholder="key=value; key2=value2"></textarea>
        <button class="ghost" id="cookieBtn" type="button">同步 Cookie 到电视</button>
        <div class="msg" id="cookieMsg"></div>
      </details>
    </div>
  </div>

  <div class="card done" id="done">
    <div class="big">✅</div>
    <div style="font-size:18px;font-weight:700;margin-top:6px">登录成功</div>
    <div class="tip">电视端已同步登录状态，可以直接回到电视操作了。</div>
  </div>

  <div class="tip">此页面由电视本地生成，只在同一局域网内可访问；关闭电视上的扫码窗口后立即失效。</div>
</div>
<script>
const TOKEN = "__TOKEN__";
const byId = (id) => document.getElementById(id);
let polling = true;

function showMsg(el, text, ok) {
  el.textContent = text;
  el.className = "msg " + (ok ? "ok" : "err");
}
async function post(path, data) {
  const body = new URLSearchParams(Object.assign({t: TOKEN}, data));
  const res = await fetch(path, {method: "POST", headers: {"Content-Type": "application/x-www-form-urlencoded"}, body});
  return res.json();
}
async function poll() {
  if (!polling) return;
  try {
    const res = await fetch("/api/status?t=" + encodeURIComponent(TOKEN));
    const data = await res.json();
    byId("status").textContent = data.status || "等待操作…";
    byId("dot").className = "dot" + (data.finished ? " ok" : "");
    if (data.finished) {
      polling = false;
      byId("forms").style.display = "none";
      byId("done").style.display = "block";
    }
  } catch (e) {
    byId("status").textContent = "与电视的连接已断开";
  }
}
setInterval(poll, 1500);
poll();

byId("loginForm").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  const btn = byId("submitBtn");
  btn.disabled = true;
  btn.textContent = "正在登录…";
  try {
    const data = await post("/api/login", {email: byId("email").value, password: byId("password").value});
    showMsg(byId("msg"), data.message || (data.ok ? "登录成功" : "登录失败"), !!data.ok);
    if (data.ok) { byId("forms").style.display = "none"; byId("done").style.display = "block"; polling = false; }
  } catch (e) {
    showMsg(byId("msg"), "请求失败，请确认手机与电视在同一网络", false);
  } finally {
    btn.disabled = false;
    btn.textContent = "登录";
  }
});

// 一键同步书签：已移除。站点会话 Cookie 是 HttpOnly，document.cookie 读不到；
// 浏览器保存书签时还会去掉 javascript: 前缀，点开只会打开站点的 404 路径。
byId("cookieBtn").addEventListener("click", async () => {
  const btn = byId("cookieBtn");
  btn.disabled = true;
  btn.textContent = "同步中…";
  try {
    const data = await post("/api/cookie", {cookie: byId("cookie").value});
    showMsg(byId("cookieMsg"), data.message || (data.ok ? "同步成功" : "同步失败"), !!data.ok);
    if (data.ok) { byId("forms").style.display = "none"; byId("done").style.display = "block"; polling = false; }
  } catch (e) {
    showMsg(byId("cookieMsg"), "请求失败，请确认手机与电视在同一网络", false);
  } finally {
    btn.disabled = false;
    btn.textContent = "同步 Cookie 到电视";
  }
});
</script>
</body>
</html>
""".trimIndent()
    }
}
