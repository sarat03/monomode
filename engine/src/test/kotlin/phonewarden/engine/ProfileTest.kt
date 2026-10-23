package phonewarden.engine

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProfileTest {
    /** settings get/put/delete plus cmd notification set_dnd, which mirrors into global zen_mode like the real service. */
    class FakeSettings(initial: Map<String, String>, private val denied: Set<String> = emptySet()) : Shell {
        val values = initial.toMutableMap()
        val calls = mutableListOf<List<String>>()
        override fun run(args: List<String>): String {
            calls += args
            return when {
                args.take(2) == listOf("settings", "get") -> (values["${args[2]} ${args[3]}"] ?: "null") + "\n"
                args.take(2) == listOf("settings", "put") -> {
                    if (args[3] in denied) throw java.io.IOException("settings exited 255: java.lang.SecurityException: Permission denial: writing to settings requires:android.permission.WRITE_SECURE_SETTINGS")
                    values["${args[2]} ${args[3]}"] = args[4]; ""
                }
                args.take(2) == listOf("settings", "delete") -> { values.remove("${args[2]} ${args[3]}"); "Deleted 1 rows\n" }
                args.take(3) == listOf("cmd", "notification", "set_dnd") -> {
                    values["global zen_mode"] = mapOf("off" to "0", "priority" to "1", "on" to "2", "alarms" to "3").getValue(args[3]); ""
                }
                else -> throw AssertionError("unexpected command $args")
            }
        }
    }

    private val start = mapOf(
        "global zen_mode" to "0", "global low_power" to "0", "global window_animation_scale" to "1.0",
        "global transition_animation_scale" to "1.0", "global stay_on_while_plugged_in" to "1",
    )
    private fun rec() = File(Files.createTempDirectory("pw").toFile(), "profile.txt")

    @Test fun `e-reader applies then restore returns every value`() {
        val sh = FakeSettings(start); val r = rec()
        applyProfile(sh, Profile.E_READER, r)
        assertEquals("1", sh.values["secure accessibility_display_daltonizer_enabled"])
        assertEquals("0", sh.values["secure accessibility_display_daltonizer"]) // monochromacy
        assertEquals("0", sh.values["global window_animation_scale"])
        assertEquals("1", sh.values["global low_power"])
        assertEquals("1", sh.values["global low_power_sticky"]) // restored explicitly; turning low_power off leaves it set
        assertEquals("1", sh.values["global zen_mode"]) // priority: total silence leaves the ringer silent after restore (seen on Android 14)
        restoreProfile(sh, r)
        assertEquals(start, sh.values) // release gate; unset ones deleted again
    }

    @Test fun `kiosk keeps the screen on while charging and enables pinning`() {
        val sh = FakeSettings(start); val r = rec()
        applyProfile(sh, Profile.KIOSK, r)
        assertEquals("7", sh.values["global stay_on_while_plugged_in"])
        assertEquals("1", sh.values["secure lock_to_app_enabled"])
        assertEquals("1", sh.values["global zen_mode"]) // priority: total silence leaves the ringer silent after restore (seen on Android 14)
        restoreProfile(sh, r)
        assertEquals(start, sh.values)
    }

    @Test fun `DND goes through the notification service, not the raw setting`() {
        val sh = FakeSettings(start + ("global zen_mode" to "3")); val r = rec()
        applyProfile(sh, Profile.KIOSK, r)
        restoreProfile(sh, r)
        assertTrue(listOf("cmd", "notification", "set_dnd", "alarms") in sh.calls)
        assertTrue(sh.calls.none { it.take(4) == listOf("settings", "put", "global", "zen_mode") })
    }

    @Test fun `a second profile on top refuses, so the original values are never lost`() {
        val sh = FakeSettings(start); val r = rec()
        applyProfile(sh, Profile.KIOSK, r)
        assertFailsWith<IllegalStateException> { applyProfile(sh, Profile.E_READER, r) }
    }

    @Test fun `a mode that fails partway undoes what it changed and keeps no record`() {
        // seen on realme UI: shell settings writes are refused unless "Disable permission monitoring" is on
        val sh = FakeSettings(start, denied = setOf("window_animation_scale")); val r = rec()
        val e = assertFailsWith<java.io.IOException> { applyProfile(sh, Profile.E_READER, r) }
        assertTrue("WRITE_SECURE_SETTINGS" in e.message.orEmpty())
        assertEquals(start, sh.values)
        assertTrue(!r.exists())
    }
}
