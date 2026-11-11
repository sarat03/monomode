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
    }) { clock }

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
        shelf.sort = "author"; shelf.colour = false
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
}
