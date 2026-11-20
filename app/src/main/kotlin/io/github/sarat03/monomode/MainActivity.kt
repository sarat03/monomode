package io.github.sarat03.monomode

import io.github.sarat03.monomode.update.UpdateCheck
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.background
import androidx.compose.material3.Surface
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import monomode.engine.Profile
import monomode.engine.Allowed
import monomode.engine.Need
import monomode.engine.Phone
import monomode.engine.PhoneState
import monomode.engine.firstAsk
import monomode.engine.label


/** The pages Home leads to; [EXTRA] in an intent opens one directly. */
object Page {
    const val EXTRA = "page"
    /** With [MODES]: the mode (Profile name) whose page opens, and whose Need prompt shows when it has one. */
    const val MODE = "mode"
    const val HOME = 0; const val CHECK = 1; const val CLEAN = 2; const val MODES = 3; const val SETTINGS = 4
}

class MainActivity : ComponentActivity() {
    internal companion object {
        var paired by mutableStateOf(false)
        /** What the phone module last reported; the screens draw only from this. */
        var ps by mutableStateOf(PhoneState())
        var phone: Phone? = null
        var tab by mutableStateOf(0)
        val selected = mutableStateListOf<String>()
        var launcherPkgs by mutableStateOf<Set<String>>(emptySet())
        var query by mutableStateOf("")
        val restorePick = mutableStateListOf<String>()
        /** The mode whose page is open; null shows Home. */
        var openMode by mutableStateOf<Profile?>(null)
        /** App cleaner's chip: 0 apps, 1 background services, 2 removed (restore). */
        var cleanFilter by mutableStateOf(0)
    }

    /** First launch after install: the intro runs before anything else. */
    private var intro by mutableStateOf(false)
    internal val modes by lazy { Records.modes(applicationContext) }
    /** Process-wide, like the state above: an open session must survive the activity being recreated (battery saver turns on dark theme). */
    internal val ph: Phone get() = phoneOf(applicationContext)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installed = modes.installed
        Appearance.load(this)
        intro = !onboarded()
        UpdateCheck.load(this)
        // colour runs under the status and navigation bars, as on the home screen; content keeps clear with safeDrawingPadding
        enableEdgeToEdge()
        openPage(intent)
        setContent { AppTheme { Surface(Modifier.fillMaxSize()) { if (intro) Onboarding { intro = false } else Screen() } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openPage(intent)
    }

    // from a mode home screen: open the mode that is on, or Home when none is
    internal fun openPage(i: Intent?) { i?.getIntExtra(Page.EXTRA, -1)?.takeIf { it >= 0 }?.let { tab = it; if (it == Page.MODES) openMode = modes.active }
        i?.getStringExtra(Page.MODE)?.let { Profile.valueOf(it) }?.let { p ->
            openMode = p
            if (p in modes.installed) when (p.firstAsk(modes.off(p), Allowed(Session.canSwitch(this), dndAccess(this)))) {
                Need.SETUP_ONCE -> ph.askSetup()
                Need.DND_ACCESS -> askDnd()
                null -> Unit
            }
        }
    }

    /** Set by "Enter e-reader": open the reader as soon as the mode is on. */
    internal var entering = false
    /** Modes the user installed; the others show on Home with Install until then. */
    internal var installed by mutableStateOf(emptySet<Profile>())

    /** All files access, reread on return from its Settings page. */
    internal var allFiles by mutableStateOf(false)
    /** The one-time setup is done: MonoMode holds the permission to change settings itself. Reread on return. */
    internal var setUp by mutableStateOf(false)
    /** Something is pinned (screen pinning) as MonoMode comes back; reread on return, since an ended pin changes nothing else. */
    internal var pinnedNow by mutableStateOf(false)
    /** Android's Do Not Disturb access, which Mute notifications uses. Reread on return from its Settings page. */
    internal var dnd by mutableStateOf(false)
    internal val allowed get() = Allowed(setUp, dnd)

    override fun onResume() {
        super.onResume()
        allFiles = Environment.isExternalStorageManager()
        setUp = Session.canSwitch(this)
        dnd = dndAccess(this)
        pinnedNow = pinned(this)
        paired = Pairing.isPaired(this)
        // Wireless debugging stays off: an action that needs it switches it on for itself. Already on (just paired):
        // connects once, which also finishes the setup by granting MonoMode its permission
        ph.refresh(paired)
        UpdateCheck.auto(this, modes.active != null)
    }

    override fun onStop() {
        // leaving the screen ends the session: some phones (realme) kill the app from Recents without onDestroy, leaving Wireless debugging on
        if (!isChangingConfigurations) ph.finishQuietly()
        super.onStop()
    }

    // own task, so opening MonoMode again brings back MonoMode, not Settings
    // the fragment_args_key extras make Settings scroll to and flash Wireless debugging (AOSP and realme UI)
    // Developer options still hidden: About phone instead, where Build number is tapped 7 times to show them;
    // a phone without either screen gets plain Settings
    internal fun openDeveloperOptions() {
        val dev = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
            .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", "toggle_adb_wireless") })
        for (i in listOf(if (devOptionsOn()) dev else Intent(Settings.ACTION_DEVICE_INFO_SETTINGS), Intent(Settings.ACTION_SETTINGS)))
            if (runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }

    internal fun devOptionsOn() = Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

    /** Cached: lists redraw on every search keystroke. */
    internal val labels = HashMap<String, String>()
    internal fun label(pkg: String) = labels.getOrPut(pkg) { appLabel(this, pkg) }

    internal val Profile.what get() = getString(whatRes)

    /** The app's own icon, small, for pick lists; cached like labels. */
    internal val smallIcons = HashMap<String, androidx.compose.ui.graphics.ImageBitmap>()
}
