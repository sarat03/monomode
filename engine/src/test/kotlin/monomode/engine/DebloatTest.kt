package monomode.engine

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DebloatTest {
    private val sh = EngineTest.FakeShell(enabled = listOf("com.bloat.a", "com.bloat.b", "com.user.app", "io.me", "com.launcher", "com.kbd"), system = setOf("com.bloat.a", "com.bloat.b"))
    private val debloat = Debloat(File(Files.createTempDirectory("pw").toFile(), "debloat.txt"))
    private val list = listOf(Entry("com.bloat.a", Risk.SAFE), Entry("com.bloat.b", Risk.RISKY))

    @Test fun `scan plans the list, names built-in apps and lists every other app but ourselves`() {
        val s = debloat.scan(sh, list, self = "io.me")
        assertEquals(listOf("com.bloat.a", "com.bloat.b"), s.plan.todo) // risky still offered, the owner decides
        assertEquals(setOf("com.bloat.a", "com.bloat.b"), s.system)
        assertTrue("com.user.app" in s.others && "io.me" !in s.others && "com.bloat.a" !in s.others)
    }

    @Test fun `removed apps are listed until restored, one at a time`() {
        debloat.remove(sh, listOf("com.bloat.a", "com.bloat.b"), Method.DISABLE)
        assertEquals(mapOf("com.bloat.a" to Method.DISABLE, "com.bloat.b" to Method.DISABLE), debloat.removed)
        debloat.restore(sh, setOf("com.bloat.a"))
        assertEquals(setOf("com.bloat.b"), debloat.removed.keys)
        assertTrue("com.bloat.a" in sh.enabled)
    }
}
