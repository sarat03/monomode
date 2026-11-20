package io.github.sarat03.monomode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.text.DateFormat
import java.util.Date
import monomode.engine.Kind
import monomode.engine.Profile
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.background
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import monomode.engine.Method
import monomode.engine.Risk
import monomode.engine.parseList
import monomode.engine.label
import io.github.sarat03.monomode.MainActivity.Companion.ps
import io.github.sarat03.monomode.MainActivity.Companion.phone
import io.github.sarat03.monomode.MainActivity.Companion.selected
import io.github.sarat03.monomode.MainActivity.Companion.launcherPkgs
import io.github.sarat03.monomode.MainActivity.Companion.query
import io.github.sarat03.monomode.MainActivity.Companion.restorePick
import io.github.sarat03.monomode.MainActivity.Companion.cleanFilter

/** Uninstall and Disable stay in sight at the bottom while the list scrolls; Restore has its own there. */
internal fun MainActivity.cleanupBar(): (@Composable () -> Unit)? = when {
    cleanFilter == 3 -> if (ps.removed.isEmpty() && ps.activeMode == null) null else {
        {
            var ask by remember { mutableStateOf(false) }
            Button({ ask = true }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = ps.busy == null) { Text(getString(R.string.revert_all)) }
            if (ask) AlertDialog(onDismissRequest = { ask = false },
                title = { Text(getString(R.string.revert_all_title)) },
                text = { Text(getString(R.string.revert_all_body, ph.history().size)) },
                confirmButton = { TextButton({ ask = false; ph.revertAll() }, Modifier.heightIn(min = 48.dp)) { Text(getString(R.string.revert_all)) } },
                dismissButton = { TextButton({ ask = false }, Modifier.heightIn(min = 48.dp)) { Text(getString(R.string.cancel)) } })
        }
    }
    cleanFilter == 2 -> if (ps.removed.isEmpty()) null else {
        { Button({ ph.restore(restorePick.toSet()); restorePick.clear() }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = ps.busy == null && restorePick.isNotEmpty()) { Text(getString(R.string.restore_1x, restorePick.size)) } }
    }
    ps.scan == null -> null
    else -> {
        {
            // Uninstall only for built-in apps: their apk stays on the phone so Restore works; a Play Store app's is deleted
            val playApps = selected.filterNot { it in ps.scan!!.system }
            if (playApps.isNotEmpty()) Text(getString(R.string.play_store_apps_disable_only, playApps.joinToString { label(it) }), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${selected.size} selected", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton({ ph.remove(selected.toList(), Method.DISABLE); selected.clear() }, Modifier.heightIn(min = 48.dp), enabled = ps.busy == null && selected.isNotEmpty()) { Text(getString(R.string.disable)) }
                Button({ ph.remove(selected.toList(), Method.REMOVE); selected.clear() }, Modifier.heightIn(min = 48.dp), enabled = ps.busy == null && selected.isNotEmpty() && playApps.isEmpty()) { Text(getString(R.string.uninstall)) }
            }
        }
    }
}

@Composable
internal fun MainActivity.CleanupTab() {
    Text(getString(R.string.remove_bloatware_and_system_apps), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(cleanFilter == 0, { cleanFilter = 0 }, { Text(getString(R.string.apps)) })
        FilterChip(cleanFilter == 1, { cleanFilter = 1 }, { Text(getString(R.string.background)) }, enabled = ps.scan != null)
        FilterChip(cleanFilter == 2, { cleanFilter = 2 }, { Text(getString(R.string.removed_1x, ps.removed.size)) })
        FilterChip(cleanFilter == 3, { cleanFilter = 3 }, { Text(getString(R.string.history)) })
    }
    if (cleanFilter == 2) return RestoreList()
    if (cleanFilter == 3) return HistoryList()
    val sc = ps.scan
    // only safe apps from the curated list start ticked
    LaunchedEffect(sc) { sc?.let { selected.clear(); selected += ps.entries.filter { e -> e.risk == Risk.SAFE && e.pkg in it.plan.todo }.map { e -> e.pkg } } }
    if (sc == null) {
        Button({
            launcherPkgs = launcherApps(applicationContext).map { it.first }.toSet()
            // the phone's own app list, not a list per brand; generic.txt only adds notes for apps we know
            ph.scan(parseList(assets.open("generic.txt").bufferedReader().readText()), packageName)
        }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = ps.busy == null) { Text(getString(R.string.show_apps)) }
    } else {
        val entries = ps.entries
        val p = sc.plan
        val others = remember(sc) { sc.others.map { it to label(it) }.sortedBy { it.second.lowercase() } }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text(getString(R.string.search_apps)) }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true, shape = MaterialTheme.shapes.large)
        val withIcon = launcherPkgs
        if (cleanFilter == 1) {
            Text(getString(R.string.many_are_needed_by_the),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PickGroup(others.filterNot { it.first in withIcon })
        } else {
            PickGroup(entries.filter { it.pkg in p.todo }.map { e -> e.pkg to label(e.pkg) + if (e.risk != Risk.SAFE) getString(R.string.risk_check_you_use_it, e.risk.name.lowercase()) else "" } +
                others.filter { it.first in withIcon })
            if (p.already.isNotEmpty()) Text(getString(R.string.already_off_1x, p.already.size), Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(getString(R.string.core_apps_are_hidden_if),
            Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Apps to tick, in one card, filtered by the search. */
@Composable
internal fun MainActivity.PickGroup(apps: List<Pair<String, String>>) {
    val shown = apps.filter { it.matches(query) }
    if (shown.isEmpty()) return Text(getString(R.string.no_apps_match), Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Panel(Modifier.fillMaxWidth()) {
        shown.forEachIndexed { i, (pkg, name) ->
            PickRowShown(pkg, name)
            if (i < shown.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Everything MonoMode uninstalled or disabled, grouped by how, to bring back. */
@Composable
internal fun MainActivity.RestoreList() {
    val done = ps.removed
    if (done.isEmpty()) return Text(getString(R.string.nothing_removed_yet), Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(getString(R.string.nothing_was_deleted), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton({ restorePick.clear(); restorePick += done.keys }, enabled = ps.busy == null) { Text(getString(R.string.select_all)) }
    }
    for ((method, title) in listOf(Method.REMOVE to getString(R.string.uninstalled), Method.DISABLE to getString(R.string.disabled))) {
        val group = done.filterValues { it == method }.keys.sortedBy { label(it).lowercase() }
        if (group.isEmpty()) continue
        Section("$title · ${group.size}")
        Panel(Modifier.fillMaxWidth()) {
            group.forEachIndexed { i, pkg ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(pkg in restorePick, role = Role.Checkbox) { if (it) restorePick += pkg else restorePick -= pkg }
                    .padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AppIcon(pkg)
                    Column(Modifier.weight(1f)) {
                        Text(label(pkg))
                        if (label(pkg) != pkg) Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Checkbox(pkg in restorePick, null)
                }
                if (i < group.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** Every change MonoMode holds, apps and the mode that is on, newest first, each with its own Restore. */
@Composable
internal fun MainActivity.HistoryList() {
    val items = remember(ps.removed, ps.activeMode) { ph.history() }
    if (items.isEmpty()) return Text(getString(R.string.history_empty), Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(getString(R.string.history_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Panel(Modifier.fillMaxWidth()) {
        items.forEachIndexed { i, item ->
            val mode = if (item.kind == Kind.MODE) Profile.valueOf(item.id) else null
            val name = mode?.label ?: label(item.id)
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (mode == null) AppIcon(item.id)
                Column(Modifier.weight(1f)) {
                    Text(if (mode != null) getString(R.string.history_mode, name, item.settings) else name)
                    val kind = when (item.kind) { Kind.REMOVED -> getString(R.string.uninstalled); Kind.DISABLED -> getString(R.string.disabled); Kind.MODE -> null }
                    val since = item.at?.let { getString(R.string.history_since, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))) }
                    Text(listOfNotNull(kind, since).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val desc = getString(R.string.history_restore_1x, name)
                TextButton({ if (mode != null) ph.turnOff() else ph.restore(setOf(item.id)) }, Modifier.heightIn(min = 48.dp).semantics { contentDescription = desc }, enabled = ps.busy == null) { Text(getString(R.string.history_restore)) }
            }
            if (i < items.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
internal fun MainActivity.PickRowShown(pkg: String, text: String) = Row(
    Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(pkg in selected, role = Role.Checkbox) { if (it) selected += pkg else selected -= pkg }.padding(horizontal = 16.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
) {
    AppIcon(pkg)
    Column(Modifier.weight(1f)) {
        Text(text)
        if (text != pkg) Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Checkbox(pkg in selected, null) // the whole row toggles it, and reads as one checkbox to TalkBack
}
