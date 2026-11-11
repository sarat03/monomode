package monomode.engine

/** Where a book was left: [inChapter] and [progress] (of the whole book) are 0..1, [at] is when, in epoch milliseconds. */
data class Place(val chapter: Int, val inChapter: Float, val progress: Float, val at: Long)

/** A bookmark: a place in a chapter (0..1), how far through the book that is, and the chapter's name to show in the list. */
data class Mark(val chapter: Int, val inChapter: Float, val progress: Float, val label: String, val at: Long)

/**
 * How the page looks. [theme] "day", "sepia" or "night"; [font] a CSS family, or "" for the book's own;
 * [size] in percent; [spacing] the line height in percent; [margin] each side, in percent of the width.
 * [indent] sets paragraphs the way a printed book does: first line indented, no gap between them.
 */
data class Look(
    val theme: String, val font: String, val size: Int, val spacing: Int, val margin: Int,
    val bold: Boolean = false, val justify: Boolean = false, val hyphens: Boolean = false, val indent: Boolean = false,
)

/**
 * What MonoMode's reader remembers, keyed by the book's path. Values are short strings in [store]:
 * SharedPreferences on the phone, a map in tests.
 */
class Shelf(private val store: Store, private val now: () -> Long = System::currentTimeMillis) {
    interface Store {
        fun get(key: String): String?
        /** null removes the key. */
        fun put(key: String, value: String?)
    }

    fun place(path: String): Place? = store.get("at:$path")?.split('|')?.let {
        try { Place(it[0].toInt(), it[1].toFloat(), it[2].toFloat(), it[3].toLong()) } catch (e: RuntimeException) { null }
    }
    fun save(path: String, chapter: Int, inChapter: Float, progress: Float) = store.put("at:$path", "$chapter|$inChapter|$progress|${now()}")

    /** Night the first time when the app is dark; after that, whatever was chosen. */
    fun look(dark: Boolean): Look {
        val v = store.get("look")?.split('|').orEmpty()
        return Look(v.getOrNull(0) ?: if (dark) "night" else "day", v.getOrNull(1) ?: "serif", v.getOrNull(2)?.toIntOrNull() ?: 110, v.getOrNull(3)?.toIntOrNull() ?: 150, v.getOrNull(4)?.toIntOrNull() ?: 6,
            v.getOrNull(5) == "true", v.getOrNull(6) == "true", v.getOrNull(7) == "true", v.getOrNull(8) == "true")
    }
    fun setLook(l: Look) = store.put("look", listOf(l.theme, l.font, l.size, l.spacing, l.margin, l.bold, l.justify, l.hyphens, l.indent).joinToString("|"))

    /** Screen brightness while in the reader, 0..1; null leaves it to the phone. */
    var brightness: Float?
        get() = store.get("brightness")?.toFloatOrNull()
        set(v) = store.put("brightness", v?.toString())

    /** Keep the screen from switching off while a book is open. */
    var keepOn: Boolean
        get() = store.get("keepOn") == "true"
        set(v) = store.put("keepOn", v.toString())

    /** "auto", "portrait" or "landscape". */
    var orientation: String
        get() = store.get("orientation") ?: "auto"
        set(v) = store.put("orientation", v)

    /** The volume keys turn the page. */
    var volumeKeys: Boolean
        get() = store.get("volumeKeys") != "false"
        set(v) = store.put("volumeKeys", v.toString())

    /** What the line at the foot of the page shows: any of "clock", "title", "battery", "left" (pages left in the chapter), "page", "percent". */
    var status: Set<String>
        get() = store.get("status")?.split(',')?.filter { it.isNotEmpty() }?.toSet() ?: setOf("title", "page", "percent")
        set(v) = store.put("status", v.joinToString(","))

    /** Library order: "recent", "title" or "author". */
    var sort: String
        get() = store.get("sort") ?: "recent"
        set(v) = store.put("sort", v)

    /** False shows the reader in grey, like e-paper. */
    var colour: Boolean
        get() = store.get("colour") != "false"
        set(v) = store.put("colour", v.toString())

    /** A book's bookmarks, in reading order. */
    fun marks(path: String): List<Mark> = store.get("marks:$path").orEmpty().lines().mapNotNull { line ->
        val v = line.split('|', limit = 5)
        try { Mark(v[0].toInt(), v[1].toFloat(), v[2].toFloat(), v[4], v[3].toLong()) } catch (e: RuntimeException) { null }
    }.sortedWith(compareBy({ it.chapter }, { it.inChapter }))

    /** The bookmark on the page of [chapter] that runs from [from] up to [until], if it has one. */
    fun markOn(path: String, chapter: Int, from: Float, until: Float): Mark? = marks(path).firstOrNull { it.chapter == chapter && it.inChapter >= from && it.inChapter < until }

    /** Bookmarks that page, or removes the bookmark it already holds. */
    fun toggleMark(path: String, chapter: Int, from: Float, until: Float, progress: Float, label: String) {
        val on = markOn(path, chapter, from, until)
        val all = if (on != null) marks(path) - on else marks(path) + Mark(chapter, from, progress, label.replace('\n', ' '), now())
        store.put("marks:$path", all.joinToString("\n") { "${it.chapter}|${it.inChapter}|${it.progress}|${it.at}|${it.label}" }.ifEmpty { null })
    }
}
