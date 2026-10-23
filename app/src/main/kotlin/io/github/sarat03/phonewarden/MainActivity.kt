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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.BorderStroke
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

class MainActivity : ComponentActivity() {
    // Process-wide, not per activity: Android recreates the activity on config changes (battery saver
    // turns on dark theme), and an open session must survive that or Wireless debugging is left on.
    private companion object {
        var paired by mutableStateOf(false)
        var wirelessOn by mutableStateOf(false)
        var session by mutableStateOf<Session?>(null)
        var busy by mutableStateOf<String?>(null)
        var message by mutableStateOf<String?>(null)
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
        setContent { AppTheme { Surface(Modifier.fillMaxSize()) { Screen() } } }
    }

    override fun onResume() {
        super.onResume()
        paired = Pairing.isPaired(this)
        wirelessOn = Session.isWirelessDebuggingOn(this)
        activeMode = modes.active
        restoreItems = debloat.removed
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
                message = "The connection to this phone was lost (Wireless debugging may have switched off). Turn it on again and tap Start."
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
        Column(Modifier.safeDrawingPadding().padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            busy?.let { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.padding(end = 12.dp)); Text(it) } }
            message?.let { Note(it) }
            val s = session
            when {
                s != null -> SessionScreen(s)
                !paired -> PairScreen()
                else -> StartScreen()
            }
        }
    }

    @Composable
    private fun PairScreen() = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PhoneWarden", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text("PhoneWarden", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    @Composable
    private fun HomeRowStatic(title: String, state: String) = OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(state, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    private fun SessionScreen(s: Session) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (tab) {
                0 -> HomeTab(s)
                1 -> CheckTab(s)
                2 -> CleanupTab(s)
                else -> ModesTab(s)
            }
        }
        NavigationBar {
            listOf(
                "Home" to Icons.Outlined.Home, "Check" to Icons.Outlined.Search,
                "Clean up" to Icons.Outlined.Delete, "Modes" to Icons.Outlined.Settings,
            ).forEachIndexed { i, (t, icon) ->
                NavigationBarItem(tab == i, { tab = i }, { Icon(icon, null) }, label = { Text(t) })
            }
        }
    }

    private fun finish(s: Session) = run("Switching off Wireless debugging…") {
        val off = s.finish(applicationContext)
        session = null; results = null; cleanup = null
        wirelessOn = Session.isWirelessDebuggingOn(applicationContext)
        message = if (off) "Done. Wireless debugging is off (checked)."
        else "Wireless debugging may still be on. Turn it off in Developer options."
    }

    /** One answer first, then a row per area with its state; the way to finish stays in sight. */
    @Composable
    private fun HomeTab(s: Session) {
        val r = results
        val look = r?.count { it.level == Level.LOOK && !it.system } ?: 0
        val (bg, fg) = if (r != null && look == 0) MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        Text("PhoneWarden", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(colors = CardDefaults.cardColors(bg, fg), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    when { r == null -> "Not checked yet"; look == 0 -> "Looks safe"; else -> "$look thing${if (look == 1) "" else "s"} to look at" },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(if (r == null) "Run a check to see what can reach this phone." else "Checked just now. The check only reads; it changes nothing.")
            }
        }
        HomeRow("Check", if (r == null) "Not run yet" else if (look == 0) "Nothing to look at" else "$look to look at") { tab = 1 }
        HomeRow("Clean up", if (restoreItems.isEmpty()) "Nothing removed" else "${restoreItems.size} removed or disabled") { tab = 2 }
        HomeRow("Modes", activeMode?.let { "${it.label} is on" } ?: "None on") { tab = 3 }
        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Wireless debugging is on", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton({ finish(s) }, enabled = busy == null) { Text("Turn off") }
            }
        }
    }

    @Composable
    private fun HomeRow(title: String, state: String, open: () -> Unit) = OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = open)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(state, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    private fun CheckTab(s: Session) {
        val r = results
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Check", style = MaterialTheme.typography.headlineSmall)
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
            OutlinedButton({ open = !open }, Modifier.fillMaxWidth()) {
                Text("System apps (${system.size})  ${if (open) "▲" else "▼"}")
            }
            if (open) {
                Text("Apps built into the phone. Their access is usually how Android works.", style = MaterialTheme.typography.bodySmall)
                system.forEach { FindingCard(it) }
            }
        }
    }

    @Composable
    private fun FindingCard(f: Finding) {
        var show by remember { mutableStateOf(false) }
        val grouped = f.apps.size > 3
        OutlinedCard(
            modifier = (if (grouped) Modifier.clickable { show = true } else Modifier).fillMaxWidth(),
            border = if (f.level == Level.LOOK && !f.system) BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary) else CardDefaults.outlinedCardBorder(),
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
                    Text("They share one user id, so Android cannot tell which of them is doing it.", style = MaterialTheme.typography.bodySmall)
                    for (pkg in f.apps.sortedBy { label(it).lowercase() }) Column {
                        Text(label(pkg))
                        if (label(pkg) != pkg) Text(pkg, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
        )
    }

    @Composable
    private fun CleanupTab(s: Session) {
        Text("Lists the apps on this phone you can uninstall or disable. Restore below brings back any of them.")
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
            Text("Only remove apps you recognise. Android's core apps are not listed, and the check after each batch undoes anything that breaks Home, calls or the keyboard.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search apps") }, singleLine = true)
            if (p.already.isNotEmpty()) Text("Already off: ${p.already.size}")
            for (e in entries.filter { it.pkg in p.todo }) PickRow(e.pkg, label(e.pkg) + if (e.risk != Risk.SAFE) "  (${e.risk.name.lowercase()}: check you do not use it)" else "")
            val withIcon = launcherPkgs
            for ((pkg, name) in others.filter { it.first in withIcon }) PickRow(pkg, name)
            val background = others.filterNot { it.first in withIcon }
            if (background.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                OutlinedButton({ open = !open }, Modifier.fillMaxWidth()) { Text("Advanced: background services (${background.size})  ${if (open) "▲" else "▼"}") }
                if (open) {
                    Text("These have no icon and run in the background. Many are needed by the phone; leave them unless you know what one does.", style = MaterialTheme.typography.bodySmall)
                    for ((pkg, name) in background) PickRow(pkg, name)
                }
            }
            // Uninstall only for built-in apps: their apk stays on the phone so Restore works; a Play Store app's is deleted
            val playApps = selected.filterNot { it in systemPkgs }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ cleanupApply(s, Method.REMOVE) }, enabled = busy == null && selected.isNotEmpty() && playApps.isEmpty()) { Text("Uninstall ${selected.size}") }
                OutlinedButton({ cleanupApply(s, Method.DISABLE) }, enabled = busy == null && selected.isNotEmpty()) { Text("Disable ${selected.size}") }
            }
            if (playApps.isNotEmpty()) Text("${playApps.joinToString { label(it) }} came from the Play Store. PhoneWarden can only disable these; to uninstall one, use the Play Store, where you can reinstall it later.", style = MaterialTheme.typography.bodySmall)
            else Text("Uninstall removes the app for you; the phone keeps a copy, so Restore brings it back. Disable leaves it installed but switched off.", style = MaterialTheme.typography.bodySmall)
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
                Text("${if (method == Method.REMOVE) "Uninstalled" else "Disabled"} · $pkg", style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({
                run("Restoring…") {
                    val n = restorePick.size
                    debloat.restore(s.shell, restorePick.toSet())
                    restorePick.clear(); cleanup = null
                    restoreItems = debloat.removed
                    message = "$n app${if (n == 1) " is" else "s are"} back."
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
    private fun PickRowShown(pkg: String, text: String) = Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(pkg in selected, { on -> if (on) selected += pkg else selected -= pkg })
        Column {
            Text(text)
            if (text != pkg) Text(pkg, style = MaterialTheme.typography.bodySmall)
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
            message = "${if (method == Method.REMOVE) "Uninstalled" else "Disabled"} ${selected.size} app${if (selected.size == 1) "" else "s"}. A copy of the restore list is in ${Records.copyToDownloads(applicationContext, record)}."
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
        Text("Modes", style = MaterialTheme.typography.headlineSmall)
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
        if (on == Profile.KIOSK && shown == Profile.KIOSK) PinPicker(s)
    }

    @Composable
    private fun ModeCard(name: String, what: String, profile: Profile, s: Session, on: Profile?) = OutlinedCard(Modifier.fillMaxWidth()) {
        val off = remember { mutableStateListOf(*modes.off(profile).toTypedArray()) }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(what)
                if (on != null && on != profile) Text("Turn off ${on.label.lowercase()} mode first.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(on == profile, { turnOn ->
                if (turnOn) run("Turning on $name mode…") {
                    modes.turnOn(s.shell, profile)
                    activeMode = modes.active
                    message = "$name mode is on."
                } else run("Turning off $name mode…") {
                    modes.turnOff(s.shell)
                    activeMode = modes.active
                    message = "$name mode is off. Your settings are back as they were."
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
        if (on == profile) Text("Turn the mode off to change these.", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
        if ("Own home screen" !in off) HomePicker(profile)
    }

    /** Which apps the mode's home screen shows; editable any time, the home screen rereads it on return. */
    @Composable
    private fun HomePicker(profile: Profile) {
        var open by remember { mutableStateOf(false) }
        val picked = remember { mutableStateListOf(*modes.apps(profile).toTypedArray()) }
        val all = remember { launcherApps(this) }
        if (profile == Profile.E_READER) {
            var reader by remember { mutableStateOf(modes.reader(all.map { it.first })) }
            var choose by remember { mutableStateOf(false) }
            val name = all.firstOrNull { it.first == reader }?.second ?: "none found"
            TextButton({ choose = !choose }, Modifier.padding(horizontal = 12.dp)) { Text("Reading app for Continue reading: $name ${if (choose) "▲" else "▼"}") }
            if (choose) for ((pkg, label) in all) Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(pkg == reader, { reader = pkg; modes.setReader(pkg); choose = false })
                Text(label)
            }
        }
        TextButton({ open = !open }, Modifier.padding(horizontal = 12.dp)) { Text("${if (profile == Profile.E_READER) "Other apps" else "Apps"} on the home screen (${picked.size}) ${if (open) "▲" else "▼"}") }
        if (open) for ((pkg, label) in all) Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(pkg in picked, { if (it) picked += pkg else picked -= pkg; modes.setApps(profile, picked.toSet()) })
            Text(label)
        }
    }

    /** Pinning hides PhoneWarden, so it is the last step: pin, then switch Wireless debugging off. */
    @Composable
    private fun PinPicker(s: Session) {
        var apps by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
        Text("Pin an app", style = MaterialTheme.typography.titleMedium)
        Text("The app fills the screen and stays there. To unpin later, hold Back and Overview together (or swipe up and hold, with gesture navigation).")
        val list = apps
        if (list == null) OutlinedButton({
            run("Listing apps…") {
                // pin() starts a component; the launch intent names the app's own start screen
                apps = launcherApps(applicationContext).mapNotNull { (pkg, label) ->
                    packageManager.getLaunchIntentForPackage(pkg)?.component?.flattenToShortString()?.let { it to label }
                }
            }
        }, enabled = busy == null) { Text("Choose an app to pin") }
        else for ((component, label) in list) OutlinedButton({
            run("Pinning $label and switching off Wireless debugging…") {
                pin(s.shell, component)
                val off = s.finish(applicationContext)
                session = null; results = null; cleanup = null
                wirelessOn = Session.isWirelessDebuggingOn(applicationContext)
                message = if (off) "$label is pinned. Wireless debugging is off (checked)." else "$label is pinned. Wireless debugging may still be on: turn it off in Developer options."
            }
        }, Modifier.fillMaxWidth(), enabled = busy == null) { Text(label) }
    }

    @Composable
    private fun Steps(vararg steps: String) = Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        steps.forEachIndexed { i, t -> Text("${i + 1}. $t") }
    }

    @Composable
    private fun Note(text: String) = Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer)) {
        Text(text, Modifier.padding(12.dp))
    }
}
