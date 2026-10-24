package io.github.sarat03.monomode.tls

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sarat03.monomode.asShell
import java.io.File
import java.net.InetAddress
import kotlin.test.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import monomode.adb.AdbConnection
import monomode.engine.Entry
import monomode.engine.Method
import monomode.engine.Risk
import monomode.engine.Shell
import monomode.engine.apply
import monomode.engine.plan
import monomode.engine.restore

/**
 * Release gate on a real device: apply then restore returns the package list to where it was.
 * Uses the Android easter egg app. Skipped unless -e connectPort is passed.
 */
@RunWith(AndroidJUnit4::class)
class RealAdbdDebloatTest {
    private val pkg = "com.android.egg"

    private fun state(sh: Shell) = sh.run(listOf("pm", "list", "packages", "-d")) to sh.run(listOf("pm", "list", "packages"))

    private fun roundTrip(method: Method) {
        val port = InstrumentationRegistry.getArguments().getString("connectPort")?.toInt()
        assumeTrue("no connect port", port != null)
        val record = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "record-$method.txt").apply { delete() }

        TlsAdbTransport(InetAddress.getLoopbackAddress(), port!!, AdbKey.getOrCreate()).use {
            val sh = AdbConnection(it).apply { connect() }.asShell()
            val before = state(sh)
            val p = plan(sh, listOf(Entry(pkg, Risk.CAUTION)), maxRisk = Risk.CAUTION)
            assertEquals(listOf(pkg), p.todo)
            apply(sh, p, record, method = method)
            restore(sh, record)
            assertEquals(before, state(sh))
        }
    }

    @Test fun disableThenRestore() = roundTrip(Method.DISABLE)
    @Test fun removeForUserThenRestore() = roundTrip(Method.REMOVE)
}
