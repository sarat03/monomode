package monomode.engine

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KoReaderTest {
    private val sdcard = Files.createTempDirectory("sd").toFile()
    private val book = File(sdcard, "Books/Dune \"1\".epub").apply { parentFile.mkdirs(); writeText("x") }

    private fun lastFile(path: String) = File(sdcard, "koreader/settings.reader.lua").apply { parentFile.mkdirs() }
        .writeText("return {\n    [\"font_size\"] = 22,\n    [\"lastfile\"] = \"${path.replace("\\", "\\\\").replace("\"", "\\\"")}\",\n}\n")

    private val meta = "return {\n    [\"doc_props\"] = {\n        [\"authors\"] = \"Frank Herbert\",\n        [\"title\"] = \"Dune\",\n    },\n    [\"percent_finished\"] = 0.4267,\n}\n"

    @Test fun `no KOReader files means no book`() = assertNull(koreaderBook(sdcard))

    @Test fun `a deleted last book means no book`() {
        lastFile(File(sdcard, "Books/gone.epub").path)
        assertNull(koreaderBook(sdcard))
    }

    @Test fun `title and progress come from the sidecar next to the book`() {
        lastFile(book.path)
        File(sdcard, "Books/Dune \"1\".sdr/metadata.epub.lua").apply { parentFile.mkdirs() }.writeText(meta)
        assertEquals(Book(book.path, "Dune", 43), koreaderBook(sdcard))
    }

    @Test fun `the sidecar is also found under koreader docsettings`() {
        lastFile(book.path)
        File(sdcard, "koreader/docsettings${book.path.substringBeforeLast('.')}.sdr/metadata.epub.lua").apply { parentFile.mkdirs() }.writeText(meta)
        assertEquals(Book(book.path, "Dune", 43), koreaderBook(sdcard))
    }

    @Test fun `a book without a sidecar shows its file name and no progress`() {
        lastFile(book.path)
        assertEquals(Book(book.path, "Dune \"1\"", null), koreaderBook(sdcard))
    }
}
