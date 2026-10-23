package io.github.sarat03.monomode.tls

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.EOFException
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.Future
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.conscrypt.Conscrypt
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import monomode.adb.pairing.PacketHeader
import monomode.adb.pairing.PacketType
import monomode.adb.pairing.PairingCipher
import monomode.adb.pairing.PairingFlow
import monomode.adb.pairing.PeerInfo
import monomode.adb.pairing.Spake2

/**
 * Runs a TLS 1.3 server in-process standing in for adbd's pairing port. It uses the same
 * exporter label adbd passes to BoringSSL: "adb-label" including its NUL (sizeof in C).
 */
@RunWith(AndroidJUnit4::class)
class TlsPairingTransportTest {
    private val pool = Executors.newSingleThreadExecutor()
    private val server: SSLServerSocket = TestTls.server()

    @After fun close() {
        server.close()
        pool.shutdownNow()
    }

    private fun <T> serve(block: (SSLSocket) -> T): Future<T> = pool.submit<T> {
        (server.accept() as SSLSocket).use { it.startHandshake(); block(it) }
    }

    /** Connects; if the handshake fails, reports the server side's error too, which is usually the real cause. */
    private fun connect(adbd: Future<*>): TlsPairingTransport = try {
        TlsPairingTransport.connect(InetAddress.getLoopbackAddress(), server.localPort, TestTls.clientKey())
    } catch (e: Exception) {
        val serverError = runCatching { adbd.get(5, java.util.concurrent.TimeUnit.SECONDS) }.exceptionOrNull()
        throw AssertionError("client failed; server said: $serverError", e)
    }

    @Test
    fun exportedKeyMatchesWhatAdbdWouldDerive() {
        val adbd = serve { Conscrypt.exportKeyingMaterial(it, "adb-label\u0000", null, 64) }
        connect(adbd).use { t ->
            assertContentEquals(adbd.get(), t.exportedKey())
        }
    }

    @Test
    fun bytesFlowBothWaysAndShortReadsFail() {
        val adbd = serve { s ->
            val got = ByteArray(6).also { java.io.DataInputStream(s.inputStream).readFully(it) } // readNBytes needs API 33
            s.outputStream.write(byteArrayOf(9, 8, 7)) // then the server closes the socket
            s.outputStream.flush()
            got
        }
        connect(adbd).use { t ->
            t.write(byteArrayOf(1, 0, 0, 0, 0, 32))
            assertContentEquals(byteArrayOf(9, 8, 7), t.readFully(3))
            assertContentEquals(byteArrayOf(1, 0, 0, 0, 0, 32), adbd.get())
            assertFailsWith<EOFException> { t.readFully(1) }
        }
    }

    @Test
    fun adbdSeesOurKeystoreCertificate() {
        val adbd = serve { it.session.peerCertificates[0] }
        connect(adbd).use { it.exportedKey() }
        assertEquals(TestTls.clientKey().certificate, adbd.get())
    }

    @Test
    fun fullPairingFlowOverRealTls() {
        val code = "123456"
        val adbdInfo = PeerInfo(PeerInfo.Type.ADB_DEVICE_GUID, "adb-emulator".toByteArray())
        val adbd = serve { s ->
            // adbd's side, built from the BoringSSL-checked pieces: Spake2 as BOB, PairingCipher, packet framing.
            val bob = Spake2.Party(Spake2.Role.BOB, code.toByteArray() + Conscrypt.exportKeyingMaterial(s, "adb-label\u0000", null, 64))
            val input = java.io.DataInputStream(s.inputStream)
            fun read(): ByteArray {
                val h = PacketHeader.decode(ByteArray(PacketHeader.SIZE).also(input::readFully))
                return ByteArray(h.payloadSize).also(input::readFully)
            }
            fun send(type: PacketType, p: ByteArray) = s.outputStream.write(PacketHeader(type, p.size).encode() + p)
            send(PacketType.SPAKE2_MSG, bob.message)
            val cipher = PairingCipher(Spake2.aesKey(bob.sharedKey(read())))
            send(PacketType.PEER_INFO, cipher.seal(adbdInfo.encode()))
            PeerInfo.decode(cipher.open(read())!!)
        }
        val ours = PeerInfo(PeerInfo.Type.ADB_RSA_PUB_KEY, "QAAAAtest monomode".toByteArray())
        val theirs = connect(adbd).use { PairingFlow(it, code, ours).run() }
        assertEquals("adb-emulator", String(theirs.data))
        assertEquals("QAAAAtest monomode", String(adbd.get().data))
    }
}
