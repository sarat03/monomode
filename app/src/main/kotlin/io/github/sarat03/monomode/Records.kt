package io.github.sarat03.monomode

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import java.io.File
import monomode.engine.Debloat
import monomode.engine.Modes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import monomode.engine.Finding
import monomode.engine.Shelf
import monomode.engine.decodeFindings
import monomode.engine.encodeFindings
import monomode.engine.newSince

/** Restore records live in app storage; a copy goes to Downloads so it survives uninstalling the app. */
object Records {
    fun debloat(ctx: Context) = File(ctx.filesDir, "debloat-record.txt")
    fun debloater(ctx: Context) = Debloat(debloat(ctx))

    /** Mode records live in app storage next to the debloat record; choices in the SharedPreferences files earlier versions used. */
    fun modes(ctx: Context) = Modes(ctx.filesDir, object : Modes.Store {
        // "off.KIOSK" -> mode_options/KIOSK, "apps.KIOSK" -> home_apps/KIOSK, "reader" -> home_apps/reader
        private fun prefs(key: String) = ctx.getSharedPreferences(if (key.startsWith("off.")) "mode_options" else "home_apps", Context.MODE_PRIVATE)
        private fun name(key: String) = key.substringAfter(".")
        override fun strings(key: String): Set<String> = prefs(key).getStringSet(name(key), emptySet())!!
        override fun putStrings(key: String, value: Set<String>) = prefs(key).edit().putStringSet(name(key), value).apply()
        override fun string(key: String) = prefs(key).getString(name(key), null)
        override fun putString(key: String, value: String) = prefs(key).edit().putString(name(key), value).apply()
    })

    /** What the built-in reader remembers, in its own SharedPreferences file. */
    fun shelf(ctx: Context) = Shelf(object : Shelf.Store {
        private val prefs = ctx.getSharedPreferences("shelf", Context.MODE_PRIVATE)
        override fun get(key: String) = prefs.getString(key, null)
        override fun put(key: String, value: String?) = prefs.edit().putString(key, value).apply()
    })

    /** Remembers the last check's findings in app storage, and which of the current ones are new since then. */
    fun rememberCheck(ctx: Context, current: List<Finding>) {
        val prefs = ctx.getSharedPreferences("last_check", Context.MODE_PRIVATE)
        val before = prefs.getString("findings", null)?.let(::decodeFindings)
        newFindings = newSince(before, current)
        prefs.edit().putString("findings", encodeFindings(current)).apply()
    }

    /** Findings new in the latest check; the Check tab draws a tag on them. */
    var newFindings: Set<Finding> by mutableStateOf(emptySet())
        private set

    /** Copies [record] to Downloads/MonoMode, one file overwritten each time (empty once everything is restored); no storage permission needed on Android 10+. */
    fun copyToDownloads(ctx: Context, record: File) {
        val name = "monomode-${record.nameWithoutExtension}.txt"
        val dir = "Download/MonoMode/"
        val files = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        // shortcut: only finds the file this install created; after a reinstall Android adds a numbered copy beside the old one
        val uri = ctx.contentResolver.query(files, arrayOf(MediaStore.Downloads._ID), "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?", arrayOf(name, dir), null)
            ?.use { if (it.moveToFirst()) ContentUris.withAppendedId(files, it.getLong(0)) else null }
            ?: ctx.contentResolver.insert(files, ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, dir)
            }) ?: error("could not create $name in Downloads")
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(if (record.exists()) record.readBytes() else ByteArray(0)) }
    }
}
