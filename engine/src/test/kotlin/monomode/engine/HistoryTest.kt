package monomode.engine

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryTest {
    private var time = 1000L
    private var failEnable = false
    private val pkgs = object : EngineTest.FakeShell(enabled = listOf("com.bloat.a", "com.bloat.b", "com.launcher", "com.kbd"), system = setOf("com.bloat.a", "com.bloat.b")) {
        override fun run(args: List<String>) =
            if (failEnable && args.take(3) == listOf("cmd", "package", "install-existing") && args.last() == "com.bloat.b") throw java.io.IOException("refused") else super.run(args)
    }
    private val start = mapOf("global zen_mode" to "0", "global low_power" to "0", "global window_animation_scale" to "1.0",
        "global transition_animation_scale" to "1.0", "global stay_on_while_plugged_in" to "1", "home activity" to "com.oppo.launcher/.Launcher")
    private val phone = ProfileTest.FakeSettings(start)
    private var settingsFail = false
    private val settings = ShellSettings { a -> if (settingsFail && a[1] == "put") throw java.io.IOException("refused") else phone.run(a) }
    private val dir = Files.createTempDirectory("pw").toFile()
    private val debloat = Debloat(File(dir, "debloat.txt")) { time }
    private val modes = Modes(dir, ModesTest.MapStore()) { time }
    private val history = History(debloat, modes)

    private fun everything() {
        debloat.remove(pkgs, listOf("com.bloat.a"), Method.DISABLE); time = 2000
        debloat.remove(pkgs, listOf("com.bloat.b"), Method.REMOVE); time = 3000
        modes.turnOn(settings, Profile.E_READER)
    }

    @Test fun `lists every held change newest first and follows restore`() {
        everything()
        assertEquals(listOf(
            HistoryItem(Kind.MODE, "E_READER", 3000, Profile.E_READER.changes.size),
            HistoryItem(Kind.REMOVED, "com.bloat.b", 2000),
            HistoryItem(Kind.DISABLED, "com.bloat.a", 1000),
        ), history.items())
        debloat.restore(pkgs, setOf("com.bloat.b"))
        modes.turnOff(settings) {}
        assertEquals(listOf(HistoryItem(Kind.DISABLED, "com.bloat.a", 1000)), history.items())
    }

    @Test fun `a record from before dates shows no date and sorts last`() {
        debloat.record.writeText("com.bloat.a\n")
        time = 5000
        debloat.remove(pkgs, listOf("com.bloat.b"), Method.DISABLE)
        assertEquals(listOf(HistoryItem(Kind.DISABLED, "com.bloat.b", 5000), HistoryItem(Kind.DISABLED, "com.bloat.a", null)), history.items())
        assertNull(history.items().last().at)
    }

    @Test fun `revert all undoes the cleaner and the mode`() {
        everything()
        assertEquals(emptyList(), history.revertAll({ pkgs }, settings) {})
        assertEquals(emptyList(), history.items())
        assertEquals(setOf("com.bloat.a", "com.bloat.b"), pkgs.enabled.filter { it.startsWith("com.bloat") }.toSet())
        assertEquals(start, phone.values)
    }

    @Test fun `an item that fails keeps its record and the rest still go back`() {
        everything()
        failEnable = true
        assertEquals(listOf(HistoryItem(Kind.REMOVED, "com.bloat.b", 2000)), history.revertAll({ pkgs }, settings) {})
        assertEquals(listOf(HistoryItem(Kind.REMOVED, "com.bloat.b", 2000)), history.items())
        assertEquals(start, phone.values)
    }

    @Test fun `a mode whose settings are refused stays held`() {
        everything()
        settingsFail = true
        assertEquals(listOf("E_READER"), history.revertAll({ pkgs }, settings) {}.map { it.id })
        assertEquals(Profile.E_READER, modes.active) // record kept, Turn off can try again
        assertEquals(listOf("E_READER"), history.items().map { it.id })
    }
}
