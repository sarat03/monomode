package io.github.sarat03.phonewarden

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** A stock emulator has no device admins, no user CAs and no VPN; this proves the reads work without extra permissions. */
@RunWith(AndroidJUnit4::class)
class DeviceChecksTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun noDeviceAdmins() = assertEquals(emptyList(), DeviceChecks.deviceAdmins(ctx))
    @Test fun noUserCertificates() = assertEquals(emptyList(), DeviceChecks.userCertificates())
    @Test fun noVpn() = assertFalse(DeviceChecks.vpnActive(ctx))
}
