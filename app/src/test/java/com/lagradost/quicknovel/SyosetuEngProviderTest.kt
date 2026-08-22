package com.lagradost.quicknovel

import com.lagradost.quicknovel.providers.SyosetuEngProvider
import com.lagradost.quicknovel.providers.SyosetuProvider
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyosetuEngProviderTest {
    private val provider = SyosetuEngProvider()
    private val original = SyosetuProvider()

    @Test
    fun testProviderProperties() {
        assertEquals("Syosetu (English)", provider.name)
        assertEquals("en", provider.lang)
        assertEquals(original.mainUrl, provider.mainUrl)
        assertEquals(original.iconId, provider.iconId)
        assertEquals(original.iconBackgroundId, provider.iconBackgroundId)
        assertEquals(original.hasMainPage, provider.hasMainPage)
        assertEquals(original.usesCloudFlareKiller, provider.usesCloudFlareKiller)
        assertEquals(original.mainCategories, provider.mainCategories)
        assertEquals(original.orderBys, provider.orderBys)
        assertEquals(original.tags, provider.tags)
        assertTrue(provider.mainCategories.isNotEmpty())
        assertTrue(provider.orderBys.isNotEmpty())
    }

    @Test
    fun testHtmlParagraphReplacementWithPElements() {
        val rawHtml = """
            <div class="p-novel__body">
                <p id="L1">何ということもない普通の人生。</p>
                <p id="L2">大学を出て一応大手と言われるゼネコンに入社し、現在一人暮らしの37歳。</p>
                <p id="L3">彼女はいない。</p>
            </div>
        """.trimIndent()

        val doc = Jsoup.parse(rawHtml)
        val pElements = doc.select("p")
        assertEquals(3, pElements.size)

        val fakeTranslations = listOf(
            "An ordinary life with nothing unusual.",
            "After graduating college, entered a major general contractor, currently 37 living alone.",
            "Has no girlfriend."
        )

        pElements.forEachIndexed { i, p ->
            fakeTranslations.getOrNull(i)?.let { p.text(it) }
        }

        val result = doc.body().html()
        assertTrue(result.contains("An ordinary life with nothing unusual."))
        assertTrue(result.contains("After graduating college, entered a major general contractor, currently 37 living alone."))
        assertTrue(result.contains("Has no girlfriend."))
    }

    @Test
    fun testHtmlFallbackWithoutPElements() {
        val rawHtml = "何ということもない普通の人生。\n大学を出て一応大手と言われるゼネコンに入社し、現在一人暮らしの37歳。\n彼女はいない。"
        val lines = rawHtml.split("\n")
        assertEquals(3, lines.size)

        val fakeTranslations = listOf(
            "An ordinary life with nothing unusual.",
            "After graduating college, entered a major general contractor, currently 37 living alone.",
            "Has no girlfriend."
        )

        val joined = fakeTranslations.joinToString("<br>")
        assertEquals(
            "An ordinary life with nothing unusual.<br>After graduating college, entered a major general contractor, currently 37 living alone.<br>Has no girlfriend.",
            joined
        )
    }

    @Test
    fun testSearchResponseCreation() {
        val response = provider.newSearchResponse("That Time I Got Reincarnated as a Slime", "https://ncode.syosetu.com/n6316bn/") {
            posterUrl = ""
            rating = 500
            latestChapter = "Episode 249"
        }

        assertEquals("That Time I Got Reincarnated as a Slime", response.name)
        assertEquals("https://ncode.syosetu.com/n6316bn/", response.url)
        assertEquals("Syosetu (English)", response.apiName)
        assertEquals("Episode 249", response.latestChapter)
    }

    @Test
    fun testStreamResponseCreation() = runBlocking {
        val chapters = listOf(
            provider.newChapterData("Episode 1", "https://ncode.syosetu.com/n6316bn/1/"),
            provider.newChapterData("Episode 2", "https://ncode.syosetu.com/n6316bn/2/")
        )

        val response = provider.newStreamResponse("That Time I Got Reincarnated as a Slime", "https://ncode.syosetu.com/n6316bn/", chapters) {
            this.synopsis = "A man is killed by a robber and reincarnated in another world as a slime."
            this.author = "Fuse"
            this.tags = listOf("Isekai", "Fantasy", "Monster")
        }

        assertNotNull(response)
        assertEquals("That Time I Got Reincarnated as a Slime", response.name)
        assertEquals("Fuse", response.author)
        assertEquals(2, response.data.size)
        assertEquals("Episode 1", response.data[0].name)
        assertEquals("https://ncode.syosetu.com/n6316bn/1/", response.data[0].url)
        assertEquals(3, response.tags?.size)
    }
}
