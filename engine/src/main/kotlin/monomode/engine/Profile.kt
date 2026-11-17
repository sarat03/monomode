package monomode.engine

import java.io.File

/** One setting a profile changes. ns is a Settings namespace, or DND for Do Not Disturb. */
data class Change(val ns: String, val key: String, val value: String)

/** The [Change.ns] of Do Not Disturb, whose values are set_dnd's: off, priority, on, alarms. */
const val DND = "dnd"
// priority, not total silence: on Android 14 "on" sets the ringer to silent and "off" does not undo it
private const val DND_ON = "priority"

/** global zen_mode values (Settings.Global.ZEN_MODE_*) as set_dnd arguments. */
private val ZEN_TO_DND = mapOf("0" to "off", "1" to "priority", "2" to "on", "3" to "alarms")

private const val HOME = "home"
/** MonoMode's own home screen. No mode sets it any more; restore still puts back the launcher an older version replaced. */
const val MODE_HOME = "io.github.sarat03.monomode/.HomeActivity"

private val grayscale = listOf(
    Change("secure", "accessibility_display_daltonizer_enabled", "1"),
    Change("secure", "accessibility_display_daltonizer", "0"), // 0 = monochromacy
)
private val noAnimations = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale").map { Change("global", it, "0") }
private val muted = Change(DND, "mode", DND_ON)
// low_power alone leaves low_power_sticky=1 behind after restore, so Battery Saver would come back on its own
// realme/Oppo's saver cuts the screen timeout to 30s (power_save_screenoff_time_state); keep the user's own while reading.
// Set before low_power so the saver starts with it off. Absent on other phones: restore deletes it again.
private val batterySaver = listOf(
    Change("system", "power_save_screenoff_time_state", "0"),
    Change("global", "low_power", "1"), Change("global", "low_power_sticky", "1"),
)

/**
 * Each mode is a set of named options the user can switch off one by one.
 * Debloat and the KOReader recommendation are offered by the app next to these; they are not settings.
 *
 * [whenOff]: what an option sets when the user switches it off, for settings whose Android default is
 * already "on" (leaving them alone would not turn them off). Other options are simply left alone.
 * [always]: what the mode cannot work without, so it is not an option.
 */
enum class Profile(val options: Map<String, List<Change>>, val whenOff: Map<String, List<Change>> = emptyMap(), val always: List<Change> = emptyList()) {
    // one app pinned to the screen, a real kiosk: no home screen of its own, the pinned app is all there is
    KIOSK(mapOf(
        "Screen stays on while charging" to listOf(Change("global", "stay_on_while_plugged_in", "7")), // AC | USB | wireless
        "Mute notifications" to listOf(muted),
        // Android asks for the PIN by default when the phone has a screen lock
        "Ask for PIN to unpin" to listOf(Change("secure", "lock_to_app_exit_locked", "1")),
    ), whenOff = mapOf("Ask for PIN to unpin" to listOf(Change("secure", "lock_to_app_exit_locked", "0"))),
        always = listOf(Change("secure", "lock_to_app_enabled", "1"))), // screen pinning, which the kiosk app is held by
    E_READER(mapOf(
        "Grayscale screen" to grayscale,
        "No animations" to noAnimations,
        "Mute notifications" to listOf(muted),
        "Battery saver" to batterySaver,
    ));

    val changes get() = always + options.values.flatten()

    /** True when [option] is only Do Not Disturb, which needs Android's Do Not Disturb access instead of the one-time setup. */
    fun mutes(option: String) = options[option].orEmpty().all { it.ns == DND }
}

/**
 * Where a mode's settings are read and written. Adapters: [ShellSettings] over Wireless debugging, the app's own
 * (Android's APIs, where it may), and a map in tests. A partial adapter says so per change, and [orElse] falls back.
 */
interface PhoneSettings {
    /** The value now, in the form [write] takes back ("null" when unset); null when this adapter cannot read it here. */
    fun read(c: Change): String?
    /** Sets [c] to [value]; false when this adapter cannot write it here. Throws when the phone refuses it. */
    fun write(c: Change, value: String): Boolean
}

/** This adapter where it can, else [then]'s, which is only made when first needed (it may switch Wireless debugging on). */
fun PhoneSettings.orElse(then: () -> PhoneSettings): PhoneSettings = object : PhoneSettings {
    override fun read(c: Change) = this@orElse.read(c) ?: then().read(c)
    override fun write(c: Change, value: String) = this@orElse.write(c, value) || then().write(c, value)
}

/** Settings over a shell (adb): `settings`, `cmd notification set_dnd`, `cmd package set-home-activity`. Can do every change. */
class ShellSettings(private val sh: Shell) : PhoneSettings {
    override fun read(c: Change): String =
        // shortcut: assumes a default launcher is set; with none, this returns the chooser, which set-home-activity rejects
        if (c.ns == HOME) sh.run(listOf("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")).trim().lines().last()
        else if (c.ns == DND) ZEN_TO_DND[sh.run(listOf("settings", "get", "global", "zen_mode")).trim()] ?: "off"
        else sh.run(listOf("settings", "get", c.ns, c.key)).trim()

    override fun write(c: Change, value: String): Boolean {
        when {
            // no default home before (the chooser was read): Android cannot set the chooser back, so leave it
            // shortcut: the mode's home screen stays default on such phones, clear it in Settings if that shows up
            c.ns == HOME -> if (!value.endsWith("ResolverActivity")) sh.run(listOf("cmd", "package", "set-home-activity", value))
            c.ns == DND -> sh.run(listOf("cmd", "notification", "set_dnd", value)) // writing zen_mode directly is ignored
            // was unset before; Android 11 exits 1 when the key is already gone, which is the state we want
            value == "null" -> try { sh.run(listOf("settings", "delete", c.ns, c.key)) } catch (e: Exception) { if (lostConnection(e)) throw e }
            else -> sh.run(listOf("settings", "put", c.ns, c.key, value))
        }
        return true
    }
}

private fun PhoneSettings.get(c: Change) = read(c) ?: throw IllegalStateException("cannot read ${c.ns} ${c.key}")
private fun PhoneSettings.set(c: Change, value: String) = check(write(c, value)) { "cannot write ${c.ns} ${c.key}" }

/**
 * Options named in [off] are skipped. Saves every previous value to [record] first, then applies. Refuses while a profile is
 * already applied, because saving again would overwrite the original values with the profile's.
 */
fun applyProfile(s: PhoneSettings, profile: Profile, record: File, off: Set<String> = emptySet()) {
    check(!record.exists()) { "a profile is already applied; restore it first" }
    val chosen = profile.always + profile.options.filterKeys { it !in off }.values.flatten() + profile.whenOff.filterKeys { it in off }.values.flatten()
    // a value with a line break would forge extra lines in the record: leave that setting alone, neither changed nor restored
    val previous = chosen.map { it to s.get(it) }.filter { (_, v) -> '\n' !in v && '\r' !in v }
    record.parentFile?.mkdirs()
    val tmp = File(record.path + ".tmp")
    tmp.writeSynced(previous.joinToString("") { (c, v) -> "${c.ns} ${c.key} $v\n" })
    check(tmp.renameTo(record)) { "could not write $record" }
    val done = mutableListOf<Pair<Change, String>>()
    try {
        for ((c, before) in previous) { s.set(c, c.value); done += c to before }
    } catch (e: Exception) {
        // undo in reverse, best effort, so a refused write never leaves the phone half changed
        val undone = done.asReversed().all { (c, before) -> runCatching { s.set(c, before) }.isSuccess }
        // an undo that failed (connection lost) keeps the record, so the mode stays on and Turn off can still put everything back
        if (undone) record.delete()
        throw e
    }
}

/**
 * Puts back every value from [record] for a setting [profile] changes, then deletes it. Lines for any other setting are
 * skipped, so an edited record cannot write settings the mode never touched; the launcher an older version replaced still is.
 */
fun restoreProfile(s: PhoneSettings, profile: Profile, record: File) {
    if (!record.exists()) return
    val own = (profile.changes + profile.whenOff.values.flatten()).map { it.ns to it.key }.toSet() + (HOME to "activity")
    for (line in record.readLines().filter { it.isNotBlank() }) {
        val (ns, key, value) = line.split(" ", limit = 3).takeIf { it.size == 3 } ?: continue
        if (ns to key in own) s.set(Change(ns, key, value), value)
    }
    record.delete()
}
