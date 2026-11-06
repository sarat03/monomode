package io.github.sarat03.monomode.tls

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sarat03.monomode.Session
import io.github.sarat03.monomode.asShell
import java.net.InetAddress
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import monomode.adb.AdbConnection
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MonoMode grants itself the Wireless debugging switch over its own connection, and Forget takes it back.
 * Skipped unless the Wireless debugging connect port is passed as -e connectPort.
 */
@RunWith(AndroidJUnit4::class)
class RealAdbdGrantTest {
    @Test
    fun grantsAndRevokesTheSwitchThroughRealAdbd() {
        val port = InstrumentationRegistry.getArguments().getString("connectPort")?.toInt()
        assumeTrue("no connect port", port != null)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext

        TlsAdbTransport(InetAddress.getLoopbackAddress(), port!!, AdbKey.getOrCreate()).use { t ->
            val shell = AdbConnection(t).apply { connect() }.asShell()
            shell.run(Session.revokeSwitch(ctx))
            assertFalse(Session.canSwitch(ctx))
            Session.grantSwitch(ctx, shell)
            assertTrue(Session.canSwitch(ctx), "pm grant over our own connection did not give the switch")
            shell.run(Session.revokeSwitch(ctx))
            assertFalse(Session.canSwitch(ctx))
        }
    }
}
