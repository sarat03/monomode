package io.github.sarat03.phonewarden.tls

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetAddress
import java.security.interfaces.RSAPublicKey
import kotlin.test.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import phonewarden.adb.AdbPublicKey
import phonewarden.adb.pairing.PairingFlow
import phonewarden.adb.pairing.PeerInfo

/**
 * Pairs with this device's real adbd. Needs a live pairing dialog, so it only runs when
 * tools/pair-emulator.sh passes the code and port; otherwise it is skipped.
 */
@RunWith(AndroidJUnit4::class)
class RealAdbdPairingTest {
    @Test
    fun pairsWithRealAdbd() {
        val args = InstrumentationRegistry.getArguments()
        val code = args.getString("pairCode")
        val port = args.getString("pairPort")?.toInt()
        assumeTrue("no live pairing dialog", code != null && port != null)

        val key = AdbKey.getOrCreate()
        val pub = key.certificate.publicKey as RSAPublicKey
        val ours = PeerInfo(PeerInfo.Type.ADB_RSA_PUB_KEY, AdbPublicKey.encode(pub.modulus, pub.publicExponent, "phonewarden@test").toByteArray())

        val theirs = TlsPairingTransport.connect(InetAddress.getLoopbackAddress(), port!!, key).use { PairingFlow(it, code!!, ours).run() }
        assertEquals(PeerInfo.Type.ADB_DEVICE_GUID, theirs.type)
    }
}
