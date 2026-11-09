package me.hanime.atv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归测试：详情页“相关推荐”必须只取 `#related-tabcontent` 里的卡片，并且能解析
 * 播放列表卡片（`h4.video-title`）与容器 `title` 属性上的标题。
 * 之前选择器写成不存在的 `#related-tab`，回退到整页 `.video-item-container` 后
 * 先命中播放列表卡片，标题全部变成“未命名影片”。
 */
class HtmlParserRelatedTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource(name)!!.readText(Charsets.UTF_8)

    @Test
    fun relatedComesFromRelatedTabContentWithTitles() {
        val detail = HtmlParser.parseVideo(fixture("watch_related_fixture.html"), "409101")

        assertEquals(listOf("409031", "409032"), detail.related.map { it.code })
        assertEquals("相关推荐的完整标题 A", detail.related[0].title)
        assertEquals("相关推荐标题 B", detail.related[1].title)
        assertTrue(
            "相关推荐不应出现未命名影片",
            detail.related.none { it.title == "未命名影片" },
        )
    }

    @Test
    fun playlistCardTitleIsParsedWhenNoRelatedTab() {
        val detail = HtmlParser.parseVideo(fixture("watch_playlist_fixture.html"), "999")

        assertEquals(1, detail.related.size)
        assertEquals("播放列表里的第一集", detail.related[0].title)
        assertTrue("播放器封面必须解析到", detail.cover.startsWith("https://vdownload.hembed.com/image/cover/409101.jpg"))
        assertEquals(1, detail.sources.size)
    }
}
