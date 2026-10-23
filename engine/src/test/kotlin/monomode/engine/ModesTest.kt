package monomode.engine

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ModesTest {
    class MapStore : Modes.Store {
        val sets = mutableMapOf<String, Set<String>>()
        val strings = mutableMapOf<String, String>()
        override fun strings(key: String) = sets[key].orEmpty()
        override fun putStrings(key: String, value: Set<String>) { sets[key] = value }
        override fun string(key: String) = strings[key]
        override fun putString(key: String, value: String) { strings[key] = value }
    }

    private val start = mapOf(
        "global zen_mode" to "0", "global low_power" to "0", "global window_animation_scale" to "1.0",
        "global transition_animation_scale" to "1.0", "global stay_on_while_plugged_in" to "1",
        "home activity" to "com.oppo.launcher/.Launcher",
    )
    private val settings = ProfileTest.FakeSettings(start)
    private val am = mutableListOf<List<String>>()
    /** settings commands go to the fake; am (pinning) is only recorded, in order with the settings writes. */
    private val sh = Shell { a -> if (a[0] == "am") { am += a; settings.calls += a; "" } else settings.run(a) }
    private val store = MapStore()
    private val modes = Modes(Files.createTempDirectory("pw").toFile(), store)

    @Test fun `nothing is on at first`() = assertNull(modes.active)

    @Test fun `turning on skips the options switched off and marks the mode active`() {
        modes.setOff(Profile.E_READER, setOf("Battery saver"))
        modes.turnOn(sh, Profile.E_READER)
        assertEquals(Profile.E_READER, modes.active)
        assertEquals("0", settings.values["global low_power"])
        assertEquals("1", settings.values["secure accessibility_display_daltonizer_enabled"])
    }

    @Test fun `active survives a new Modes over the same folder, like an app restart`() {
        val dir = Files.createTempDirectory("pw").toFile()
        Modes(dir, store).turnOn(sh, Profile.KIOSK)
        assertEquals(Profile.KIOSK, Modes(dir, store).active)
    }

    @Test fun `kiosk unpins before putting settings back`() {
        modes.turnOn(sh, Profile.KIOSK)
        val before = settings.calls.size
        modes.turnOff(sh)
        assertNull(modes.active)
        assertEquals(start, settings.values)
        assertEquals(listOf("am", "task", "lock", "stop"), settings.calls[before]) // a pinned app would block the restored launcher
    }

    @Test fun `a second mode cannot go on over the first`() {
        modes.turnOn(sh, Profile.KIOSK)
        assertFailsWith<IllegalStateException> { modes.turnOn(sh, Profile.E_READER) }
    }

    @Test fun `reader is the chosen app, else the first known reader, never another app`() {
        assertEquals("org.readera", modes.reader(listOf("com.google.android.calendar", "org.koreader.launcher", "org.readera")))
        assertNull(modes.reader(listOf("com.google.android.calendar")))
        modes.setReader("org.koreader.launcher")
        assertEquals("org.koreader.launcher", modes.reader(listOf("org.koreader.launcher", "org.readera")))
        assertEquals("org.readera", modes.reader(listOf("org.readera"))) // chosen one uninstalled
    }

    @Test fun `per-mode choices are kept apart`() {
        modes.setApps(Profile.KIOSK, setOf("a.b"))
        modes.setOff(Profile.KIOSK, setOf("Mute notifications"))
        assertEquals(setOf("a.b"), modes.apps(Profile.KIOSK))
        assertEquals(emptySet(), modes.apps(Profile.E_READER))
        assertEquals(emptySet(), modes.off(Profile.E_READER))
    }
}
