package me.hanime.atv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归测试：`?genre=` 的结果页不使用 .video-item-container，
 * 而是 div.search-videos + .home-rows-videos-title 这套网格卡片。
 * 之前只解析 .video-item-container，导致"里番/泡面番"分类显示为空。
 */
class HtmlParserSearchTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource(name)!!.readText(Charsets.UTF_8)

    @Test
    fun genrePageParsesSearchGridCards() {
        val page = HtmlParser.parseSearch(fixture("search_genre_fixture.html"), 1)

        assertEquals(listOf("408317", "408321", "408319"), page.items.map { it.code })
        assertEquals("[中字後補] 與毫無自覺的青梅竹馬出於好奇試著做愛之後 3", page.items[0].title)
        assertTrue(page.items[0].cover.startsWith("https://vdownload.hembed.com/image/cover/408317.jpg"))
        assertTrue("分类页封面是 2:3 竖版海报，卡片必须用竖图布局", page.items.all { it.portrait })
        assertTrue("广告卡（erolabs）必须被跳过", page.items.none { it.code.isBlank() })
        assertTrue("翻页链接应识别为还有下一页", page.hasNext)
    }
}
