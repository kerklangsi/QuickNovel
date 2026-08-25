package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.util.amap
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class GadgetizedPandaProvider : MainAPI() {
    override val name = "GadgetizedPanda"
    override val mainUrl = "https://gadgetizedpanda.net"
    override val hasMainPage = true
    override val iconId = R.drawable.icon_gadgetizedpanda
    override val iconBackgroundId = R.color.colorPrimaryWhite
    override val lang = "en"

    companion object {
        private val METADATA = Regex("(?i)(?:\\b(vol(?:ume)?|chapter|ch|part)[-_\\s]*(\\d+(?:\\.\\d+)?)(?:[-_](\\d+))?|^\\s*(\\d+(?:\\.\\d+)?)[-_](\\d+)\\s*$)")
        private val KOFI_POST = Regex("(?i)post/(.*?)(?:-[A-Z0-9]{6,15})?/?(?:[?#].*)?$")
        private val CHAPTER_RANGE = Regex("(?i)(?:Chapter\\s*)?(\\d+)\\s*[-–—]\\s*(\\d+)")
        private val NON_ALPHANUM = Regex("[^a-z0-9]+")

        private val PROMO_KEYWORDS = listOf(
            "amazon link", "green button above", "source material", "english translations", "support the author", "page translated", "green bar", "ln translations",
            "localizermeerkat", "galaxianarwhal", "check out", "join the membership", "gadgetizedpanda", "always read at", "table of contents", "support me", "ko-fi link",
            "buy the official release", "donation for faster release", "translation requests", "consider to donate", "faster translations", "donate for faster",
            "translators note", "translator note", "tlnote", "tl note", "scene transition", "picked up for english translation", "picked up for translation"
        )
    }

    private fun String.cleanUrl(): String = substringBefore('#').substringBefore('?').trimEnd('/')
    private fun String.extractVolumeNumber(): Int? =
        METADATA.findAll(replace('\u00A0', ' ')).firstOrNull { it.groupValues[1].startsWith("vol", true) }?.groupValues?.get(2)?.toDoubleOrNull()?.toInt()

    private fun String.extractChapterNumber(): Double? {
        if (contains("final chapter", true)) return null
        val clean = replace('\u00A0', ' ')
        for (m in METADATA.findAll(clean)) {
            val kw = m.groupValues[1]
            if (kw.isNotEmpty()) {
                if (kw.startsWith("chapter", true) || kw.equals("ch", true)) {
                    return m.groupValues[2].toDoubleOrNull()
                }
            } else {
                val part = m.groupValues[5].toIntOrNull()
                if (part != null && part <= 20 && part !in listOf(25, 50, 75)) {
                    return m.groupValues[4].toDoubleOrNull()
                }
            }
        }
        return null
    }

    private fun String.extractPartNumber(): Int? {
        val clean = replace('\u00A0', ' ')
        for (m in METADATA.findAll(clean)) {
            val kw = m.groupValues[1]
            if (kw.isNotEmpty()) {
                if (kw.startsWith("part", true)) {
                    return m.groupValues[2].toDoubleOrNull()?.toInt()
                } else if (kw.startsWith("chapter", true) || kw.equals("ch", true)) {
                    val sub = m.groupValues[3].toIntOrNull()
                    if (sub != null && sub <= 20 && sub !in listOf(25, 50, 75)) return sub
                }
            } else {
                val part = m.groupValues[5].toIntOrNull()
                if (part != null && part <= 20 && part !in listOf(25, 50, 75)) return part
            }
        }
        return null
    }
    private fun Double.formatNum() = if (this % 1.0 == 0.0) toInt().toString() else toString()
    private fun String.toSlug() = lowercase().replace(NON_ALPHANUM, "-").trim('-')
    fun extractKofiSlug(url: String): String? = KOFI_POST.find(url)?.groupValues?.get(1)?.toSlug()

    private val categoryPages = listOf(
        "Translation Projects" to mainUrl,
        "Personal Projects" to "$mainUrl/page/2/",
        "Caught up projects" to "$mainUrl/page/3/"
    )

    override val mainCategories = listOf("All Projects" to "") + categoryPages

    // Loads novel directory pages based on selected project category or pagination index.
    override suspend fun loadMainPage(page: Int, mainCategory: String?, orderBy: String?, tag: String?): HeadMainPageResponse {
        if (mainCategory.isNullOrEmpty() || mainCategory == "All Projects") {
            if (page > 1) return HeadMainPageResponse(mainUrl, emptyList())
            val allNovels = categoryPages.amap { (_, pageUrl) -> parseNovels(app.get(pageUrl).document) }.flatten().distinctBy { it.url }.sortedBy { it.name.lowercase() }
            return HeadMainPageResponse(mainUrl, allNovels)
        }
        val entry = categoryPages.firstOrNull { it.second == mainCategory } ?: return HeadMainPageResponse(mainUrl, emptyList())
        if (page > 1) return HeadMainPageResponse(entry.second, emptyList())
        return HeadMainPageResponse(entry.second, parseNovels(app.get(entry.second).document))
    }

    // Extracts the first valid image URL from common lazy-loading and responsive image attributes.
    private fun Element?.extractImgSrc(): String? = this?.let { el ->
        listOf("data-src", "data-lazy-src", "src", "data-full-url", "srcset", "data-srcset")
            .firstNotNullOfOrNull { el.attr(it).trim().takeIf(String::isNotEmpty)?.substringBefore(" ") }
            ?.let(::fixUrlNull)
    }

    // Parses novel cards and covers from WordPress page content and navigation menus.
    private fun parseNovels(doc: Document): List<SearchResponse> {
        val elements = doc.select("div.entry-content > *")
        val coverMap = mutableMapOf<String, String>()

        for (i in elements.indices) {
            val el = elements[i]
            val src = el.selectFirst("img").extractImgSrc() ?: continue
            if (src.contains("kofi", true) || src.contains("button", true) || src.contains("w=139")) continue

            for (cand in listOfNotNull(el, elements.getOrNull(i - 1), elements.getOrNull(i + 1))) {
                for (a in cand.select("a[href]")) {
                    val h = fixUrl(a.attr("href").trim()).cleanUrl()
                    val titleKey = a.text().filter(Char::isLetterOrDigit).lowercase()
                    coverMap[h] = src
                    coverMap[h.substringAfterLast('/')] = src
                    if (titleKey.isNotEmpty()) coverMap[titleKey] = src
                }
            }
        }

        val menuNames = doc.select("ul#main-menu ul.sub-menu a[href], nav#site-navigation ul.sub-menu a[href], div.menu-menu-container ul.sub-menu a[href]")
            .associate { fixUrl(it.attr("href").trim()).cleanUrl().substringAfterLast('/') to it.text().trim() }

        return elements.mapNotNull { el ->
            val a = el.selectFirst("a[href]") ?: return@mapNotNull null
            val href = fixUrl(a.attr("href").trim())
            val rawName = a.text().trim()
            val cleanHref = href.cleanUrl()
            val slug = cleanHref.substringAfterLast('/')
            val name = menuNames[slug]?.takeIf(String::isNotEmpty) ?: rawName

            if (rawName.isEmpty() || rawName in listOf("A-G", "H-Z") || href == "#" || href == mainUrl || href == "$mainUrl/" ||
                listOf("/page/", "post_type=post", "announcement", "ko-fi.com").any { href.contains(it, true) }) return@mapNotNull null

            val poster = coverMap[cleanHref] ?: coverMap[slug] ?: coverMap[rawName.filter(Char::isLetterOrDigit).lowercase()] ?: coverMap[href]
            newSearchResponse(name, href) { posterUrl = fixUrlNull(poster) }
        }.distinctBy { it.url }.sortedBy { it.name.lowercase() }
    }

    // Filters and ranks novels by query relevance using FuzzySearch.
    fun filterAndRankNovels(novels: List<SearchResponse>, query: String): List<SearchResponse> {
        if (query.isBlank()) return novels
        val clean = query.trim().lowercase()
        return novels.mapNotNull { novel ->
            val score = maxOf(FuzzySearch.partialRatio(novel.name.lowercase(), clean), FuzzySearch.weightedRatio(novel.name.lowercase(), clean))
            if (score > 50) novel to score else null
        }.sortedByDescending { it.second }.map { it.first }
    }

    // Fetches novel listings across project categories and filters by query using FuzzySearch.
    override suspend fun search(query: String): List<SearchResponse> = filterAndRankNovels(
        categoryPages.amap { (_, pageUrl) -> parseNovels(app.get(pageUrl).document) }.flatten().distinctBy { it.url },
        query
    )

    private fun Element.isHeading() = tagName().lowercase() in listOf("h1", "h2", "h3", "h4", "h5", "h6")
    private fun Document.entryContent() = selectFirst("div#page div#content div#primary main#main article div.entry-content, div.entry-content")
    private fun Document.isDeleted404() = selectFirst("body.error404, section.error-404") != null
    fun Document.isDomainDown() = selectFirst("p.site-label, div.card h1, a.btn-archive, a.btn-kofi") != null

    // Filters out promo banners, affiliate links, pagination numbers, separator elements, and donation buttons from chapter content.
    private fun Element.isUnwanted(): Boolean {
        val tag = tagName().lowercase()
        if (hasClass("page-links") || hasClass("post-nav-links") || selectFirst(".page-links, .post-page-numbers") != null) return true
        if (tag == "hr" && (hasClass("wp-block-separator") || hasClass("has-alpha-channel-opacity") || hasClass("is-style-wide"))) return true
        if (tag == "p" && hasClass("has-black-color") && hasClass("has-text-color")) return true
        if (selectFirst("img[src*='image.png'], img[src*='kofi']") != null || select("a[href*='ko-fi.com']").any { !it.attr("href").contains("/post/", true) }) return true
        if ((tag in listOf("p", "div")) && selectFirst("img, svg, picture, video") == null && text().none(Char::isLetterOrDigit)) return true

        val cleanText = text().lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }
        if (cleanText.contains("table of contents") || cleanText in listOf("index", "toc")) return true
        return (tag in listOf("p", "div", "figure") || isHeading()) && PROMO_KEYWORDS.any { cleanText.contains(it) }
    }

    // Validates whether a link is a legitimate chapter or special content link.
    fun isChapterLink(href: String, rawTitle: String, baseUrl: String = ""): Boolean {
        val h = href.lowercase().trim()
        val t = rawTitle.lowercase().trim()
        if (h.isEmpty() || h.startsWith("#") || h.contains("#comment") || h.contains("web.archive.org/web/")) return false
        val cleanH = h.cleanUrl()
        val cleanB = baseUrl.lowercase().cleanUrl()
        if (cleanB.isNotEmpty() && (cleanH == cleanB || (cleanH.startsWith(cleanB) && cleanH.removePrefix(cleanB).trim('/').toIntOrNull() != null))) return false
        if (t.matches(Regex("""^[\d/\s\-_.:]+$"""))) return false
        val isAllowed = listOf("https://gadgetizedpanda.net", "gadgetizedpanda", "ko-fi.com/post/", "preview=true", "?p=").any { h.contains(it) }
        val keywords = listOf("chapter", "illust", "prologue", "part", "epilogue", "afterword", "extra", "interlude", "side story", "ending", "final")
        val hasKeyword = keywords.any { t.contains(it) || h.contains(it) } || Regex("""\b(ss|side-story)\b""").containsMatchIn(t) || Regex("""[-_/]ss[-_/]""").containsMatchIn(h)
        return isAllowed && (hasKeyword || h.contains("?p="))
    }

    // Expands structured Ko-fi multi-chapter post links into individual chapter entries.
    fun expandKofiLink(href: String, currentVolume: String): List<ChapterData>? {
        val slug = extractKofiSlug(href) ?: return null
        val range = extractChapterRange(slug, href) ?: return null
        val volNum = slug.extractVolumeNumber() ?: currentVolume.filter(Char::isDigit).toIntOrNull() ?: 1
        val novelSlug = slug.substringBefore("-volume", slug.substringBefore("-vol", slug.substringBefore("-chapter", "")))
            .takeIf { !it.startsWith("vol") && !it.startsWith("chapter") }?.toSlug().orEmpty()
        val prefix = if (novelSlug.isNotEmpty()) "$novelSlug-" else ""
        return range.map { newChapterData(standardizeChapterTitle("Chapter $it", "Volume $volNum"), "$mainUrl/${prefix}volume-$volNum-chapter-$it") }
    }

    // Extracts start and end chapter numbers from range strings (e.g. 'Chapter 21-30').
    fun extractChapterRange(title: String, href: String): IntRange? {
        val titleMatch = CHAPTER_RANGE.find(title)
        if (titleMatch != null) {
            val start = titleMatch.groupValues[1].toIntOrNull() ?: return null
            val end = titleMatch.groupValues[2].toIntOrNull() ?: return null
            return if (end in (start + 1)..(start + 10)) start..end else null
        }
        if (title.extractChapterNumber() != null) return null
        val hrefMatch = CHAPTER_RANGE.find(href) ?: return null
        val start = hrefMatch.groupValues[1].toIntOrNull() ?: return null
        val end = hrefMatch.groupValues[2].toIntOrNull() ?: return null
        return if (end in (start + 1)..(start + 10)) start..end else null
    }

    // Queries the Wayback Machine CDX API concurrently using amap to find the latest valid snapshot as fast as possible.
    suspend fun resolveSnapshotUrl(exactUrl: String): String? {
        val slugHint = exactUrl.substringAfter('#', "").takeIf { it.isNotEmpty() && !it.startsWith("comment") }
        val slug = extractKofiSlug(exactUrl) ?: slugHint ?: exactUrl.cleanUrl().substringAfterLast('/').toSlug().ifEmpty { return null }
        val cdx = "https://web.archive.org/cdx/search/cdx?fl=original,timestamp&filter=statuscode:200&limit=1&output=json"
        return listOf("gadgetizedpanda.com", "gadgetizedpanda.net").map { "$cdx&url=$it&matchType=prefix&filter=original:.*/$slug/?$" }
            .amap { app.get(it).parsedSafe<List<List<String>>>()?.getOrNull(1)?.let { row -> "https://web.archive.org/web/${row[1]}/${row[0]}" } }
            .firstOrNull { !it.isNullOrEmpty() }
    }

    // Cleans and extracts HTML text paragraphs from a chapter post.
    fun fetchChapterContent(doc: Document): String {
        val entryContent = doc.entryContent() ?: return ""
        entryContent.select("script, style, iframe, svg, noscript, .sharedaddy, .jp-relatedposts, .wpcnt, #jp-post-flair, .wp-block-spacer").remove()
        val builder = StringBuilder()
        var started = false
        for (element in entryContent.children()) {
            val tag = element.tagName().lowercase()
            if (started && tag == "div" && element.hasClass("wp-block-columns")) break
            if (element.isUnwanted()) continue
            if (!started && (tag in listOf("p", "figure") || element.isHeading())) started = true
            if (started) builder.appendLine(element.apply { select("a").unwrap() }.outerHtml())
        }
        return builder.toString().trim()
    }

    // Formats and standardizes chapter names with consistent volume, chapter, and part prefixes.
    fun standardizeChapterTitle(rawTitle: String, volume: String?): String {
        val title = rawTitle.trim()
        val chNum = title.extractChapterNumber()
        val partNum = title.extractPartNumber()
        val formatted = when {
            chNum != null && partNum != null -> "Chapter ${chNum.formatNum()} - Part $partNum"
            chNum == null && title.contains("illust", true) -> "illustrations"
            title.equals("prologue", true) -> "Prologue"
            title.equals("epilogue", true) -> "Epilogue"
            title.equals("afterword", true) -> "Afterword"
            else -> title
        }
        return volume?.let { "$it - ${formatted.removePrefix(it).trimStart(' ', '-', ':')}" } ?: formatted
    }

    // Normalizes, numbers, and associates chapters and parts under their respective volumes.
    fun normalizeChaptersAndParts(rawElements: List<Element>, novelSlug: String = "", baseUrl: String = ""): List<ChapterData> {
        val chapterList = mutableListOf<ChapterData>()
        var currentVolume = "Volume 1"
        var lastUnlinkedChapter: String? = null
        var lastLinkedChapter: String? = null

        for (element in rawElements) {
            if (element.isUnwanted()) continue
            val text = element.text().trim()
            val volNum = text.extractVolumeNumber()

            // 1. Detect Volume headers (<h*>, <p>, or .wp-block-heading) to update volume context
            if (volNum != null && (element.isHeading() || element.tagName().equals("p", true) || element.hasClass("wp-block-heading"))) {
                currentVolume = "Volume $volNum"; lastUnlinkedChapter = null; lastLinkedChapter = null; continue
            }
            val links = element.select("a[href]")

            // 2. Track unlinked chapter headings (e.g. "Chapter 1") that precede linked sub-parts ("Part 1", "Part 2")
            if (links.isEmpty()) { if (text.extractChapterNumber() != null) lastUnlinkedChapter = text; continue }
            for (link in links) {
                val href = link.attr("href").trim()
                val rawTitle = link.text().trim().ifEmpty {
                    extractKofiSlug(href) ?: href.cleanUrl().split('/').dropLastWhile { it.toIntOrNull() != null }.lastOrNull().orEmpty()
                }

                // 3. Skip non-chapter links (navigation anchors, comments, archive pages) or empty links
                if (rawTitle.isEmpty() || !isChapterLink(href, rawTitle, baseUrl)) continue

                // Check sub-parts & chapter numbers first (use href for final chapter links)
                val chNum = if (rawTitle.contains("final chapter", true)) href.extractChapterNumber() else (rawTitle.extractChapterNumber() ?: href.extractChapterNumber())
                val partNum = if (rawTitle.contains("final chapter", true)) href.extractPartNumber() else (rawTitle.extractPartNumber() ?: href.extractPartNumber())
                val isKofi = href.contains("ko-fi.com", true)

                // 4. Expand structured Ko-fi multi-chapter posts or batch ranges
                if (isKofi && partNum == null) {
                    expandKofiLink(href, currentVolume)?.let { kofi ->
                        chapterList.addAll(kofi)
                        kofi.last().name.extractChapterNumber()?.let { num -> lastLinkedChapter = "Chapter ${num.formatNum()}".also { lastUnlinkedChapter = it } }
                        continue
                    }
                    extractChapterRange(rawTitle, href)?.let { range ->
                        val base = extractKofiSlug(href)?.let { slug -> "$mainUrl/$slug" } ?: href
                        range.forEach { ch -> chapterList.add(newChapterData(standardizeChapterTitle("Chapter $ch", currentVolume), base)) }
                        continue
                    }
                }

                var title = rawTitle
                val baseCh = lastUnlinkedChapter ?: lastLinkedChapter
                // 6. Format chapter title: sub-parts without "Chapter" inherit predecessor, otherwise format with chNum
                if (!rawTitle.contains("Chapter", true) && partNum != null && baseCh != null && !rawTitle.contains("final", true)) {
                    title = "$baseCh - Part $partNum"
                } else if (chNum != null) {
                    val chStr = "Chapter ${chNum.formatNum()}".also { lastLinkedChapter = it; lastUnlinkedChapter = it }
                    title = if (partNum != null) "$chStr - Part $partNum" else if (!rawTitle.contains("Chapter", true) || rawTitle.contains("final", true)) chStr else rawTitle
                }

                // 8. Remove duplicate parent placeholder when sub-parts exist (ONLY if placeholder has NO part number)
                if (partNum != null && chNum != null) {
                    val chStr = "Chapter ${chNum.formatNum()}"
                    val parentName = standardizeChapterTitle(chStr, currentVolume)
                    chapterList.removeAll { it.name.extractPartNumber() == null && (it.name == parentName || it.name.startsWith("$parentName:") || it.name.startsWith("$parentName ") || it.name == "$currentVolume - Final Chapter" || it.name == "$currentVolume - Another Ending") }
                } else if (partNum != null && baseCh != null) {
                    val parentName = standardizeChapterTitle(baseCh, currentVolume)
                    chapterList.removeAll { it.name.extractPartNumber() == null && (it.name == parentName || it.name.startsWith("$parentName:") || it.name.startsWith("$parentName ")) }
                }

                // 9. Add formatted chapter entry to list (converting Ko-fi links to canonical blog URLs)
                val baseSlug = extractKofiSlug(href)
                val chapterUrl = if (baseSlug != null) {
                    val finalSlug = if (partNum != null && !baseSlug.contains("part")) "$baseSlug-part-$partNum" else baseSlug
                    "$mainUrl/$finalSlug"
                } else if ((href.contains("?p=", true) || href.contains("preview=true", true)) && novelSlug.isNotEmpty()) {
                    val chTitleSlug = standardizeChapterTitle(title, currentVolume).toSlug()
                    val slug = if (chTitleSlug.startsWith(novelSlug)) chTitleSlug else "$novelSlug-$chTitleSlug"
                    "$href#$slug"
                } else href
                chapterList.add(newChapterData(standardizeChapterTitle(title, currentVolume), chapterUrl))
            }
        }
        return chapterList
    }

    // Deterministic chapter sorting: Volume -> Chapter -> Part -> Special entries, filtering parent placeholders when parts exist
    fun sortChapters(chapters: List<ChapterData>): List<ChapterData> {
        val chaptersWithParts = chapters.mapNotNull { ch ->
            val vol = ch.name.extractVolumeNumber() ?: ch.url.extractVolumeNumber() ?: 1
            val cNum = ch.name.extractChapterNumber() ?: ch.url.extractChapterNumber()
            val pNum = ch.name.extractPartNumber() ?: ch.url.extractPartNumber()
            if (cNum != null && pNum != null) vol to cNum else null
        }.toSet()

        return chapters.filterNot { ch ->
            val vol = ch.name.extractVolumeNumber() ?: ch.url.extractVolumeNumber() ?: 1
            val cNum = ch.name.extractChapterNumber() ?: ch.url.extractChapterNumber()
            val pNum = ch.name.extractPartNumber() ?: ch.url.extractPartNumber()
            (cNum != null && pNum == null && (vol to cNum) in chaptersWithParts) ||
            (ch.name.contains("final chapter", true) && chaptersWithParts.any { it.first == vol && it.second == 7.0 }) ||
            (ch.name.contains("another ending", true) && chaptersWithParts.any { it.first == vol && it.second == 8.0 })
        }.mapIndexed { idx, ch -> ch to idx }.sortedWith(compareBy(
            { (ch, _) -> ch.name.extractVolumeNumber() ?: ch.url.extractVolumeNumber() ?: 1 },
            { (ch, idx) -> ch.name.extractChapterNumber() ?: ch.url.extractChapterNumber() ?: when {
                ch.name.contains("illust", true) -> -2.0
                ch.name.contains("prologue", true) -> -1.0
                ch.name.contains("extra", true) -> 9980.0
                ch.name.contains("epilogue", true) -> 9990.0
                ch.name.contains("afterword", true) -> 9995.0
                else -> 5000.0 + idx
            }},
            { (ch, _) -> ch.name.extractPartNumber() ?: ch.url.extractPartNumber() ?: 0 }, { (_, idx) -> idx }
        )).map { it.first }
    }

    // Collect all TOC pages; sortChapters() determines logical order and removes duplicate parent placeholders.
    suspend fun buildTableOfContents(doc: Document, baseUrl: String): List<ChapterData> {
        val cleanBaseUrl = baseUrl.cleanUrl()
        val maxPage = doc.select("a[href]").mapNotNull { a ->
            val href = a.attr("href").cleanUrl()
            if (href.startsWith(cleanBaseUrl)) href.substringAfterLast('/').toIntOrNull() else null
        }.maxOrNull() ?: 1
        val allRawElements = (maxPage downTo 1).flatMap {
            val pageDoc = if (it == 1) doc else app.get("$cleanBaseUrl/$it/").document
            pageDoc.select("div.entry-content > *, div#content > *")
                .filterNot { el -> el.tagName() in listOf("header", "footer", "nav", "aside") || el.id() in listOf("comments", "secondary") || el.hasClass("entry-meta") || el.hasClass("entry-header") || el.hasClass("widget-area") || el.hasClass("widget") }
                .distinct()
        }
        val novelSlug = cleanBaseUrl.substringAfterLast('/').replace("-ln", "").toSlug()
        return sortChapters(normalizeChaptersAndParts(allRawElements, novelSlug, cleanBaseUrl)).distinctBy { it.name }
    }

    // Extracts the novel synopsis from the main novel details page.
    fun fetchSynopsis(doc: Document): String {
        val builder = StringBuilder()
        var started = false
        for (el in doc.entryContent()?.children().orEmpty()) {
            val text = el.text().trim()
            if (!started && text.contains("Synopsis", true)) {
                started = true
                text.substringAfter("Synopsis", "").trimStart(':', ' ').takeIf(String::isNotEmpty)?.let { builder.appendLine(it).appendLine() }
            } else if (started) {
                if (el.tagName().equals("figure", true) || el.hasClass("wp-block-image") || text.startsWith("Index", true)) break
                if (text.isNotEmpty()) builder.appendLine(text).appendLine()
            }
        }
        return builder.toString().trim()
    }

    // Loads novel metadata, cover image, synopsis, and full chapter list for details view.
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1.entry-title")?.text()?.trim()
            ?: doc.selectFirst("title")?.text()?.substringBefore("–")?.substringBefore("-")?.trim()
            ?: throw ErrorLoadingException("Failed to find novel title for $url")
        return newStreamResponse(title, url, buildTableOfContents(doc, url)) {
            posterUrl = doc.selectFirst("figure.wp-block-image img, div.entry-content figure img, div.entry-content img").extractImgSrc()
            this.synopsis = fetchSynopsis(doc)
        }
    }

    // Loads chapter content from the live site, falling back to archived Wayback Machine snapshots.
    override suspend fun loadHtml(url: String): String? {
        val isKofi = url.contains("ko-fi.com", true)
        val isArchive = url.contains("web.archive.org", true)

        // 1. Fetch live page or direct timestamped snapshot (bypass direct loading for Ko-fi links)
        if (!isKofi && !isArchive) {
            val doc = app.get(url).document
            if (!doc.isDeleted404()) fetchChapterContent(doc).takeIf(String::isNotEmpty)?.let { return it }
            // If 404, site down, or empty, check if author embedded an archive link inside the post
            doc.select("div.page-content a[href], div.entry-content a[href]")
                .firstOrNull { it.attr("href").contains("web.archive.org", true) }?.attr("href")?.trim()
                ?.takeIf(String::isNotEmpty)?.let { embedded ->
                    val archiveUrl = if (url.contains('#') && !embedded.contains('#')) "$embedded#${url.substringAfter('#')}" else embedded
                    return loadHtml(archiveUrl)
                }
        }

        // 2. Fallback: resolve latest Wayback snapshot via CDX (handles WordPress posts, Ko-fi, and wildcard search)
        val snapshot = (if (isArchive && !url.contains("/web/*/")) url else resolveSnapshotUrl(url.substringAfter("/web/*/")))
            ?: throw ErrorLoadingException("No archive snapshot found for $url")
        val snapshotDoc = app.get(snapshot).document
        if (snapshotDoc.isDomainDown()) throw ErrorLoadingException("Archived snapshot for $url was offline")
        return fetchChapterContent(snapshotDoc).takeIf(String::isNotEmpty) ?: throw ErrorLoadingException("Failed to load chapter content for $url")
    }
}
