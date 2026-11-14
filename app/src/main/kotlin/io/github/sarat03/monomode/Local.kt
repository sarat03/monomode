package io.github.sarat03.monomode

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.provider.Settings

/**
 * Runs the few shell commands a mode needs inside the app, so they need no Wireless debugging: settings with the
 * permission granted once over it (WRITE_SECURE_SETTINGS), Do Not Disturb with Android's Do Not Disturb access.
 * Returns what the shell would print, or null when the app cannot do it here; the command then goes over Wireless debugging.
 */
fun runLocally(ctx: Context, a: List<String>): String? = try {
    val cr = ctx.contentResolver
    when {
        a.size == 4 && a[0] == "settings" && a[1] == "get" -> when (a[2]) {
            "secure" -> Settings.Secure.getString(cr, a[3])
            "global" -> Settings.Global.getString(cr, a[3])
            "system" -> Settings.System.getString(cr, a[3])
            else -> return null
        } ?: "null" // as the shell prints an unset key
        a.size in 4..5 && a[0] == "settings" && (a[1] == "put" || a[1] == "delete") -> {
            if (!Session.canSwitch(ctx)) return null
            val v = if (a[1] == "put") a.getOrNull(4) else null // no public delete: an unset key reads back as null either way
            val ok = when (a[2]) {
                "secure" -> Settings.Secure.putString(cr, a[3], v)
                "global" -> Settings.Global.putString(cr, a[3], v)
                "system" -> Settings.System.putString(cr, a[3], v)
                else -> false
            }
            if (ok) "" else null
        }
        a.size == 4 && a.take(3) == listOf("cmd", "notification", "set_dnd") -> {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val filter = when (a[3]) {
                "off" -> NotificationManager.INTERRUPTION_FILTER_ALL
                "priority" -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
                "alarms" -> NotificationManager.INTERRUPTION_FILTER_ALARMS
                "on" -> NotificationManager.INTERRUPTION_FILTER_NONE
                else -> return null
            }
            if (!nm.isNotificationPolicyAccessGranted) return null
            nm.setInterruptionFilter(filter); ""
        }
        // Kiosk's turn off unpins first; nothing is pinned while MonoMode is on screen, so nothing to stop
        a == listOf("am", "task", "lock", "stop") ->
            if (ctx.getSystemService(ActivityManager::class.java).lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) "" else null
        else -> null
    }
} catch (e: Exception) { null } // some phones refuse a key to apps (SecurityException, IllegalArgumentException): Wireless debugging does it instead

/** Mute notifications can run without Wireless debugging once Android's Do Not Disturb access is allowed. */
fun dndAccess(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
