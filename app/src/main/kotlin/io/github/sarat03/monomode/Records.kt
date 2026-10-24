package io.github.sarat03.monomode

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import java.io.File
import monomode.engine.Debloat
import monomode.engine.Modes

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
