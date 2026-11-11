package monomode.engine

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.zip.ZipFile

private fun attr(tag: String, name: String) = Regex("""\b$name\s*=\s*["']([^"']*)["']""").find(tag)?.groupValues?.get(1)

private val ENTITIES = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")
/** The text of some XML as a browser's textContent gives it: tags dropped with nothing put in their place, entities decoded. */
private fun shown(xml: String) = xml.replace(Regex("<[^>]*>"), "")
    .replace(Regex("&(#x?[0-9a-fA-F]+|\\w+);")) { m ->
        val e = m.groupValues[1]
        (if (e.startsWith("#x")) e.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
        else if (e.startsWith("#")) e.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
        else ENTITIES[e]) ?: m.value
    }
/** Text of an XML element for showing in a list: as [shown], with whitespace collapsed. */
private fun plain(xml: String) = shown(xml).replace(Regex("\\s+"), " ").trim()

/** One place a search found: the [nth] match (from 0) in the text of [chapter], with the words around it. */
data class Hit(val chapter: Int, val nth: Int, val snippet: String)

/**
 * An EPUB book, read straight from its zip: the OPF package file names the title, the files and their reading order.
 * Throws IOException for a file that is not an EPUB. shortcut: regex over the XML instead of a parser, which
 * is enough for the handful of tags read here; DRM-protected books open but show nothing readable.
 */
class Epub(val file: File) : Closeable {
    private val zip = ZipFile(file)
    private val opfPath: String
    private val opf: String
    /** manifest id -> (zip entry, media type) */
    private val items: Map<String, Pair<String, String>>

    val title: String
    val author: String
    /** Zip entries of the chapters, in reading order. */
    val spine: List<String>
    /** Contents: label to "zip entry" or "zip entry#anchor". Empty when the book has none. */
    val toc: List<Pair<String, String>>

    init {
        try {
            opfPath = attr(text("META-INF/container.xml") ?: throw IOException("not an EPUB: ${file.name}"), "full-path") ?: throw IOException("no package file")
            opf = text(opfPath) ?: throw IOException("no package file")
            val tags = Regex("<item\\s[^>]*>").findAll(opf).map { it.value }.toList()
            items = tags.mapNotNull { t -> attr(t, "id")?.let { id -> attr(t, "href")?.let { id to (resolve(opfPath, it) to attr(t, "media-type").orEmpty()) } } }.toMap()
            title = Regex("<dc:title[^>]*>(.*?)</dc:title>", RegexOption.DOT_MATCHES_ALL).find(opf)?.let { plain(it.groupValues[1]) }?.ifBlank { null } ?: file.nameWithoutExtension
            author = Regex("<dc:creator[^>]*>(.*?)</dc:creator>", RegexOption.DOT_MATCHES_ALL).find(opf)?.let { plain(it.groupValues[1]) }.orEmpty()
            spine = Regex("<itemref\\s[^>]*>").findAll(opf).mapNotNull { attr(it.value, "idref")?.let(items::get)?.first }.toList()
            // EPUB 3 has a nav document, EPUB 2 an NCX file named by the spine
            val nav = tags.firstOrNull { "nav" in attr(it, "properties").orEmpty().split(' ') }?.let { attr(it, "id") }?.let(items::get)?.first
            val ncx = Regex("<spine\\s[^>]*>").find(opf)?.let { attr(it.value, "toc") }?.let(items::get)?.first
            toc = nav?.let { path ->
                Regex("<nav[^>]*toc[^>]*>(.*?)</nav>", RegexOption.DOT_MATCHES_ALL).find(text(path).orEmpty())?.groupValues?.get(1)?.let { body ->
                    Regex("<a\\s[^>]*>.*?</a>", RegexOption.DOT_MATCHES_ALL).findAll(body).mapNotNull { a -> attr(a.value, "href")?.let { plain(a.value) to resolve(path, it) } }.toList()
                }
            }?.ifEmpty { null } ?: ncx?.let { path ->
                Regex("<navPoint.*?<text>(.*?)</text>.*?<content\\s[^>]*>", RegexOption.DOT_MATCHES_ALL).findAll(text(path).orEmpty())
                    .mapNotNull { m -> attr(m.value.substringAfterLast("<content"), "src")?.let { plain(m.groupValues[1]) to resolve(path, it) } }.toList()
            }.orEmpty()
        } catch (e: IOException) { zip.close(); throw e }
    }

    /** [href] as written inside the file [from], as a zip entry (keeping any #anchor). */
    private fun resolve(from: String, href: String): String {
        val path = URLDecoder.decode(href.substringBefore('#').replace("+", "%2B"), "UTF-8")
        return File(File(from).parentFile, path).normalize().invariantSeparatorsPath + if ('#' in href) "#" + href.substringAfter('#') else ""
    }

    fun bytes(entry: String): ByteArray? = zip.getEntry(entry)?.let { zip.getInputStream(it).use { s -> s.readBytes() } }
    private fun text(entry: String) = bytes(entry)?.decodeToString()

    /** The type the book declares for [entry], e.g. application/xhtml+xml; null for a file outside the manifest. */
    fun mediaType(entry: String): String? = items.values.firstOrNull { it.first == entry }?.second?.ifEmpty { null }

    /**
     * Every place [query] occurs, ignoring case, at most 200. The text searched is each chapter's body as the page's own
     * script reads it (textContent), so the page can find the same [Hit.nth] match again and turn to it.
     */
    fun search(query: String): List<Hit> {
        if (query.isBlank()) return emptyList()
        return spine.asSequence().flatMapIndexed { chapter, entry ->
            val body = shown(Regex("<body[^>]*>(.*)</body>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(text(entry).orEmpty())?.groupValues?.get(1).orEmpty())
            generateSequence(body.indexOf(query, ignoreCase = true)) { body.indexOf(query, it + 1, ignoreCase = true) }.takeWhile { it >= 0 }.mapIndexed { nth, at ->
                Hit(chapter, nth, body.substring((at - 40).coerceAtLeast(0), (at + query.length + 40).coerceAtMost(body.length)).replace(Regex("\\s+"), " ").trim())
            }
        }.take(200).toList()
    }

    /**
     * The text of the footnote a link to [target] ("zip entry#anchor") points at, or null when the target is not a
     * short passage (a heading, a long section, nothing), in which case the link is an ordinary jump.
     * shortcut: a note is cut at the first closing tag of its own kind, so a note with a block of the same kind nested in it shows its first part only.
     */
    fun note(target: String): String? {
        val id = target.substringAfter('#', "").ifEmpty { return null }
        val html = text(target.substringBefore('#')) ?: return null
        val open = Regex("<(\\w+)\\b[^>]*\\bid\\s*=\\s*[\"']${Regex.escape(id)}[\"'][^>]*>").find(html) ?: return null
        var tag = open.groupValues[1].lowercase()
        var from = open.range.first
        if (tag in setOf("a", "span", "sup", "sub", "b", "i", "em", "strong")) {
            // the id sits on the note's number or back-link: the note is the block around it
            val block = Regex("<(p|li|div|aside|dd|td|section)\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html.substring(0, from)).lastOrNull() ?: return null
            tag = block.groupValues[1].lowercase(); from = block.range.first
        }
        if (tag.matches(Regex("h[1-6]|body|html"))) return null
        val end = html.indexOf("</$tag", from, ignoreCase = true).takeIf { it > 0 } ?: return null
        return plain(html.substring(from, end)).takeIf { it.isNotEmpty() && it.length <= 800 }
    }

    /** The cover image file. EPUB 3 marks the item with properties="cover-image"; EPUB 2 names its id in <meta name="cover">. */
    fun cover(): ByteArray? {
        val tags = Regex("<item\\s[^>]*>").findAll(opf).map { it.value }.toList()
        val coverId = Regex("<meta[^>]*>").findAll(opf).map { it.value }.firstOrNull { attr(it, "name") == "cover" }?.let { attr(it, "content") }
        val id = tags.firstOrNull { "cover-image" in attr(it, "properties").orEmpty() }?.let { attr(it, "id") } ?: coverId
        return items[id]?.first?.let(::bytes)
    }

    // chapters differ a lot in length, so each one counts by its size in the file
    private val sizes = spine.map { (zip.getEntry(it)?.size ?: 0L).coerceAtLeast(1L) }

    /** How far through the whole book (0..1) the reader is, [inChapter] (0..1) into chapter [index]. */
    fun progress(index: Int, inChapter: Float): Float =
        if (spine.isEmpty()) 0f else ((sizes.take(index).sum() + sizes[index] * inChapter.coerceIn(0f, 1f)) / sizes.sum()).coerceIn(0f, 1f)

    /** The opposite of [progress]: the chapter and the place in it for a point [p] (0..1) of the book. */
    fun locate(p: Float): Pair<Int, Float> {
        var left = p.coerceIn(0f, 1f) * sizes.sum()
        sizes.forEachIndexed { i, s -> if (left < s || i == sizes.lastIndex) return i to (left / s).coerceIn(0f, 1f); left -= s }
        return 0 to 0f
    }

    override fun close() = zip.close()
}

/** The cover image file inside an EPUB, or null. shortcut: EPUB only; other formats keep the plain book outline. */
fun epubCover(book: File): ByteArray? = try { Epub(book).use { it.cover() } } catch (e: IOException) { null }

/** Every EPUB under [root], skipping hidden folders and Android's app folders. */
fun findEpubs(root: File): List<File> = root.walkTopDown().maxDepth(6)
    .onEnter { it == root || (!it.name.startsWith(".") && it.name != "Android") }
    .filter { it.isFile && it.extension.equals("epub", ignoreCase = true) }.toList()
