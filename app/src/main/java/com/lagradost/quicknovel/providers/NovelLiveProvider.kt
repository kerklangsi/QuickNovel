package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.*
import org.jsoup.nodes.Document

open class NovelLiveProvider : LibReadProvider() {
    override val name = "NovelLive" //same as allnovelupdates
    override val mainUrl = "https://novellive.app"
    override val hasMainPage = true
    override val usesCloudFlareKiller = true
    override val iconId = R.drawable.icon_novellive
    override val iconBackgroundId = R.color.wuxiaWorldOnlineColor

    override val orderBys = listOf(
        "All" to "latest-novels",
        "Latest" to "latest-release-novels",
        "Popular" to "most-popular-novels",
        "Complete" to "completed-novels",
    )

    fun parseNovelRow(row: org.jsoup.nodes.Element): SearchResponse? {
        val h3 = row.selectFirst("h3.tit > a") ?: return null
        val title = h3.attr("title").ifBlank { h3.text() }
        val href = h3.attr("href")
        if (href.isBlank()) return null

        return newSearchResponse(name = title, url = href) {
            posterUrl = fixUrlNull(row.selectFirst("div.pic img")?.attr("src"))
            latestChapter = row.selectFirst("div.desc div.item a.chapter")?.text()
        }
    }

    fun parseNovelList(document: Document): List<SearchResponse> =
        document.select("div.ul-list1.ul-list1-2.ss-custom > div.li-row").mapNotNull { parseNovelRow(it) }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        val url = if (!tag.isNullOrBlank()) "$mainUrl/genres/$tag/$page"
            else "$mainUrl/list/${if (orderBy.isNullOrBlank()) "latest-novels" else orderBy}/$page"
        val document = app.get(url).document
        val returnValue = parseNovelList(document)
        return HeadMainPageResponse(url, returnValue)
    }

    suspend fun getChapterList(document: Document, novelUrl: String): List<ChapterData> {
        val novelSlug = novelUrl.removeSuffix("/").substringAfterLast("/")
        val novelId = document.selectFirst("select#indexselect")?.attr("novel-id")
            ?: document.selectFirst(".stars-section li[data-novel-id]")?.attr("data-novel-id") ?: novelSlug

        val res = app.get("$mainUrl/ajax/get-list-chapter?novel_id=$novelId")
        val parsed = res.parsedSafe<AjaxChapterResponse>()
        if (parsed?.success == true && !parsed.chapters.isNullOrEmpty()) {
            return parsed.chapters.mapNotNull { ch ->
                val chId = ch.chapterId ?: return@mapNotNull null
                val chName = ch.chapterName ?: chId
                newChapterData(chName, "$mainUrl/book/$novelSlug/$chId")
            }
        }

        // Fallback to table of contents in HTML
        val htmlChapters = document.select("div.m-newest2 ul.ul-list5 li a.con")
        return htmlChapters.mapNotNull { a ->
            val href = a.attr("href")
            if (href.isBlank()) return@mapNotNull null
            val title = a.attr("title").ifBlank { a.text() }
            newChapterData(title, href)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val name = document.selectFirst("h1.tit, div.m-desc h1")?.text() ?: return null

        val chapters = getChapterList(document, url)

        return newStreamResponse(url = url, name = name, data = chapters) {
            posterUrl = fixUrlNull(document.selectFirst("div.m-imgtxt div.pic img")?.attr("src"))
            author = document.selectFirst("div.item:has(span.glyphicon-user) a, div.m-imgtxt a[href*='/author/']")?.text()

            tags = document.select("div.item:has(span.glyphicon-th-list) a")
                .ifEmpty { document.select("div.m-imgtxt a[href*='/genres/']") }
                .mapNotNull { it.text().trim().ifBlank { null } }

            synopsis = document.selectFirst("div.m-desc div.txt div.inner, div.m-desc div.txt")?.text()

            val voteElement = document.selectFirst("div.m-desc div.score p.vote")
            if (voteElement != null) {
                val voteText = voteElement.text()
                peopleVoted = voteText.substringAfter('(').substringBefore("votes").filter { it.isDigit() }.toIntOrNull()
            }

            val statusText = document.selectFirst(
                "div.item:has(span.glyphicon-time) span a, div.item:has(span.glyphicon-time) span")?.text()
            setStatus(statusText)
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url)
        val document = response.document

        document.select("script, style, iframe, .ads, .ads-holder, .slot-frame, .notice-text").remove()

        val content = document.selectFirst("#main1 > div > div > div.txt, div.txt") ?: return null

        content.select(".chapter-nav, .btn-more, .page").remove()
        return content.html()
    }

    data class AjaxChapterResponse(
        @JsonProperty("success") val success: Boolean? = null,
        @JsonProperty("chapters") val chapters: List<AjaxChapterItem>? = null,
    )

    data class AjaxChapterItem(
        @JsonProperty("_id") val id: String? = null,
        @JsonProperty("chapter_name") val chapterName: String? = null,
        @JsonProperty("chapter_id") val chapterId: String? = null,
    )
}
