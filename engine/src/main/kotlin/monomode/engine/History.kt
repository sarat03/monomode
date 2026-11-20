package monomode.engine

enum class Kind { DISABLED, REMOVED, MODE }

/** One change MonoMode holds: an app (id = package) or the mode that is on (id = profile name, [settings] it changed). [at] is null for a record from before dates. */
data class HistoryItem(val kind: Kind, val id: String, val at: Long?, val settings: Int = 0)

/** Everything MonoMode currently holds, from the cleaner's and the modes' records. Restoring one item is Debloat.restore or Modes.turnOff. */
class History(private val debloat: Debloat, private val modes: Modes) {
    /** Newest first; undated last. */
    fun items(): List<HistoryItem> {
        val at = debloat.since
        val apps = debloat.removed.map { (pkg, m) -> HistoryItem(if (m == Method.REMOVE) Kind.REMOVED else Kind.DISABLED, pkg, at[pkg]) }
        val mode = modes.active?.let { HistoryItem(Kind.MODE, it.name, modes.activeSince, modes.activeSettings) }
        return (apps + listOfNotNull(mode)).sortedWith(compareByDescending<HistoryItem> { it.at ?: Long.MIN_VALUE }.thenBy { it.id })
    }

    /** Restores every item; one that fails keeps its record and is returned, the rest still go back. A lost connection ends it. [shell] is only asked for when an app is restored. */
    fun revertAll(shell: () -> Shell, s: PhoneSettings, unpin: () -> Unit): List<HistoryItem> {
        val failed = mutableListOf<HistoryItem>()
        for (item in items()) try {
            if (item.kind == Kind.MODE) modes.turnOff(s, unpin) else debloat.restore(shell(), setOf(item.id))
        } catch (e: Exception) {
            if (lostConnection(e)) throw e
            failed += item
        }
        return failed
    }
}
