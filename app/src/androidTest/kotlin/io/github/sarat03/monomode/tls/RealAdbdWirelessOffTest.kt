package io.github.sarat03.monomode.tls

import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.ConnectException
import java.net.InetAddress
import java.net.Socket
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import monomode.adb.AdbConnection

/**
 * Switches Wireless debugging off through our own connection, then verifies from the app side.
 * Skipped unless the connect port is passed as -e connectPort. Leaves Wireless debugging off.
 */
@RunWith(AndroidJUnit4::class)
class RealAdbdWirelessOffTest {
    @Test
    fun switchesOffAndPortCloses() {
        val port = InstrumentationRegistry.getArguments().getString("connectPort")?.toInt()
        assumeTrue("no connect port", port != null)

        TlsAdbTransport(InetAddress.getLoopbackAddress(), port!!, AdbKey.getOrCreate()).use {
            AdbConnection(it).run { connect(); disableWirelessDebugging() }
        }

        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        assertEquals(0, Settings.Global.getInt(resolver, "adb_wifi_enabled", -1))
        Thread.sleep(1000) // adbd closes the TLS listener asynchronously
        assertFailsWith<ConnectException> { Socket(InetAddress.getLoopbackAddress(), port).close() }
    }
}
