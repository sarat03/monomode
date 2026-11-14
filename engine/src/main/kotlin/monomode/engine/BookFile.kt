package monomode.engine

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.Base64
import java.util.zip.ZipFile

/** One place a search found: the [nth] match (from 0) in the text of [chapter], with the words around it. */
data class Hit(val chapter: Int, val nth: Int, val snippet: String)

/**
 * A book as the reading page and the library see it, whatever kind of file it came from: chapters of HTML in
 * reading order, and the files those chapters show. [openBook] gives the right one for a file.
 */
abstract class BookFile : Closeable {
    abstract val title: String
    open val author: String get() = ""
    /** The blurb, as plain text; "" when the book has none. */
    open val description: String get() = ""
    /** The series the book belongs to and its number in it; "" and null when it stands alone. */
    open val series: String get() = ""
    open val seriesIndex: Float? get() = null
    /** Names of the chapters, in reading order; each is a page of HTML that [bytes] gives. */
    abstract val spine: List<String>
    /** Contents: label to "chapter" or "chapter#anchor". Empty when the book has none. */
    open val toc: List<Pair<String, String>> get() = emptyList()

    /** A chapter, or a file a chapter shows (a picture, a style sheet); null when the book has no such file. */
    abstract fun bytes(entry: String): ByteArray?
    /** The type the book declares for [entry], e.g. application/xhtml+xml; null when it declares none. */
    abstract fun mediaType(entry: String): String?
    open fun cover(): ByteArray? = null
    /** How much [entry] counts towards the length of the book. */
    protected open fun size(entry: String): Long = bytes(entry)?.size?.toLong() ?: 0L
    protected fun text(entry: String) = bytes(entry)?.decodeToString()

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
     * The text of the footnote a link to [target] ("chapter#anchor") points at, or null when the target is not a
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

    // chapters differ a lot in length, so each one counts by its size
    private val sizes by lazy { spine.map { size(it).coerceAtLeast(1L) } }

    /** How far through the whole book (0..1) the reader is, [inChapter] (0..1) into chapter [index]. */
    fun progress(index: Int, inChapter: Float): Float =
        if (spine.isEmpty()) 0f else ((sizes.take(index).sum() + sizes[index] * inChapter.coerceIn(0f, 1f)) / sizes.sum()).coerceIn(0f, 1f)

    /** The opposite of [progress]: the chapter and the place in it for a point [p] (0..1) of the book. */
    fun locate(p: Float): Pair<Int, Float> {
        var left = p.coerceIn(0f, 1f) * sizes.sum()
        sizes.forEachIndexed { i, s -> if (left < s || i == sizes.lastIndex) return i to (left / s).coerceIn(0f, 1f); left -= s }
        return 0 to 0f
    }

    override fun close() {}
}

/** The kinds of file the reader opens, by extension. */
val BOOK_TYPES = setOf("epub", "txt", "html", "htm", "fb2", "cbz", "pdf")

/** The book in [file], read the way its extension says. Throws IOException for a file that is not what it claims, or of no known kind. */
fun openBook(file: File): BookFile = when (file.extension.lowercase()) {
    "epub" -> Epub(file)
    "txt" -> Made(file.nameWithoutExtension) { txt(read(file.readBytes(), markup = false)) }
    "html", "htm" -> html(file)
    "fb2" -> fb2(file)
    "cbz" -> Cbz(file)
    // a PDF has no chapters of HTML: the phone draws its pages (PdfPages in the app), and only its name is known here
    "pdf" -> Made(file.nameWithoutExtension) { Parts(emptyList(), emptyMap()) }
    else -> throw IOException("not a book: ${file.name}")
}

/** The cover picture of the book in [file], or null: it has none, or will not open. */
fun bookCover(file: File): ByteArray? = try { openBook(file).use { it.cover() } } catch (e: IOException) { null }

/** Every book under [root], skipping hidden folders and Android's app folders. */
fun findBooks(root: File): List<File> = root.walkTopDown().maxDepth(6)
    .onEnter { it == root || (!it.name.startsWith(".") && it.name != "Android") }
    .filter { it.isFile && it.extension.lowercase() in BOOK_TYPES }.toList()

// ---- books made in memory from a single file: TXT, HTML, FB2

private class Parts(val spine: List<String>, val files: Map<String, ByteArray>, val toc: List<Pair<String, String>> = emptyList(),
                    val types: Map<String, String> = emptyMap(), val cover: ByteArray? = null)

/** A book whose chapters are worked out from its file the first time one is asked for, so listing it in the library stays cheap. */
private class Made(override val title: String, override val author: String = "", override val description: String = "",
                   override val series: String = "", override val seriesIndex: Float? = null, load: () -> Parts) : BookFile() {
    private val parts by lazy(load)
    override val spine get() = parts.spine
    override val toc get() = parts.toc
    override fun bytes(entry: String) = parts.files[entry]
    override fun mediaType(entry: String) = parts.types[entry] ?: if (entry.endsWith(".html")) "text/html" else null
    override fun cover() = parts.cover
}

/**
 * The text in [bytes]: in the character set its first lines name when it is [markup] (XML's encoding=, HTML's charset=),
 * else UTF-8. shortcut: text that is not valid UTF-8 and names nothing is read as Latin-1; other old encodings come out garbled.
 */
private fun read(bytes: ByteArray, markup: Boolean = true): String {
    val named = if (!markup) null else Regex("""(?:encoding|charset)\s*=\s*["']?([\w-]+)""", RegexOption.IGNORE_CASE)
        .find(String(bytes, 0, minOf(bytes.size, 1024), Charsets.ISO_8859_1))?.let { try { charset(it.groupValues[1]) } catch (e: IllegalArgumentException) { null } }
    val text = String(bytes, named ?: Charsets.UTF_8)
    return (if (named == null && '\uFFFD' in text) String(bytes, Charsets.ISO_8859_1) else text).removePrefix("\uFEFF")
}

/** The first 64 KB of [file], which is where a file says what it is. */
private fun head(file: File): String = file.inputStream().use { s -> ByteArray(65536).let { read(it.copyOf(s.read(it).coerceAtLeast(0))) } }

private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
private fun page(body: String) = "<html><head></head><body>$body</body></html>".toByteArray()
private fun tag(xml: String, name: String) = Regex("<$name\\b[^>]*>(.*?)</$name>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(xml)?.groupValues?.get(1)

/**
 * Plain text as chapters. Paragraphs are parted by blank lines, the lines inside one being a paragraph wrapped by
 * hand; a text without blank lines has a paragraph per line. A chapter ends at the first paragraph break past 60 000
 * characters, since a whole novel laid out as one page is slow to open and to turn.
 */
private fun txt(text: String): Parts {
    val paragraphs = (if (Regex("\\R[ \\t]*\\R").containsMatchIn(text.trim())) text.split(Regex("\\R(?:[ \\t]*\\R)+")).map { it.replace(Regex("\\s*\\R\\s*"), " ") } else text.lines())
        .map { it.trim() }.filter { it.isNotEmpty() }
    val chapters = mutableListOf(StringBuilder())
    for (p in paragraphs) { if (chapters.last().length > 60_000) chapters += StringBuilder(); chapters.last().append("<p>").append(escape(p)).append("</p>") }
    return Parts(chapters.indices.map { "$it.html" }, chapters.withIndex().associate { (i, c) -> "$i.html" to page(c.toString()) })
}

/** A web page saved as a file: one chapter. shortcut: pictures and styles kept in files beside it are not shown. */
private fun html(file: File) = Made(tag(head(file), "title")?.let(::plain)?.ifBlank { null } ?: file.nameWithoutExtension) {
    val text = read(file.readBytes())
    Parts(listOf("index.html"), mapOf("index.html" to if (Regex("<body", RegexOption.IGNORE_CASE).containsMatchIn(text)) text.toByteArray() else page(text)))
}

// ---- FB2: one XML file, its pictures inside it as base64

private val FB2_TAGS = mapOf("section" to "div", "title" to "h2", "subtitle" to "h3", "emphasis" to "em", "strikethrough" to "s",
    "epigraph" to "blockquote", "cite" to "blockquote", "poem" to "blockquote", "stanza" to "p", "text-author" to "p", "annotation" to "div")

/** What stands before the first section of an FB2 body (the book's title, an epigraph), and its top-level sections, each with its tags. */
private fun sections(body: String): Pair<String, List<String>> {
    val cuts = mutableListOf<IntRange>()
    var depth = 0; var from = 0
    for (m in Regex("<(/?)section\\b[^>]*>").findAll(body)) {
        if (m.value.endsWith("/>")) continue
        if (m.groupValues[1].isEmpty()) { if (depth++ == 0) from = m.range.first }
        else if (--depth == 0) cuts += from..m.range.last
    }
    return if (cuts.isEmpty()) "" to listOf(body) else body.substring(0, cuts[0].first) to cuts.map(body::substring)
}

/**
 * FictionBook 2. What the library lists is read from the start of the file alone; the chapters are made when the book
 * is opened: one per top-level section (the first opened by what stands before it), then one for all the notes, so that a link to a note finds it in the book.
 * shortcut: regex over the XML, as for EPUB; tables come out as running text.
 */
private fun fb2(file: File): BookFile {
    val head = head(file)
    if (!head.contains("<FictionBook", ignoreCase = true)) throw IOException("not an FB2: ${file.name}")
    val info = tag(head, "title-info") ?: head
    val sequence = Regex("<sequence\\b[^>]*>").find(info)?.value
    return Made(
        title = tag(info, "book-title")?.let(::plain)?.ifBlank { null } ?: file.nameWithoutExtension,
        author = tag(info, "author")?.let { a -> listOf("first-name", "middle-name", "last-name").mapNotNull { tag(a, it)?.let(::plain)?.ifBlank { null } }.joinToString(" ") }.orEmpty(),
        description = tag(info, "annotation")?.let { plain(it.replace("</p>", " ")) }.orEmpty(),
        series = sequence?.let { attr(it, "name") }.orEmpty(),
        seriesIndex = sequence?.let { attr(it, "number") }?.toFloatOrNull(),
    ) {
        val xml = read(file.readBytes())
        val binaries = Regex("<binary\\b([^>]*)>([^<]*)</binary>").findAll(xml).mapNotNull { m -> attr(m.groupValues[1], "id")?.let { Triple(it, attr(m.groupValues[1], "content-type"), m.groupValues[2]) } }.toList()
        val bodies = Regex("<body\\b[^>]*>(.*?)</body>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { it.groupValues[1] }.toList()
        val (lead, sections) = sections(bodies.firstOrNull().orEmpty())
        val raw = sections + listOfNotNull(bodies.drop(1).joinToString("").ifEmpty { null })
        val spine = raw.indices.map { "$it.html" }
        // where each id is, for the links: a link names only the id, the page needs the chapter too
        val home = raw.flatMapIndexed { i, x -> Regex("\\bid\\s*=\\s*[\"']([^\"']*)[\"']").findAll(x).map { it.groupValues[1] to spine[i] } }.toMap()
        fun encode(id: String) = URLEncoder.encode(id, "UTF-8").replace("+", "%20")
        fun html(x: String) =
            x.replace(Regex("<image\\b[^>]*>")) { "<img src=\"/img/${encode(attr(it.value, "href").orEmpty().removePrefix("#"))}\" alt=\"\"/>" }
                // a link out of the book leads nowhere on a page that is kept off the network
                .replace(Regex("<a\\b[^>]*>")) { a -> attr(a.value, "href")?.takeIf { it.startsWith("#") }?.drop(1)?.let { id -> home[id]?.let { "<a href=\"/$it#$id\">" } } ?: "<a>" }
                .replace(Regex("<empty-line\\s*/>"), "<br/>").replace(Regex("<v\\b[^>]*>"), "").replace("</v>", "<br/>")
                .replace(Regex("<(/?)([\\w-]+)\\b")) { m -> "<${m.groupValues[1]}${FB2_TAGS[m.groupValues[2]] ?: m.groupValues[2]}" }
                .replace("</p>", "</p>\n") // so that words of neighbouring paragraphs do not run together where the tags are dropped
        val pages = raw.map(::html)
        val cover = tag(head, "coverpage")?.let { attr(it, "href") }?.removePrefix("#")
        fun picture(base64: String) = try { Base64.getMimeDecoder().decode(base64) } catch (e: IllegalArgumentException) { ByteArray(0) }
        Parts(spine, spine.zip(pages.mapIndexed { i, p -> page(if (i == 0) html(lead) + p else p) }).toMap() + binaries.associate { "img/${it.first}" to picture(it.third) },
            // a chapter without a heading is listed by its first words
            toc = pages.mapIndexed { i, p -> (tag(p, "h2")?.let(::plain)?.ifBlank { null } ?: plain(p).take(40).trim()) to spine[i] },
            types = binaries.mapNotNull { b -> b.second?.let { "img/${b.first}" to it } }.toMap(),
            cover = binaries.firstOrNull { it.first == cover }?.let { picture(it.third) })
    }
}

// ---- CBZ: a zip of pictures

/**
 * A comic: every picture in the zip is a chapter of one page, in the order of their names with numbers counted as
 * numbers (2 before 10). Throws IOException for a file that is not a zip or holds no pictures.
 */
private class Cbz(file: File) : BookFile() {
    private val zip = ZipFile(file)
    private val pictures = zip.entries().asSequence().map { it.name }
        .filter { it.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp") && !it.startsWith("__MACOSX/") && !it.substringAfterLast('/').startsWith(".") }
        .sortedBy { n -> n.lowercase().replace(Regex("\\d+")) { it.value.padStart(9, '0') } }.toList()
    init { if (pictures.isEmpty()) { zip.close(); throw IOException("no pictures: ${file.name}") } }

    override val title: String = file.nameWithoutExtension
    override val spine = pictures.indices.map { "page/$it.html" }
    // the class is for the page's own style sheet: a picture fills the screen instead of a column of text
    override fun bytes(entry: String): ByteArray? = spine.indexOf(entry).takeIf { it >= 0 }?.let { i ->
        "<html><head></head><body class=\"comic\"><img src=\"/${pictures[i].split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }}\" alt=\"\"/></body></html>".toByteArray()
    } ?: zip.getEntry(entry)?.let { zip.getInputStream(it).use { s -> s.readBytes() } }
    override fun mediaType(entry: String) = if (entry in spine) "text/html" else null
    override fun cover() = bytes(pictures[0])
    override fun size(entry: String) = 1L
    override fun close() = zip.close()
}
