package monomode.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogTest {
    private fun book(path: String, title: String, author: String = "", series: String = "", index: Float? = null, favourite: Boolean = false, status: String = "", collections: Set<String> = emptySet()) =
        BookInfo(path, title, author, series, index, favourite, status, collections, null)

    private val dune = book("/sd/Books/dune.epub", "Dune", "Frank Herbert", "Dune", 1f, favourite = true, collections = setOf("Desert"))
    private val messiah = book("/sd/Books/messiah.epub", "Dune Messiah", "Frank Herbert", "Dune", 2f, status = "toread")
    private val emma = book("/sd/Download/emma.epub", "Emma", "Jane Austen", status = "read", collections = setOf("Classics", "Desert"))
    private val notes = book("/sd/Download/notes.epub", "Notes")
    private val all = listOf(messiah, emma, notes, dune)

    @Test fun `the reading lists pick out favourites, books to read and books read`() {
        assertEquals(listOf(dune), onList(all, "favourites"))
        assertEquals(listOf(messiah), onList(all, "toread"))
        assertEquals(listOf(emma), onList(all, "read"))
        assertEquals(all, onList(all, "all"))
    }

    @Test fun `books group by author, series, collection and folder, each group with its count`() {
        assertEquals(listOf("" to 1, "Frank Herbert" to 2, "Jane Austen" to 1), groups(all, "authors"))
        assertEquals(listOf("Dune" to 2), groups(all, "series")) // books outside a series are not a group
        assertEquals(listOf("Classics" to 1, "Desert" to 2), groups(all, "collections"))
        assertEquals(listOf("/sd/Books" to 2, "/sd/Download" to 2), groups(all, "folders"))
    }

    @Test fun `a group lists its books, a series in its own order`() {
        assertEquals(listOf(dune, messiah), members(all, "series", "Dune"))
        assertEquals(listOf(emma, dune), members(all, "collections", "Desert"))
        assertEquals(listOf(notes), members(all, "authors", ""))
        assertEquals(listOf(emma, notes), members(all, "folders", "/sd/Download"))
    }
}
