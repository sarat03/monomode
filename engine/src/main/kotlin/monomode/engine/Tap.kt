package monomode.engine

/** What a tap on the page does. */
enum class Tap { BACK, FORWARD, MENU, MARK }

/**
 * The action for a tap at ([x], [y]), each 0..1 of the page. [layout]: "sides" (left back, right forward),
 * "swapped" (the mirror, for the left hand) or "forward" (both sides forward; a swipe goes back).
 */
fun tapZone(x: Float, y: Float, layout: String): Tap = when {
    x > 0.85f && y < 0.12f -> Tap.MARK // the top corner, as a page is dog-eared
    x in 0.3f..0.7f -> Tap.MENU
    layout == "forward" -> Tap.FORWARD
    (x < 0.3f) == (layout == "swapped") -> Tap.FORWARD
    else -> Tap.BACK
}
