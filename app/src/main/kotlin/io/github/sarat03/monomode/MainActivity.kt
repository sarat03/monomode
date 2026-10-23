package io.github.sarat03.monomode

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlin.concurrent.thread
import monomode.engine.Entry
import monomode.engine.Finding
import monomode.engine.HealthException
import monomode.engine.Level
import monomode.engine.Method
import monomode.engine.Plan
import monomode.engine.Profile
import monomode.engine.Risk
import monomode.engine.parseList
import monomode.engine.Phone
import monomode.engine.PhoneState
import monomode.engine.label

/** The pages Home leads to; [EXTRA] in an intent opens one directly. */
object Page {
    const val EXTRA = "page"
    const val HOME = 0; const val CHECK = 1; const val CLEAN = 2; const val MODES = 3; const val SETTINGS = 4
}

class MainActivity : ComponentActivity() {
    private companion object {
        var paired by mutableStateOf(false)
        /** What the phone module last reported; the screens draw only from this. */
        var ps by mutableStateOf(PhoneState())
        var phone: Phone? = null
        var tab by mutableStateOf(0)
        val selected = mutableStateListOf<String>()
        var launcherPkgs by mutableStateOf<Set<String>>(emptySet())
        var query by mutableStateOf("")
        val restorePick = mutableStateListOf<String>()
        /** The mode whose page is open under Modes; null shows the list of modes. */
        var openMode by mutableStateOf<Profile?>(null)
        /** Unused apps' chip: 0 apps, 1 background services, 2 removed (restore). */
        var cleanFilter by mutableStateOf(0)
    }

    private val modes by lazy { Records.modes(applicationContext) }
    /** Process-wide, like the state above: an open session must survive the activity being recreated (battery saver turns on dark theme). */
    private val ph: Phone get() = phone ?: applicationContext.let { app ->
        Phone(
            open = { Session.open(app) }, debloat = Records.debloater(app), modes = Records.modes(app),
            wirelessOn = { Session.isWirelessDebuggingOn(app) }, saveCopy = { Records.copyToDownloads(app, it) }, onChange = { ps = it },
        ).also { phone = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Appearance.load(this)
        openPage(intent)
        setContent { AppTheme { Surface(Modifier.fillMaxSize()) { Screen() } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openPage(intent)
    }

    private fun openPage(i: Intent?) { i?.getIntExtra(Page.EXTRA, -1)?.takeIf { it >= 0 }?.let { tab = it } }

    override fun onResume() {
        super.onResume()
        paired = Pairing.isPaired(this)
        // paired and Wireless debugging on: connects and checks straight away, no Start button
        ph.refresh(paired)
    }

    override fun onDestroy() {
        // a session must never be left with Wireless debugging on; best effort when the app is closed
        if (isFinishing) ph.finishQuietly()
        super.onDestroy()
    }

    // own task, so opening MonoMode again brings back MonoMode, not Settings
    // the fragment_args_key extras make Settings scroll to and flash Wireless debugging (AOSP and realme UI)
    // Developer options still hidden: About phone instead, where Build number is tapped 7 times to show them;
    // a phone without either screen gets plain Settings
    private fun openDeveloperOptions() {
        val dev = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
            .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", "toggle_adb_wireless") })
        for (i in listOf(if (devOptionsOn()) dev else Intent(Settings.ACTION_DEVICE_INFO_SETTINGS), Intent(Settings.ACTION_SETTINGS)))
            if (runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }

    private fun devOptionsOn() = Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

    @Composable
    private fun Screen() {
        Scaffold(contentWindowInsets = WindowInsets(0)) { pad -> Column(Modifier.padding(pad).safeDrawingPadding().padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ps.busy?.let { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(it, style = MaterialTheme.typography.bodyMedium) } }
            ps.message?.let { Note(it) }
            when {
                tab == Page.SETTINGS -> Paged("Settings") { SettingsPage() }
                ps.connected -> SessionScreen()
                !paired -> PairScreen()
                else -> StartScreen()
            }
        } }
    }

    @Composable
    private fun PairScreen() = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppHeader()
        Text("Set up once", style = MaterialTheme.typography.headlineSmall)
        Text("Pair once over Wireless debugging. After that MonoMode connects by itself.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        val dev = devOptionsOn()
        // the first step only while Developer options are hidden; the button then opens About phone for it
        Steps(*listOfNotNull(
            if (dev) null else "In About phone, tap Build number 7 times.",
            "In Developer options, turn on Wireless debugging.",
            "Tap Pair device with pairing code.",
            "Type the code into the MonoMode notification.",
        ).toTypedArray())
        Button({
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            Pairing.prompt(this@MainActivity)
            openDeveloperOptions()
        }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (dev) "Start pairing" else "Open About phone") }
    }

    @Composable
    private fun StartScreen() = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppHeader()
        Hero(
            neutralTone(), if (ps.wirelessOn) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
            if (ps.wirelessOn) "Ready" else "Not connected",
            if (ps.wirelessOn) "Connecting to this phone." else "Turn on Wireless debugging, then come back.",
        )
        if (!ps.wirelessOn) Button(::openDeveloperOptions, Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(if (devOptionsOn()) "Open Developer options" else "Open About phone")
        }
        if (!ps.wirelessOn && !devOptionsOn()) Text("Developer options are hidden. In About phone, tap Build number 7 times.",
            Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Retry looks again for Wireless debugging, then connects and checks; shown per PhoneState.retryShown
        if (ps.retryShown) OutlinedButton(ph::connect, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = ps.busy == null) { Text("Retry") }
        ps.activeMode?.let { HomeRowStatic("${it.label} is on", "Connect to change it or turn it off.") }
    }

    /** App name on the left, settings gear on the right. */
    @Composable
    private fun AppHeader() = Row(verticalAlignment = Alignment.CenterVertically) {
        Text("MonoMode", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
        IconButton({ tab = Page.SETTINGS }) { Icon(Icons.Outlined.Settings, "Settings") }
    }

    @Composable
    private fun HomeRowStatic(title: String, state: String) = Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(state, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    private fun SessionScreen() = when (tab) {
        Page.CHECK -> Paged("Safety check") { CheckTab() }
        Page.CLEAN -> Paged("Unused apps", bottom = cleanupBar()) { CleanupTab() }
        Page.MODES -> openMode?.let { p -> Paged(p.label, back = { openMode = null }) { ModePage(p) } } ?: Paged("Modes") { ModesTab() }
        else -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { HomeTab() }
    }

    /** A page: back arrow and title on top, content scrolling, an optional action bar pinned at the bottom; Android's Back does [back]. */
    @Composable
    private fun Paged(title: String, back: () -> Unit = { tab = Page.HOME }, bottom: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) =
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BackHandler(onBack = back)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
            bottom?.let { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant); it() }
        }

    /** A small heading over a group of cards. */
    @Composable
    private fun Section(text: String) = Text(text, Modifier.padding(start = 8.dp, top = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

    /** The status card at the top of a page: icon, one big line, a sentence, then its actions. */
    @Composable
    private fun Hero(tone: Tone, icon: ImageVector, title: String, text: String, actions: @Composable () -> Unit = {}) =
        Card(colors = CardDefaults.cardColors(tone.bg, tone.fg), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null, Modifier.size(32.dp))
                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
                Text(text, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
            }
        }

    @Composable
    private fun neutralTone() = Tone(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)

    /** App settings, reachable from the gear on Home, with or without a session. */
    @Composable
    private fun SettingsPage() {
        Section("Connection")
        SettingRow("Pairing", if (paired) "This phone is paired with MonoMode." else "Not paired yet.") {
            if (paired) OutlinedButton({ Pairing.forget(applicationContext); paired = false; ph.disconnect(); tab = Page.HOME },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Forget and pair again") }
        }
        Section("Appearance")
        SettingRow("Theme", "Colours for MonoMode and its mode home screens.") {
            Column {
                for ((key, name) in Appearance.options) Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(Appearance.mode == key, role = Role.RadioButton) { Appearance.set(applicationContext, key) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) { RadioButton(Appearance.mode == key, null); Text(name) }
            }
        }
        Section("Backups")
        SettingRow("Restore lists", "A copy of what was removed is saved in Downloads/MonoMode.")
        Section("About")
        SettingRow("MonoMode", "MonoMode ${packageManager.getPackageInfo(packageName, 0).versionName}. Open source, MIT licence.") {
            OutlinedButton({ startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/sarat03/monomode"))) }) { Text("Source code") }
        }
    }

    @Composable
    private fun SettingRow(title: String, text: String, action: (@Composable () -> Unit)? = null) = Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke()
        }
    }

    /** One answer first, then a row per area with its state; the way to finish stays in sight. */
    @Composable
    private fun HomeTab() {
        val r = ps.results
        val look = r?.count { it.level == Level.LOOK && !it.system } ?: 0
        val tone = when { r == null -> Tone(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant); look == 0 -> safeTone(); else -> warnTone() }
        val (bg, fg) = tone.bg to tone.fg
        AppHeader()
        // the one answer, big, with its one action (redesign hero card)
        Card(colors = CardDefaults.cardColors(bg, fg), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(if (r != null && look == 0) Icons.Outlined.CheckCircle else Icons.Outlined.Info, null, Modifier.size(32.dp))
                Text(
                    when { r == null -> "Not checked yet"; look == 0 -> "Looks safe"; else -> "$look thing${if (look == 1) "" else "s"} to look at" },
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(if (r == null) "See what can reach this phone." else "Checked just now. Nothing was changed.", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button((ph::check), enabled = ps.busy == null,
                        colors = ButtonDefaults.buttonColors(fg, bg)) { Text(if (r == null) "Check" else "Check again") }
                    if (r != null) TextButton({ tab = Page.CHECK }) { Text("See details", color = fg) }
                }
            }
        }
        HomeRow(AppIcons.Shield, "Safety check", if (r == null) "Not run yet" else if (look == 0) "Nothing to look at" else "$look to look at") { tab = Page.CHECK }
        HomeRow(AppIcons.Broom, "Unused apps", if (ps.removed.isEmpty()) "Nothing removed" else "${ps.removed.size} removed or disabled") { tab = Page.CLEAN }
        HomeRow(AppIcons.Toggle, "Modes", ps.activeMode?.let { "${it.label} is on" } ?: "None on") { tab = Page.MODES }
        val w = warnTone()
        Card(colors = CardDefaults.cardColors(w.bg, w.fg), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Warning, null)
                Text("Wireless debugging is on", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(ph::finish, enabled = ps.busy == null) { Text("Turn off", color = w.fg) }
            }
        }
    }

    @Composable
    private fun HomeRow(icon: ImageVector, title: String, state: String, open: () -> Unit) = Panel(Modifier.fillMaxWidth().clickable(onClick = open)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(state, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    private fun CheckTab() {
        val r = ps.results
        val look = r?.count { it.level == Level.LOOK && !it.system } ?: 0
        val tone = when { r == null -> neutralTone(); look == 0 -> safeTone(); else -> warnTone() }
        Hero(
            tone, if (r != null && look == 0) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
            when { r == null -> "Not checked yet"; look == 0 -> "Nothing to look at"; else -> "$look thing${if (look == 1) "" else "s"} to look at" },
            if (r == null) "Read-only. Nothing is changed." else if (look == 0) "Checked just now. Nothing was changed." else "Everything else looks normal.",
        ) {
            Button((ph::check), enabled = ps.busy == null, colors = ButtonDefaults.buttonColors(tone.fg, tone.bg)) {
                Text(if (r == null) "Check" else "Check again")
            }
        }
        val (system, own) = (r ?: return).sortedBy { it.level }.partition { it.system }
        val (needs, fine) = own.partition { it.level == Level.LOOK }
        if (needs.isNotEmpty()) { Section("Needs a look"); needs.forEach { FindingCard(it) } }
        if (fine.isNotEmpty()) { Section("For your information"); fine.forEach { FindingCard(it) } }
        if (system.isNotEmpty()) {
            var open by remember { mutableStateOf(false) }
            ListRow("Built-in apps", "${system.size} found, usually how Android works") { open = true }
            if (open) ListDialog("Built-in apps", "Apps built into the phone. Their access is usually how Android works.", { open = false }) {
                items(system) { FindingCard(it) }
            }
        }
    }

    @Composable
    private fun FindingCard(f: Finding) {
        var show by remember { mutableStateOf(false) }
        val grouped = f.apps.size > 3
        Panel(
            modifier = (if (grouped) Modifier.clickable { show = true } else Modifier).fillMaxWidth(),
            border = if (f.level == Level.LOOK && !f.system) BorderStroke(2.dp, warnTone().fg) else null,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (f.level == Level.LOOK && !f.system) Icon(Icons.Outlined.Warning, null, Modifier.size(20.dp), tint = warnTone().fg)
                    Text(f.title, style = MaterialTheme.typography.titleMedium)
                }
                Text(f.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (grouped) Text("Tap to see all ${f.apps.size} apps", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                // one app to act on: its own settings page is where its access is turned off
                else if (f.level == Level.LOOK && !f.system && f.apps.size == 1) OutlinedButton({
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", f.apps.first(), null)))
                }) { Text("Open its settings") }
            }
        }
        if (show) AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = { TextButton({ show = false }) { Text("Close") } },
            title = { Text("${f.apps.size} apps") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // these share one Android user id, so the system cannot say which of them made the connection
                    Text("They share one user id, so Android cannot tell which of them is doing it.", style = MaterialTheme.typography.bodyMedium)
                    for (pkg in f.apps.sortedBy { label(it).lowercase() }) Column {
                        Text(label(pkg))
                        if (label(pkg) != pkg) Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
        )
    }

    /** Uninstall and Disable stay in sight at the bottom while the list scrolls; Restore has its own there. */
    private fun cleanupBar(): (@Composable () -> Unit)? = when {
        cleanFilter == 2 -> if (ps.removed.isEmpty()) null else {
            { Button({ ph.restore(restorePick.toSet()); restorePick.clear() }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = ps.busy == null && restorePick.isNotEmpty()) { Text("Restore ${restorePick.size}") } }
        }
        ps.scan == null -> null
        else -> {
            {
                // Uninstall only for built-in apps: their apk stays on the phone so Restore works; a Play Store app's is deleted
                val playApps = selected.filterNot { it in ps.scan!!.system }
                if (playApps.isNotEmpty()) Text("${playApps.joinToString { label(it) }} came from the Play Store. MonoMode can only disable these; to uninstall one, use the Play Store, where you can reinstall it later.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton({ ph.remove(selected.toList(), Method.DISABLE); selected.clear() }, Modifier.weight(1f).heightIn(min = 48.dp), enabled = ps.busy == null && selected.isNotEmpty()) { Text("Disable ${selected.size}") }
                    Button({ ph.remove(selected.toList(), Method.REMOVE); selected.clear() }, Modifier.weight(1f).heightIn(min = 48.dp), enabled = ps.busy == null && selected.isNotEmpty() && playApps.isEmpty()) { Text("Uninstall ${selected.size}") }
                }
            }
        }
    }

    @Composable
    private fun CleanupTab() {
        Text("Remove apps you don't use. You can bring them back.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(cleanFilter == 0, { cleanFilter = 0 }, { Text("Apps") })
            FilterChip(cleanFilter == 1, { cleanFilter = 1 }, { Text("Background") }, enabled = ps.scan != null)
            FilterChip(cleanFilter == 2, { cleanFilter = 2 }, { Text("Removed · ${ps.removed.size}") })
        }
        if (cleanFilter == 2) return RestoreList()
        val sc = ps.scan
        // only safe apps from the curated list start ticked
        LaunchedEffect(sc) { sc?.let { selected.clear(); selected += ps.entries.filter { e -> e.risk == Risk.SAFE && e.pkg in it.plan.todo }.map { e -> e.pkg } } }
        if (sc == null) {
            Button({
                launcherPkgs = launcherApps(applicationContext).map { it.first }.toSet()
                // the phone's own app list, not a list per brand; generic.txt only adds notes for apps we know
                ph.scan(parseList(assets.open("generic.txt").bufferedReader().readText()), packageName)
            }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = ps.busy == null) { Text("Show apps") }
        } else {
            val entries = ps.entries
            val p = sc.plan
            val others = remember(sc) { sc.others.map { it to label(it) }.sortedBy { it.second.lowercase() } }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search apps") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true, shape = RoundedCornerShape(28.dp))
            val withIcon = launcherPkgs
            if (cleanFilter == 1) {
                Text("Many are needed by the phone. Leave the ones you don't know.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PickGroup(others.filterNot { it.first in withIcon })
            } else {
                PickGroup(entries.filter { it.pkg in p.todo }.map { e -> e.pkg to label(e.pkg) + if (e.risk != Risk.SAFE) "  (${e.risk.name.lowercase()}: check you do not use it)" else "" } +
                    others.filter { it.first in withIcon })
                if (p.already.isNotEmpty()) Text("Already off: ${p.already.size}", Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Core apps are hidden. If a batch breaks Home, calls or the keyboard, it is undone.",
                Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    /** Apps to tick, in one card, filtered by the search. */
    @Composable
    private fun PickGroup(apps: List<Pair<String, String>>) {
        val shown = apps.filter { it.matches(query) }
        if (shown.isEmpty()) return Text("No apps match.", Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Panel(Modifier.fillMaxWidth()) {
            shown.forEachIndexed { i, (pkg, name) ->
                PickRowShown(pkg, name)
                if (i < shown.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    /** Everything MonoMode uninstalled or disabled, grouped by how, to bring back. */
    @Composable
    private fun RestoreList() {
        val done = ps.removed
        if (done.isEmpty()) return Text("Nothing removed yet.", Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Nothing was deleted.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton({ restorePick.clear(); restorePick += done.keys }, enabled = ps.busy == null) { Text("Select all") }
        }
        for ((method, title) in listOf(Method.REMOVE to "Uninstalled", Method.DISABLE to "Disabled")) {
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

    @Composable
    private fun PickRowShown(pkg: String, text: String) = Row(
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

    /** App name, or the package name for apps Android does not let us see. Cached: lists redraw on every search keystroke. */
    private val labels = HashMap<String, String>()
    private fun label(pkg: String) = labels.getOrPut(pkg) {
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }

    private val Profile.what get() = when (this) {
        Profile.KIOSK -> "Only the apps you choose."
        Profile.E_READER -> "A calm, grey screen for reading."
    }
    private val Profile.icon get() = when (this) { Profile.KIOSK -> AppIcons.Grid; Profile.E_READER -> AppIcons.Book }

    /** One card per mode with its state; a card opens the mode's own page. */
    @Composable
    private fun ModesTab() {
        val on = ps.activeMode
        Text("One mode at a time. Turning it off restores your settings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (p in Profile.entries) Panel(Modifier.fillMaxWidth().clickable { openMode = p }) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(48.dp).clip(MaterialTheme.shapes.medium).background(if (p == on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(p.icon, null, tint = if (p == on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f)) {
                    Text(p.label, style = MaterialTheme.typography.titleLarge)
                    Text(p.what, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (on != null && on != p) Text("Turn off ${on.label} first. One mode at a time.", style = MaterialTheme.typography.bodyMedium, color = warnTone().fg)
                }
                if (p == on) Text("On", Modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primary).padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    /** A mode's page: its state and switch on top, then what it changes, its apps and, for Kiosk, pinning. */
    @Composable
    private fun ModePage(profile: Profile) {
        val on = ps.activeMode
        val name = profile.label
        val off = remember(profile) { mutableStateListOf(*modes.off(profile).toTypedArray()) }
        Hero(
            if (on == profile) safeTone() else neutralTone(), profile.icon,
            if (on == profile) "$name is on" else "$name is off",
            if (on == profile) "Your settings come back when you turn it off."
            else if (on != null) "Turn off ${on.label} first. One mode at a time." else profile.what,
        ) {
            val t = if (on == profile) safeTone() else neutralTone()
            if (on == profile) Button({
                ph.turnOff()
            }, enabled = ps.busy == null, colors = ButtonDefaults.buttonColors(t.fg, t.bg)) { Text("Turn off") }
            else Button({
                ph.turnOn(profile)
            }, enabled = ps.busy == null && on == null) { Text("Turn on") }
        }
        Section("What changes")
        Panel(Modifier.fillMaxWidth()) {
            profile.options.keys.forEachIndexed { i, option ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(option !in off, enabled = on == null, role = Role.Switch) { keep ->
                    if (keep) off -= option else off += option
                    modes.setOff(profile, off.toSet())
                }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(option, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    // drawn enabled even while locked: a disabled "on" switch looks off; the row itself ignores taps then
                    Switch(option !in off, null)
                }
                if (i < profile.options.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        if (on == profile) Text("Turn the mode off to change these.", Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if ("Own home screen" !in off) { Section("Apps"); HomePicker(profile) }
        if (profile == Profile.KIOSK) {
            Section("Pin one app")
            if (realmeGestures()) Text(
                // checked on the Realme XT: a swipe never reaches a third-party home screen
                "On this phone a swipe leaves Kiosk. Pin an app to stay in one app.",
                Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PinPicker(on == profile, askPin = "Ask for PIN to unpin" !in off)
        }
    }

    /** Which apps the mode's home screen shows; editable any time, the home screen rereads it on return. */
    @Composable
    private fun HomePicker(profile: Profile) {
        var open by remember { mutableStateOf(false) }
        val picked = remember { mutableStateListOf(*modes.apps(profile).toTypedArray()) }
        val all = remember { launcherApps(this) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (profile == Profile.E_READER) {
                var reader by remember { mutableStateOf(modes.reader(all.map { it.first })) }
                var choose by remember { mutableStateOf(false) }
                ListRow("Reading app", all.firstOrNull { it.first == reader }?.second ?: "None found: choose one") { choose = true }
                if (choose) ListDialog("Reading app", "Opens from Continue reading.", { choose = false }, search = true) { q ->
                    items(all.filter { it.matches(q) }, key = { it.first }) { (pkg, label) ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(pkg == reader, role = Role.RadioButton) { reader = pkg; modes.setReader(pkg); choose = false }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(pkg == reader, null); Spacer(Modifier.width(12.dp)); AppIcon(pkg); Text(label, Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
            val names = all.filter { it.first in picked }.joinToString { it.second }
            ListRow(if (profile == Profile.E_READER) "Other apps" else "Apps on the home screen", names.ifEmpty { "None chosen" }) { open = true }
            if (open) ListDialog(if (profile == Profile.E_READER) "Other apps" else "Apps on the home screen", null, { open = false }, search = true) { q ->
                items(all.filter { it.matches(q) }, key = { it.first }) { (pkg, label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(pkg in picked, role = Role.Checkbox) { if (it) picked += pkg else picked -= pkg; modes.setApps(profile, picked.toSet()) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(pkg in picked, null); Spacer(Modifier.width(12.dp)); AppIcon(pkg); Text(label, Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
    }

    /**
     * Gesture navigation per Android's navigation_mode (2), or per realme/Oppo's own switch
     * (hide_navigationbar_enable > 0), which they use instead: there navigation_mode stays 0.
     */
    /** realme/Oppo gesture navigation: their launcher handles the swipes, whatever home screen is set. */
    private fun realmeGestures() = Settings.Secure.getInt(contentResolver, "hide_navigationbar_enable", 0) > 0

    private fun usesGestures() = Settings.Secure.getInt(contentResolver, "navigation_mode", 0) == 2 ||
        Settings.Secure.getInt(contentResolver, "hide_navigationbar_enable", 0) > 0

    private fun unpinHow() = if (usesGestures()) "swipe up from the bottom of the screen and hold" else "touch and hold Back and Recents together"

    /** Pinning hides MonoMode, so it is the last step: pin, then switch Wireless debugging off. */
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    private fun PinPicker(kioskOn: Boolean, askPin: Boolean) {
        var apps by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
        Column {
            // always shown, so it is clear pinning exists; it needs Kiosk on, which enables screen pinning
            ListRow("Pin an app", if (kioskOn) "One app fills the screen until you unpin it" else "Turn Kiosk on first, then choose the app") {
                if (kioskOn) run {
                    // pin() starts a component; the launch intent names the app's own start screen
                    apps = launcherApps(applicationContext).mapNotNull { (pkg, label) ->
                        packageManager.getLaunchIntentForPackage(pkg)?.component?.flattenToShortString()?.let { it to label }
                    }
                }
            }
            // what pinning does, up front: people were surprised by the PIN prompt and by debugging being off afterwards
            Text(
                "Pinning turns off Wireless debugging. To unpin, ${unpinHow()}.",
                Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        apps?.let { list ->
            // Material bottom sheet: the list rises from where the thumb is, with how to unpin above it
            var q by remember { mutableStateOf("") }
            ModalBottomSheet({ apps = null }) { Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Pin an app", Modifier.padding(horizontal = 8.dp).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                Text("The app fills the screen and Wireless debugging switches off.", Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("To unpin: ${unpinHow()}${if (askPin) ". The phone then asks for your PIN." else "."}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RoundedCornerShape(28.dp))
                LazyColumn(Modifier.heightIn(max = 480.dp)) { items(list.filter { it.matches(q) }) { (component, label) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.medium).clickable(enabled = ps.busy == null) {
                            apps = null
                            ph.pin(component, label)
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Spacer(Modifier.width(8.dp)); AppIcon(component.substringBefore("/")); Text(label, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge) }
                } }
            } }
        }
    }

    @Composable
    private fun Steps(vararg steps: String) = Panel(Modifier.fillMaxWidth()) {
        steps.forEachIndexed { i, t ->
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                }
                Text(t, style = MaterialTheme.typography.bodyLarge)
            }
            if (i < steps.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    @Composable
    private fun Note(text: String) = errorTone().let { t ->
        Card(colors = CardDefaults.cardColors(t.bg, t.fg), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Warning, null)
                Text(text, Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp))
                IconButton(ph::dismiss) { Icon(Icons.Outlined.Close, "Close") }
            }
        }
    }

    /** A row that opens a pop-up list: the title, what is chosen, and a chevron. */
    @Composable
    private fun ListRow(title: String, value: String, onClick: () -> Unit) = Panel(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    /** Android's list pop-up: a title, a scrolling list, and Done. */
    @Composable
    private fun ListDialog(title: String, note: String? = null, onClose: () -> Unit, search: Boolean = false, items: LazyListScope.(query: String) -> Unit) {
        var q by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onClose,
            confirmButton = { TextButton(onClose) { Text("Done") } },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    if (search) OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), label = { Text("Search") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true)
                    LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { items(q) }
                }
            },
        )
    }

    private fun Pair<String, String>.matches(q: String) = q.isBlank() || second.contains(q, true) || first.contains(q, true)

    /** The app's own icon, small, for pick lists; cached like labels. */
    private val smallIcons = HashMap<String, androidx.compose.ui.graphics.ImageBitmap>()
    @Composable
    private fun AppIcon(pkg: String) {
        val px = (32 * resources.displayMetrics.density).toInt()
        val icon = smallIcons.getOrPut(pkg) { runCatching { packageManager.getApplicationIcon(pkg) }.getOrElse { packageManager.defaultActivityIcon }.toBitmap(px) }
        androidx.compose.foundation.Image(icon, null, Modifier.size(32.dp))
    }
}
