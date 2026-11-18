package monomode.engine

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The formats other than EPUB, each read through [BookFile] from a small file made here. */
class BookFileTest {
    private val dir = Files.createTempDirectory("book").toFile()
    private fun file(name: String, bytes: ByteArray) = File(dir, name).also { it.parentFile.mkdirs(); it.writeBytes(bytes) }
    private fun file(name: String, text: String) = file(name, text.toByteArray())
    private fun BookFile.page(i: Int) = bytes(spine[i])!!.decodeToString()

    // TXT

    @Test fun `a text file is a book named after the file, its paragraphs made safe for a page`() =
        openBook(file("Notes of a Sailor.txt", "First line\nwrapped.\n\nSecond <b> & albatross.\n")).use { b ->
            assertEquals("Notes of a Sailor", b.title)
            assertEquals(1, b.spine.size)
            assertEquals("text/html", b.mediaType(b.spine[0]))
            assertContains(b.page(0), "<p>First line wrapped.</p>")
            assertContains(b.page(0), "<p>Second &lt;b&gt; &amp; albatross.</p>")
            assertContains(b.page(0), "<body")
            assertEquals(listOf(0), b.search("ALBATROSS").map { it.chapter })
        }

    @Test fun `a text file without blank lines has a paragraph per line`() =
        openBook(file("a.txt", "one\r\ntwo\r\n")).use { assertContains(it.page(0), "<p>one</p><p>two</p>") }

    @Test fun `a long text file is cut into chapters between paragraphs`() =
        openBook(file("long.txt", (1..5).joinToString("\n\n") { "$it" + "x".repeat(40_000) })).use { b ->
            assertTrue(b.spine.size in 2..5, "${b.spine.size} chapters")
            assertEquals(5, b.spine.indices.sumOf { Regex("<p>").findAll(b.page(it)).count() })
            assertEquals(1f, b.progress(b.spine.lastIndex, 1f))
            assertEquals(0 to 0f, b.locate(0f))
        }

    @Test fun `a text file that is not UTF-8 is read as Latin-1`() =
        openBook(file("l.txt", "café".toByteArray(Charsets.ISO_8859_1))).use { assertContains(it.page(0), "café") }

    // HTML

    @Test fun `a web page is a one-chapter book with the page's own title`() =
        openBook(file("saved.html", "<html><head><title>My &amp; Page</title></head><body><p>Hello albatross</p></body></html>")).use { b ->
            assertEquals("My & Page", b.title)
            assertEquals(1, b.spine.size)
            assertEquals("text/html", b.mediaType(b.spine[0]))
            assertContains(b.page(0), "<p>Hello albatross</p>")
            assertEquals(1, b.search("albatross").size)
        }

    @Test fun `a web page without a title or a body gets the file's name and a body`() =
        openBook(file("Bare.htm", "<p>just this</p>")).use { b ->
            assertEquals("Bare", b.title)
            assertContains(b.page(0), "<body><p>just this</p></body>")
            assertEquals(1, b.search("just").size)
        }

    @Test fun `a web page is read in the character set it names`() =
        openBook(file("ru.html", "<html><head><meta charset=\"windows-1251\"><title>Мир</title></head><body>x</body></html>".toByteArray(charset("windows-1251")))).use {
            assertEquals("Мир", it.title)
        }

    // FB2

    private val picture = byteArrayOf(9, 8, 7, 6)
    private val fb2 = """<?xml version="1.0" encoding="utf-8"?>
<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">
<description><title-info><genre>prose</genre>
<author><first-name>Ann</first-name><middle-name>B.</middle-name><last-name>Writer</last-name></author>
<book-title>Deep &amp; Blue</book-title><annotation><p>A sea story.</p></annotation>
<coverpage><image l:href="#c.jpg"/></coverpage><sequence name="Tides" number="2"/></title-info>
<document-info><author><nickname>someone else</nickname></author></document-info></description>
<body><title><p>Deep and Blue</p></title>
<section><title><p>Part</p><p>One</p></title><p>It was <emphasis>calm</emphasis><a l:href="#n1" type="note">[1]</a>.</p><empty-line/>
<section><title><p>Inner</p></title><p>Nested albatross.</p><image l:href="#c.jpg"/></section></section>
<section><p>No heading here, only words that go on.</p><p id="far">See <a l:href="#far">here</a> and <a l:href="http://example.com/">there</a>.</p></section>
</body>
<body name="notes"><title><p>Notes</p></title><section id="n1"><title><p>1</p></title><p>The note.</p></section></body>
<binary id="c.jpg" content-type="image/jpeg">${Base64.getMimeEncoder(2, "\n".toByteArray()).encodeToString(picture)}</binary>
</FictionBook>"""

    @Test fun `an FB2 names its title, author, series and blurb`() = openBook(file("b.fb2", fb2)).use { b ->
        assertEquals("Deep & Blue", b.title)
        assertEquals("Ann B. Writer", b.author)
        assertEquals("Tides", b.series)
        assertEquals(2f, b.seriesIndex)
        assertEquals("A sea story.", b.description)
    }

    @Test fun `an FB2 has a chapter per top section and one for its notes, with contents`() = openBook(file("b.fb2", fb2)).use { b ->
        assertEquals(3, b.spine.size)
        assertEquals(listOf("Part One", "No heading here, only words that go on.", "Notes"), b.toc.map { it.first })
        assertEquals(b.spine, b.toc.map { it.second })
        assertContains(b.page(0), "Deep and Blue") // what stands before the first section opens the book
        assertContains(b.page(0), "<em>calm</em>")
        assertContains(b.page(0), "Nested albatross.")
        assertContains(b.page(0), "<br/>")
        assertEquals(listOf(0), b.search("albatross").map { it.chapter })
    }

    @Test fun `an FB2's links lead to the chapter that holds their target, and a note opens as a footnote`() = openBook(file("b.fb2", fb2)).use { b ->
        assertContains(b.page(0), "<a href=\"/${b.spine[2]}#n1\"")
        assertContains(b.page(1), "<a href=\"/${b.spine[1]}#far\"")
        assertTrue("example.com" !in b.page(1))
        assertEquals("1 The note.", b.note("${b.spine[2]}#n1"))
    }

    @Test fun `an FB2's pictures and cover come from its binaries`() = openBook(file("b.fb2", fb2)).use { b ->
        assertContentEquals(picture, b.cover())
        assertContains(b.page(0), "<img src=\"/img/c.jpg\"")
        assertContentEquals(picture, b.bytes("img/c.jpg"))
        assertEquals("image/jpeg", b.mediaType("img/c.jpg"))
    }

    @Test fun `an FB2 without sections is one chapter, and one in another character set is read`() {
        val text = "<?xml version=\"1.0\" encoding=\"windows-1251\"?><FictionBook><description><title-info><book-title>Море</book-title></title-info></description><body><p>Вода</p></body></FictionBook>"
        openBook(file("ru.fb2", text.toByteArray(charset("windows-1251")))).use { b ->
            assertEquals("Море", b.title)
            assertEquals(1, b.spine.size)
            assertContains(b.page(0), "<p>Вода</p>")
            assertNull(b.cover())
        }
    }

    @Test fun `a file that only claims to be an FB2 is refused`() { assertFailsWith<IOException> { openBook(file("no.fb2", "hello")) } }

    // CBZ

    private fun cbz(name: String, vararg entries: String) = File(dir, name).also { f ->
        ZipOutputStream(f.outputStream()).use { z -> for (e in entries) { z.putNextEntry(ZipEntry(e)); z.write(e.toByteArray()); z.closeEntry() } }
    }

    @Test fun `a comic is a page per picture, in the order of their numbers`() =
        openBook(cbz("Space Cat 3.cbz", "10.png", "2.PNG", "1.png", "notes.txt", "__MACOSX/._1.png", "extra/a b.jpg")).use { b ->
            assertEquals("Space Cat 3", b.title)
            assertEquals(4, b.spine.size)
            assertEquals("text/html", b.mediaType(b.spine[0]))
            assertEquals(listOf("/1.png", "/2.PNG", "/10.png", "/extra/a%20b.jpg"), b.spine.indices.map { Regex("src=\"([^\"]*)\"").find(b.page(it))!!.groupValues[1] })
            assertContentEquals("extra/a b.jpg".toByteArray(), b.bytes("extra/a b.jpg"))
            assertContentEquals("1.png".toByteArray(), b.cover())
            assertEquals(emptyList(), b.search("png"))
            assertEquals(0.5f, b.progress(1, 1f))
        }

    @Test fun `a comic without pictures, or one that is not a zip, is refused`() {
        assertFailsWith<IOException> { openBook(cbz("empty.cbz", "notes.txt")) }
        assertFailsWith<IOException> { openBook(file("bad.cbz", "hello")) }
    }

    // PDF, and the library

    @Test fun `a PDF is known by its file name only, its pages being the phone's to draw`() =
        openBook(file("Manual v2.pdf", "%PDF-1.4")).use { assertEquals("Manual v2", it.title); assertEquals(emptyList(), it.spine) }

    @Test fun `a file of no known kind is refused`() { assertFailsWith<IOException> { openBook(file("a.docx", "x")) } }

    @Test fun `every kind of book is found, and nothing else`() {
        val found = listOf("a.epub", "b.TXT", "c.html", "d.htm", "e.fb2", "f.cbz", "g.pdf").map { file("lib/$it", "x") }
        file("lib/h.docx", "x"); file("lib/.hidden/i.txt", "x"); file("lib/Android/j.txt", "x")
        assertEquals(found.toSet(), findBooks(File(dir, "lib")).toSet())
    }

    @Test fun `a cover is asked of any kind of book, and a file that will not open has none`() {
        assertContentEquals(picture, bookCover(file("b.fb2", fb2)))
        assertNull(bookCover(file("no.fb2", "hello")))
        assertNull(bookCover(file("t.txt", "hello")))
    }

    /** Zeros, [size] of them, without holding them in memory. */
    private fun zeros(size: Long) = object : java.io.InputStream() {
        var left = size
        override fun read() = if (left-- > 0) 0 else -1
        override fun read(b: ByteArray, off: Int, len: Int): Int = if (left <= 0) -1 else minOf(len.toLong(), left).toInt().also { left -= it; b.fill(0, off, off + it) }
    }

    @Test fun `a read stops past the cap instead of running out of memory`() {
        assertEquals(MAX_BOOK_BYTES, zeros(MAX_BOOK_BYTES.toLong()).readCapped()?.size)
        assertNull(zeros(MAX_BOOK_BYTES + 1L).readCapped())
    }

    @Test fun `a whole-file book over the cap does not open`() {
        val big = File(dir, "big.txt").also { java.io.RandomAccessFile(it, "rw").use { f -> f.setLength(MAX_BOOK_BYTES + 1L) } } // sparse: no real disk use
        assertFailsWith<IOException> { openBook(big).spine }
    }
}
