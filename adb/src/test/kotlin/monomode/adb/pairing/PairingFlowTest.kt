package monomode.adb.pairing

import java.io.ByteArrayOutputStream
import java.io.EOFException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Pairing flow against a scripted adbd. The password is code + TLS-exported key; the fake splits the
 * BoringSSL vector password 4f5a0146 into code "OZ" (4f5a) and exported key 0146, so every expected
 * value comes from tools/spake2-vectors.
 */
class PairingFlowTest {
    /** Plays back adbd's bytes in order and records everything we write. */
    class FakeAdbd(private val exported: ByteArray, vararg replies: ByteArray) : PairingTransport {
        private val incoming = replies.fold(ByteArray(0)) { a, b -> a + b }
        private var pos = 0
        val sent = ByteArrayOutputStream()

        override fun exportedKey() = exported
        override fun write(bytes: ByteArray) = sent.write(bytes)
        override fun readFully(n: Int): ByteArray {
            if (pos + n > incoming.size) throw EOFException()
            return incoming.copyOfRange(pos, pos + n).also { pos += n }
        }
    }

    private val ourInfo = PeerInfo(PeerInfo.Type.ADB_RSA_PUB_KEY, "QAAAAkey monomode".toByteArray())
    private val aliceKey = "489c3ec20630727e868f336be234133524754e0a3e053202e44ec32851d1ae2a".unhex()

    @Test
    fun `first sends a SPAKE2 packet built from code plus exported key`() {
        val adbd = FakeAdbd("0146".unhex())
        assertFailsWith<EOFException> { PairingFlow(adbd, "OZ", ourInfo, aliceKey).run() }
        assertEquals("010000000020" + ALICE_MSG, adbd.sent.toByteArray().hex())
    }

    @Test
    fun `swaps encrypted peer info and returns adbd's`() {
        val adbdInfo = PeerInfo(PeerInfo.Type.ADB_DEVICE_GUID, "adb-RMX1921-abc".toByteArray())
        val adbd = FakeAdbd("0146".unhex(), BOB_PACKET, peerInfoPacket(adbdInfo))

        val got = PairingFlow(adbd, "OZ", ourInfo, aliceKey).run()

        assertEquals(PeerInfo.Type.ADB_DEVICE_GUID, got.type)
        assertEquals("adb-RMX1921-abc", String(got.data))
        val sent = adbd.sent.toByteArray()
        val ourPacket = sent.copyOfRange(6 + 32, sent.size)
        assertEquals("01010000" + "2010", ourPacket.copyOf(6).hex()) // PEER_INFO, 8192 + 16 tag
        val opened = PairingCipher(AES_KEY).open(ourPacket.copyOfRange(6, ourPacket.size))!!
        assertEquals(String(ourInfo.data), String(PeerInfo.decode(opened).data))
    }

    @Test
    fun `a wrong code fails with WrongCodeException`() {
        val adbd = FakeAdbd("0146".unhex(), BOB_PACKET, peerInfoPacket(PeerInfo(PeerInfo.Type.ADB_DEVICE_GUID, "x".toByteArray())))
        assertFailsWith<WrongCodeException> { PairingFlow(adbd, "OY", ourInfo, aliceKey).run() }
    }

    @Test
    fun `a packet of the wrong type fails before any key is used`() {
        val swapped = BOB_PACKET.copyOf().also { it[1] = 1 } // PEER_INFO where SPAKE2_MSG belongs
        val adbd = FakeAdbd("0146".unhex(), swapped)
        assertFailsWith<monomode.adb.ProtocolException> { PairingFlow(adbd, "OZ", ourInfo, aliceKey).run() }
    }

    companion object {
        const val ALICE_MSG = "c01e4c90b706ad5f506db898e6b21ed8e94adc95abb6cae6ba989279644b5714"
        val BOB_PACKET = ("010000000020" + "a3b2cbad399eb0cbf1be3345f88fcc530a483ea5e6e6983191656c76961ef3d1").unhex()
        val AES_KEY = "d7fbf98a9da9e4153e082b87cf8572be".unhex()

        fun peerInfoPacket(info: PeerInfo): ByteArray {
            val sealed = PairingCipher(AES_KEY).seal(info.encode())
            return PacketHeader(PacketType.PEER_INFO, sealed.size).encode() + sealed
        }
    }
}
