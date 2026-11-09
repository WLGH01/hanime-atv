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
        var items = body.select(".video-item-container").mapNotNull { it.toCard() }
        // `?genre=` 的结果页用的是另一套网格卡片（div.search-videos），
        // 没有 .video-item-container / .video-link，必须走下面的回退解析。
        if (items.isEmpty()) {
            items = body.select("div.search-videos").mapNotNull { it.toSearchGridCard() }
        }
        return SearchPage(
            items = items.distinctBy { it.code },
            page = page,
            hasNext = hasNextPage(body, page),
        )
    }

    /**
     * 分类页（`?genre=`）的卡片结构：
     * `<a href="/watch?v=123"><div class="home-rows-videos-div search-videos">
     *    <div class="video-card-inner"><img src=".../image/cover/123.jpg">
     *    <div class="home-rows-videos-title">标题</div></div></div></a>`
     * 没有时长/评分/作者信息，只有封面和标题。
     */
    private fun Element.toSearchGridCard(): HanimeCard? {
        val anchor = closest("a[href]") ?: return null
        val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
        val code = watchCode.find(href)?.groupValues?.get(1) ?: return null
        val img = selectFirst("img") ?: return null
        val cover = img.absUrl("src").ifBlank { img.attr("src") }
        if (cover.isBlank()) return null
        val title = selectFirst(".home-rows-videos-title")?.text()?.trim()
            ?: img.attr("alt").trim()
        return HanimeCard(
            code = code,
            title = title.ifBlank { "未命名影片" },
            cover = cover,
            duration = selectFirst(".duration")?.text()?.trim()?.ifBlank { null },
            // 这套网格卡片用的是 image/cover/xxx.jpg 竖版海报（268×394）
            portrait = true,
        )
    }

    fun parseCardList(html: String, page: Int): FavoritePage {
        val body = doc(html).body()
        val items = body.select(".video-item-container").mapNotNull { it.toCard() }
        return FavoritePage(items = items.distinctBy { it.code }, page = page, hasNext = hasNextPage(body, page))
    }

    // ---------------------------------------------------------------- 订阅作者

    /**
     * 站点 `/subscriptions`（登录后可见）：
     * - 顶部/侧栏是已订阅作者，条目带 `.search-artist-title`（部分页面是 `.artist-option`）；
     * - 下面是这些作者的最新作品，卡片结构与首页/搜索一致（`.video-item-container`，
     *   极少数布局会退回 `div.search-videos` 网格卡片）。
     * 未登录时站点 302 到 `/login`，这里的登录表单可作为会话失效的判据。
     */
    fun parseSubscriptions(html: String, page: Int): SubscriptionsPage {
        val body = doc(html).body()
        if (body.selectFirst("form[action*=/login]") != null) {
            return SubscriptionsPage(page = page, needsLogin = true)
        }

        // 真实页面结构：`.subscriptions-nav` 里是 `.subscriptions-artist-card`，
        // 标题在 `.card-mobile-title`；其它版本的作者卡类名做兜底。
        val scopedArtists = body.select(".subscriptions-nav .subscriptions-artist-card")
        val artistElements = if (scopedArtists.isNotEmpty()) {
            scopedArtists
        } else {
            body.select(".subscriptions-artist-card, .artist-option, .search-artist-card")
        }
        val artists = artistElements
            .mapNotNull { it.toSubscriptionArtist() }
            .distinctBy { it.name }

        // 作者作品在 `.content-padding-new` 内容区里；页面没有该容器时退回整页
        val content: Element = body.selectFirst(".content-padding-new") ?: body
        var items = content.select(".video-item-container").mapNotNull { it.toCard() }
        if (items.isEmpty()) {
            // 订阅页也可能用分类页那套网格卡片（div.search-videos / .home-rows-videos-div）
            items = (if (content !== body) content else body)
                .select("div.search-videos, .home-rows-videos-div")
                .mapNotNull { it.toSearchGridCard() }
        }
        if (items.isEmpty() && content !== body) {
            items = body.select(".video-item-container").mapNotNull { it.toCard() }
        }

        return SubscriptionsPage(
            artists = artists,
            items = items.distinctBy { it.code },
            page = page,
            hasNext = hasNextPage(body, page),
        )
    }

    private fun Element.toSubscriptionArtist(): SubscriptionArtist? {
        val name = (
            takeIf { hasClass("search-artist-title") || hasClass("card-mobile-title") }?.text()
                ?: selectFirst(".card-mobile-title")?.text()
                ?: selectFirst(".search-artist-title")?.text()
                ?: return null
            ).trim()
        if (name.isBlank()) return null

        // 作者卡的背景图固定是 card_artist_background.jpg，跳过它取真正的头像；
        // 站点作者卡通常是 [背景图, 头像] 两张 img，第二张就是头像。
        val images = select("img").mapNotNull { img ->
            img.absUrl("src").ifBlank { img.attr("src") }.ifBlank { null }
        }
        val avatar = images.firstOrNull { !it.contains("card_artist_background") }
            ?: images.getOrNull(1)
        val count = selectFirst(".search-artist-count")?.text()?.trim()?.ifBlank { null }
        return SubscriptionArtist(name = name, avatar = avatar, countText = count)
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
            ?: selectFirst("h4.video-title a[href*=watch]")
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
        // 站点的卡片标题有三种写法：
        // - 首页/搜索：`div.title`
        // - 相关推荐/作者列表：容器自身的 title 属性，文字最完整
        // - 播放列表（playlist-video-card）：`h4.video-title > a`
        // 之前只认 div.title，导致相关推荐里的播放列表卡片全变成“未命名影片”。
        val title = attr("title").trim()
            .ifBlank { selectFirst("div.title")?.text()?.trim().orEmpty() }
            .ifBlank { selectFirst("h4.video-title, .video-title, .grid-title, .home-rows-videos-title")?.text()?.trim().orEmpty() }
            .ifBlank { link.attr("title").trim() }
            .ifBlank { img?.attr("alt").orEmpty() }
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

        // 相关推荐在 `#related-tabcontent` 里；注意页面没有 `#related-tab` 这个 id，
        // 之前的选择器永远匹配不到，回退成整页的 .video-item-container 后，
        // 先命中的是播放列表（playlist-video-card）——标题为“未命名影片”。
        val relatedScope = body.selectFirst("#related-tabcontent")
        val relatedElements = relatedScope?.select(".video-item-container").orEmpty()
        val related = (if (relatedElements.isNotEmpty()) relatedElements else body.select(".video-item-container"))
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
