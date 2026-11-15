package monomode.engine

import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.zip.ZipFile

internal fun attr(tag: String, name: String) = Regex("""\b$name\s*=\s*["']([^"']*)["']""").find(tag)?.groupValues?.get(1)

private val ENTITIES = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")
/** The text of some XML as a browser's textContent gives it: tags dropped with nothing put in their place, entities decoded. */
internal fun shown(xml: String) = xml.replace(Regex("<[^>]*>"), "")
    .replace(Regex("&(#x?[0-9a-fA-F]+|\\w+);")) { m ->
        val e = m.groupValues[1]
        (if (e.startsWith("#x")) e.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
        else if (e.startsWith("#")) e.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
        else ENTITIES[e]) ?: m.value
    }
/** Text of an XML element for showing in a list: as [shown], with whitespace collapsed. */
internal fun plain(xml: String) = shown(xml).replace(Regex("\\s+"), " ").trim()

/**
 * An EPUB book, read straight from its zip: the OPF package file names the title, the files and their reading order.
 * Throws IOException for a file that is not an EPUB. shortcut: regex over the XML instead of a parser, which
 * is enough for the handful of tags read here; DRM-protected books open but show nothing readable.
 */
class Epub(val file: File) : BookFile() {
    private val zip = ZipFile(file)
    private val opfPath: String
    private val opf: String
    /** manifest id -> (zip entry, media type) */
    private val items: Map<String, Pair<String, String>>

    override val title: String
    override val author: String
    /** The blurb, as plain text; "" when the book has none. */
    override val description: String
    /** The series the book belongs to and its number in it; "" and null when it stands alone. */
    override val series: String
    override val seriesIndex: Float?
    /** Zip entries of the chapters, in reading order. */
    override val spine: List<String>
    /** Contents: label to "zip entry" or "zip entry#anchor". Empty when the book has none. */
    override val toc: List<Pair<String, String>>

    init {
        try {
            opfPath = attr(text("META-INF/container.xml") ?: throw IOException("not an EPUB: ${file.name}"), "full-path") ?: throw IOException("no package file")
            opf = text(opfPath) ?: throw IOException("no package file")
            val tags = Regex("<item\\s[^>]*>").findAll(opf).map { it.value }.toList()
            items = tags.mapNotNull { t -> attr(t, "id")?.let { id -> attr(t, "href")?.let { id to (resolve(opfPath, it) to attr(t, "media-type").orEmpty()) } } }.toMap()
            title = Regex("<dc:title[^>]*>(.*?)</dc:title>", RegexOption.DOT_MATCHES_ALL).find(opf)?.let { plain(it.groupValues[1]) }?.ifBlank { null } ?: file.nameWithoutExtension
            author = Regex("<dc:creator[^>]*>(.*?)</dc:creator>", RegexOption.DOT_MATCHES_ALL).find(opf)?.let { plain(it.groupValues[1]) }.orEmpty()
            // a description is often HTML, escaped once more to sit inside the XML: decode, then strip
            description = Regex("<dc:description[^>]*>(.*?)</dc:description>", RegexOption.DOT_MATCHES_ALL).find(opf)?.let { plain(plain(it.groupValues[1])) }.orEmpty()
            val metas = Regex("<meta\\b[^>]*?(?:/>|>(.*?)</meta>)", RegexOption.DOT_MATCHES_ALL).findAll(opf).toList()
            fun named(name: String) = metas.firstOrNull { attr(it.value, "name") == name }?.let { attr(it.value, "content") }       // Calibre and EPUB 2
            fun property(name: String) = metas.firstOrNull { attr(it.value, "property") == name }?.let { plain(it.groupValues[1]) } // EPUB 3
            series = named("calibre:series") ?: property("belongs-to-collection") ?: ""
            seriesIndex = (named("calibre:series_index") ?: property("group-position"))?.toFloatOrNull()
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

    override fun bytes(entry: String): ByteArray? = zip.getEntry(entry)?.let { zip.getInputStream(it).use { s -> s.readCapped() } }

    /** The type the book declares for [entry], e.g. application/xhtml+xml; null for a file outside the manifest. */
    override fun mediaType(entry: String): String? = items.values.firstOrNull { it.first == entry }?.second?.ifEmpty { null }

    /** The cover image file. EPUB 3 marks the item with properties="cover-image"; EPUB 2 names its id in <meta name="cover">. */
    override fun cover(): ByteArray? {
        val tags = Regex("<item\\s[^>]*>").findAll(opf).map { it.value }.toList()
        val coverId = Regex("<meta[^>]*>").findAll(opf).map { it.value }.firstOrNull { attr(it, "name") == "cover" }?.let { attr(it, "content") }
        val id = tags.firstOrNull { "cover-image" in attr(it, "properties").orEmpty() }?.let { attr(it, "id") } ?: coverId
        return items[id]?.first?.let(::bytes)
    }

    override fun size(entry: String) = zip.getEntry(entry)?.size ?: 0L

    override fun close() = zip.close()
}
