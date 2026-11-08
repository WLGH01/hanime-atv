package me.hanime.atv.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * hanime1.me 是一个服务端渲染的网站，App 端直接解析 HTML。
 * 这里的选择器都做了容错，页面小改版时尽量降级而不是直接崩溃。
 */
object HtmlParser {

    private const val BASE = HanimeClient.BASE_URL

    private val watchCode = Regex("""[?&]v=(\d+)""")
    private val bannerCode = Regex("""/thumbnail/(\d{3,})h\.jpg""")
    private val userLink = Regex("""/user/(\d+)""")
    private val viewsAndDate = Regex("""觀看次數[：:]\s*(.+?)[\s\u00a0]+(\d{4}-\d{2}-\d{2})""")
    private val sourceScript = Regex("""const source\s*=\s*'([^']+)'""")
    private val pageParam = Regex("""[?&]page=(\d+)""")
    private val tagCount = Regex("""\s*[（(]\s*\d+\s*[)）]\s*$""")

    private fun doc(html: String): Document = Jsoup.parse(html, BASE)

    fun extractToken(html: String): String =
        doc(html).body().selectFirst("input[name=_token]")?.attr("value").orEmpty()

    /** 从登录失败的响应里提取站点给出的错误提示（Laravel 校验错误/提示条） */
    fun extractErrorMessage(html: String): String? {
        if (html.isBlank()) return null
        val body = doc(html).body()
        val selectors = listOf(
            ".alert.alert-danger", ".alert-danger", ".alert",
            ".invalid-feedback", ".text-danger", ".help-block",
            "#login .error", ".error-message", ".swal2-content",
        )
        for (selector in selectors) {
            val text = body.selectFirst(selector)?.text()?.trim().orEmpty()
            if (text.isNotBlank() && text.length < 160) return text
        }
        return null
    }

    // ---------------------------------------------------------------- 首页

    fun parseHome(html: String): HomePage {
        val body = doc(html).body()
        val csrf = body.selectFirst("input[name=_token]")?.attr("value")
        val user = parseUser(body)
        val banner = parseBanner(body)
        val rows = mutableListOf<HomeRow>()

        body.select("#home-rows-wrapper a.horizontal-row-title, a.horizontal-row-title").forEach { titleEl ->
            val h3 = titleEl.selectFirst("h3")
            val title = (h3?.ownText()?.ifBlank { null } ?: h3?.text() ?: titleEl.text())
                .replace("查看更多", "")
                .trim()
            var sibling: Element? = titleEl.nextElementSibling()
            var guard = 0
            while (sibling != null && sibling.select(".video-item-container").isEmpty() && guard < 4) {
                sibling = sibling.nextElementSibling()
                guard++
            }
            val items = sibling?.select(".video-item-container")
                ?.mapNotNull { it.toCard() }
                .orEmpty()
            if (items.isNotEmpty() && title.isNotBlank()) {
                rows += HomeRow(title, items)
            }
        }

        if (rows.isEmpty()) {
            // 回退：整页卡片按 24 个一组
            val all = body.select(".video-item-container").mapNotNull { it.toCard() }
            if (all.isNotEmpty()) {
                all.chunked(24).forEachIndexed { index, chunk ->
                    rows += HomeRow(if (index == 0) "最新上架" else "更多推荐", chunk)
                }
            }
        }
        return HomePage(banner = banner, rows = rows, user = user, csrf = csrf)
    }

    private fun parseBanner(body: Element): HanimeCard? {
        val wrap = body.getElementById("home-banner-wrapper") ?: return null
        val h1 = wrap.selectFirst("h1")?.text()?.trim().orEmpty()
        val metaText = wrap.selectFirst("h4")?.text()?.trim().orEmpty()
        var imgEl: Element? = wrap.previousElementSibling()?.selectFirst("img")
        if (imgEl == null) {
            imgEl = body.selectFirst("img[alt]:not(.main-thumb)")
        }
        val cover = imgEl?.absUrl("src").orEmpty()
        val code = bannerCode.find(cover)?.groupValues?.get(1)
            ?: body.select("a[href*=watch]").firstOrNull()
                ?.absUrl("href")?.let { watchCode.find(it)?.groupValues?.get(1) }
            ?: return null
        val parts = metaText.split("•").map { it.trim() }.filter { it.isNotEmpty() }
        return HanimeCard(
            code = code,
            title = h1.ifBlank { "精选推荐" },
            cover = cover,
            views = parts.getOrNull(1),
            uploader = parts.getOrNull(0),
            timeAgo = parts.getOrNull(2),
            duration = null,
        )
    }

    fun parseUser(body: Element): UserInfo? {
        val trigger = body.getElementById("user-modal-trigger")
        val triggerHref = trigger?.absUrl("href").orEmpty().ifBlank { trigger?.attr("href").orEmpty() }
        val triggerId = userLink.find(triggerHref)?.groupValues?.get(1)

        val modal = body.getElementById("user-modal-dp-wrapper")
        val profileLink = modal?.selectFirst("a[href*=/user/]")
            ?: body.selectFirst("#user-modal-dp-wrapper a[href*=/user/]")
        val profileHref = profileLink?.absUrl("href").orEmpty()
            .ifBlank { profileLink?.attr("href").orEmpty() }
        val profileId = userLink.find(profileHref)?.groupValues?.get(1)

        // 未登录时账号入口指向 /login；只有出现"退出登录"或用户弹窗/用户主页才算已登录。
        // 注意：影片/作者卡片里也有 /user/ 链接，不能作为已登录依据。
        val hasLogout = body.selectFirst("a[href*=/logout]") != null
        val loggedIn = hasLogout || triggerId != null || (modal != null && profileId != null)
        if (!loggedIn) return null

        val id = triggerId ?: profileId
        val avatar = trigger?.selectFirst("img")?.absUrl("src")
            ?: modal?.selectFirst("img")?.absUrl("src")
        val name = modal?.selectFirst("div[class*=name], h4, h5, .user-name")?.text()?.trim()
            ?.ifBlank { null }
            ?: profileLink?.text()?.trim()?.ifBlank { null }
            ?: "已登录"
        return UserInfo(id = id ?: "0", name = name, avatar = avatar)
    }

    // ---------------------------------------------------------------- 列表

    fun parseSearch(html: String, page: Int): SearchPage {
        val body = doc(html).body()
        val items = body.select(".video-item-container").mapNotNull { it.toCard() }
        return SearchPage(
            items = items.distinctBy { it.code },
            page = page,
            hasNext = hasNextPage(body, page),
        )
    }

    fun parseCardList(html: String, page: Int): FavoritePage {
        val body = doc(html).body()
        val items = body.select(".video-item-container").mapNotNull { it.toCard() }
        return FavoritePage(items = items.distinctBy { it.code }, page = page, hasNext = hasNextPage(body, page))
    }

    /** 分页链接里是否还有比当前页更大的页码（兼容 laravel 的 ?page=N 结构） */
    private fun hasNextPage(body: Element, page: Int): Boolean {
        if (body.select("a[rel=next]").isNotEmpty()) return true
        val links = body.select(".pagination a[href], ul.pagination a[href], nav a[href*=page=]")
        return links.any { a ->
            val p = pageParam.find(a.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
            p != null && p > page
        }
    }

    private fun Element.toCard(): HanimeCard? {
        val link = selectFirst("a.video-link")
            ?: selectFirst("a[href*=watch]")
            ?: return null
        val href = link.absUrl("href").ifBlank { link.attr("href") }
        val code = watchCode.find(href)?.groupValues?.get(1) ?: return null
        val img = selectFirst("img.main-thumb") ?: selectFirst("img")
        val cover = img?.absUrl("src").orEmpty()
        if (cover.isBlank()) return null

        val stats = select(".stats-container .stat-item")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
        val title = (selectFirst("div.title")?.text()?.trim()
            ?: attr("title").trim())
            .replace("\n", " ")
            .trim()
        val uploader = selectFirst(".subtitle a")?.text()?.trim()
        val timeAgo = selectFirst(".subtitle-time")?.text()
            ?.replace("•", "")?.trim()
        val rating = stats.firstOrNull { it.contains("%") }
            ?: selectFirst(".stat-item i.material-icons, .stat-item .thumb_up")
                ?.text()?.trim()?.takeIf { it.contains("%") }
        return HanimeCard(
            code = code,
            title = title.ifBlank { "未命名影片" },
            cover = cover,
            duration = selectFirst(".duration")?.text()?.trim()?.ifBlank { null },
            views = stats.firstOrNull { it.contains("次") },
            uploader = uploader?.ifBlank { null },
            timeAgo = timeAgo?.ifBlank { null },
            rating = rating?.trim()?.ifBlank { null },
        )
    }

    // ---------------------------------------------------------------- 详情页

    fun parseVideo(html: String, code: String): VideoDetail {
        val doc = doc(html)
        val body = doc.body()
        val csrf = body.selectFirst("input[name=_token]")?.attr("value")

        val title = body.selectFirst("h3#shareBtn-title")?.text()?.trim()
            ?: doc.title().substringAfter("|", "").trim()
        val player = body.selectFirst("video#player")
        val cover = player?.attr("poster").orEmpty()

        val sources = mutableListOf<VideoSource>()
        player?.select("source")?.forEach { s ->
            val url = s.absUrl("src").ifBlank { s.attr("src") }
            if (url.isNotBlank()) {
                val size = s.attr("size").trim()
                sources += VideoSource(
                    label = if (size.isBlank() || size == "0") "原画" else "${size}P",
                    url = url,
                    type = s.attr("type").ifBlank { "video/mp4" },
                )
            }
        }
        if (sources.isEmpty()) {
            body.select("div#player-div-wrapper script, script").forEach { s ->
                sourceScript.find(s.data())?.groupValues?.get(1)?.let { url ->
                    sources += VideoSource(label = "自动", url = url, type = "video/mp4")
                }
            }
        }
        val sortedSources = sources.distinctBy { it.url }
            .sortedByDescending { it.label.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }

        val metaEl = body.select(".video-details-wrapper")
            .firstOrNull { it.text().contains("觀看次數") || it.text().contains("观看次数") }
        val metaText = metaEl?.text().orEmpty().replace('\u00a0', ' ')
        val meta = viewsAndDate.find(metaText)

        val description = (body.selectFirst(".video-caption-text")
            ?: body.selectFirst("#video-caption")
            ?: body.selectFirst(".video-description"))
            ?.text()?.trim()?.ifBlank { null }

        val tags = parseTags(body)

        val artist = body.selectFirst("#video-artist-name")?.text()?.trim()?.ifBlank { null }
        val artistAvatar = body.selectFirst("a[href*=/user/] img[style*=border-radius]")
            ?.absUrl("src")?.ifBlank { null }
            ?: body.selectFirst("img#video-user-avatar")?.absUrl("src")?.ifBlank { null }

        val related = (body.select("#related-tab .video-item-container").ifEmpty {
            null
        } ?: body.select(".video-item-container"))
            .mapNotNull { it.toCard() }
            .filter { it.code != code }
            .distinctBy { it.code }
            .take(30)

        val likeText = body.selectFirst("#video-like-btn")?.text()
            ?.replace(Regex("""\s+"""), " ")?.trim()?.ifBlank { null }
        val userId = body.select("a[href*=/user/]").firstNotNullOfOrNull { a ->
            userLink.find(a.absUrl("href").ifBlank { a.attr("href") })?.groupValues?.get(1)
        }

        return VideoDetail(
            code = code,
            title = title.ifBlank { "影片 $code" },
            cover = cover,
            views = meta?.groupValues?.get(1)?.trim(),
            date = meta?.groupValues?.get(2)?.trim(),
            description = description,
            tags = tags,
            artist = artist,
            artistAvatar = artistAvatar,
            sources = sortedSources,
            related = related,
            likeText = likeText,
            csrf = csrf,
            userId = userId,
        )
    }

    /**
     * 详情页的标签是 `/search?tags[]=巨乳&genre=裏番` 形式的链接，文字形如 `巨乳 (4)`。
     * 这里同时兼容旧的 `.single-video-tag` 结构。
     */
    private fun parseTags(body: Element): List<String> {
        val byHref = body.select("a[href*=tags]").mapNotNull { a ->
            val href = a.attr("href")
            if (!href.contains("tags")) return@mapNotNull null
            a.text().trim().replace(tagCount, "").removePrefix("#").trim().ifBlank { null }
        }
        val fallback = body.select(".single-video-tag a").mapNotNull { a ->
            a.text().trim().removePrefix("#").trim().ifBlank { null }
        }
        return (byHref.ifEmpty { fallback }).distinct().take(40)
    }
}
