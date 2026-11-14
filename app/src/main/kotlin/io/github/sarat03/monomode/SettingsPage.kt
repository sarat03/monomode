package io.github.sarat03.monomode

import android.content.Intent
import kotlin.concurrent.thread
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.key
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sarat03.monomode.MainActivity.Companion.paired
import io.github.sarat03.monomode.MainActivity.Companion.ps
import io.github.sarat03.monomode.MainActivity.Companion.tab

/** App settings, reachable from the gear on Home, with or without a session. */
@Composable
internal fun MainActivity.SettingsPage() {
    Section(getString(R.string.connection))
    SettingRow(getString(R.string.pairing), if (paired) getString(R.string.this_phone_is_paired_with) else getString(R.string.not_paired_yet)) {
        if (paired) OutlinedButton({ forgetPairing() },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(getString(R.string.forget_and_pair_again)) }
    }
    Section(getString(R.string.appearance))
    SettingRow(getString(R.string.theme), getString(R.string.colours_for_monomode_and_its)) {
        Column {
            for ((key, name) in Appearance.options) Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(Appearance.mode == key, role = Role.RadioButton) { Appearance.set(applicationContext, key) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) { RadioButton(Appearance.mode == key, null); Text(getString(name)) }
        }
    }
    Section(getString(R.string.home_screen))
    // Android decides the default home app; this only shows which it is and opens Android's own chooser
    val ours = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
    SettingRow(getString(R.string.home_screen), getString(if (ours) R.string.monomode_is_your_home_screen else R.string.your_phones_own_home_screen)) {
        OutlinedButton({ runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } }) { Text(getString(R.string.change)) }
    }
    Section(getString(R.string.backups))
    SettingRow(getString(R.string.restore_lists), getString(R.string.a_copy_of_what_was))
    Section(getString(R.string.about))
    SettingRow(getString(R.string.app_name), getString(R.string.about_version, packageManager.getPackageInfo(packageName, 0).versionName)) {
        OutlinedButton({ startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/sarat03/monomode"))) }) { Text(getString(R.string.source_code)) }
    }
}

/** Forgets the pairing and takes back MonoMode's Wireless debugging switch, connected or not. */
internal fun MainActivity.forgetPairing() {
    val app = applicationContext
    Pairing.forget(app); paired = false; tab = Page.HOME
    if (ps.connected) ph.disconnect(Session.revokeSwitch(app))
    // not connected: switch on, connect once to revoke, then off over that link (after the revoke the app cannot switch it)
    else if (Session.canSwitch(app)) thread {
        runCatching { Session.switch(app, true); Session.open(app).run { runCatching { shell.run(Session.revokeSwitch(app)) }; finish() } }
        if (Session.canSwitch(app)) Session.switch(app, false) // the revoke failed: at least leave it off
    }
}

@Composable
internal fun MainActivity.SettingRow(title: String, text: String, action: (@Composable () -> Unit)? = null) = Panel(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}
