package io.github.sarat03.monomode.tls

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sarat03.monomode.asShell
import java.net.InetAddress
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import monomode.adb.AdbConnection
import monomode.engine.network

/** Runs the exposure engine on this device's real /proc/net. Skipped unless -e connectPort is passed. */
@RunWith(AndroidJUnit4::class)
class RealAdbdExposureTest {
    @Test
    fun seesItsOwnAdbListenerAsExposed() {
        val port = InstrumentationRegistry.getArguments().getString("connectPort")?.toInt()
        assumeTrue("no connect port", port != null)

        val report = TlsAdbTransport(InetAddress.getLoopbackAddress(), port!!, AdbKey.getOrCreate()).use {
            network(AdbConnection(it).apply { connect() }.asShell())
        }
        val adbd = report.listening.firstOrNull { it.port == port }
        assertTrue(adbd != null, "TLS port $port not in ${report.listening.map { it.local }}")
        // CI starts the listener under `adb root`, so adbd is uid 0 there; on phones it is shell (2000)
        if (adbd.uid == 2000) assertTrue(report.adbExposed)
    }
}
