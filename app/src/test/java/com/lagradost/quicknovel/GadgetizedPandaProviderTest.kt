package com.lagradost.quicknovel

import com.lagradost.quicknovel.providers.GadgetizedPandaProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class GadgetizedPandaProviderTest {
    private val provider = GadgetizedPandaProvider()

    // Test 1: Devil Princess multi-volume ordering and non-interleaving
    @Test
    fun testDevilPrincessMultiVolumeOrdering() {
        val input = listOf(
            provider.newChapterData("Volume 5 - Prologue", "http://example.com/v5pro"),
            provider.newChapterData("Volume 5 - Chapter 1 - Part 1", "http://example.com/v5c1p1"),
            provider.newChapterData("Volume 5 - Chapter 6", "http://example.com/v5c6"),
            provider.newChapterData("Volume 4 - Illustrations", "http://example.com/v4ill"),
            provider.newChapterData("Volume 4 - Chapter 1 - Part 1", "http://example.com/v4c1p1"),
            provider.newChapterData("Volume 4 - Chapter 14 - Part 2", "http://example.com/v4c14p2"),
            provider.newChapterData("Volume 4 - Extra - Part 1", "http://example.com/v4extrap1"),
            provider.newChapterData("Volume 4 - Afterword", "http://example.com/v4after"),
            provider.newChapterData("Volume 1 - Illustrations", "http://example.com/v1ill"),
            provider.newChapterData("Volume 1 - Chapter 1", "http://example.com/v1c1")
        )
        val sorted = provider.sortChapters(input)
        assertEquals("Volume 1 - Illustrations", sorted[0].name)
        assertEquals("Volume 1 - Chapter 1", sorted[1].name)
        assertEquals("Volume 4 - Illustrations", sorted[2].name)
        assertEquals("Volume 4 - Chapter 1 - Part 1", sorted[3].name)
        assertEquals("Volume 4 - Chapter 14 - Part 2", sorted[4].name)
        assertEquals("Volume 4 - Extra - Part 1", sorted[5].name)
        assertEquals("Volume 4 - Afterword", sorted[6].name)
        assertEquals("Volume 5 - Prologue", sorted[7].name)
        assertEquals("Volume 5 - Chapter 1 - Part 1", sorted[8].name)
        assertEquals("Volume 5 - Chapter 6", sorted[9].name)
    }

    // Test 2: Single-page TOC sorting
    @Test
    fun testSinglePageTOC() {
        val input = listOf(
            provider.newChapterData("Volume 1 - Chapter 2", "http://example.com/c2"),
            provider.newChapterData("Volume 1 - Chapter 1", "http://example.com/c1"),
            provider.newChapterData("Volume 1 - Illustrations", "http://example.com/c0")
        )
        val sorted = provider.sortChapters(input)
        assertEquals("Volume 1 - Illustrations", sorted[0].name)
        assertEquals("Volume 1 - Chapter 1", sorted[1].name)
        assertEquals("Volume 1 - Chapter 2", sorted[2].name)
    }

    // Test 3: Volume split across pages (Page 1 has Vol 5, Page 2 has Vol 1)
    @Test
    fun testVolumeSplitAcrossPages() {
        val page1Elements = listOf(
            provider.newChapterData("Volume 5 - Chapter 1", "http://example.com/p1_v5c1"),
            provider.newChapterData("Volume 5 - Chapter 2", "http://example.com/p1_v5c2")
        )
        val page2Elements = listOf(
            provider.newChapterData("Volume 1 - Chapter 1", "http://example.com/p2_v1c1"),
            provider.newChapterData("Volume 1 - Chapter 2", "http://example.com/p2_v1c2")
        )
        val combined = page1Elements + page2Elements
        val sorted = provider.sortChapters(combined)
        assertEquals("Volume 1 - Chapter 1", sorted[0].name)
        assertEquals("Volume 1 - Chapter 2", sorted[1].name)
        assertEquals("Volume 5 - Chapter 1", sorted[2].name)
        assertEquals("Volume 5 - Chapter 2", sorted[3].name)
    }

    // Test 4: Multiple volumes on one page (Vol 4 and Vol 5)
    @Test
    fun testMultipleVolumesOnOnePage() {
        val input = listOf(
            provider.newChapterData("Volume 4 - Chapter 1", "http://example.com/v4c1"),
            provider.newChapterData("Volume 5 - Chapter 1", "http://example.com/v5c1"),
            provider.newChapterData("Volume 4 - Chapter 2", "http://example.com/v4c2")
        )
        val sorted = provider.sortChapters(input)
        assertEquals("Volume 4 - Chapter 1", sorted[0].name)
        assertEquals("Volume 4 - Chapter 2", sorted[1].name)
        assertEquals("Volume 5 - Chapter 1", sorted[2].name)
    }

    // Test 5: Chapter numeric sorting (decimals, Chapter 2 vs Chapter 10)
    @Test
    fun testNumericChapterSorting() {
        val input = listOf(
            provider.newChapterData("Volume 1 - Chapter 10", "http://example.com/10"),
            provider.newChapterData("Volume 1 - Chapter 1.5", "http://example.com/1.5"),
            provider.newChapterData("Volume 1 - Chapter 2", "http://example.com/2"),
            provider.newChapterData("Volume 1 - Chapter 1", "http://example.com/1")
        )
        val sorted = provider.sortChapters(input)
        assertEquals("Volume 1 - Chapter 1", sorted[0].name)
        assertEquals("Volume 1 - Chapter 1.5", sorted[1].name)
        assertEquals("Volume 1 - Chapter 2", sorted[2].name)
        assertEquals("Volume 1 - Chapter 10", sorted[3].name)
    }

    // Test 6: Chapter parts ordering (and removal of parent placeholder when parts exist)
    @Test
    fun testChapterPartsOrder() {
        val input = listOf(
            provider.newChapterData("Volume 1 - Chapter 2", "http://example.com/c2"),
            provider.newChapterData("Volume 1 - Chapter 1 - Part 2", "http://example.com/c1p2"),
            provider.newChapterData("Volume 1 - Chapter 1 - Part 1", "http://example.com/c1p1"),
            provider.newChapterData("Volume 1 - Chapter 1", "http://example.com/c1")
        )
        val sorted = provider.sortChapters(input)
        assertEquals(3, sorted.size)
        assertEquals("Volume 1 - Chapter 1 - Part 1", sorted[0].name)
        assertEquals("Volume 1 - Chapter 1 - Part 2", sorted[1].name)
        assertEquals("Volume 1 - Chapter 2", sorted[2].name)
    }

    // Test 7: Special entries within same volume (Illustrations, Prologue, Epilogue, Afterword)
    @Test
    fun testSpecialEntriesPositions() {
        val input = listOf(
            provider.newChapterData("Volume 2 - Afterword", "http://example.com/v2after"),
            provider.newChapterData("Volume 2 - Chapter 1", "http://example.com/v2c1"),
            provider.newChapterData("Volume 2 - Illustrations", "http://example.com/v2ill"),
            provider.newChapterData("Volume 2 - Prologue", "http://example.com/v2pro"),
            provider.newChapterData("Volume 2 - Epilogue", "http://example.com/v2epi")
        )
        val sorted = provider.sortChapters(input)
        assertEquals("Volume 2 - Illustrations", sorted[0].name)
        assertEquals("Volume 2 - Prologue", sorted[1].name)
        assertEquals("Volume 2 - Chapter 1", sorted[2].name)
        assertEquals("Volume 2 - Epilogue", sorted[3].name)
        assertEquals("Volume 2 - Afterword", sorted[4].name)
    }

    // Test 8: Sub-parts normalization after Ko-fi links and href slug fallbacks
    @Test
    fun testNormalizeChaptersAndPartsKofiAndHrefSlugs() {
        val doc = org.jsoup.Jsoup.parse("""
            <div class="entry-content">
                <h2>Volume 4</h2>
                <p><a href="https://gadgetizedpanda.net/2025/08/02/the-devil-princess-volume-4-chapter-9/">Chapter 9</a></p>
                <p><a href="https://ko-fi.com/Post/The-Devil-Princess-Volume-4-Chapter-10-C0C61F8FZO/">Chapter 10</a></p>
                <p><a href="https://gadgetizedpanda.net/2025/08/09/the-devil-princess-volume-4-chapter-10-part-1/">part 1</a></p>
                <p><a href="https://gadgetizedpanda.net/2025/08/16/the-devil-princess-volume-4-chapter-10-part-2/">part 2</a></p>
                <h2>Volume 3</h2>
                <p><a href="https://ko-fi.com/Post/Aristocratic-Daughters-Volume-3-Chapter-3-H2H117SXOF/">Chapter 3</a></p>
                <p><a href="https://gadgetizedpanda.net/2025/01/22/aristocratic-daughters-volume-3-chapter-3-part-1/">part 1</a></p>
                <p><a href="https://gadgetizedpanda.net/2025/02/09/aristocratic-daughters-volume-3-chapter-3-5/">Chapter 3.5</a></p>
                <p><a href="https://ko-fi.com/Post/Aristocratic-Daughters-Volume-3-Chapter-4-A0A417SZXJ/">Chapter 4</a></p>
                <p><a href="https://gadgetizedpanda.net/2025/02/28/aristocratic-daughters-volume-3-chapter-4part1/">part 1</a></p>
            </div>
        """.trimIndent())
        val elements = doc.select("div.entry-content").first()?.children()?.toList() ?: emptyList()
        val chapters = provider.normalizeChaptersAndParts(elements)
        val names = chapters.map { it.name }
        
        // Devil Princess Volume 4 Chapter 10 sub-parts (parent Chapter 10 removed in favor of Part 1/2)
        org.junit.Assert.assertFalse(names.contains("Volume 4 - Chapter 10"))
        org.junit.Assert.assertTrue(names.contains("Volume 4 - Chapter 10 - Part 1"))
        org.junit.Assert.assertTrue(names.contains("Volume 4 - Chapter 10 - Part 2"))
        
        // Aristocratic Daughters Volume 3 Chapter 3/4 sub-parts (parent chapters replaced by part 1)
        org.junit.Assert.assertFalse(names.contains("Volume 3 - Chapter 3"))
        org.junit.Assert.assertTrue(names.contains("Volume 3 - Chapter 3 - Part 1"))
        org.junit.Assert.assertTrue(names.contains("Volume 3 - Chapter 3.5"))
        org.junit.Assert.assertFalse(names.contains("Volume 3 - Chapter 4"))
        org.junit.Assert.assertTrue(names.contains("Volume 3 - Chapter 4 - Part 1"))
    }

    // Test 9: Ko-fi multi-part links and parent duplicate suppression in Volume 5
    @Test
    fun testKofiMultiPartAndParentSuppression() {
        val doc = org.jsoup.Jsoup.parse("""
            <div class="entry-content">
                <p class="has-x-large-font-size">Volume 5</p>
                <p><a href="https://ko-fi.com/Post/Aristocratic-Daughters-Volume-5-Chapter-2-F1F01QKEMG/">Chapter 2</a></p>
                <p><a href="https://gadgetizedpanda.net/2026/01/26/aristocratic-daughters-volume-5-chapter-2-part-1/">part 1</a></p>
                <p><a href="https://gadgetizedpanda.net/2026/01/29/aristocratic-daughters-volume-5-chapter-2-part-2/">part 2</a></p>
                <p><a href="https://ko-fi.com/Post/Aristocratic-Daughters-Volume-5-Chapter-2-F1F01QKEMG/">part 3</a></p>
                <p><a href="https://ko-fi.com/Post/Aristocratic-Daughters-Volume-5-Chapter-3-I2I51TFNLN/">Chapter 3</a></p>
                <p><a href="https://ko-fi.com/Post/Aristocratic-Daughters-Volume-5-Chapter-3-I2I51TFNLN/">part 1</a></p>
                <p><a href="https://ko-fi.com/Post/Aristocratic-Daughters-Volume-5-Chapter-3-I2I51TFNLN/">part 2</a></p>
            </div>
        """.trimIndent())
        val elements = doc.select("div.entry-content").first()?.children()?.toList() ?: emptyList()
        val chapters = provider.sortChapters(provider.normalizeChaptersAndParts(elements))
        val names = chapters.map { it.name }

        org.junit.Assert.assertFalse(names.contains("Volume 5 - Chapter 2"))
        org.junit.Assert.assertTrue(names.contains("Volume 5 - Chapter 2 - Part 1"))
        org.junit.Assert.assertTrue(names.contains("Volume 5 - Chapter 2 - Part 2"))
        org.junit.Assert.assertTrue(names.contains("Volume 5 - Chapter 2 - Part 3"))

        org.junit.Assert.assertFalse(names.contains("Volume 5 - Chapter 3"))
        org.junit.Assert.assertTrue(names.contains("Volume 5 - Chapter 3 - Part 1"))
        org.junit.Assert.assertTrue(names.contains("Volume 5 - Chapter 3 - Part 2"))
    }

    // Test 10: FuzzySearch filtering and ranking
    @Test
    fun testFuzzySearchFilteringAndRanking() {
        val mockNovels = listOf(
            provider.newSearchResponse("Aristocratic Daughters Got Used to Me", "https://gadgetizedpanda.net/aristocratic"),
            provider.newSearchResponse("The Devil Princess", "https://gadgetizedpanda.net/devil-princess"),
            provider.newSearchResponse("When I Reincarnated Into a World Doomed by Card Games", "https://gadgetizedpanda.net/card-game"),
            provider.newSearchResponse("The Castle of Canaan", "https://gadgetizedpanda.net/canaan"),
            provider.newSearchResponse("Love Thy Dark Lord", "https://gadgetizedpanda.net/dark-lord")
        )

        // Exact match
        val search1 = provider.filterAndRankNovels(mockNovels, "Devil Princess")
        assertEquals("The Devil Princess", search1.first().name)

        // Typo tolerance
        val search2 = provider.filterAndRankNovels(mockNovels, "Dvil Princes")
        assertEquals("The Devil Princess", search2.first().name)

        // Substring / keyword match
        val search3 = provider.filterAndRankNovels(mockNovels, "Card Games")
        assertEquals("When I Reincarnated Into a World Doomed by Card Games", search3.first().name)

        // No matches for completely unrelated query
        val search4 = provider.filterAndRankNovels(mockNovels, "Zzz Xyz Random Story")
        org.junit.Assert.assertTrue(search4.isEmpty())
    }

    // Test 11: Shortlink and special chapter detection (rejects TOC page numbers)
    @Test
    fun testShortlinkAndNumericChapterDetection() {
        org.junit.Assert.assertTrue(provider.isChapterLink("https://gadgetizedpanda.net/?p=2220", "Prologue"))
        org.junit.Assert.assertTrue(provider.isChapterLink("https://gadgetizedpanda.net/?p=2226", "part 1"))
        org.junit.Assert.assertTrue(provider.isChapterLink("https://gadgetizedpanda.net/?p=123", "5"))
        org.junit.Assert.assertTrue(provider.isChapterLink("https://gadgetizedpanda.net/?p=456", "Side Story 1"))
        org.junit.Assert.assertTrue(provider.isChapterLink("https://gadgetizedpanda.net/?p=789", "Interlude"))
        org.junit.Assert.assertTrue(provider.isChapterLink("https://gadgetizedpanda.net/?p=5826&preview=true", "part 2"))

        // Multi-page TOC pagination links should NOT be detected as chapters
        org.junit.Assert.assertFalse(provider.isChapterLink("https://gadgetizedpanda.net/2023/10/22/leave-this-to-me/2/", "2"))
        org.junit.Assert.assertFalse(provider.isChapterLink("https://gadgetizedpanda.net/2023/10/22/leave-this-to-me/", "1"))
    }

    // Test 12: Top navigation columns do not break chapter parsing
    @Test
    fun testTopNavigationColumnsDoNotBreakChapterParsing() {
        val html = """
            <div class="entry-content">
                <div class="wp-block-columns"><p>Top Banner / Navigation</p></div>
                <p>Real chapter content starts here.</p>
                <p>Second paragraph of story.</p>
                <div class="wp-block-columns"><p>Bottom Navigation</p></div>
            </div>
        """.trimIndent()
        val doc = org.jsoup.Jsoup.parse(html)
        val content = provider.fetchChapterContent(doc)
        org.junit.Assert.assertTrue(content.contains("Real chapter content starts here."))
        org.junit.Assert.assertTrue(content.contains("Second paragraph of story."))
        org.junit.Assert.assertFalse(content.contains("Bottom Navigation"))
    }

    // Test 13: Ko-fi link with fragment anchor and range expansion (e.g. Chapter 26-30 #checkoutModal)
    @Test
    fun testKofiLinkWithFragmentAnchorAndChapterRange() {
        val link = "https://ko-fi.com/Post/Unwanted-Galactic-Uprising-Volume-3-Chapter-26-30-W7W81TE9B5/#checkoutModal"
        val chapters = provider.expandKofiLink(link, "Volume 3")
        org.junit.Assert.assertNotNull(chapters)
        assertEquals(5, chapters!!.size)
        assertEquals("Volume 3 - Chapter 26", chapters[0].name)
        assertEquals("https://gadgetizedpanda.net/unwanted-galactic-uprising-volume-3-chapter-26", chapters[0].url)
        assertEquals("Volume 3 - Chapter 30", chapters[4].name)
        assertEquals("https://gadgetizedpanda.net/unwanted-galactic-uprising-volume-3-chapter-30", chapters[4].url)
    }

    // Test 14: Ko-fi link expansion and Wayback snapshot resolution timing test
    @Test
    fun testKofiExpansionAndWaybackSnapshotResolution() = kotlinx.coroutines.runBlocking {
        val link = "https://ko-fi.com/Post/Unwanted-Galactic-Uprising-Volume-3-Chapter-26-30-W7W81TE9B5/#checkoutModal"
        val startExp = System.currentTimeMillis()
        val chapters = provider.expandKofiLink(link, "Volume 3")
        val expTime = System.currentTimeMillis() - startExp
        org.junit.Assert.assertNotNull(chapters)
        println("Ko-fi link expanded in: ${expTime}ms (${chapters!!.size} chapters)")

        val ch30 = chapters.last()
        assertEquals("Volume 3 - Chapter 30", ch30.name)
        val startCdx = System.currentTimeMillis()
        val snapshot = provider.resolveSnapshotUrl(ch30.url)
        val cdxTime = System.currentTimeMillis() - startCdx
        println("Wayback CDX snapshot resolved in: ${cdxTime}ms -> $snapshot")
        org.junit.Assert.assertTrue(snapshot != null && snapshot.contains("web.archive.org/web/"))
    }

    // Test 15: Direct Ko-fi URL slug extraction without token and Wayback resolution
    @Test
    fun testDirectKofiSlugExtraction() {
        val kofiUrl = "https://ko-fi.com/Post/Aristocratic-Daughters-Volume-5-Chapter-2-F1F01QKEMG/"
        val slug = provider.extractKofiSlug(kofiUrl)
        assertEquals("aristocratic-daughters-volume-5-chapter-2", slug)

        val batchKofi = "https://ko-fi.com/Post/Unwanted-Galactic-Uprising-Volume-3-Chapter-26-30-W7W81TE9B5/#checkoutModal"
        val batchSlug = provider.extractKofiSlug(batchKofi)
        assertEquals("unwanted-galactic-uprising-volume-3-chapter-26-30", batchSlug)
    }

    // Test 16: Ko-fi link on sub-parts appends -part-X to canonical blog URL
    @Test
    fun testKofiSubPartsMapToUniquePartUrls() {
        val html = """
            <div class="entry-content">
                <h2>Volume 5</h2>
                <p>Chapter 3</p>
                <p>
                    <a href="https://ko-fi.com/Post/Aristocratic-Daughters-Volume-5-Chapter-3-I2I51TFNLN/#checkoutModal">Part 1</a>
                    <a href="https://ko-fi.com/Post/Aristocratic-Daughters-Volume-5-Chapter-3-I2I51TFNLN/#checkoutModal">Part 2</a>
                    <a href="https://ko-fi.com/Post/Aristocratic-Daughters-Volume-5-Chapter-3-I2I51TFNLN/#checkoutModal">Part 3</a>
                </p>
            </div>
        """.trimIndent()
        val doc = org.jsoup.Jsoup.parse(html)
        val chapters = provider.normalizeChaptersAndParts(doc.select("div.entry-content").first()!!.children())
        assertEquals(3, chapters.size)
        assertEquals("Volume 5 - Chapter 3 - Part 1", chapters[0].name)
        assertEquals("https://gadgetizedpanda.net/aristocratic-daughters-volume-5-chapter-3-part-1", chapters[0].url)
        assertEquals("Volume 5 - Chapter 3 - Part 2", chapters[1].name)
        assertEquals("https://gadgetizedpanda.net/aristocratic-daughters-volume-5-chapter-3-part-2", chapters[1].url)
        assertEquals("Volume 5 - Chapter 3 - Part 3", chapters[2].name)
        assertEquals("https://gadgetizedpanda.net/aristocratic-daughters-volume-5-chapter-3-part-3", chapters[2].url)
    }
}

