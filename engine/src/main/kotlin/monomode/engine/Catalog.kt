package monomode.engine

import java.io.File

/** A book as the library lists it: what its file says, and what the reader has said about it. [status] is "", "toread" or "read". */
data class BookInfo(
    val path: String, val title: String, val author: String, val series: String, val seriesIndex: Float?,
    val favourite: Boolean, val status: String, val collections: Set<String>, val place: Place?,
)

/** The books on a reading list: "favourites", "toread" or "read"; any other [list] is every book. */
fun onList(books: List<BookInfo>, list: String): List<BookInfo> = when (list) {
    "favourites" -> books.filter { it.favourite }
    "toread", "read" -> books.filter { it.status == list }
    else -> books
}

// the groups a book belongs to under each way of grouping; a book with no author is in the group ""
private fun keys(b: BookInfo, by: String): Collection<String> = when (by) {
    "authors" -> listOf(b.author)
    "series" -> listOfNotNull(b.series.ifEmpty { null })
    "collections" -> b.collections
    else -> listOf(File(b.path).parent.orEmpty())
}

/** The groups the books fall into [by] "authors", "series", "collections" or "folders", by name, each with its number of books. */
fun groups(books: List<BookInfo>, by: String): List<Pair<String, Int>> =
    books.flatMap { keys(it, by) }.groupingBy { it }.eachCount().toList().sortedBy { it.first.lowercase() }

/** The books of one group; a series in the order of its numbers, the others as given. */
fun members(books: List<BookInfo>, by: String, name: String): List<BookInfo> =
    books.filter { name in keys(it, by) }.let { if (by == "series") it.sortedBy { b -> b.seriesIndex ?: Float.MAX_VALUE } else it }
