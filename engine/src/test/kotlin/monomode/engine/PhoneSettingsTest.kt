package monomode.engine

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The settings port: a partial adapter (the app) falling back to a full one (Wireless debugging), as Phone uses them. */
class PhoneSettingsTest {
    private val start = mapOf(
        "global zen_mode" to "0", "global low_power" to "0", "global low_power_sticky" to "0",
        "secure accessibility_display_daltonizer_enabled" to "0", "secure accessibility_display_daltonizer" to "-1",
        "global window_animation_scale" to "1.0", "global transition_animation_scale" to "1.0", "global animator_duration_scale" to "1.0",
        "home activity" to "com.oppo.launcher/.Launcher",
    )
    private val phone = ProfileTest.FakeSettings(start)

    /** Writes only the keys in [can] itself, reads everything: a phone that refuses some keys to apps. */
    private inner class App(private val can: Set<String>) : PhoneSettings {
        val wrote = mutableListOf<String>()
        private val inner = ShellSettings(phone)
        override fun read(c: Change) = inner.read(c)
        override fun write(c: Change, value: String) = (c.key in can && inner.write(c, value)).also { if (it) wrote += c.key }
    }

    private fun rec() = File(Files.createTempDirectory("ps").toFile(), "mode.txt")

    @Test fun `what the app can write never opens Wireless debugging`() {
        var opened = false
        val app = App(can = Profile.E_READER.changes.map { it.key }.toSet())
        val r = rec()
        applyProfile(app.orElse { opened = true; ShellSettings(phone) }, Profile.E_READER, r)
        restoreProfile(app.orElse { opened = true; ShellSettings(phone) }, Profile.E_READER, r)
        assertFalse(opened)
        assertEquals(start, phone.values)
    }

    @Test fun `a key the app is refused goes over Wireless debugging, the rest stays in the app`() {
        var opened = 0
        val app = App(can = setOf("accessibility_display_daltonizer_enabled", "accessibility_display_daltonizer"))
        applyProfile(app.orElse { opened++; ShellSettings(phone) }, Profile.E_READER, rec(), off = setOf("No animations", "Mute notifications", "Battery saver"))
        assertEquals(listOf("accessibility_display_daltonizer_enabled", "accessibility_display_daltonizer"), app.wrote)
        assertEquals(0, opened) // grayscale alone: all in the app
        applyProfile(app.orElse { opened++; ShellSettings(phone) }, Profile.KIOSK, rec())
        assertEquals("1", phone.values["secure lock_to_app_enabled"]) // refused to the app, written over Wireless debugging
        assertTrue(opened > 0)
    }

    @Test fun `the shell adapter speaks Do Not Disturb and the home screen as the phone's own commands`() {
        val s = ShellSettings(phone)
        assertEquals("off", s.read(Change(DND, "mode", "")))
        s.write(Change(DND, "mode", ""), "priority")
        assertEquals("1", phone.values["global zen_mode"])
        assertEquals("null", s.read(Change("secure", "never_set", "")))
    }
}
