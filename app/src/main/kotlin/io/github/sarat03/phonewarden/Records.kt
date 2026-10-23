package io.github.sarat03.phonewarden

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import java.io.File
import phonewarden.engine.Debloat
import phonewarden.engine.Modes

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

    /** Copies [record] to Downloads/PhoneWarden; no storage permission needed on Android 10+. Returns the display path. */
    fun copyToDownloads(ctx: Context, record: File): String {
        val name = "phonewarden-${record.nameWithoutExtension}-${System.currentTimeMillis() / 1000}.txt"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/PhoneWarden")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("could not create $name in Downloads")
        ctx.contentResolver.openOutputStream(uri)!!.use { it.write(record.readBytes()) }
        return "Download/PhoneWarden/$name"
    }
}
