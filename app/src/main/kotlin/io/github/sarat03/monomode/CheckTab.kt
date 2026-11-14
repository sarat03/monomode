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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import monomode.engine.Finding
import monomode.engine.Level
import monomode.engine.label
import io.github.sarat03.monomode.MainActivity.Companion.ps

@Composable
internal fun MainActivity.CheckTab() {
    val r = ps.results
    val look = r?.count { it.level == Level.LOOK && !it.system } ?: 0
    val tone = when { r == null -> neutralTone(); look == 0 -> safeTone(); else -> warnTone() }
    Hero(
        tone, if (r != null && look == 0) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
        when { r == null -> getString(R.string.not_checked_yet); look == 0 -> getString(R.string.nothing_to_look_at); else -> resources.getQuantityString(R.plurals.things_to_look_at, look, look) },
        if (r == null) getString(R.string.read_only_nothing_is_changed) else if (look == 0) getString(R.string.checked_just_now_nothing_was) else getString(R.string.everything_else_looks_normal),
    ) {
        Button((ph::check), enabled = ps.busy == null, colors = ButtonDefaults.buttonColors(tone.fg, tone.bg)) {
            Text(if (r == null) getString(R.string.check) else getString(R.string.check_again))
        }
    }
    val (system, own) = (r ?: return).sortedBy { it.level }.partition { it.system }
    val (needs, fine) = own.partition { it.level == Level.LOOK }
    if (needs.isNotEmpty()) { Section(getString(R.string.needs_a_look)); needs.forEach { FindingCard(it) } }
    if (fine.isNotEmpty()) { if (fine.any { it.apps.isNotEmpty() }) Section(getString(R.string.using_the_internet)); fine.forEach { FindingCard(it) } }
    if (system.isNotEmpty()) {
        var open by remember { mutableStateOf(false) }
        ListRow(getString(R.string.built_in_apps), getString(R.string.s_1x_found_usually_how_android, system.size)) { open = true }
        if (open) ListDialog(getString(R.string.built_in_apps), getString(R.string.apps_built_into_the_phone), { open = false }) {
            items(system) { FindingCard(it) }
        }
    }
}

@Composable
internal fun MainActivity.FindingCard(f: Finding) {
    var show by remember { mutableStateOf(false) }
    val grouped = f.apps.size > 3
    Panel(
        modifier = (if (grouped) Modifier.clickable { show = true } else Modifier).fillMaxWidth(),
        border = if (f.level == Level.LOOK && !f.system) BorderStroke(2.dp, warnTone().fg) else null,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (f.apps.size == 1) AppIcon(f.apps.first())
                if (f.level == Level.LOOK && !f.system) Icon(Icons.Outlined.Warning, null, Modifier.size(20.dp), tint = warnTone().fg)
                Text(f.title, style = MaterialTheme.typography.titleMedium)
            }
            Text(f.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (grouped) Text(getString(R.string.tap_to_see_all_1x, f.apps.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            // one app to act on: its own settings page is where its access is turned off
            else if (f.level == Level.LOOK && !f.system && f.apps.size == 1) OutlinedButton({
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", f.apps.first(), null)))
            }) { Text(getString(R.string.open_its_settings)) }
        }
    }
    if (show) AlertDialog(
        onDismissRequest = { show = false },
        confirmButton = { TextButton({ show = false }) { Text(getString(R.string.close)) } },
        title = { Text("${f.apps.size} apps") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // these share one Android user id, so the system cannot say which of them made the connection
                Text(getString(R.string.they_share_one_user_id), style = MaterialTheme.typography.bodyMedium)
                for (pkg in f.apps.sortedBy { label(it).lowercase() }) Column {
                    Text(label(pkg))
                    if (label(pkg) != pkg) Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}
