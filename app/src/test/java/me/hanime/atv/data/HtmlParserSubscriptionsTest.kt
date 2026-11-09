package me.hanime.atv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 订阅页解析回归：已订阅作者（`.artist-option` / `.search-artist-card` 两种作者卡）
 * 与他们的最新作品（`.video-item-container`）；未登录被重定向到登录页时不能当成空订阅。
 */
class HtmlParserSubscriptionsTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource(name)!!.readText(Charsets.UTF_8)

    @Test
    fun parsesArtistsAndVideos() {
        val page = HtmlParser.parseSubscriptions(fixture("subscriptions_fixture.html"), 1)

        assertFalse("登录页才能判定为未登录", page.needsLogin)
        assertEquals(listOf("作者 A", "作者 B", "作者 C"), page.artists.map { it.name })
        assertEquals("3 部影片", page.artists[0].countText)
        assertEquals(
            "作者卡背景图必须跳过，取真正的头像",
            "https://vdownload.hembed.com/image/avatar/author_a.png?secure=av,1",
            page.artists[2].avatar,
        )
        assertEquals(listOf("400001", "400002"), page.items.map { it.code })
        assertEquals("订阅作者 A 的新作品", page.items[0].title)
        assertTrue("翻页链接应识别为还有下一页", page.hasNext)
    }

    @Test
    fun loginRedirectPageIsDetected() {
        val page = HtmlParser.parseSubscriptions(fixture("subscriptions_login_fixture.html"), 1)

        assertTrue("会话失效时站点会重定向到登录页", page.needsLogin)
        assertTrue(page.items.isEmpty())
        assertTrue(page.artists.isEmpty())
    }
}
