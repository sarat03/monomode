package io.github.sarat03.phonewarden

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import java.io.File

/** Restore records live in app storage; a copy goes to Downloads so it survives uninstalling the app. */
object Records {
    fun debloat(ctx: Context) = File(ctx.filesDir, "debloat-record.txt")
    fun mode(ctx: Context) = File(ctx.filesDir, "mode-record.txt")

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
