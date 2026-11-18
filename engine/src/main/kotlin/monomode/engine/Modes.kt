package monomode.engine

import java.io.File

/** MonoMode's own reader, named by MonoMode's package; the reading app until the user chooses another. */
val BUILT_IN_READER = MODE_HOME.substringBefore('/')

/**
 * Everything about the modes: which one is on, what the user chose for each, and turning them on
 * and off. The mode that is on is the one with a restore record in [dir], so it survives restarts.
 */
class Modes(private val dir: File, private val store: Store) {
    /** Where the per-mode choices live: SharedPreferences on the phone, a map in tests. */
    interface Store {
        fun strings(key: String): Set<String>
        fun putStrings(key: String, value: Set<String>)
        fun string(key: String): String?
        fun putString(key: String, value: String)
    }

    private fun record(p: Profile) = File(dir, "mode-${p.name}.txt")

    val active: Profile? get() = Profile.entries.firstOrNull { record(it).exists() }

    /** Modes the user installed from mode options; MonoMode starts as a launcher alone. The mode that is on always counts, so an update never hides it. */
    val installed: Set<Profile> get() = Profile.entries.filter { it.name in store.strings("installed") || it == active }.toSet()
    fun install(p: Profile) = store.putStrings("installed", store.strings("installed") + p.name)
    /** Hides the mode again; its choices are kept for a later install. */
    fun uninstall(p: Profile) {
        check(active != p) { "turn ${p.name.lowercase()} mode off first" }
        store.putStrings("installed", store.strings("installed") - p.name)
    }

    /** Options the user switched off; they can change only while the mode is off, as the record holds what was applied. */
    fun off(p: Profile) = store.strings("off.${p.name}")
    fun setOff(p: Profile, options: Set<String>) = store.putStrings("off.${p.name}", options)

    /** Packages shown on the mode's home screen. */
    fun apps(p: Profile) = store.strings("apps.${p.name}")
    fun setApps(p: Profile, pkgs: Set<String>) = store.putStrings("apps.${p.name}", pkgs)

    /** The e-reader's Continue reading app: the chosen one while it is still [installed], else MonoMode's own reader. */
    fun reader(installed: Collection<String>): String =
        store.string("reader")?.takeIf { it in installed } ?: BUILT_IN_READER
    fun setReader(pkg: String) = store.putString("reader", pkg)

    fun turnOn(s: PhoneSettings, p: Profile) {
        check(active == null) { "turn off ${active!!.name.lowercase()} mode first" }
        applyProfile(s, p, record(p), off(p))
    }

    /** Puts back every setting of the mode that is on. Kiosk runs [unpin] first, or the pinned app would hide the restored home screen. */
    fun turnOff(s: PhoneSettings, unpin: () -> Unit) {
        val p = active ?: return
        if (p == Profile.KIOSK) unpin()
        restoreProfile(s, p, record(p))
    }
}
