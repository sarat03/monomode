package monomode.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NeedsTest {
    private val nothing = Allowed(setUp = false, dnd = false)
    private val all = Allowed(setUp = true, dnd = true)

    @Test fun `before anything is allowed, Mute needs Do Not Disturb access and the rest the one-time setup`() {
        assertEquals(Need.DND_ACCESS, Profile.E_READER.need("Mute notifications", nothing))
        assertEquals(Need.SETUP_ONCE, Profile.E_READER.need("Grayscale screen", nothing))
        assertEquals(Need.SETUP_ONCE, Profile.KIOSK.need("Ask for PIN to unpin", nothing))
    }

    @Test fun `each need goes once it is allowed`() {
        assertNull(Profile.E_READER.need("Mute notifications", Allowed(setUp = false, dnd = true)))
        assertNull(Profile.E_READER.need("Grayscale screen", Allowed(setUp = true, dnd = false)))
        Profile.entries.forEach { p -> p.options.keys.forEach { assertNull(p.need(it, all)) } }
    }

    @Test fun `turn on asks for the setup first when anything chosen needs it, as it covers Mute too`() {
        assertEquals(Need.SETUP_ONCE, Profile.E_READER.firstAsk(off = emptySet(), nothing))
    }

    @Test fun `with only Mute chosen, turn on asks for Do Not Disturb access, not the setup`() {
        val onlyMute = Profile.E_READER.options.keys - "Mute notifications"
        assertEquals(Need.DND_ACCESS, Profile.E_READER.firstAsk(onlyMute, nothing))
        assertNull(Profile.E_READER.firstAsk(onlyMute, Allowed(setUp = false, dnd = true)))
    }

    @Test fun `set up but without Do Not Disturb access, Mute still asks for it rather than use Wireless debugging`() {
        assertEquals(Need.DND_ACCESS, Profile.E_READER.firstAsk(emptySet(), Allowed(setUp = true, dnd = false)))
    }

    @Test fun `kiosk always needs the setup, for pinning, even with every option off`() {
        assertEquals(Need.SETUP_ONCE, Profile.KIOSK.firstAsk(Profile.KIOSK.options.keys, Allowed(setUp = false, dnd = true)))
        assertNull(Profile.KIOSK.firstAsk(Profile.KIOSK.options.keys, Allowed(setUp = true, dnd = false)))
    }

    @Test fun `with nothing chosen and nothing allowed an e-reader can still turn on`() =
        assertNull(Profile.E_READER.firstAsk(Profile.E_READER.options.keys, nothing))
}
