package monomode.engine

import java.io.File

/** Known reading apps, best first; used for Continue reading when the user has not chosen one. */
private val READERS = listOf(
    "org.readera", "org.readera.premium", "org.koreader.launcher", "com.flyersoft.moonreader", "com.flyersoft.moonreaderp",
    "com.foobnix.pdf.reader", "com.amazon.kindle", "com.kobobooks.android", "com.google.android.apps.books",
)

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

    /** Options the user switched off; they can change only while the mode is off, as the record holds what was applied. */
    fun off(p: Profile) = store.strings("off.${p.name}")
    fun setOff(p: Profile, options: Set<String>) = store.putStrings("off.${p.name}", options)

    /** Packages shown on the mode's home screen. */
    fun apps(p: Profile) = store.strings("apps.${p.name}")
    fun setApps(p: Profile, pkgs: Set<String>) = store.putStrings("apps.${p.name}", pkgs)

    /** The e-reader's Continue reading app: the chosen one, else the first known reader in [installed], else null. */
    fun reader(installed: Collection<String>): String? =
        store.string("reader")?.takeIf { it in installed } ?: READERS.firstOrNull { it in installed }
    fun setReader(pkg: String) = store.putString("reader", pkg)

    fun turnOn(sh: Shell, p: Profile) {
        check(active == null) { "turn off ${active!!.name.lowercase()} mode first" }
        applyProfile(sh, p, record(p), off(p))
    }

    /** Puts back every setting of the mode that is on. Kiosk unpins first, or the pinned app would hide the restored home screen. */
    fun turnOff(sh: Shell) {
        val p = active ?: return
        if (p == Profile.KIOSK) unpin(sh)
        restoreProfile(sh, record(p))
    }
}
