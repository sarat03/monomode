package monomode.engine

/** What MonoMode needs from the user before it can do something in a mode itself. */
enum class Need {
    /** Pairing Wireless debugging once, which also grants MonoMode its permission to change settings. */
    SETUP_ONCE,
    /** Android's Do Not Disturb access, which Mute notifications uses instead of the setup. */
    DND_ACCESS,
}

/** What the user has allowed so far. */
data class Allowed(val setUp: Boolean, val dnd: Boolean)

/** The need still open for [option], shown under it; null once nothing stands in its way. */
fun Profile.need(option: String, allowed: Allowed): Need? =
    if (mutes(option)) Need.DND_ACCESS.takeUnless { allowed.dnd } else Need.SETUP_ONCE.takeUnless { allowed.setUp }

/**
 * What turning the mode on asks for first, with the options in [off] left out; null: it can go ahead.
 * The setup comes first when anything chosen needs it, since Wireless debugging can then mute too; Kiosk always
 * needs it, for pinning. Once set up, Mute still asks for Do Not Disturb access, so it never needs Wireless debugging.
 */
fun Profile.firstAsk(off: Set<String>, allowed: Allowed): Need? {
    val chosen = options.keys - off
    return when {
        !allowed.setUp && (this == Profile.KIOSK || chosen.any { !mutes(it) }) -> Need.SETUP_ONCE
        !allowed.dnd && chosen.any { mutes(it) } -> Need.DND_ACCESS
        else -> null
    }
}
