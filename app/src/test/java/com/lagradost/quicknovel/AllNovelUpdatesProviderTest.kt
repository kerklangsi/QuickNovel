package com.lagradost.quicknovel

/*
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.quicknovel.providers.AllNovelUpdatesProvider
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AllNovelUpdatesProviderTest {
    private val provider = AllNovelUpdatesProvider()
    private val mapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build()

    @Test
    fun testProviderProperties() {
        assertEquals("AllNovelUpdates", provider.name)
        assertEquals("https://allnovelupdates.com", provider.mainUrl)
        assertTrue(provider.hasMainPage)
        assertTrue(provider.usesCloudFlareKiller)
        assertEquals(R.drawable.icon_allnovelupdates, provider.iconId)
        assertTrue(provider.orderBys.isNotEmpty())
        assertTrue(provider.tags.isNotEmpty())
    }

    @Test
    fun testParseNovelRow() {
        val html = """
            <div class="li-row">
                <div class="li">
                    <div class="con">
                        <div class="pic">
                            <a href="https://allnovelupdates.com/book/shadow-slave">
                                <img src="https://media.allnovelupdates.com/novel/shadow-slave.jpg" alt="Shadow Slave" title="Shadow Slave">
                            </a>
                        </div>
                        <div class="txt">
                            <h3 class="tit"><a href="https://allnovelupdates.com/book/shadow-slave" title="Shadow Slave">Shadow Slave</a></h3>
                            <div class="desc">
                                <div class="item">
                                    <span class="glyphicon glyphicon-book chi-tiet-icon"></span>
                                    <div class="right">
                                        <a href="https://allnovelupdates.com/book/shadow-slave/chapter-3130-bleak-days" class="chapter" title="Chapter 3130 Bleak Days">Chapter 3130 Bleak Days</a>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        """.trimIndent()

        val doc = Jsoup.parse(html)
        val row = doc.selectFirst("div.li-row")!!
        val result = provider.parseNovelRow(row)

        assertNotNull(result)
        assertEquals("Shadow Slave", result?.name)
        assertEquals("https://allnovelupdates.com/book/shadow-slave", result?.url)
        assertEquals("https://media.allnovelupdates.com/novel/shadow-slave.jpg", result?.posterUrl)
        assertEquals("Chapter 3130 Bleak Days", result?.latestChapter)
    }

    @Test
    fun testParseNovelList() {
        val html = """
            <div class="col-content">
                <div class="ul-list1 ul-list1-2 ss-custom">
                    <div class="li-row">
                        <div class="li">
                            <div class="con">
                                <div class="pic"><a href="/book/novel-1"><img src="/img1.jpg"></a></div>
                                <div class="txt"><h3 class="tit"><a href="/book/novel-1" title="Novel One">Novel One</a></h3></div>
                            </div>
                        </div>
                    </div>
                    <div class="li-row">
                        <div class="li">
                            <div class="con">
                                <div class="pic"><a href="/book/novel-2"><img src="/img2.jpg"></a></div>
                                <div class="txt"><h3 class="tit"><a href="/book/novel-2" title="Novel Two">Novel Two</a></h3></div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        """.trimIndent()

        val doc = Jsoup.parse(html)
        val list = provider.parseNovelList(doc)

        assertEquals(2, list.size)
        assertEquals("Novel One", list[0].name)
        assertEquals("https://allnovelupdates.com/book/novel-1", list[0].url)
        assertEquals("Novel Two", list[1].name)
        assertEquals("https://allnovelupdates.com/book/novel-2", list[1].url)
    }

    @Test
    fun testAjaxChapterJsonDeserialization() {
        val json = """
            {
                "success": true,
                "chapters": [
                    {
                        "_id": "62934c51db08f35b905a2dcb",
                        "chapter_name": "Chapter 1 Nightmare Begins",
                        "chapter_id": "chapter1-nightmare-begins"
                    },
                    {
                        "_id": "62934c51db08f35b905a2dcc",
                        "chapter_name": "Chapter 2 Slave Caravan",
                        "chapter_id": "chapter2-slave-caravan"
                    }
                ]
            }
        """.trimIndent()

        val response: AllNovelUpdatesProvider.AjaxChapterResponse = mapper.readValue(json)
        assertTrue(response.success == true)
        assertEquals(2, response.chapters?.size)
        assertEquals("Chapter 1 Nightmare Begins", response.chapters?.get(0)?.chapterName)
        assertEquals("chapter1-nightmare-begins", response.chapters?.get(0)?.chapterId)
        assertEquals("Chapter 2 Slave Caravan", response.chapters?.get(1)?.chapterName)
        assertEquals("chapter2-slave-caravan", response.chapters?.get(1)?.chapterId)
    }

    @Test
    fun testChapterContentSanitization() {
        val rawHtml = """
            <div id="main1">
                <div>
                    <div>
                        <div class="txt">
                            <div class="notice-text">Notice: Read on allnovelupdates.com</div>
                            <script>var x = 1;</script>
                            <p>Sunny woke up in a cold cell.</p>
                            <p>The nightmare had just begun.</p>
                            <div class="slot-frame">Ads here</div>
                            <div class="btn-more"><a href="#">Next Chapter</a></div>
                        </div>
                    </div>
                </div>
            </div>
        """.trimIndent()

        val doc = Jsoup.parse(rawHtml)
        doc.select("script, style, iframe, .ads, .ads-holder, .slot-frame, .notice-text").remove()
        val content = doc.selectFirst("#main1 > div > div > div.txt")
        assertNotNull(content)
        content?.select(".chapter-nav, .btn-more, .page")?.remove()

        val cleaned = content?.html() ?: ""
        assertTrue(cleaned.contains("Sunny woke up in a cold cell."))
        assertTrue(cleaned.contains("The nightmare had just begun."))
        assertTrue(!cleaned.contains("script"))
        assertTrue(!cleaned.contains("Notice: Read on allnovelupdates.com"))
        assertTrue(!cleaned.contains("Ads here"))
        assertTrue(!cleaned.contains("Next Chapter"))
    }
}
*/
