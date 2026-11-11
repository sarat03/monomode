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

    @Test fun `finds an EPUB 2 cover through the cover meta`() = assertContentEquals(byteArrayOf(1, 2, 3), epubCover(epub(
        "<package><metadata><meta content=\"cov\" name=\"cover\"/></metadata><manifest><item id=\"ch1\" href=\"a.xhtml\"/><item href=\"img/c.jpg\" id=\"cov\" media-type=\"image/jpeg\"/></manifest></package>")))

    @Test fun `finds an EPUB 3 cover through the cover-image property`() = assertContentEquals(byteArrayOf(1, 2, 3), epubCover(epub(
        "<package><manifest><item id=\"x\" properties=\"cover-image\" href=\"img/c.jpg\"/></manifest></package>")))

    @Test fun `no cover in the book, or not an EPUB, gives null`() {
        assertNull(epubCover(epub("<package><manifest><item id=\"ch1\" href=\"a.xhtml\"/></manifest></package>")))
        val text = File(dir, "plain.epub").apply { writeText("x") }
        assertNull(epubCover(text))
        assertFailsWith<IOException> { Epub(text) }
    }

    @Test fun `the library scan finds EPUBs but not in hidden or Android folders`() {
        val found = epub(two, name = "lib/Books/Deep/one.EPUB")
        epub(two, name = "lib/.trash/two.epub"); epub(two, name = "lib/Android/data/three.epub")
        File(dir, "lib/Books/notes.txt").writeText("x")
        assertEquals(listOf(found), findEpubs(File(dir, "lib")))
    }
}
