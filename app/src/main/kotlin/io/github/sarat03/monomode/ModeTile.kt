package io.github.sarat03.monomode

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import monomode.engine.Allowed
import monomode.engine.Phone
import monomode.engine.PhoneState
import monomode.engine.Profile
import monomode.engine.firstAsk

/** The one Phone of the process, shared by the screens and the tiles, so a tile runs the same actions Home does. */
internal fun phoneOf(app: Context): Phone = MainActivity.phone ?: Phone(
    open = { Session.open(app) }, debloat = Records.debloater(app), modes = Records.modes(app),
    wirelessOn = { Session.isWirelessDebuggingOn(app) }, saveCopy = { Records.copyToDownloads(app, it) }, onChange = { MainActivity.ps = it; ModeTile.changed(app, it) },
    switchOff = { if (Session.canSwitch(app)) Session.switch(app, false) },
    local = AppSettings(app), pinned = { pinned(app) }, paired = { Pairing.isPaired(app) }, switchOn = { Session.canSwitch(app) && Session.switch(app, true) },
    lister = AppPackages(app),
).also { MainActivity.phone = it }

/** Quick Settings tile for one mode: shows whether it is on, and a tap turns it on or off. One subclass per mode, as Android needs one service per tile. */
abstract class ModeTile(private val profile: Profile) : TileService() {
    private val modes by lazy { Records.modes(applicationContext) }

    override fun onStartListening() { listening = this; sync() }
    override fun onStopListening() { if (listening === this) listening = null }

    private fun sync() {
        val tile = qsTile ?: return
        val on = modes.active
        tile.state = when (on) { profile -> Tile.STATE_ACTIVE; null -> Tile.STATE_INACTIVE; else -> Tile.STATE_UNAVAILABLE } // one mode at a time
        tile.subtitle = getString(if (on == profile) R.string.tile_on else R.string.tile_off)
        tile.updateTile()
    }

    override fun onClick() = unlockAndRun {
        val active = modes.active
        val allowed = Allowed(Session.canSwitch(this), dndAccess(this))
        when {
            active == profile -> phoneOf(applicationContext).turnOff()
            active != null -> Unit
            // what the app asks for first (ADR 0006), a mode not installed yet, and Kiosk (it needs its app chosen): the app shows the same prompt or page
            profile !in modes.installed || profile == Profile.KIOSK || profile.firstAsk(modes.off(profile), allowed) != null -> openApp()
            else -> phoneOf(applicationContext).turnOn(profile)
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).putExtra(Page.EXTRA, Page.MODES).putExtra(Page.MODE, profile.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Android 14 replaced the Intent form with a PendingIntent
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, profile.ordinal, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated") startActivityAndCollapse(intent) // below 34 only
    }

    companion object {
        @Volatile private var listening: ModeTile? = null

        /** After an action ends with a message or a missing setup, nobody is looking at a tile: open the app, which shows it. */
        fun changed(app: Context, s: PhoneState) {
            val tile = listening ?: return
            tile.sync()
            if (s.busy == null && (s.needsSetup || s.message != null)) tile.openApp()
        }
    }
}

class KioskTile : ModeTile(Profile.KIOSK)
class EReaderTile : ModeTile(Profile.E_READER)
