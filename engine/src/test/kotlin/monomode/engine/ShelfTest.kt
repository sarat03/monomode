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
}
