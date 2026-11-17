package io.github.sarat03.monomode

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS
import android.app.NotificationManager.INTERRUPTION_FILTER_ALL
import android.app.NotificationManager.INTERRUPTION_FILTER_NONE
import android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
import android.content.Context
import android.os.Build
import android.provider.Settings
import monomode.engine.Change
import monomode.engine.DND
import monomode.engine.PhoneSettings

/**
 * A mode's settings changed by MonoMode itself, with no Wireless debugging: settings with the permission granted once
 * over it (WRITE_SECURE_SETTINGS), Do Not Disturb with Android's Do Not Disturb access. What it cannot do here (the
 * home screen, a key this phone refuses to apps) it says so, and the change goes over Wireless debugging instead.
 */
class AppSettings(private val ctx: Context) : PhoneSettings {
    private val cr get() = ctx.contentResolver
    private val nm get() = ctx.getSystemService(NotificationManager::class.java)

    override fun read(c: Change): String? = here {
        when (c.ns) {
            DND -> DND_NAMES[nm.currentInterruptionFilter]
            "secure" -> Settings.Secure.getString(cr, c.key) ?: "null"
            "global" -> Settings.Global.getString(cr, c.key) ?: "null"
            "system" -> Settings.System.getString(cr, c.key) ?: "null"
            else -> null
        }
    }

    override fun write(c: Change, value: String): Boolean = here {
        // no public delete: putting null leaves the key unset, which reads back as "null" as before
        val v = value.takeUnless { it == "null" }
        when (c.ns) {
            DND -> nm.isNotificationPolicyAccessGranted && DND_NAMES.entries.firstOrNull { it.value == value }?.let { nm.setInterruptionFilter(it.key); true } == true
            "secure" -> Session.canSwitch(ctx) && Settings.Secure.putString(cr, c.key, v)
            "global" -> Session.canSwitch(ctx) && Settings.Global.putString(cr, c.key, v)
            // refused (SecurityException, or IllegalArgumentException for an unknown key) and the key is absent: a vendor key this phone lacks (realme's saver one), nothing to set
            "system" -> Session.canSwitch(ctx) && try { Settings.System.putString(cr, c.key, v) } catch (e: RuntimeException) { Settings.System.getString(cr, c.key) == null && Build.MANUFACTURER.lowercase() !in OPPO_FAMILY }
            else -> false
        }
    } ?: false

    // some phones refuse a key to apps (SecurityException, IllegalArgumentException): not here, so Wireless debugging does it
    private fun <T> here(f: () -> T): T? = try { f() } catch (e: Exception) { null }

    private companion object {
        // the key is theirs, and they refuse it to apps: the shell must write it even when it is unset
        val OPPO_FAMILY = setOf("realme", "oppo", "oneplus")
        val DND_NAMES = mapOf(INTERRUPTION_FILTER_ALL to "off", INTERRUPTION_FILTER_PRIORITY to "priority", INTERRUPTION_FILTER_NONE to "on", INTERRUPTION_FILTER_ALARMS to "alarms")
    }
}

/** Mute notifications runs without Wireless debugging once Android's Do Not Disturb access is allowed. */
fun dndAccess(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

/** An app is pinned (screen pinning); turning Kiosk off must then unpin it, over Wireless debugging. */
fun pinned(ctx: Context) = ctx.getSystemService(ActivityManager::class.java).lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
