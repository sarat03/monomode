package io.github.sarat03.phonewarden.tls

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sarat03.phonewarden.asShell
import java.io.File
import java.net.InetAddress
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import phonewarden.adb.AdbConnection
import phonewarden.engine.Profile
import phonewarden.engine.Shell
import phonewarden.engine.applyProfile
import phonewarden.engine.restoreProfile

/** Release gate for profiles on a real device. Skipped unless -e connectPort is passed. */
@RunWith(AndroidJUnit4::class)
class RealAdbdProfileTest {
    private fun snapshot(sh: Shell) = listOf("global", "secure").associateWith { sh.run(listOf("settings", "list", it)) }

    private fun roundTrip(profile: Profile) {
        val port = InstrumentationRegistry.getArguments().getString("connectPort")?.toInt()
        assumeTrue("no connect port", port != null)
        val record = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "profile.txt").apply { delete() }

        TlsAdbTransport(InetAddress.getLoopbackAddress(), port!!, AdbKey.getOrCreate()).use {
            val sh = AdbConnection(it).apply { connect() }.asShell()
            val before = snapshot(sh)
            applyProfile(sh, profile, record)
            assertNotEquals(before, snapshot(sh))
            restoreProfile(sh, record)
            val after = snapshot(sh)
            for (ns in before.keys) {
                val b = before.getValue(ns).lines().toSet(); val a = after.getValue(ns).lines().toSet()
                assertEquals(emptySet(), (b - a) + (a - b), "$ns: before ${b - a}, after ${a - b}")
            }
        }
    }

    @Test fun kioskThenRestore() = roundTrip(Profile.KIOSK)
    @Test fun eReaderThenRestore() = roundTrip(Profile.E_READER)
}
