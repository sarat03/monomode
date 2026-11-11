package monomode.engine

import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

const val KOREADER = "org.koreader.launcher"

/** The book a reading app last had open. [percent] is null when the app has no progress on record. */
data class Book(val path: String, val title: String, val percent: Int?)

/** A value KOReader wrote as `["key"] = "..."` (Lua %q: only quote, backslash and newline are escaped). */
private fun luaString(lua: String, key: String): String? =
    Regex("""\["$key"\] = "((?:[^"\\]|\\.)*)"""", RegexOption.DOT_MATCHES_ALL).find(lua)
        ?.groupValues?.get(1)?.replace(Regex("""\\(.)""", RegexOption.DOT_MATCHES_ALL), "$1")

private fun read(f: File): String? = try { f.readText() } catch (e: IOException) { null } // missing, or no file access

/**
 * KOReader's last book, from its own files under [sdcard] (frontend/docsettings.lua, reader.lua in its source):
 * koreader/settings.reader.lua names it as "lastfile"; title and progress are in the book's sidecar,
 * `<book without suffix>.sdr/metadata.<suffix>.lua`, next to the book or under koreader/docsettings.
 * Null when KOReader has not opened a book, the book is gone, or the files cannot be read.
 */
fun koreaderBook(sdcard: File): Book? {
    val path = luaString(read(File(sdcard, "koreader/settings.reader.lua")) ?: return null, "lastfile") ?: return null
    val book = File(path)
    if (!book.isFile) return null
    val base = path.substringBeforeLast('.')
    val name = "metadata.${book.extension.ifEmpty { "_" }}.lua"
    // shortcut: KOReader's "hash" metadata location is not read (needs its partial MD5); such books show the file name and no progress
    val meta = listOf(File("$base.sdr", name), File(File(sdcard, "koreader/docsettings$base.sdr"), name)).firstNotNullOfOrNull(::read).orEmpty()
    val done = Regex("""\["percent_finished"\] = ([-0-9.e]+)""").find(meta)?.groupValues?.get(1)?.toDoubleOrNull()
    return Book(path, luaString(meta, "title")?.ifBlank { null } ?: book.nameWithoutExtension, done?.let { (it * 100).roundToInt().coerceIn(0, 100) })
}
