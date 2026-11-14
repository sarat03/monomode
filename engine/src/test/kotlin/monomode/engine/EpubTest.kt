package monomode.engine

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class EpubTest {
    private val dir = Files.createTempDirectory("epub").toFile()

    private fun epub(opf: String, vararg more: Pair<String, String>, name: String = "b.epub") = File(dir, name).also { f ->
        f.parentFile.mkdirs()
        ZipOutputStream(f.outputStream()).use { z ->
            fun put(name: String, bytes: ByteArray) { z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() }
            put("META-INF/container.xml", "<container><rootfiles><rootfile full-path=\"OEBPS/content.opf\"/></rootfiles></container>".toByteArray())
            put("OEBPS/content.opf", opf.toByteArray())
            put("OEBPS/img/c.jpg", byteArrayOf(1, 2, 3))
            for ((n, t) in more) put(n, t.toByteArray())
        }
    }

    private val two = "<package><metadata><dc:title>Tom &amp; Jerry</dc:title><dc:creator opf:role=\"aut\">A. Writer</dc:creator></metadata>" +
        "<manifest><item id=\"a\" href=\"text/a%20b.xhtml\" media-type=\"application/xhtml+xml\"/><item id=\"b\" href=\"text/b.xhtml\" media-type=\"application/xhtml+xml\"/>" +
        "<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/></manifest><spine toc=\"ncx\"><itemref idref=\"a\"/><itemref idref=\"b\" linear=\"yes\"/></spine></package>"
    private val ncx = "<ncx><navMap><navPoint id=\"1\"><navLabel><text>One</text></navLabel><content src=\"text/a%20b.xhtml\"/>" +
        "<navPoint id=\"2\"><navLabel><text>One, part 2</text></navLabel><content src=\"text/a%20b.xhtml#p2\"/></navPoint></navPoint>" +
        "<navPoint id=\"3\"><navLabel><text>Two</text></navLabel><content src=\"text/b.xhtml\"/></navPoint></navMap></ncx>"

    @Test fun `reads title, author, reading order and the type of each file`() = Epub(epub(two, "OEBPS/text/a b.xhtml" to "x".repeat(300), "OEBPS/text/b.xhtml" to "x".repeat(100))).use { b ->
        assertEquals("Tom & Jerry", b.title)
        assertEquals("A. Writer", b.author)
        assertEquals(listOf("OEBPS/text/a b.xhtml", "OEBPS/text/b.xhtml"), b.spine)
        assertEquals("application/xhtml+xml", b.mediaType("OEBPS/text/b.xhtml"))
        assertEquals(300, b.bytes("OEBPS/text/a b.xhtml")!!.size)
    }

    @Test fun `reads the contents of an EPUB 2 book, nested entries included`() = Epub(epub(two, "OEBPS/toc.ncx" to ncx)).use { b ->
        assertEquals(listOf("One" to "OEBPS/text/a b.xhtml", "One, part 2" to "OEBPS/text/a b.xhtml#p2", "Two" to "OEBPS/text/b.xhtml"), b.toc)
    }

    @Test fun `reads the contents of an EPUB 3 book from its nav file`() = Epub(epub(
        "<package><manifest><item id=\"n\" href=\"nav.xhtml\" properties=\"nav\"/><item id=\"a\" href=\"a.xhtml\"/></manifest><spine><itemref idref=\"a\"/></spine></package>",
        "OEBPS/nav.xhtml" to "<html><body><nav epub:type=\"toc\"><ol><li><a href=\"a.xhtml#s1\">Part <b>I</b></a></li></ol></nav><nav epub:type=\"landmarks\"><a href=\"x\">no</a></nav></body></html>",
    )).use { b ->
        assertEquals(listOf("Part I" to "OEBPS/a.xhtml#s1"), b.toc)
        assertEquals("b", b.title) // no title in the book: the file name
    }

    @Test fun `progress counts each chapter by its size, and locate undoes it`() = Epub(epub(two, "OEBPS/text/a b.xhtml" to "x".repeat(300), "OEBPS/text/b.xhtml" to "x".repeat(100))).use { b ->
        assertEquals(0.375f, b.progress(0, 0.5f))
        assertEquals(0.875f, b.progress(1, 0.5f))
        assertEquals(1 to 0.5f, b.locate(0.875f))
        assertEquals(0 to 0f, b.locate(0f))
        assertEquals(1 to 1f, b.locate(1f))
    }

    @Test fun `finds an EPUB 2 cover through the cover meta`() = assertContentEquals(byteArrayOf(1, 2, 3), bookCover(epub(
        "<package><metadata><meta content=\"cov\" name=\"cover\"/></metadata><manifest><item id=\"ch1\" href=\"a.xhtml\"/><item href=\"img/c.jpg\" id=\"cov\" media-type=\"image/jpeg\"/></manifest></package>")))

    @Test fun `finds an EPUB 3 cover through the cover-image property`() = assertContentEquals(byteArrayOf(1, 2, 3), bookCover(epub(
        "<package><manifest><item id=\"x\" properties=\"cover-image\" href=\"img/c.jpg\"/></manifest></package>")))

    @Test fun `no cover in the book, or not an EPUB, gives null`() {
        assertNull(bookCover(epub("<package><manifest><item id=\"ch1\" href=\"a.xhtml\"/></manifest></package>")))
        val text = File(dir, "plain.epub").apply { writeText("x") }
        assertNull(bookCover(text))
        assertFailsWith<IOException> { Epub(text) }
    }

    @Test fun `the library scan finds EPUBs but not in hidden or Android folders`() {
        val found = epub(two, name = "lib/Books/Deep/one.EPUB")
        epub(two, name = "lib/.trash/two.epub"); epub(two, name = "lib/Android/data/three.epub")
        File(dir, "lib/Books/notes.docx").writeText("x")
        assertEquals(listOf(found), findBooks(File(dir, "lib")))
    }

    private fun chapter(body: String) = "<html><head><title>albatross in the title</title></head><body>$body</body></html>"

    @Test fun `search finds words in the text of every chapter, whatever their case, counting matches per chapter`() = Epub(epub(two,
        "OEBPS/text/a b.xhtml" to chapter("<p>An Albatross flew.</p><p>Then a second albatross came, far out over the grey water.</p>"),
        "OEBPS/text/b.xhtml" to chapter("<p>No birds here.</p><p>Only one ALBATROSS.</p>"),
    )).use { b ->
        assertEquals(listOf(Hit(0, 0, "An Albatross flew.Then a second albatross came, far"), Hit(0, 1, "An Albatross flew.Then a second albatross came, far out over the grey water."), Hit(1, 0, "No birds here.Only one ALBATROSS.")), b.search("albatross"))
        assertEquals(emptyList(), b.search("penguin"))
        assertEquals(emptyList(), b.search("  "))
    }

    @Test fun `search reads text the way the page shows it - across markup, with entities decoded`() = Epub(epub(two,
        "OEBPS/text/a b.xhtml" to chapter("<p>Tom &amp; Jerry saw an <i>alba</i>tross.</p>"),
    )).use { b ->
        assertEquals(1, b.search("albatross").size)
        assertEquals(listOf(Hit(0, 0, "Tom & Jerry saw an albatross.")), b.search("tom & jerry"))
    }

    private val notes = chapter(
        "<h2 id=\"part\">Part One</h2><p>Text<a href=\"#fn1\">1</a>.</p>" +
        "<aside id=\"fn1\"><p>1. Landfall means <i>sighting</i> the shore.</p></aside>" +
        "<p><a id=\"fn2\" href=\"#r2\">2</a> A note whose id sits on its back-link.</p>" +
        "<p id=\"long\">" + "word ".repeat(200) + "</p>")

    @Test fun `a link to a short passage is a footnote - its text is read out of the chapter`() = Epub(epub(two, "OEBPS/text/b.xhtml" to notes)).use { b ->
        assertEquals("1. Landfall means sighting the shore.", b.note("OEBPS/text/b.xhtml#fn1"))
        assertEquals("2 A note whose id sits on its back-link.", b.note("OEBPS/text/b.xhtml#fn2"))
    }

    @Test fun `a link to a heading, a long passage, or nowhere is not a footnote`() = Epub(epub(two, "OEBPS/text/b.xhtml" to notes)).use { b ->
        assertNull(b.note("OEBPS/text/b.xhtml#part"))
        assertNull(b.note("OEBPS/text/b.xhtml#long"))
        assertNull(b.note("OEBPS/text/b.xhtml#missing"))
        assertNull(b.note("OEBPS/text/b.xhtml"))
    }

    @Test fun `reads the description and the series a book belongs to, as Calibre or EPUB 3 write it`() {
        Epub(epub("<package><metadata><dc:title>T</dc:title><dc:description>&lt;p&gt;A crew crosses &amp;amp; says little.&lt;/p&gt;</dc:description>" +
            "<meta name=\"calibre:series\" content=\"Grey Water\"/><meta content=\"2.5\" name=\"calibre:series_index\"/></metadata><manifest/></package>")).use { b ->
            assertEquals("A crew crosses & says little.", b.description)
            assertEquals("Grey Water", b.series)
            assertEquals(2.5f, b.seriesIndex)
        }
        Epub(epub("<package><metadata><meta property=\"belongs-to-collection\" id=\"c1\">Tides</meta><meta refines=\"#c1\" property=\"group-position\">3</meta></metadata><manifest/></package>")).use { b ->
            assertEquals("Tides", b.series)
            assertEquals(3f, b.seriesIndex)
            assertEquals("", b.description)
        }
        Epub(epub(two)).use { b -> assertEquals("", b.series); assertNull(b.seriesIndex) }
    }
}
