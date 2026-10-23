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
    /**
     * The profile's own keys plus the side effects seen on real devices (ringer, battery saver sticky).
     * Not the whole settings list: a freshly booted emulator keeps changing unrelated keys on its own.
     */
    private fun snapshot(sh: Shell, profile: Profile): Map<String, String> =
        (profile.changes.filter { it.ns != "dnd" }.map { it.ns to it.key } +
            listOf("global" to "zen_mode", "global" to "mode_ringer", "global" to "low_power", "global" to "low_power_sticky"))
            .associate { (ns, key) -> "$ns $key" to sh.run(listOf("settings", "get", ns, key)).trim() }

    private fun roundTrip(profile: Profile) {
        val port = InstrumentationRegistry.getArguments().getString("connectPort")?.toInt()
        assumeTrue("no connect port", port != null)
        val record = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "profile.txt").apply { delete() }

        TlsAdbTransport(InetAddress.getLoopbackAddress(), port!!, AdbKey.getOrCreate()).use {
            val sh = AdbConnection(it).apply { connect() }.asShell()
            val before = snapshot(sh, profile)
            applyProfile(sh, profile, record)
            assertNotEquals(before, snapshot(sh, profile))
            restoreProfile(sh, record)
            assertEquals(before, snapshot(sh, profile))
        }
    }

    @Test fun kioskThenRestore() = roundTrip(Profile.KIOSK)
    @Test fun eReaderThenRestore() = roundTrip(Profile.E_READER)
}
