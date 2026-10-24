package monomode.engine

import java.io.EOFException
import java.io.File
import java.io.IOException
import java.net.SocketException
import javax.net.ssl.SSLException
import kotlin.concurrent.thread

/** An open connection to this phone's adbd. Session on the phone, a fake in tests. */
interface Link {
    val shell: Shell
    /** Reads what can reach the phone; changes nothing. */
    fun check(): List<Finding>
    /** Switches Wireless debugging off over this link and closes it. True when verified off. */
    fun finish(): Boolean
    fun close()
}

/** Everything the screens show about the phone, as one snapshot. */
data class PhoneState(
    val wirelessOn: Boolean = false,
    val connected: Boolean = false,
    /** What is running now ("Checking…"), or null. Actions are ignored while busy. */
    val busy: String? = null,
    /** An error or something that needs the user; stays until [Phone.dismiss]. */
    val message: String? = null,
    val results: List<Finding>? = null,
    val scan: Debloat.Scan? = null,
    val entries: List<Entry> = emptyList(),
    val removed: Map<String, Method> = emptyMap(),
    val activeMode: Profile? = null,
    /** A pin switched Wireless debugging off; cleared by the next connection. */
    val afterPin: Boolean = false,
) {
    /** Retry only helps after an error, or after a pin ended the session; otherwise coming back connects by itself. */
    val retryShown get() = message != null || afterPin
}

/**
 * This phone, as the app sees it: connect, check, remove and restore apps, switch modes, pin, finish.
 * Every action runs in [background] and reports through [onChange]; errors become [PhoneState.message]
 * in plain words, and a dropped connection ends the session instead of failing later.
 */
class Phone(
    private val open: () -> Link,
    private val debloat: Debloat,
    private val modes: Modes,
    private val wirelessOn: () -> Boolean,
    /** Called after each removal and restore with the restore record (maybe deleted: nothing left), to keep a copy outside the app (Downloads). */
    private val saveCopy: (File) -> Unit = {},
    private val background: (() -> Unit) -> Unit = { work -> thread { work() } },
    private val onChange: (PhoneState) -> Unit = {},
) {
    @Volatile var state = PhoneState()
        private set
    @Volatile private var link: Link? = null

    @Synchronized private fun update(f: PhoneState.() -> PhoneState) { state = state.f(); onChange(state) }

    /** Rereads the phone (on every return to the app); connects and checks when paired and Wireless debugging is on. */
    fun refresh(paired: Boolean) {
        val on = wirelessOn()
        update { copy(wirelessOn = on, activeMode = modes.active, removed = debloat.removed) }
        // Android switched Wireless debugging off while we were away: that link is dead
        if (!on && state.busy == null) link?.let { l -> background { runCatching { l.close() } }; drop() }
        if (paired && on && link == null && state.busy == null) connect()
    }

    /** Connects and checks. A no-op that says so when Wireless debugging is still off. */
    fun connect() {
        val on = wirelessOn()
        update { copy(wirelessOn = on) }
        if (!on) return update { copy(message = "Wireless debugging is still off.") }
        run("Connecting and checking this phone…") {
            val l = open()
            link = l
            update { copy(connected = true, afterPin = false) }
            val r = l.check()
            update { copy(results = r) }
        }
    }

    fun check() = withLink("Checking…") { l -> val r = l.check(); update { copy(results = r) } }

    /** Lists what can be removed: the curated [entries] and every other app but [self]. */
    fun scan(entries: List<Entry>, self: String) = withLink("Reading the app list…") { l ->
        this.self = self
        val s = debloat.scan(l.shell, entries, self)
        update { copy(scan = s, entries = entries) }
    }
    private var self = ""

    // re-read the list over the same connection so the App cleaner never asks for "Show apps" again
    private fun rescan(l: Link) =
        state.scan?.let { runCatching { debloat.scan(l.shell, state.entries, self) }.getOrNull() }

    /** Removes in checked batches; a batch that breaks Home, calls or the keyboard is undone and reported. */
    fun remove(pkgs: List<String>, method: Method) = withLink("Working in small batches, checking the phone after each…") { l ->
        try {
            debloat.remove(l.shell, pkgs, method)
        } catch (e: HealthException) {
            update { copy(message = "Stopped: Home, phone or keyboard stopped working after a batch, so that batch was undone. Earlier batches stay uninstalled or disabled; Restore lists them.") }
        } catch (e: Exception) {
            if (lostConnection(e)) throw e
            update { copy(message = "Stopped and undid the last batch: ${e.message}. If the phone refused to disable an app, try Uninstall.") }
        } finally {
            runCatching { saveCopy(debloat.record) }
            val s = rescan(l)
            update { copy(removed = debloat.removed, scan = s) }
        }
    }

    fun restore(pkgs: Set<String>) = withLink("Restoring…") { l ->
        try { debloat.restore(l.shell, pkgs) } finally { runCatching { saveCopy(debloat.record) } }
        val s = rescan(l)
        update { copy(removed = debloat.removed, scan = s) }
    }

    fun turnOn(p: Profile) = withLink("Turning on ${p.label} mode…") { l -> modes.turnOn(l.shell, p); update { copy(activeMode = modes.active) } }

    fun turnOff() = withLink("Turning off ${state.activeMode?.label} mode…") { l -> modes.turnOff(l.shell); update { copy(activeMode = modes.active) } }

    /** Pins [component] and switches Wireless debugging off: pinning hides MonoMode, so nothing else can follow. */
    fun pin(component: String, label: String) = withLink("Pinning $label and switching off Wireless debugging…") { l ->
        pin(l.shell, component)
        val off = l.finish()
        drop()
        update { copy(afterPin = true, wirelessOn = wirelessOn(), message = if (off) null else "$label is pinned. Wireless debugging may still be on: turn it off in Developer options.") }
    }

    /** Switches Wireless debugging off and ends the session. */
    fun finish() = withLink("Switching off Wireless debugging…") { l ->
        val off = l.finish()
        drop()
        update { copy(wirelessOn = wirelessOn(), message = if (off) null else "Wireless debugging may still be on. Turn it off in Developer options.") }
    }

    /** Best effort when the app closes for good: never leave Wireless debugging on. */
    fun finishQuietly() { link?.let { l -> background { runCatching { l.finish() } } } }

    /** Forgets the session, e.g. after the pairing was forgotten. */
    fun disconnect() { link?.let { l -> background { runCatching { l.close() } } }; drop() }

    fun dismiss() = update { copy(message = null) }

    private fun drop() { link = null; update { copy(connected = false, results = null, scan = null) } }

    private fun withLink(label: String, work: (Link) -> Unit) { val l = link ?: return; run(label) { work(l) } }

    private fun run(label: String, work: () -> Unit) {
        if (state.busy != null) return
        update { copy(busy = label, message = null) }
        background {
            try { work() } catch (e: IOException) {
                if (!lostConnection(e)) update { copy(message = explain(e)) }
                else {
                    link?.let { runCatching { it.close() } }; drop()
                    update { copy(wirelessOn = wirelessOn(), message = "The connection to this phone was lost. If Wireless debugging went off, turn it on again, then tap Retry.") }
                }
            } catch (e: Exception) { update { copy(message = explain(e)) } } finally { update { copy(busy = null) } }
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
}

/** adbd dropped us, e.g. Wireless debugging went off when Wi-Fi changed or the phone slept; other IOExceptions are refused commands. */
internal fun lostConnection(e: Throwable) = e is SocketException || e is EOFException || e is SSLException

/** "Kiosk", "E-reader": the name the screens use. */
val Profile.label get() = name.lowercase().replace('_', '-').replaceFirstChar { it.uppercase() }
