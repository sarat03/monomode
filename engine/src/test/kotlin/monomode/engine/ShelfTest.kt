package monomode.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShelfTest {
    private val saved = mutableMapOf<String, String>()
    private var clock = 1_000L
    private val shelf = Shelf(object : Shelf.Store {
        override fun get(key: String) = saved[key]
        override fun put(key: String, value: String?) { if (value == null) saved.remove(key) else saved[key] = value }
    }, { clock }, java.time.ZoneOffset.UTC)

    @Test fun `a book's place is remembered with the time it was left, per book`() {
        assertNull(shelf.place("/b/one.epub"))
        shelf.save("/b/one.epub", chapter = 2, inChapter = 0.25f, progress = 0.4f)
        clock = 2_000L
        shelf.save("/b/two.epub", chapter = 0, inChapter = 0f, progress = 0.01f)
        assertEquals(Place(2, 0.25f, 0.4f, 1_000L), shelf.place("/b/one.epub"))
        assertEquals(Place(0, 0f, 0.01f, 2_000L), shelf.place("/b/two.epub"))
    }

    @Test fun `the page look starts as night in a dark app and day otherwise, then stays as chosen`() {
        assertEquals(Look(theme = "day", font = "serif", size = 110, spacing = 150, margin = 6), shelf.look(dark = false))
        assertEquals("night", shelf.look(dark = true).theme)
        shelf.setLook(shelf.look(dark = false).copy(theme = "sepia", font = "", size = 130))
        assertEquals(Look("sepia", "", 130, 150, 6), shelf.look(dark = true))
    }

    @Test fun `library sort and colour keep their choice`() {
        assertEquals("recent", shelf.sort)
        assertEquals(true, shelf.colour)
        assertEquals(false, shelf.grid)
        shelf.sort = "author"; shelf.colour = false; shelf.grid = true
        assertEquals(true, shelf.grid)
        assertEquals("author", shelf.sort)
        assertEquals(false, shelf.colour)
    }

    @Test fun `bookmarks are listed in reading order, per book`() {
        assertEquals(emptyList(), shelf.marks("/b/one.epub"))
        shelf.toggleMark("/b/one.epub", chapter = 3, from = 0.5f, until = 0.6f, progress = 0.7f, label = "Landfall")
        clock = 2_000L
        shelf.toggleMark("/b/one.epub", chapter = 1, from = 0.2f, until = 0.3f, progress = 0.1f, label = "Setting | Out")
        shelf.toggleMark("/b/two.epub", chapter = 0, from = 0f, until = 1f, progress = 0f, label = "x")
        assertEquals(listOf(Mark(1, 0.2f, 0.1f, "Setting | Out", 2_000L), Mark(3, 0.5f, 0.7f, "Landfall", 1_000L)), shelf.marks("/b/one.epub"))
    }

    @Test fun `marking a page that holds a bookmark removes it, wherever on the page it was made`() {
        shelf.toggleMark("/b/one.epub", chapter = 1, from = 0.25f, until = 0.5f, progress = 0.1f, label = "a")
        // text made larger since: the same place now sits on a page that starts earlier
        assertEquals(Mark(1, 0.25f, 0.1f, "a", 1_000L), shelf.markOn("/b/one.epub", chapter = 1, from = 0.2f, until = 0.3f))
        assertNull(shelf.markOn("/b/one.epub", chapter = 1, from = 0.3f, until = 0.4f))
        assertNull(shelf.markOn("/b/one.epub", chapter = 2, from = 0.2f, until = 0.3f))
        shelf.toggleMark("/b/one.epub", chapter = 1, from = 0.2f, until = 0.3f, progress = 0.1f, label = "b")
        assertEquals(emptyList(), shelf.marks("/b/one.epub"))
    }

    @Test fun `text options are part of the look and start off`() {
        val plain = shelf.look(dark = false)
        assertEquals(listOf(false, false, false, false), listOf(plain.bold, plain.justify, plain.hyphens, plain.indent))
        shelf.setLook(plain.copy(bold = true, hyphens = true))
        assertEquals(plain.copy(bold = true, hyphens = true), shelf.look(dark = false))
    }

    @Test fun `reading choices have e-reader defaults and keep what was chosen`() {
        assertNull(shelf.brightness) // the phone's own
        assertEquals(false, shelf.keepOn)
        assertEquals("auto", shelf.orientation)
        assertEquals(true, shelf.volumeKeys)
        assertEquals(setOf("title", "page", "percent"), shelf.status)
        shelf.brightness = 0.2f; shelf.keepOn = true; shelf.orientation = "portrait"; shelf.volumeKeys = false; shelf.status = setOf("clock")
        assertEquals(listOf(0.2f, true, "portrait", false, setOf("clock")), listOf(shelf.brightness, shelf.keepOn, shelf.orientation, shelf.volumeKeys, shelf.status))
        shelf.brightness = null; shelf.status = emptySet()
        assertNull(shelf.brightness)
        assertEquals(emptySet(), shelf.status)
    }

    @Test fun `the info line is shown until switched off, and its contents are kept while it is off`() {
        assertEquals(true, shelf.infoLine)
        shelf.status = setOf("clock"); shelf.infoLine = false
        assertEquals(false, shelf.infoLine)
        assertEquals(setOf("clock"), shelf.status)
        shelf.infoLine = true
        assertEquals(true, shelf.infoLine)
    }

    @Test fun `tap layout and scrolling instead of pages keep their choice`() {
        assertEquals("sides", shelf.taps)
        assertEquals(false, shelf.look(dark = false).scroll)
        shelf.taps = "forward"
        shelf.setLook(shelf.look(dark = false).copy(scroll = true, indent = true))
        assertEquals("forward", shelf.taps)
        assertEquals(listOf(true, true, false), shelf.look(dark = false).let { listOf(it.scroll, it.indent, it.bold) })
    }

    @Test fun `highlights are kept in reading order with their text, per book`() {
        shelf.addQuote("/b/one.epub", chapter = 2, start = 40, end = 52, text = "grey | water\nand more")
        clock = 2_000L
        shelf.addQuote("/b/one.epub", chapter = 0, start = 7, end = 9, text = "ab")
        shelf.addQuote("/b/two.epub", chapter = 0, start = 1, end = 2, text = "x")
        assertEquals(listOf(Quote(0, 7, 9, "ab", "", 2_000L), Quote(2, 40, 52, "grey | water\nand more", "", 1_000L)), shelf.quotes("/b/one.epub"))
    }

    @Test fun `a highlight can carry a note, and can be removed`() {
        shelf.addQuote("/b/one.epub", chapter = 0, start = 7, end = 9, text = "ab")
        shelf.addQuote("/b/one.epub", chapter = 1, start = 1, end = 5, text = "cdef")
        shelf.setNote("/b/one.epub", shelf.quotes("/b/one.epub")[0], "why | this\nmatters")
        assertEquals(listOf("why | this\nmatters", ""), shelf.quotes("/b/one.epub").map { it.note })
        shelf.removeQuote("/b/one.epub", shelf.quotes("/b/one.epub")[0])
        assertEquals(listOf("cdef"), shelf.quotes("/b/one.epub").map { it.text })
    }

    @Test fun `a book can be a favourite and be marked to read or read`() {
        assertEquals(false, shelf.favourite("/b/one.epub"))
        assertEquals("", shelf.status("/b/one.epub"))
        shelf.setFavourite("/b/one.epub", true); shelf.setStatus("/b/one.epub", "toread"); shelf.setStatus("/b/two.epub", "read")
        assertEquals(listOf(true, "toread", false, "read"), listOf(shelf.favourite("/b/one.epub"), shelf.status("/b/one.epub"), shelf.favourite("/b/two.epub"), shelf.status("/b/two.epub")))
        shelf.setFavourite("/b/one.epub", false); shelf.setStatus("/b/one.epub", "")
        assertEquals(listOf(false, ""), listOf(shelf.favourite("/b/one.epub"), shelf.status("/b/one.epub")))
    }

    @Test fun `collections are named shelves, and a book can stand on several`() {
        assertEquals(emptyList(), shelf.collections())
        shelf.setInCollection("Sea", "/b/one.epub", true); shelf.setInCollection("Sea", "/b/two.epub", true); shelf.setInCollection("Again", "/b/one.epub", true)
        assertEquals(listOf("Again", "Sea"), shelf.collections())
        assertEquals(setOf("Again", "Sea"), shelf.collectionsOf("/b/one.epub"))
        assertEquals(setOf("Sea"), shelf.collectionsOf("/b/two.epub"))
        shelf.setInCollection("Sea", "/b/one.epub", false)
        assertEquals(setOf("Again"), shelf.collectionsOf("/b/one.epub"))
        shelf.removeCollection("Again")
        assertEquals(listOf("Sea"), shelf.collections())
        assertEquals(emptySet(), shelf.collectionsOf("/b/one.epub"))
    }

    @Test fun `reading time is the time between page turns, a long pause counting as five minutes`() {
        clock = 0L; shelf.opened("/b/one.epub")
        clock = 60_000L; shelf.pageTurned("/b/one.epub")
        clock = 100_000L; shelf.pageTurned("/b/one.epub")
        assertEquals(100L, shelf.timeRead("/b/one.epub"))
        clock += 45 * 60_000L; shelf.pageTurned("/b/one.epub") // the phone was put down
        assertEquals(400L, shelf.timeRead("/b/one.epub"))
        assertEquals(0L, shelf.timeRead("/b/two.epub"))
    }

    @Test fun `reading time is also kept per day, for all books together`() {
        val day = 24 * 3600_000L
        clock = 10 * day; shelf.opened("/b/one.epub")
        clock += 120_000L; shelf.pageTurned("/b/one.epub")
        shelf.opened("/b/two.epub")
        clock += 30_000L; shelf.pageTurned("/b/two.epub")
        clock = 11 * day + 5_000L; shelf.opened("/b/one.epub")
        clock += 60_000L; shelf.pageTurned("/b/one.epub")
        assertEquals(mapOf("1970-01-11" to 150L, "1970-01-12" to 60L), shelf.timeByDay())
    }

    @Test fun `exports highlights as markdown with title, place and note`() {
        val quotes = listOf(Quote(0, 7, 9, "ab", "", 1L), Quote(2, 40, 52, "grey\nwater", "why", 2L))
        assertEquals("# Dune\n\n> ab\n\n- Chapter 1\n\n> grey\n> water\n\n- Chapter 3\n- Note: why\n", exportQuotes("Dune", quotes) { "Chapter ${it.chapter + 1}" })
    }
}
