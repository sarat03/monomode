package io.github.sarat03.phonewarden.tls

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sarat03.phonewarden.asShell
import java.net.InetAddress
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import phonewarden.adb.AdbConnection
import phonewarden.engine.network

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
        assertTrue(report.listening.any { it.port == port }, "TLS port $port not in ${report.listening.map { it.local }}")
        assertTrue(report.adbExposed)
    }
}
