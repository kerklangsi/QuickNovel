package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.util.GoogleTranslateOnline
import com.lagradost.quicknovel.util.amap
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.jsoup.Jsoup

class SyosetuEngProvider : MainAPI() {
    private val original = SyosetuProvider()

    override val name = "Syosetu (English)"
    override val mainUrl = original.mainUrl
    override val iconId = original.iconId
    override val iconBackgroundId = original.iconBackgroundId
    override val lang = "en"
    override val hasMainPage = original.hasMainPage
    override val mainCategories = original.mainCategories
    override val orderBys = original.orderBys
    override val tags = original.tags
    override val usesCloudFlareKiller = original.usesCloudFlareKiller
    override val rateLimitTime = original.rateLimitTime
    override val hasReviews = original.hasReviews

    private fun extractSearchTerms(query: String): List<String> {
        val clean = query.trim()
        if (clean.length < 20) return listOf(clean)

        val terms = mutableListOf(clean)
        clean.split(',', ':', '!', '?', '"', '“', '”', '「', '」', '~', '～', '-')
            .map { it.trim() }
            .filter { it.length >= 4 && !terms.contains(it) }
            .forEach { terms.add(it) }
        return terms
    }

    private suspend fun translateText(text: String, from: String = "ja", to: String = "en"): String {
        if (text.isBlank()) return text
        val res = GoogleTranslateOnline.onlineTranslate(listOf(text), from = from, to = to)
        return res.firstOrNull()?.ifBlank { text } ?: text
    }

    private suspend fun translateList(
        texts: List<String>,
        from: String = "ja",
        to: String = "en"
    ): List<String> {
        if (texts.isEmpty()) return emptyList()
        val result = GoogleTranslateOnline.onlineTranslate(texts, from = from, to = to)
        return if (result.size == texts.size) result else texts.amap { translateText(it, from = from, to = to) }
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val response = original.loadMainPage(page, mainCategory, orderBy, tag)
        val titles = response.list.map { it.name }
        val translatedTitles = translateList(titles, from = "ja", to = "en")

        val items = response.list.mapIndexed { index, item ->
            newSearchResponse(translatedTitles.getOrNull(index) ?: item.name, item.url) {
                posterUrl = item.posterUrl
                posterHeaders = item.posterHeaders
                rating = item.rating
                latestChapter = item.latestChapter
            }
        }
        return HeadMainPageResponse(response.url, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return emptyList()

        val terms = extractSearchTerms(cleanQuery)
        val candidateMap = LinkedHashMap<String, SearchResponse>()

        for (term in terms) {
            val jaQuery = translateText(term, from = "en", to = "ja")
            val queriesToSearch = if (jaQuery != term && jaQuery.isNotBlank()) listOf(jaQuery, term) else listOf(term)

            for (q in queriesToSearch) {
                original.search(q).forEach { candidateMap.putIfAbsent(it.url, it) }
            }
            if (candidateMap.size >= 20) break
        }

        val candidates = candidateMap.values.toList()
        if (candidates.isEmpty()) return emptyList()

        val titles = candidates.map { it.name }
        val translatedTitles = translateList(titles, from = "ja", to = "en")

        val scoredResults = candidates.mapIndexed { index, item ->
            val translatedName = translatedTitles.getOrNull(index) ?: item.name
            val lowerTitle = translatedName.lowercase()
            val lowerQuery = cleanQuery.lowercase()
            val score = maxOf(
                FuzzySearch.tokenSetRatio(lowerTitle, lowerQuery),
                FuzzySearch.partialRatio(lowerTitle, lowerQuery),
                FuzzySearch.weightedRatio(lowerTitle, lowerQuery)
            )
            score to newSearchResponse(translatedName, item.url) {
                posterUrl = item.posterUrl
                posterHeaders = item.posterHeaders
                rating = item.rating
                latestChapter = item.latestChapter
            }
        }

        return scoredResults.sortedByDescending { it.first }.map { it.second }
    }

    override suspend fun load(url: String): LoadResponse {
        val rawResponse = original.load(url)

        return coroutineScope {
            val titleAsync = async { translateText(rawResponse.name, from = "ja", to = "en") }
            val synopsisAsync = async { rawResponse.synopsis?.let { translateText(it, from = "ja", to = "en") } }
            val authorAsync = async { rawResponse.author?.let { translateText(it, from = "ja", to = "en") } }
            val tagsAsync = async {
                val tags = rawResponse.tags
                if (!tags.isNullOrEmpty()) translateList(tags, from = "ja", to = "en") else tags
            }

            val translatedTitle = titleAsync.await()
            val translatedSynopsis = synopsisAsync.await()
            val translatedAuthor = authorAsync.await()
            val translatedTags = tagsAsync.await()

            when (rawResponse) {
                is StreamResponse -> {
                    newStreamResponse(translatedTitle, rawResponse.url, rawResponse.data) {
                        posterUrl = rawResponse.posterUrl
                        posterHeaders = rawResponse.posterHeaders
                        this.synopsis = translatedSynopsis
                        this.author = translatedAuthor
                        this.status = rawResponse.status
                        this.tags = translatedTags
                        this.rating = rawResponse.rating
                        this.peopleVoted = rawResponse.peopleVoted
                        this.views = rawResponse.views
                    }
                }
                else -> rawResponse
            }
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val rawHtml = original.loadHtml(url) ?: return null
        val document = Jsoup.parse(rawHtml)
        val pElements = document.select("p")
        return if (pElements.isNotEmpty()) {
            val pTexts = pElements.map { it.text() }
            val translated = translateList(pTexts, from = "ja", to = "en")
            pElements.forEachIndexed { i, p -> translated.getOrNull(i)?.let { p.text(it) } }
            document.body().html()
        } else {
            val lines = rawHtml.split("\n")
            val translatedLines = translateList(lines, from = "ja", to = "en")
            translatedLines.joinToString("<br>")
        }
    }
}
