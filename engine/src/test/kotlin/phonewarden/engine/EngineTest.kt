package phonewarden.engine

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ported from test_core.py; same fake device, same expectations. */
class EngineTest {
    /** Records every argv; simulates package state like a real device. */
    open class FakeShell(enabled: List<String>, disabled: List<String> = emptyList(), var homeOk: Boolean = true) : Shell {
        val enabled = enabled.toMutableSet()
        val disabled = disabled.toMutableSet()
        val calls = mutableListOf<List<String>>()

        override fun run(args: List<String>): String {
            calls += args
            return when {
                args.take(3) == listOf("pm", "list", "packages") ->
                    (if ("-d" in args) disabled else enabled + disabled).sorted().joinToString("") { "package:$it\n" }
                args.take(2) == listOf("pm", "disable-user") -> {
                    enabled -= args.last(); disabled += args.last()
                    if (args.last() == "com.breaks.home") homeOk = false
                    "Package ${args.last()} new state: disabled-user"
                }
                args.take(2) == listOf("pm", "enable") -> {
                    disabled -= args.last(); enabled += args.last()
                    if (args.last() == "com.breaks.home") homeOk = true
                    "Package ${args.last()} new state: enabled"
                }
                args.take(3) == listOf("cmd", "package", "resolve-activity") -> if (homeOk) "com.launcher/.Home" else "No activity found"
                args.take(3) == listOf("settings", "get", "secure") -> "com.kbd/.Ime"
                else -> throw AssertionError("unexpected command $args")
            }
        }
    }

    private fun rec() = File(Files.createTempDirectory("pw").toFile(), "record.txt")

    // parse_list
    @Test fun `comments, blanks and risk`() =
        assertEquals(listOf(Entry("com.a.b", Risk.SAFE), Entry("com.c.d", Risk.CAUTION)), parseList("# header\n\ncom.a.b  # safe\ncom.c.d|caution\n"))

    @Test fun `rejects shell injection`() {
        for (bad in listOf("com.a; reboot", "\$(id)", "com.a b", "-f", "com..a", "a")) assertFailsWith<IllegalArgumentException>(bad) { parseList(bad) }
    }

    @Test fun `rejects unknown risk`() { assertFailsWith<IllegalArgumentException> { parseList("com.a.b|yolo") } }

    // plan
    @Test fun `protected packages refused`() {
        val p = plan(FakeShell(listOf("com.android.systemui", "com.bloat.x")), listOf(Entry("com.android.systemui", Risk.SAFE), Entry("com.bloat.x", Risk.SAFE)))
        assertEquals(listOf("com.bloat.x"), p.todo)
        assertEquals(listOf("com.android.systemui"), p.refused)
    }

    @Test fun `override allows protected`() =
        assertEquals(listOf("com.android.systemui"), plan(FakeShell(listOf("com.android.systemui")), listOf(Entry("com.android.systemui", Risk.SAFE)), allowProtected = setOf("com.android.systemui")).todo)

    @Test fun `skips missing and already disabled`() {
        val p = plan(FakeShell(listOf("com.a.b"), listOf("com.c.d")), listOf(Entry("com.a.b", Risk.SAFE), Entry("com.c.d", Risk.SAFE), Entry("com.not.here", Risk.SAFE)))
        assertEquals(listOf("com.a.b"), p.todo)
        assertEquals(listOf("com.c.d"), p.already)
        assertEquals(listOf("com.not.here"), p.missing)
    }

    @Test fun `risky needs flag`() {
        val sh = FakeShell(listOf("com.a.b"))
        assertEquals(emptyList(), plan(sh, listOf(Entry("com.a.b", Risk.RISKY))).todo)
        assertEquals(listOf("com.a.b"), plan(sh, listOf(Entry("com.a.b", Risk.RISKY)), maxRisk = Risk.RISKY).todo)
    }

    // apply / restore
    @Test fun `dry run touches nothing`() {
        val sh = FakeShell(listOf("com.a.b")); val r = rec()
        apply(sh, plan(sh, listOf(Entry("com.a.b", Risk.SAFE))), r, dryRun = true)
        assertFalse("com.a.b" in sh.disabled)
        assertFalse(r.exists())
    }

    @Test fun `apply writes record then restore matches original`() {
        val sh = FakeShell(listOf("com.a.b", "com.c.d", "com.keep.me")); val r = rec()
        val before = sh.enabled.toSet()
        apply(sh, plan(sh, listOf(Entry("com.a.b", Risk.SAFE), Entry("com.c.d", Risk.SAFE))), r, dryRun = false)
        assertEquals(listOf("com.a.b", "com.c.d"), readRecord(r))
        restore(sh, r)
        assertEquals(before, sh.enabled) // release gate
    }

    @Test fun `record merges across runs`() {
        val sh = FakeShell(listOf("com.a.b", "com.c.d")); val r = rec()
        apply(sh, plan(sh, listOf(Entry("com.a.b", Risk.SAFE))), r, dryRun = false)
        apply(sh, plan(sh, listOf(Entry("com.c.d", Risk.SAFE))), r, dryRun = false)
        assertEquals(listOf("com.a.b", "com.c.d"), readRecord(r))
    }

    @Test fun `auto rollback when home breaks`() {
        val sh = FakeShell(listOf("com.breaks.home", "com.a.b")); val r = rec()
        assertFailsWith<HealthException> {
            apply(sh, plan(sh, listOf(Entry("com.a.b", Risk.SAFE), Entry("com.breaks.home", Risk.SAFE))), r, dryRun = false, batch = 1)
        }
        assertTrue("com.breaks.home" in sh.enabled) // bad batch rolled back
        assertTrue("com.a.b" in sh.disabled)        // good batch kept and recorded
        assertEquals(listOf("com.a.b"), readRecord(r))
    }

    @Test fun `only allowlisted commands reach the shell`() {
        val sh = FakeShell(listOf("com.a.b")); val r = rec()
        apply(sh, plan(sh, listOf(Entry("com.a.b", Risk.SAFE))), r, dryRun = false)
        restore(sh, r)
        for (c in sh.calls) assertTrue(c[0] in setOf("pm", "cmd", "settings"), "$c")
    }

    @Test fun `restore skips tampered record lines`() {
        val sh = FakeShell(emptyList(), listOf("com.a.b")); val r = rec()
        r.writeText("com.a.b\ncom.x; reboot\n")
        restore(sh, r)
        assertEquals(listOf(listOf("pm", "enable", "com.a.b")), sh.calls)
    }

    // command line for adbd, which runs it through sh
    @Test fun `commandLine joins plain args`() =
        assertEquals("pm disable-user --user 0 com.a.b", commandLine(listOf("pm", "disable-user", "--user", "0", "com.a.b")))

    @Test fun `commandLine refuses anything sh would interpret`() {
        for (bad in listOf("a b", "a;b", "\$(id)", "`id`", "a|b", "a>b", "a&b", "'a'", "\"a\"", "a\nb", "*", "~", "")) assertFailsWith<IllegalArgumentException>(bad) { commandLine(listOf("echo", bad)) }
    }

    // drift
    @Test fun `reports re-enabled after update`() {
        val sh = FakeShell(listOf("com.a.b", "com.c.d")); val r = rec()
        apply(sh, plan(sh, listOf(Entry("com.a.b", Risk.SAFE), Entry("com.c.d", Risk.SAFE))), r, dryRun = false)
        sh.disabled -= "com.c.d"; sh.enabled += "com.c.d" // OTA re-enabled it
        assertEquals(listOf("com.c.d"), drift(sh, r))
    }
}
