package io.github.sarat03.monomode.tls

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetAddress
import kotlin.test.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import monomode.adb.AdbConnection
import monomode.adb.ShellResult

/**
 * Runs a shell command on this device's real adbd with the key paired by RealAdbdPairingTest.
 * Skipped unless the Wireless debugging connect port is passed as -e connectPort.
 */
@RunWith(AndroidJUnit4::class)
class RealAdbdShellTest {
    @Test
    fun echoesThroughRealAdbd() {
        val port = InstrumentationRegistry.getArguments().getString("connectPort")?.toInt()
        assumeTrue("no connect port", port != null)

        val out = TlsAdbTransport(InetAddress.getLoopbackAddress(), port!!, AdbKey.getOrCreate()).use {
            AdbConnection(it).run { connect(); shell("echo hi") }
        }
        assertEquals("hi\n", out)
    }

    @Test
    fun shellV2SplitsStreamsAndExitCodeThroughRealAdbd() {
        val port = InstrumentationRegistry.getArguments().getString("connectPort")?.toInt()
        assumeTrue("no connect port", port != null)

        val result = TlsAdbTransport(InetAddress.getLoopbackAddress(), port!!, AdbKey.getOrCreate()).use {
            AdbConnection(it).run { connect(); shellV2("echo out; echo err >&2; exit 3") }
        }
        assertEquals(ShellResult("out\n", "err\n", 3), result)
    }
}
