package monomode.engine

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** Each command reaches the phone over adb, so one operation must not ask the phone the same thing twice. */
class CallAuditTest {
    private fun repeats(calls: List<List<String>>) = calls.groupingBy { it }.eachCount().filterValues { it > 1 }

    private fun phone() = object : EngineTest.FakeShell(
        enabled = listOf("com.bloat.a", "com.bloat.b", "com.bloat.c", "com.bloat.d", "com.bloat.e", "com.bloat.f", "com.user.app"),
        system = setOf("com.bloat.a", "com.bloat.b", "com.bloat.c", "com.bloat.d", "com.bloat.e", "com.bloat.f"),
    ) {
        override fun run(args: List<String>): String = when (args[0]) {
            "cat" -> { calls += args; "  sl  local_address rem_address   st\n" }
            "settings" -> if (args[1] == "get" && args[3] != "default_input_method") { calls += args; "null" } else super.run(args)
            else -> super.run(args)
        }
    }

    @Test fun `check asks each thing once`() {
        val sh = phone()
        network(sh); access(sh)
        assertEquals(emptyMap(), repeats(sh.calls))
    }

    @Test fun `debloat scan asks each thing once`() {
        val sh = phone()
        Debloat(File(Files.createTempDirectory("pw").toFile(), "r.txt")).scan(sh, listOf(Entry("com.bloat.a", Risk.SAFE)), "io.me")
        assertEquals(emptyMap(), repeats(sh.calls))
    }

    @Test fun `removing two batches asks only what each batch needs`() {
        val sh = phone()
        val pkgs = listOf("com.bloat.a", "com.bloat.b", "com.bloat.c", "com.bloat.d", "com.bloat.e", "com.bloat.f")
        Debloat(File(Files.createTempDirectory("pw").toFile(), "r.txt")).remove(sh, pkgs, Method.REMOVE)
        // each batch changes the phone, so its verification read and health check (home, dialer, keyboard) rerun; nothing else may
        val perBatch = { c: List<String> -> c == listOf("pm", "list", "packages") || c.take(3) == listOf("cmd", "package", "resolve-activity") || c.take(3) == listOf("settings", "get", "secure") }
        assertEquals(emptyMap(), repeats(sh.calls).filterKeys { !perBatch(it) })
    }
}
