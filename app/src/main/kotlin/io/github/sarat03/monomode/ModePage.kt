package io.github.sarat03.monomode

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import monomode.engine.KOREADER
import monomode.engine.Profile
import monomode.engine.Need
import monomode.engine.firstAsk
import monomode.engine.need
import monomode.engine.label
import io.github.sarat03.monomode.MainActivity.Companion.ps
import io.github.sarat03.monomode.MainActivity.Companion.tab
import io.github.sarat03.monomode.MainActivity.Companion.openMode

/** A mode's page: its state and switch on top, then what it changes, its apps and, for Kiosk, pinning. */
@Composable
internal fun MainActivity.ModePage(profile: Profile) {
    val on = ps.activeMode
    val name = profile.label
    val off = remember(profile) { mutableStateListOf(*modes.off(profile).toTypedArray()) }
    // held here, not in the picker, so choosing an app enables "Turn on" at once
    var pinApp by remember(profile) { mutableStateOf(if (profile == Profile.KIOSK) kioskApp() else null) }
    // not installed yet: the page only offers to install it; its options and apps come after
    if (profile !in installed) {
        Hero(neutralTone(), profile.icon, getString(R.string.install_1x, name), profile.what) {
            Button({ modes.install(profile); installed = modes.installed }) { Text(getString(R.string.install)) }
        }
        return
    }
    Hero(
        if (on == profile) safeTone() else neutralTone(), profile.icon,
        if (on == profile) getString(R.string.s_1x_is_on, name) else getString(R.string.s_1x_is_off, name),
        if (on == profile) getString(R.string.your_settings_come_back_when)
        else if (on != null) getString(R.string.turn_off_1x_first_one, on.label) else profile.what,
    ) {
        val t = if (on == profile) safeTone() else neutralTone()
        val kioskApp = pinApp?.let { kioskApp() } // read again: the app may have been uninstalled since it was chosen
        if (on == profile) {
            Button({
                ph.turnOff()
            }, enabled = ps.busy == null, colors = ButtonDefaults.buttonColors(t.fg, t.bg)) { Text(getString(R.string.turn_off)) }
            // after someone unpinned: Kiosk is still on, so the app can be pinned again
            kioskApp?.let { (component, label) -> OutlinedButton({ ph.pin(component, label) }, enabled = ps.busy == null) { Text(getString(R.string.pin_again), color = t.fg) } }
        }
        else Button({
            when (profile.firstAsk(off.toSet(), allowed)) {
                Need.SETUP_ONCE -> return@Button ph.askSetup()
                Need.DND_ACCESS -> return@Button askDnd()
                null -> Unit
            }
            entering = profile == Profile.E_READER
            // Kiosk is one step: settings, then its app pinned, then Wireless debugging off
            if (profile == Profile.KIOSK) kioskApp?.let { (component, label) -> ph.turnOnKiosk(component, label) } else ph.turnOn(profile)
        }, enabled = ps.busy == null && on == null && (profile != Profile.KIOSK || kioskApp != null)) { Text(getString(if (profile == Profile.E_READER) R.string.enter_e_reader else R.string.turn_on)) }
        if (on != profile) TextButton({ modes.uninstall(profile); installed = modes.installed; openMode = null; tab = Page.HOME }, enabled = ps.busy == null) { Text(getString(R.string.uninstall), color = t.fg) }
    }
    // straight into the reader once the mode is on, as switching on an e-reader does
    LaunchedEffect(on) {
        if (entering && on == Profile.E_READER) { entering = false; startActivity(Intent(this@ModePage, HomeActivity::class.java).putExtra(EXTRA_LIBRARY, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    Section(getString(R.string.what_changes))
    Panel(Modifier.fillMaxWidth()) {
        profile.options.keys.forEachIndexed { i, option ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(option !in off, enabled = on == null, role = Role.Switch) { keep ->
                if (keep) off -= option else off += option
                modes.setOff(profile, off.toSet())
            }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(option, style = MaterialTheme.typography.bodyLarge)
                    // what the option needs first, until it is done; tapping the Do Not Disturb line opens that permission
                    when (profile.need(option, allowed)) {
                        Need.SETUP_ONCE -> NeedLine(getString(R.string.needs_wireless_debugging_once))
                        Need.DND_ACCESS -> NeedLine(getString(R.string.needs_dnd_access_once), ::askDnd)
                        null -> Unit
                    }
                }
                // drawn enabled even while locked: a disabled "on" switch looks off; the row itself ignores taps then
                Switch(option !in off, null)
            }
            if (i < profile.options.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
    if (on == profile) Text(getString(R.string.turn_the_mode_off_to), Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (profile == Profile.E_READER) { Section(getString(R.string.apps)); ReadingApp() }
    if (profile == Profile.KIOSK) {
        Section(getString(R.string.kiosk_app))
        NeedLine(getString(R.string.pinning_uses_wireless_debugging_each_time))
        KioskAppPicker(askPin = "Ask for PIN to unpin" !in off, chosen = pinApp) { pinApp = it }
    }
}

/** The second line under an option or tool: what it needs from the user, or that it uses Wireless debugging each time. */
@Composable
internal fun MainActivity.NeedLine(text: String, onClick: (() -> Unit)? = null) = Text(text,
    if (onClick == null) Modifier else Modifier.clickable(onClickLabel = getString(R.string.allow), onClick = onClick).padding(vertical = 4.dp),
    style = MaterialTheme.typography.bodySmall, color = if (onClick == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)

internal fun MainActivity.askDnd() = startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))

/** The e-reader's reading app: MonoMode's own reader or another one, and KOReader's current book. */
@Composable
internal fun MainActivity.ReadingApp() {
    val all = remember { launcherApps(this) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        run {
            val readers = remember { listOf(packageName to getString(R.string.monomode_reader)) + all }
            var reader by remember { mutableStateOf(modes.reader(all.map { it.first })) }
            var choose by remember { mutableStateOf(false) }
            ListRow(getString(R.string.reading_app), readers.first { it.first == reader }.second) { choose = true }
            if (choose) ListDialog(getString(R.string.reading_app), getString(R.string.opens_from_continue_reading), { choose = false }, search = true) { q ->
                items(readers.filter { it.matches(q) }, key = { it.first }) { (pkg, label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(pkg == reader, role = Role.RadioButton) { reader = pkg; modes.setReader(pkg); choose = false }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(pkg == reader, null); Spacer(Modifier.width(12.dp)); AppIcon(pkg); Text(label, Modifier.padding(start = 12.dp))
                    }
                }
            }
            // KOReader's last book is in its own files on shared storage; only All files access can read them
            if (reader == KOREADER) ListRow(getString(R.string.show_the_current_book), getString(if (allFiles) R.string.title_cover_and_progress_on else R.string.needs_all_files_access)) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
    }
}

/**
 * Gesture navigation per Android's navigation_mode (2), or per realme/Oppo's own switch
 * (hide_navigationbar_enable > 0), which they use instead: there navigation_mode stays 0.
 */
internal fun MainActivity.usesGestures() = Settings.Secure.getInt(contentResolver, "navigation_mode", 0) == 2 ||
    Settings.Secure.getInt(contentResolver, "hide_navigationbar_enable", 0) > 0

internal fun MainActivity.unpinHow() = if (usesGestures()) getString(R.string.swipe_up_from_the_bottom) else getString(R.string.touch_and_hold_back_and)

/** Kiosk's one app, as a startable component and its name; null until chosen (or when it was uninstalled). */
internal fun MainActivity.kioskApp(): Pair<String, String>? = modes.apps(Profile.KIOSK).firstNotNullOfOrNull { pkg ->
    packageManager.getLaunchIntentForPackage(pkg)?.component?.flattenToShortString()?.let { it to label(pkg) }
}

/** Which app Kiosk pins; changeable only while Kiosk is off, like its other options. */
@Composable
internal fun MainActivity.KioskAppPicker(askPin: Boolean, chosen: Pair<String, String>?, onChosen: (Pair<String, String>?) -> Unit) {
    var choose by remember { mutableStateOf(false) }
    val kioskOff = ps.activeMode != Profile.KIOSK
    ListRow(getString(R.string.app_to_pin), chosen?.second ?: getString(R.string.choose_the_app_kiosk_pins)) { if (kioskOff) choose = true }
    // what pinning does, up front: people were surprised by the PIN prompt and by debugging being off afterwards
    Text(
        getString(if (askPin) R.string.to_unpin_asks_pin else R.string.to_unpin, unpinHow()) + " " + getString(R.string.pinning_switches_off_wireless_debugging),
        Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (choose) {
        val all = remember { launcherApps(this) }
        ListDialog(getString(R.string.app_to_pin), null, { choose = false }, search = true) { q ->
            items(all.filter { it.matches(q) }, key = { it.first }) { (pkg, label) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(pkg == chosen?.first?.substringBefore("/"), role = Role.RadioButton) {
                    modes.setApps(Profile.KIOSK, setOf(pkg)); onChosen(kioskApp()); choose = false
                }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(pkg == chosen?.first?.substringBefore("/"), null); Spacer(Modifier.width(12.dp)); AppIcon(pkg); Text(label, Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}
