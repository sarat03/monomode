package monomode.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class TapTest {
    @Test fun `sides - the left third goes back, the right third forward, the middle opens the menu`() {
        assertEquals(Tap.BACK, tapZone(0.1f, 0.5f, "sides"))
        assertEquals(Tap.MENU, tapZone(0.5f, 0.5f, "sides"))
        assertEquals(Tap.FORWARD, tapZone(0.9f, 0.9f, "sides"))
    }

    @Test fun `swapped mirrors the sides, for the left hand`() {
        assertEquals(Tap.FORWARD, tapZone(0.1f, 0.5f, "swapped"))
        assertEquals(Tap.BACK, tapZone(0.9f, 0.5f, "swapped"))
        assertEquals(Tap.MENU, tapZone(0.5f, 0.5f, "swapped"))
    }

    @Test fun `forward - both sides go forward, so either thumb turns the page`() {
        assertEquals(Tap.FORWARD, tapZone(0.1f, 0.5f, "forward"))
        assertEquals(Tap.FORWARD, tapZone(0.9f, 0.5f, "forward"))
        assertEquals(Tap.MENU, tapZone(0.5f, 0.5f, "forward"))
    }

    @Test fun `the top right corner bookmarks the page in every layout`() {
        for (layout in listOf("sides", "swapped", "forward")) assertEquals(Tap.MARK, tapZone(0.95f, 0.05f, layout))
        assertEquals(Tap.FORWARD, tapZone(0.95f, 0.2f, "sides")) // below the corner
    }
}
