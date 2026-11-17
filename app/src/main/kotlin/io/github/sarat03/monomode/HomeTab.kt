package io.github.sarat03.monomode

import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import monomode.engine.Level
import monomode.engine.Profile
import monomode.engine.need
import monomode.engine.label
import io.github.sarat03.monomode.MainActivity.Companion.ps
import io.github.sarat03.monomode.MainActivity.Companion.tab
import io.github.sarat03.monomode.MainActivity.Companion.openMode

/** App name on the left, settings gear on the right. */
@Composable
internal fun MainActivity.AppHeader() = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_launcher_monochrome), null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimary)
    }
    Text(getString(R.string.monomode), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
    if (!rail()) IconButton({ tab = Page.SETTINGS }) { Icon(Icons.Outlined.Settings, getString(R.string.settings)) }
}

/** Modes first, one tile each with its state; the tools sit below, smaller. */
@Composable
internal fun MainActivity.HomeTab() {
    val r = ps.results
    val look = r?.count { it.level == Level.LOOK && !it.system } ?: 0
    val on = ps.activeMode
    AppHeader()
    Section(getString(R.string.modes))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (p in Profile.entries) {
            val t = if (p == on) Tone(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) else neutralTone()
            Card(colors = CardDefaults.cardColors(t.bg, t.fg), shape = MaterialTheme.shapes.large,
                modifier = Modifier.weight(1f).clickable { openMode = p; tab = Page.MODES }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(p.icon, null, Modifier.size(28.dp))
                    Text(getString(if (p == on) R.string.on else if (p in installed) R.string.off else R.string.install), style = MaterialTheme.typography.headlineMedium)
                    Text(p.label, style = MaterialTheme.typography.titleMedium)
                    Text(p.what, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    // tablets reach the tools from the rail
    if (!rail()) {
        Section(getString(R.string.tools))
        HomeRow(AppIcons.Shield, getString(R.string.network_access), if (r == null) getString(R.string.see_which_apps_use_the) else if (look == 0) getString(R.string.nothing_to_look_at) else getString(R.string.s_1x_to_look_at, look), getString(R.string.uses_wireless_debugging_each_time)) { tab = Page.CHECK }
        HomeRow(AppIcons.Broom, getString(R.string.app_cleaner), if (ps.removed.isEmpty()) getString(R.string.remove_bloatware_and_unused_apps) else getString(R.string.s_1x_removed_or_disabled, ps.removed.size), getString(R.string.uses_wireless_debugging_each_time)) { tab = Page.CLEAN }
    }
    val w = warnTone()
    if (ps.wirelessOn) Card(colors = CardDefaults.cardColors(w.bg, w.fg), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Warning, null)
            Text(getString(R.string.wireless_debugging_is_on), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(ph::finish, enabled = ps.busy == null) { Text(getString(R.string.turn_off), color = w.fg) }
        }
    }
}

@Composable
internal fun MainActivity.HomeRow(icon: ImageVector, title: String, state: String, need: String? = null, open: () -> Unit) = Panel(Modifier.fillMaxWidth().clickable(onClick = open)) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(state, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            need?.let { NeedLine(it) }
        }
        Icon(Icons.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
