package monomode.engine

/** Where a book was left: [inChapter] and [progress] (of the whole book) are 0..1, [at] is when, in epoch milliseconds. */
data class Place(val chapter: Int, val inChapter: Float, val progress: Float, val at: Long)

/**
 * How the page looks. [theme] "day", "sepia" or "night"; [font] a CSS family, or "" for the book's own;
 * [size] in percent; [spacing] the line height in percent; [margin] each side, in percent of the width.
 */
data class Look(val theme: String, val font: String, val size: Int, val spacing: Int, val margin: Int)

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
        return Look(v.getOrNull(0) ?: if (dark) "night" else "day", v.getOrNull(1) ?: "serif", v.getOrNull(2)?.toIntOrNull() ?: 110, v.getOrNull(3)?.toIntOrNull() ?: 150, v.getOrNull(4)?.toIntOrNull() ?: 6)
    }
    fun setLook(l: Look) = store.put("look", listOf(l.theme, l.font, l.size, l.spacing, l.margin).joinToString("|"))

    /** Library order: "recent", "title" or "author". */
    var sort: String
        get() = store.get("sort") ?: "recent"
        set(v) = store.put("sort", v)

    /** False shows the reader in grey, like e-paper. */
    var colour: Boolean
        get() = store.get("colour") != "false"
        set(v) = store.put("colour", v.toString())
}
