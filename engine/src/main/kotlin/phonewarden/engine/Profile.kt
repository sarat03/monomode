package phonewarden.engine

import java.io.File

/** One setting a profile changes. ns is a Settings namespace, or DND for Do Not Disturb. */
data class Change(val ns: String, val key: String, val value: String)

private const val DND = "dnd"
// priority, not total silence: on Android 14 "on" sets the ringer to silent and "off" does not undo it
private const val DND_ON = "priority"

/** global zen_mode values (Settings.Global.ZEN_MODE_*) as set_dnd arguments. */
private val ZEN_TO_DND = mapOf("0" to "off", "1" to "priority", "2" to "on", "3" to "alarms")

private val grayscale = listOf(
    Change("secure", "accessibility_display_daltonizer_enabled", "1"),
    Change("secure", "accessibility_display_daltonizer", "0"), // 0 = monochromacy
)
private val noAnimations = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale").map { Change("global", it, "0") }
private val muted = Change(DND, "mode", DND_ON)
// low_power alone leaves low_power_sticky=1 behind after restore, so Battery Saver would come back on its own
private val batterySaver = listOf(Change("global", "low_power", "1"), Change("global", "low_power_sticky", "1"))

/**
 * Each mode is a set of named options the user can switch off one by one.
 * Debloat and the KOReader recommendation are offered by the app next to these; they are not settings.
 */
enum class Profile(val options: Map<String, List<Change>>) {
    KIOSK(mapOf(
        "Allow pinning an app" to listOf(Change("secure", "lock_to_app_enabled", "1")), // the user pins the app
        "Screen stays on while charging" to listOf(Change("global", "stay_on_while_plugged_in", "7")), // AC | USB | wireless
        "Mute notifications" to listOf(muted),
    )),
    E_READER(mapOf(
        "Grayscale screen" to grayscale,
        "No animations" to noAnimations,
        "Mute notifications" to listOf(muted),
        "Battery saver" to batterySaver,
    ));

    val changes get() = options.values.flatten()
}

private fun Shell.read(c: Change): String =
    if (c.ns == DND) ZEN_TO_DND[run(listOf("settings", "get", "global", "zen_mode")).trim()] ?: "off"
    else run(listOf("settings", "get", c.ns, c.key)).trim()

private fun Shell.write(c: Change, value: String) {
    when {
        c.ns == DND -> run(listOf("cmd", "notification", "set_dnd", value)) // writing zen_mode directly is ignored
        value == "null" -> run(listOf("settings", "delete", c.ns, c.key)) // was unset before
        else -> run(listOf("settings", "put", c.ns, c.key, value))
    }
}

/**
 * Options named in [off] are skipped. Saves every previous value to [record] first, then applies. Refuses while a profile is
 * already applied, because saving again would overwrite the original values with the profile's.
 */
fun applyProfile(sh: Shell, profile: Profile, record: File, off: Set<String> = emptySet()) {
    check(!record.exists()) { "a profile is already applied; restore it first" }
    val previous = profile.options.filterKeys { it !in off }.values.flatten().map { it to sh.read(it) }
    record.parentFile?.mkdirs()
    val tmp = File(record.path + ".tmp")
    tmp.writeText(previous.joinToString("") { (c, v) -> "${c.ns} ${c.key} $v\n" })
    check(tmp.renameTo(record)) { "could not write $record" }
    val done = mutableListOf<Pair<Change, String>>()
    try {
        for ((c, before) in previous) { sh.write(c, c.value); done += c to before }
    } catch (e: Exception) {
        // undo in reverse, best effort, so a refused write never leaves the phone half changed
        for ((c, before) in done.asReversed()) runCatching { sh.write(c, before) }
        record.delete()
        throw e
    }
}

/** Puts back every value from [record], then deletes it. */
fun restoreProfile(sh: Shell, record: File) {
    if (!record.exists()) return
    for (line in record.readLines().filter { it.isNotBlank() }) {
        val (ns, key, value) = line.split(" ", limit = 3)
        sh.write(Change(ns, key, value), value)
    }
    record.delete()
}
