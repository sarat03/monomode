package monomode.engine

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
    open class FakeShell(enabled: List<String>, disabled: List<String> = emptyList(), var homeOk: Boolean = true, val system: Set<String> = setOf("com.vendor.locked")) : Shell {
        val enabled = enabled.toMutableSet()
        val disabled = disabled.toMutableSet()
        val removed = mutableSetOf<String>()
        val calls = mutableListOf<List<String>>()

        override fun run(args: List<String>): String {
            calls += args
            if (args.take(2) == listOf("pm", "disable-user") && args.last() == "com.vendor.locked")
                throw java.io.IOException("pm exited 255: java.lang.IllegalArgumentException: Cannot disable a protected package")
            return when {
                args.take(3) == listOf("pm", "list", "packages") ->
                    (if ("-d" in args) disabled else if ("-s" in args) system else enabled + disabled).sorted().joinToString("") { "package:$it\n" }
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
                args.take(2) == listOf("pm", "uninstall") -> { enabled -= args.last(); removed += args.last(); "Success" }
                args.take(3) == listOf("cmd", "package", "install-existing") -> { removed -= args.last(); enabled += args.last(); "Package ${args.last()} installed for user: 0" }
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
    @Test fun `apply writes record then restore matches original`() {
        val sh = FakeShell(listOf("com.a.b", "com.c.d", "com.keep.me")); val r = rec()
        val before = sh.enabled.toSet()
        apply(sh, plan(sh, listOf(Entry("com.a.b", Risk.SAFE), Entry("com.c.d", Risk.SAFE))), r)
        assertEquals(listOf("com.a.b", "com.c.d"), recorded(r).keys.toList())
        restore(sh, r)
        assertEquals(before, sh.enabled) // release gate
    }

    @Test fun `record merges across runs`() {
        val sh = FakeShell(listOf("com.a.b", "com.c.d")); val r = rec()
        apply(sh, plan(sh, listOf(Entry("com.a.b", Risk.SAFE))), r)
        apply(sh, plan(sh, listOf(Entry("com.c.d", Risk.SAFE))), r)
        assertEquals(listOf("com.a.b", "com.c.d"), recorded(r).keys.toList())
    }

    @Test fun `auto rollback when home breaks`() {
        val sh = FakeShell(listOf("com.breaks.home", "com.a.b")); val r = rec()
        assertFailsWith<HealthException> {
            apply(sh, plan(sh, listOf(Entry("com.a.b", Risk.SAFE), Entry("com.breaks.home", Risk.SAFE))), r, batch = 1)
        }
        assertTrue("com.breaks.home" in sh.enabled) // bad batch rolled back
        assertTrue("com.a.b" in sh.disabled)        // good batch kept and recorded
        assertEquals(listOf("com.a.b"), recorded(r).keys.toList())
    }

    @Test fun `only allowlisted commands reach the shell`() {
        val sh = FakeShell(listOf("com.a.b")); val r = rec()
        apply(sh, plan(sh, listOf(Entry("com.a.b", Risk.SAFE))), r)
        restore(sh, r)
        for (c in sh.calls) assertTrue(c[0] in setOf("pm", "cmd", "settings"), "$c")
    }

    @Test fun `restore skips tampered record lines`() {
        val sh = FakeShell(emptyList(), listOf("com.a.b")); val r = rec()
        r.writeText("com.a.b\ncom.x; reboot\n")
        restore(sh, r)
        assertEquals(listOf(listOf("pm", "enable", "com.a.b")), sh.calls)
    }

    @Test fun `a command failing mid-batch rolls the batch back`() {
        val sh = FakeShell(listOf("com.a.b", "com.vendor.locked")); val r = rec()
        assertFailsWith<java.io.IOException> {
            apply(sh, plan(sh, listOf(Entry("com.a.b", Risk.SAFE), Entry("com.vendor.locked", Risk.SAFE))), r)
        }
        assertTrue("com.a.b" in sh.enabled)
        assertFalse(r.exists())
    }

    @Test fun `remove for this user is recorded and restored with install-existing`() {
        val sh = FakeShell(listOf("com.vendor.locked", "com.keep.me")); val r = rec()
        val before = sh.enabled.toSet()
        apply(sh, plan(sh, listOf(Entry("com.vendor.locked", Risk.CAUTION)), maxRisk = Risk.CAUTION), r, method = Method.REMOVE)
        assertTrue(listOf("pm", "uninstall", "-k", "--user", "0", "com.vendor.locked") in sh.calls)
        assertEquals(listOf("com.vendor.locked removed"), r.readLines())
        restore(sh, r)
        assertTrue(listOf("cmd", "package", "install-existing", "com.vendor.locked") in sh.calls)
        assertEquals(before, sh.enabled) // release gate
    }

    // command line for adbd, which runs it through sh
    @Test fun `commandLine joins plain args`() =
        assertEquals("pm disable-user --user 0 com.a.b", commandLine(listOf("pm", "disable-user", "--user", "0", "com.a.b")))

    @Test fun `commandLine refuses anything sh would interpret`() {
        for (bad in listOf("a b", "a;b", "\$(id)", "`id`", "a|b", "a>b", "a&b", "'a'", "\"a\"", "a\nb", "*", "~", "")) assertFailsWith<IllegalArgumentException>(bad) { commandLine(listOf("echo", bad)) }
    }

    @Test fun `other apps are the enabled ones not in the list and not protected`() {
        val sh = Shell { args ->
            check(args == listOf("pm", "list", "packages", "-e")) { "unexpected $args" }
            "package:com.android.systemui\npackage:com.listed.app\npackage:com.vendor.extra\npackage:com.chat.app\npackage:io.me\npackage:android.frameworkres.overlay.Network\npackage:com.android.theme.icon.circle\npackage:com.android.internal.systemui.navbar.threebutton\n" // last: navigation bar style, seen on Realme XT
        }
        assertEquals(listOf("com.chat.app", "com.vendor.extra"), otherApps(sh, listed = setOf("com.listed.app"), self = "io.me"))
    }

    @Test fun `remove for this user refuses apps that are not built in, before changing anything`() {
        // seen on Realme XT: a Play Store app's apk is deleted, so install-existing leaves a broken entry
        val sh = FakeShell(listOf("com.vendor.locked", "com.play.calculator")); val r = rec()
        val e = assertFailsWith<IllegalArgumentException> {
            apply(sh, Plan(todo = listOf("com.vendor.locked", "com.play.calculator")), r, method = Method.REMOVE)
        }
        assertTrue("com.play.calculator" in e.message.orEmpty())
        assertTrue(sh.calls.none { it.take(2) == listOf("pm", "uninstall") })
        assertFalse(r.exists())
    }

    @Test fun `restore only the picked apps and keep the rest in the record`() {
        val sh = FakeShell(listOf("com.a.b", "com.c.d", "com.vendor.locked")); val r = rec()
        apply(sh, plan(sh, listOf(Entry("com.a.b", Risk.SAFE), Entry("com.c.d", Risk.SAFE))), r)
        apply(sh, Plan(todo = listOf("com.vendor.locked")), r, method = Method.REMOVE)
        assertEquals(mapOf("com.a.b" to Method.DISABLE, "com.c.d" to Method.DISABLE, "com.vendor.locked" to Method.REMOVE), recorded(r))
        restore(sh, r, only = setOf("com.c.d", "com.vendor.locked"))
        assertTrue("com.c.d" in sh.enabled && "com.vendor.locked" in sh.enabled)
        assertTrue("com.a.b" in sh.disabled)
        assertEquals(mapOf("com.a.b" to Method.DISABLE), recorded(r))
    }

    @Test fun `apps changed before the connection drops stay on the Restore list`() {
        val sh = object : FakeShell(listOf("com.a.b", "com.c.d", "com.e.f")) {
            var cmds = 0
            // two apps are disabled, then every command fails, the undo included
            override fun run(args: List<String>): String = if (++cmds > 2) throw java.net.SocketException("connection lost") else super.run(args)
        }
        val r = rec()
        assertFailsWith<java.net.SocketException> { apply(sh, plan(FakeShell(listOf("com.a.b", "com.c.d", "com.e.f")), listOf(Entry("com.a.b", Risk.SAFE), Entry("com.c.d", Risk.SAFE), Entry("com.e.f", Risk.SAFE))), r) }
        assertEquals(setOf("com.a.b", "com.c.d"), sh.disabled)
        assertEquals(listOf("com.a.b", "com.c.d"), recorded(r).keys.toList())
    }
}
