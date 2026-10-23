package io.github.sarat03.phonewarden

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
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
import phonewarden.engine.apply
import phonewarden.engine.applyProfile
import phonewarden.engine.launchableApps
import phonewarden.engine.otherApps
import phonewarden.engine.parseList
import phonewarden.engine.pin
import phonewarden.engine.unpin
import phonewarden.engine.plan
import phonewarden.engine.restore
import phonewarden.engine.restoreProfile

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { Screen() } } }
    }

    override fun onResume() {
        super.onResume()
        paired = Pairing.isPaired(this)
        wirelessOn = Session.isWirelessDebuggingOn(this)
        activeMode = Profile.entries.firstOrNull { Records.mode(this, it).exists() }
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
            try { work() } catch (e: Exception) { message = explain(e) } finally { busy = null }
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
    private fun openDeveloperOptions() = startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

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
        Text("First, let PhoneWarden talk to this phone. This happens once.")
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
        if (!wirelessOn) {
            Text("Turn on Wireless debugging in Developer options, then come back. PhoneWarden switches it off again when you finish.")
            Button(::openDeveloperOptions) { Text("Open Developer options") }
        } else {
            Text("Wireless debugging is on. PhoneWarden will connect to this phone only, and switch Wireless debugging off when you finish.")
            Button({ run("Connecting…") { session = Session.open(applicationContext) } }, enabled = busy == null) { Text("Start") }
        }
    }

    @Composable
    private fun SessionScreen(s: Session) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryTabRow(tab) {
            listOf("Check", "Clean up", "Modes").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (tab) {
                0 -> CheckTab(s)
                1 -> CleanupTab(s)
                else -> ModesTab(s)
            }
        }
        Button({
            run("Switching off Wireless debugging…") {
                val off = s.finish(applicationContext)
                session = null; results = null; cleanup = null
                wirelessOn = Session.isWirelessDebuggingOn(applicationContext)
                message = if (off) "Done. Wireless debugging is off (checked)."
                else "Wireless debugging may still be on. Turn it off in Developer options."
            }
        }, Modifier.fillMaxWidth(), enabled = busy == null) { Text("Finish and switch off Wireless debugging") }
    }

    @Composable
    private fun CheckTab(s: Session) {
        Text("Looks at what can reach this phone and what apps have special access. Read-only: it changes nothing.")
        Button({ run("Checking…") { results = s.check(applicationContext) } }, enabled = busy == null) { Text(if (results == null) "Check this phone" else "Check again") }
        val (system, own) = (results ?: return).sortedBy { it.level }.partition { it.system }
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
    private fun FindingCard(f: Finding) =
        Card(colors = if (f.level == Level.LOOK && !f.system) CardDefaults.cardColors(MaterialTheme.colorScheme.tertiaryContainer) else CardDefaults.cardColors()) {
            Column(Modifier.padding(12.dp)) {
                Text(f.title, style = MaterialTheme.typography.titleSmall)
                Text(f.detail, style = MaterialTheme.typography.bodyMedium)
            }
        }

    @Composable
    private fun CleanupTab(s: Session) {
        Text("Lists every app on this phone that you can turn off. Nothing is deleted; Restore brings everything back.")
        val c = cleanup
        if (c == null) {
            Button({
                run("Reading the app list…") {
                    // the phone's own app list, not a list per brand; generic.txt only adds notes for apps we know
                    val entries = parseList(assets.open("generic.txt").bufferedReader().readText())
                    val p = plan(s.shell, entries, maxRisk = Risk.RISKY)
                    selected.clear()
                    selected += entries.filter { it.risk == Risk.SAFE && it.pkg in p.todo }.map { it.pkg } // only safe pre-selected
                    launcherPkgs = launchableApps(s.shell).map { it.substringBefore("/") }.toSet()
                    others = otherApps(s.shell, entries.map { it.pkg }.toSet(), packageName)
                        .map { it to label(it) }.sortedBy { it.second.lowercase() }
                    cleanup = entries to p
                }
            }, enabled = busy == null) { Text("Show what can be turned off") }
        } else {
            val (entries, p) = c
            Text("Only turn off apps you recognise. Android's core apps are not listed, and the check after each batch undoes anything that breaks Home, calls or the keyboard.", style = MaterialTheme.typography.bodySmall)
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ cleanupApply(s, Method.DISABLE) }, enabled = busy == null && selected.isNotEmpty()) { Text("Turn off ${selected.size}") }
                OutlinedButton({ cleanupApply(s, Method.REMOVE) }, enabled = busy == null && selected.isNotEmpty()) { Text("Remove for this user") }
            }
            Text("Use \"Remove for this user\" only for apps the phone refuses to turn off. The app stays on the phone and Restore brings it back.", style = MaterialTheme.typography.bodySmall)
        }
        if (Records.debloat(this).exists()) OutlinedButton({
            run("Restoring…") {
                restore(s.shell, Records.debloat(applicationContext))
                Records.debloat(applicationContext).delete()
                cleanup = null
                message = "Everything PhoneWarden turned off is back on."
            }
        }, enabled = busy == null) { Text("Restore everything") }
    }

    @Composable
    private fun PickRow(pkg: String, text: String) = Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(pkg in selected, { on -> if (on) selected += pkg else selected -= pkg })
        Column {
            Text(text)
            if (text != pkg) Text(pkg, style = MaterialTheme.typography.bodySmall)
        }
    }

    /** App name, or the package name for apps Android does not let us see. */
    private fun label(pkg: String) =
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    private fun cleanupApply(s: Session, method: Method) = run("Working in small batches, checking the phone after each…") {
        val record = Records.debloat(applicationContext)
        try {
            apply(s.shell, Plan(todo = selected.toList()), record, dryRun = false, method = method)
            message = "Done: ${selected.size} apps. Restore record saved to ${Records.copyToDownloads(applicationContext, record)}."
        } catch (e: HealthException) {
            message = "Stopped: Home, phone or keyboard stopped working after a batch, so that batch was undone. Earlier batches stay off."
        } catch (e: Exception) {
            message = "Stopped and undid the last batch: ${e.message}. If the phone refused to turn an app off, try \"Remove for this user\"."
        } finally {
            if (record.exists()) runCatching { Records.copyToDownloads(applicationContext, record) }
            cleanup = null
        }
    }

    @Composable
    private fun ModesTab(s: Session) {
        val on = activeMode
        Text("A mode changes a few settings for one job. Turning it off puts your own settings back exactly as they were.")
        ModeCard("Kiosk", "Screen stays on while charging, notifications muted (alarms still ring), and one app pinned to the screen. Use Clean up too to remove distractions.", Profile.KIOSK, s, on)
        ModeCard("E-reader", "Grayscale screen, no animations, notifications muted, battery saver on. For reading, we recommend KOReader (free, on F-Droid).", Profile.E_READER, s, on)
        if (on == Profile.KIOSK) PinPicker(s)
    }

    @Composable
    private fun ModeCard(name: String, what: String, profile: Profile, s: Session, on: Profile?) = Card {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(what)
                if (on != null && on != profile) Text("Turn off ${on.name.lowercase().replace('_', '-')} mode first.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(on == profile, { turnOn ->
                val record = Records.mode(applicationContext, profile)
                if (turnOn) run("Turning on $name mode…") {
                    applyProfile(s.shell, profile, record)
                    activeMode = profile
                    message = "$name mode is on."
                } else run("Turning off $name mode…") {
                    if (profile == Profile.KIOSK) unpin(s.shell)
                    restoreProfile(s.shell, record)
                    activeMode = null
                    message = "$name mode is off. Your settings are back as they were."
                }
            }, enabled = busy == null && (on == null || on == profile))
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
                apps = launchableApps(s.shell).filterNot { it.startsWith(packageName) }
                    .map { c -> c to runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(c.substringBefore("/"), 0)).toString() }.getOrDefault(c.substringBefore("/")) }
                    .sortedBy { it.second.lowercase() }
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
