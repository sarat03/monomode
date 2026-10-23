package io.github.sarat03.phonewarden

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
import androidx.compose.material3.OutlinedCard
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
import phonewarden.engine.Entry
import phonewarden.engine.Finding
import phonewarden.engine.HealthException
import phonewarden.engine.Level
import phonewarden.engine.Method
import phonewarden.engine.Plan
import phonewarden.engine.Profile
import phonewarden.engine.Risk
import phonewarden.engine.parseList
import phonewarden.engine.pin

/** The pages Home leads to; [EXTRA] in an intent opens one directly. */
object Page {
    const val EXTRA = "page"
    const val HOME = 0; const val CHECK = 1; const val CLEAN = 2; const val MODES = 3; const val SETTINGS = 4
}

class MainActivity : ComponentActivity() {
    // Process-wide, not per activity: Android recreates the activity on config changes (battery saver
    // turns on dark theme), and an open session must survive that or Wireless debugging is left on.
    private companion object {
        var paired by mutableStateOf(false)
        var wirelessOn by mutableStateOf(false)
        var session by mutableStateOf<Session?>(null)
        var busy by mutableStateOf<String?>(null)
        /** Something went wrong or needs the user: stays on screen until closed. */
        var message by mutableStateOf<String?>(null)
        /** A done action ("Kiosk mode is on"): shown briefly at the bottom. */
        var toast by mutableStateOf<String?>(null)
        var tab by mutableStateOf(0)
        var results by mutableStateOf<List<Finding>?>(null)
        var cleanup by mutableStateOf<Pair<List<Entry>, Plan>?>(null)
        val selected = mutableStateListOf<String>()
        /** The mode that is on, mirrored from its record file so the screen updates. */
        var activeMode by mutableStateOf<Profile?>(null)
        var others by mutableStateOf<List<Pair<String, String>>>(emptyList())
        var launcherPkgs by mutableStateOf<Set<String>>(emptySet())
        var systemPkgs by mutableStateOf<Set<String>>(emptySet())
        var query by mutableStateOf("")
        val restorePick = mutableStateListOf<String>()
        /** Mirrors the debloat record so the restore list redraws when it changes. */
        var restoreItems by mutableStateOf<Map<String, Method>>(emptyMap())
    }

    private val modes by lazy { Records.modes(applicationContext) }
    private val debloat by lazy { Records.debloater(applicationContext) }

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
        wirelessOn = Session.isWirelessDebuggingOn(this)
        activeMode = modes.active
        restoreItems = debloat.removed
        // Android switched Wireless debugging off while we were away (lock, Wi-Fi change): that session is dead
        // closing sends TLS close_notify, which is network: never on the main thread
        if (!wirelessOn && busy == null) session?.let { s -> thread { runCatching { s.close() } }; session = null; results = null; cleanup = null }
        // paired and Wireless debugging on: connect and check straight away, no Start button
        if (paired && wirelessOn && session == null && busy == null) run("Connecting and checking this phone…") {
            val s = Session.open(applicationContext)
            session = s
            results = s.check(applicationContext)
        }
    }

    override fun onDestroy() {
        // a session must never be left with Wireless debugging on; best effort when the app is closed
        session?.let { s -> if (isFinishing) thread { runCatching { s.finish(applicationContext) } } }
        super.onDestroy()
    }

    /** Runs [work] off the main thread with a progress label; errors become the message. */
    private fun run(label: String, work: () -> Unit) {
        busy = label; message = null
        thread {
            try { work() } catch (e: java.io.IOException) {
                if (e !is java.net.SocketException && e !is java.io.EOFException && e !is javax.net.ssl.SSLException) { message = explain(e); return@thread }
                // adbd dropped us, e.g. Wireless debugging went off when Wi-Fi changed or the phone slept
                session?.close(); session = null
                wirelessOn = Session.isWirelessDebuggingOn(applicationContext)
                message = "The connection to this phone was lost. If Wireless debugging went off, turn it on again, then tap Check this phone."
            } catch (e: Exception) { message = explain(e) } finally { busy = null }
        }
    }

    private fun explain(e: Exception): String {
        val m = e.message ?: e.toString()
        // realme, Oppo (ColorOS) and Xiaomi (MIUI) refuse settings writes from adb behind an extra developer switch
        return if ("WRITE_SECURE_SETTINGS" in m)
            "This phone blocked the change, so nothing was changed. In Developer options, turn on \"Disable permission monitoring\" " +
                "(realme, Oppo) or \"USB debugging (Security settings)\" (Xiaomi), then try again."
        else m
    }

    // own task, so opening PhoneWarden again brings back PhoneWarden, not Settings
    // the fragment_args_key extras make Settings scroll to and flash Wireless debugging (AOSP and realme UI)
    private fun openDeveloperOptions() = startActivity(
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
            .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", "toggle_adb_wireless") })
    )

    @Composable
    private fun Screen() {
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(toast) { toast?.let { snack.showSnackbar(it); toast = null } }
        Scaffold(snackbarHost = { SnackbarHost(snack) }, contentWindowInsets = WindowInsets(0)) { pad -> Column(Modifier.padding(pad).safeDrawingPadding().padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            busy?.let { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(it, style = MaterialTheme.typography.bodyMedium) } }
            message?.let { Note(it) }
            val s = session
            when {
                tab == Page.SETTINGS -> Paged("Settings") { SettingsPage() }
                s != null -> SessionScreen(s)
                !paired -> PairScreen()
                else -> StartScreen()
            }
        } }
    }

    @Composable
    private fun PairScreen() = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppHeader()
        Text("Set up once", style = MaterialTheme.typography.headlineSmall)
        Text("PhoneWarden talks to this phone over Wireless debugging. Pair it once; after that it connects by itself.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Steps(
            "Turn on Developer options if you have not (Settings > About phone > tap Build number 7 times).",
            "In Developer options, turn on Wireless debugging and open it.",
            "Tap \"Pair device with pairing code\".",
            "Pull down the PhoneWarden notification and type the 6-digit code into it.",
        )
        Button({
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            Pairing.prompt(this@MainActivity)
            openDeveloperOptions()
        }) { Text("Start pairing") }
    }

    @Composable
    private fun StartScreen() = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppHeader()
        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (wirelessOn) "Ready to check" else "Not connected", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (wirelessOn) "PhoneWarden connects to this phone only, and switches Wireless debugging off when you finish."
                    else "Turn on Wireless debugging, then come back. PhoneWarden checks the phone straight away and switches it off again when you finish.",
                )
            }
        }
        if (!wirelessOn) Button(::openDeveloperOptions, Modifier.fillMaxWidth()) { Text("Open Developer options") }
        else Button({ run("Connecting and checking this phone…") { val s = Session.open(applicationContext); session = s; results = s.check(applicationContext) } }, Modifier.fillMaxWidth(), enabled = busy == null) { Text("Check this phone") }
        activeMode?.let { HomeRowStatic("Modes", "${it.label} is on. Connect to change or turn it off.") }
    }

    /** App name on the left, settings gear on the right. */
    @Composable
    private fun AppHeader() = Row(verticalAlignment = Alignment.CenterVertically) {
        Text("PhoneWarden", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        IconButton({ tab = Page.SETTINGS }) { Icon(Icons.Outlined.Settings, "Settings") }
    }

    @Composable
    private fun HomeRowStatic(title: String, state: String) = OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(state, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    private fun SessionScreen(s: Session) = when (tab) {
        Page.CHECK -> Paged("Check") { CheckTab(s) }
        Page.CLEAN -> Paged("Clean up") { CleanupTab(s) }
        Page.MODES -> Paged("Modes") { ModesTab(s) }
        else -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { HomeTab(s) }
    }

    /** A page under Home: back arrow and title on top; Android's Back also returns to Home. */
    @Composable
    private fun Paged(title: String, content: @Composable () -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackHandler { tab = Page.HOME }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ tab = Page.HOME }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to home") }
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        }
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }

    /** App settings, reachable from the gear on Home, with or without a session. */
    @Composable
    private fun SettingsPage() {
        SettingRow("Pairing", if (paired) "This phone is paired with PhoneWarden." else "Not paired yet.") {
            if (paired) OutlinedButton({ Pairing.forget(applicationContext); paired = false; session?.let { s -> thread { runCatching { s.close() } } }; session = null; tab = Page.HOME; toast = "Pairing forgotten. Pair again to continue." }) { Text("Forget and pair again") }
        }
        SettingRow("Restore lists", "Each clean up saves a copy of its restore list in Downloads/PhoneWarden, so apps can be brought back even after reinstalling PhoneWarden.")
        SettingRow("Appearance", "Colours for PhoneWarden and its mode home screens.") {
            Column {
                for ((key, name) in Appearance.options) Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(Appearance.mode == key, role = Role.RadioButton) { Appearance.set(applicationContext, key) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) { RadioButton(Appearance.mode == key, null); Text(name) }
            }
        }
        SettingRow("About", "PhoneWarden ${packageManager.getPackageInfo(packageName, 0).versionName}. Open source, MIT licence.") {
            OutlinedButton({ startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/sarat03/phonewarden"))) }) { Text("Source code") }
        }
    }

    @Composable
    private fun SettingRow(title: String, text: String, action: (@Composable () -> Unit)? = null) = OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke()
        }
    }

    private fun finish(s: Session) = run("Switching off Wireless debugging…") {
        val off = s.finish(applicationContext)
        session = null; results = null; cleanup = null
        wirelessOn = Session.isWirelessDebuggingOn(applicationContext)
        if (off) toast = "Wireless debugging is off" else message = "Wireless debugging may still be on. Turn it off in Developer options."
    }

    /** One answer first, then a row per area with its state; the way to finish stays in sight. */
    @Composable
    private fun HomeTab(s: Session) {
        val r = results
        val look = r?.count { it.level == Level.LOOK && !it.system } ?: 0
        val tone = when { r == null -> Tone(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant); look == 0 -> safeTone(); else -> warnTone() }
        val (bg, fg) = tone.bg to tone.fg
        AppHeader()
        Card(colors = CardDefaults.cardColors(bg, fg), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(if (r != null && look == 0) Icons.Outlined.CheckCircle else Icons.Outlined.Info, null)
                    Text(
                        when { r == null -> "Not checked yet"; look == 0 -> "Looks safe"; else -> "$look thing${if (look == 1) "" else "s"} to look at" },
                        Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                Text(if (r == null) "Run a check to see what can reach this phone." else "Checked just now. The check only reads; it changes nothing.")
            }
        }
        HomeRow(Icons.Outlined.Search, "Check", if (r == null) "Not run yet" else if (look == 0) "Nothing to look at" else "$look to look at") { tab = Page.CHECK }
        HomeRow(Icons.Outlined.Delete, "Clean up", if (restoreItems.isEmpty()) "Nothing removed" else "${restoreItems.size} removed or disabled") { tab = Page.CLEAN }
        HomeRow(Icons.Outlined.Lock, "Modes", activeMode?.let { "${it.label} is on" } ?: "None on") { tab = Page.MODES }
        val w = warnTone()
        Card(colors = CardDefaults.cardColors(w.bg, w.fg), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Warning, null)
                Text("Wireless debugging is on", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton({ finish(s) }, enabled = busy == null) { Text("Turn off", color = w.fg) }
            }
        }
    }

    @Composable
    private fun HomeRow(icon: ImageVector, title: String, state: String, open: () -> Unit) = OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = open)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(state, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    private fun CheckTab(s: Session) {
        val r = results
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (r == null) "What can reach this phone, and which apps have special access. Read-only."
                    else r.count { it.level == Level.LOOK && !it.system }.let { look ->
                        // the "Nothing stood out" note has no apps, so it is not counted as information
                        val info = r.count { it.level == Level.INFO && !it.system && it.apps.isNotEmpty() }
                        listOfNotNull(if (look == 0) "Nothing to look at" else "$look to look at", if (info > 0) "$info for your information" else null).joinToString(", ")
                    },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton({ run("Checking…") { results = s.check(applicationContext) } }, enabled = busy == null) { Text(if (r == null) "Check" else "Check again") }
        }
        val (system, own) = (r ?: return).sortedBy { it.level }.partition { it.system }
        own.forEach { FindingCard(it) }
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
        OutlinedCard(
            modifier = (if (grouped) Modifier.clickable { show = true } else Modifier).fillMaxWidth(),
            border = if (f.level == Level.LOOK && !f.system) BorderStroke(1.5.dp, warnTone().fg) else CardDefaults.outlinedCardBorder(),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(f.title, style = MaterialTheme.typography.titleSmall)
                Text(f.detail, style = MaterialTheme.typography.bodyMedium)
                if (grouped) Text("Tap to see all ${f.apps.size} apps", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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

    @Composable
    private fun CleanupTab(s: Session) {
        Text("Uninstall or disable apps you don't use. Restore below brings back any of them.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        val c = cleanup
        if (c == null) {
            Button({
                run("Reading the app list…") {
                    // the phone's own app list, not a list per brand; generic.txt only adds notes for apps we know
                    val entries = parseList(assets.open("generic.txt").bufferedReader().readText())
                    val scan = debloat.scan(s.shell, entries, packageName)
                    val p = scan.plan
                    selected.clear()
                    selected += entries.filter { it.risk == Risk.SAFE && it.pkg in p.todo }.map { it.pkg } // only safe pre-selected
                    launcherPkgs = launcherApps(applicationContext).map { it.first }.toSet()
                    systemPkgs = scan.system
                    others = scan.others.map { it to label(it) }.sortedBy { it.second.lowercase() }
                    cleanup = entries to p
                }
            }, enabled = busy == null) { Text("Show apps") }
        } else {
            val (entries, p) = c
            Text("Only remove apps you recognise. Android's core apps are not listed, and the check after each batch undoes anything that breaks Home, calls or the keyboard.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search apps") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true)
            if (p.already.isNotEmpty()) Text("Already off: ${p.already.size}")
            for (e in entries.filter { it.pkg in p.todo }) PickRow(e.pkg, label(e.pkg) + if (e.risk != Risk.SAFE) "  (${e.risk.name.lowercase()}: check you do not use it)" else "")
            val withIcon = launcherPkgs
            for ((pkg, name) in others.filter { it.first in withIcon }) PickRow(pkg, name)
            val background = others.filterNot { it.first in withIcon }
            if (background.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                // what was picked in the pop-up shows here too, so the list matches what Uninstall/Disable will act on
                val chosen = background.filter { it.first in selected }
                if (chosen.isNotEmpty()) {
                    Text("Background services you chose", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for ((pkg, name) in chosen) PickRowShown(pkg, name)
                }
                ListRow("Background services", "${background.size} found · ${if (chosen.isEmpty()) "add some" else "${chosen.size} chosen, add more"}") { open = true }
                if (open) ListDialog("Background services", "Parts of apps and of Android that run in the background and have no screen you can open. Many are needed by the phone; leave them unless you know what one does.", { open = false }, search = true) { q ->
                    items(background.filter { it.matches(q) }, key = { it.first }) { (pkg, name) -> PickRowShown(pkg, name) }
                }
            }
            // Uninstall only for built-in apps: their apk stays on the phone so Restore works; a Play Store app's is deleted
            val playApps = selected.filterNot { it in systemPkgs }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ cleanupApply(s, Method.REMOVE) }, enabled = busy == null && selected.isNotEmpty() && playApps.isEmpty()) { Text("Uninstall ${selected.size}") }
                OutlinedButton({ cleanupApply(s, Method.DISABLE) }, enabled = busy == null && selected.isNotEmpty()) { Text("Disable ${selected.size}") }
            }
            if (playApps.isNotEmpty()) Text("${playApps.joinToString { label(it) }} came from the Play Store. PhoneWarden can only disable these; to uninstall one, use the Play Store, where you can reinstall it later.", style = MaterialTheme.typography.bodyMedium)
            else Text("Uninstall removes the app for you; the phone keeps a copy, so Restore brings it back. Disable leaves it installed but switched off.", style = MaterialTheme.typography.bodyMedium)
        }
        RestoreList(s)
    }

    /** Everything PhoneWarden uninstalled or disabled, to bring back one by one. */
    @Composable
    private fun RestoreList(s: Session) {
        val done = restoreItems
        if (done.isEmpty()) return
        Text("Restore", style = MaterialTheme.typography.titleMedium)
        for ((pkg, method) in done.entries.sortedBy { label(it.key).lowercase() }) Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(pkg in restorePick, { on -> if (on) restorePick += pkg else restorePick -= pkg })
            Column {
                Text(label(pkg))
                Text("${if (method == Method.REMOVE) "Uninstalled" else "Disabled"} · $pkg", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({
                run("Restoring…") {
                    val n = restorePick.size
                    debloat.restore(s.shell, restorePick.toSet())
                    restorePick.clear(); cleanup = null
                    restoreItems = debloat.removed
                    toast = "$n app${if (n == 1) " is" else "s are"} back"
                }
            }, enabled = busy == null && restorePick.isNotEmpty()) { Text("Restore ${restorePick.size}") }
            TextButton({ restorePick.clear(); restorePick += done.keys }, enabled = busy == null) { Text("Select all") }
        }
    }

    @Composable
    private fun PickRow(pkg: String, text: String) {
        if (query.isNotBlank() && !text.contains(query, true) && !pkg.contains(query, true)) return
        PickRowShown(pkg, text)
    }

    @Composable
    private fun PickRowShown(pkg: String, text: String) = Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(pkg in selected, role = Role.Checkbox) { if (it) selected += pkg else selected -= pkg }.padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Checkbox(pkg in selected, null) // the whole row toggles it, and reads as one checkbox to TalkBack
        Column(Modifier.weight(1f)) {
            Text(text)
            if (text != pkg) Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    /** App name, or the package name for apps Android does not let us see. Cached: lists redraw on every search keystroke. */
    private val labels = HashMap<String, String>()
    private fun label(pkg: String) = labels.getOrPut(pkg) {
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }

    private fun cleanupApply(s: Session, method: Method) = run("Working in small batches, checking the phone after each…") {
        val record = debloat.record
        try {
            debloat.remove(s.shell, selected.toList(), method)
            Records.copyToDownloads(applicationContext, record)
            toast = "${if (method == Method.REMOVE) "Uninstalled" else "Disabled"} ${selected.size} app${if (selected.size == 1) "" else "s"}. Restore list saved in Downloads."
            selected.clear()
        } catch (e: HealthException) {
            message = "Stopped: Home, phone or keyboard stopped working after a batch, so that batch was undone. Earlier batches stay uninstalled or disabled; Restore lists them."
        } catch (e: Exception) {
            message = "Stopped and undid the last batch: ${e.message}. If the phone refused to disable an app, try Uninstall."
        } finally {
            if (record.exists()) runCatching { Records.copyToDownloads(applicationContext, record) }
            restoreItems = debloat.removed
            cleanup = null
        }
    }

    @Composable
    private fun ModesTab(s: Session) {
        val on = activeMode
        var shown by remember { mutableStateOf(on ?: Profile.KIOSK) }
        Text("A mode changes a few settings for one job. Turning it off puts your own settings back exactly as they were.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (p in Profile.entries) OutlinedCard(
                Modifier.weight(1f).clickable { shown = p },
                border = if (p == shown) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else CardDefaults.outlinedCardBorder(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(p.label, style = MaterialTheme.typography.titleMedium)
                    Text(if (p == on) "On" else "Off", color = if (p == on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        when (shown) {
            Profile.KIOSK -> ModeCard("Kiosk", "A few apps on a simple home screen, or one app pinned. Muting still lets alarms ring.", Profile.KIOSK, s, on)
            Profile.E_READER -> ModeCard("E-reader", "A paper-like home screen that opens your reading app. KOReader and ReadEra are good free readers.", Profile.E_READER, s, on)
        }
    }

    @Composable
    private fun ModeCard(name: String, what: String, profile: Profile, s: Session, on: Profile?) = OutlinedCard(Modifier.fillMaxWidth()) {
        val off = remember { mutableStateListOf(*modes.off(profile).toTypedArray()) }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(what)
                if (on != null && on != profile) Text("Turn off ${on.label.lowercase()} mode first.", style = MaterialTheme.typography.bodyMedium)
            }
            Switch(on == profile, { turnOn ->
                if (turnOn) run("Turning on $name mode…") {
                    modes.turnOn(s.shell, profile)
                    activeMode = modes.active
                    toast = "$name mode is on"
                } else run("Turning off $name mode…") {
                    modes.turnOff(s.shell)
                    activeMode = modes.active
                    toast = "$name mode is off. Your settings are back."
                }
            }, enabled = busy == null && (on == null || on == profile))
        }
        Text("Changes", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (option in profile.options.keys) Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(option, Modifier.weight(1f))
            Switch(option !in off, { keep ->
                if (keep) off -= option else off += option
                modes.setOff(profile, off.toSet())
            }, enabled = on == null)
        }
        if (on == profile) Text("Turn the mode off to change these.", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium)
        if ("Own home screen" !in off) HomePicker(profile)
        if (profile == Profile.KIOSK) PinPicker(s, on == profile, askPin = "Ask for PIN to unpin" !in off)
    }

    /** Which apps the mode's home screen shows; editable any time, the home screen rereads it on return. */
    @Composable
    private fun HomePicker(profile: Profile) {
        var open by remember { mutableStateOf(false) }
        val picked = remember { mutableStateListOf(*modes.apps(profile).toTypedArray()) }
        val all = remember { launcherApps(this) }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    private fun usesGestures() = Settings.Secure.getInt(contentResolver, "navigation_mode", 0) == 2 ||
        Settings.Secure.getInt(contentResolver, "hide_navigationbar_enable", 0) > 0

    private fun unpinHow() = if (usesGestures()) "swipe up from the bottom of the screen and hold" else "touch and hold Back and Recents together"

    /** Pinning hides PhoneWarden, so it is the last step: pin, then switch Wireless debugging off. */
    @Composable
    private fun PinPicker(s: Session, kioskOn: Boolean, askPin: Boolean) {
        var apps by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            // always shown, so it is clear pinning exists; it needs Kiosk on, which enables screen pinning
            ListRow("Pin an app", if (kioskOn) "One app fills the screen until you unpin it" else "Turn Kiosk on first, then choose the app") {
                if (!kioskOn) toast = "Turn on Kiosk mode first"
                else run("Listing apps…") {
                    // pin() starts a component; the launch intent names the app's own start screen
                    apps = launcherApps(applicationContext).mapNotNull { (pkg, label) ->
                        packageManager.getLaunchIntentForPackage(pkg)?.component?.flattenToShortString()?.let { it to label }
                    }
                }
            }
            // what pinning does, up front: people were surprised by the PIN prompt and by debugging being off afterwards
            Text(
                "When an app is pinned, Wireless debugging switches off, because PhoneWarden can't be reached. " +
                    "To unpin, ${unpinHow()}${if (askPin) "; the phone then asks for your PIN" else ""}. To change Kiosk after that, turn Wireless debugging on again.",
                Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        apps?.let { list ->
            ListDialog(
                "Pin an app",
                "The app fills the screen and Wireless debugging switches off. To unpin it, ${unpinHow()}.",
                { apps = null }, search = true,
            ) { q ->
                items(list.filter { it.matches(q) }) { (component, label) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(enabled = busy == null) {
                            apps = null
                            run("Pinning $label and switching off Wireless debugging…") {
                                pin(s.shell, component)
                                val off = s.finish(applicationContext)
                                session = null; results = null; cleanup = null
                                wirelessOn = Session.isWirelessDebuggingOn(applicationContext)
                                if (off) toast = "$label is pinned and Wireless debugging is off. To unpin, ${unpinHow()}." else message = "$label is pinned. Wireless debugging may still be on: turn it off in Developer options."
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) { AppIcon(component.substringBefore("/")); Text(label, Modifier.padding(start = 12.dp)) }
                }
            }
        }
    }

    @Composable
    private fun Steps(vararg steps: String) = Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        steps.forEachIndexed { i, t -> Text("${i + 1}. $t") }
    }

    @Composable
    private fun Note(text: String) = errorTone().let { t ->
        Card(colors = CardDefaults.cardColors(t.bg, t.fg), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Warning, null)
                Text(text, Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp))
                IconButton({ message = null }) { Icon(Icons.Outlined.Close, "Close") }
            }
        }
    }

    /** A row that opens a pop-up list: the title, what is chosen, and a chevron. */
    @Composable
    private fun ListRow(title: String, value: String, onClick: () -> Unit) = OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
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
