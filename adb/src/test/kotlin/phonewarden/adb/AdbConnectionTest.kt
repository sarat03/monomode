package phonewarden.adb

import java.io.ByteArrayOutputStream
import java.io.EOFException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Connection against a scripted adbd; constants come from AOSP adb.h (A_VERSION, MAX_PAYLOAD, A_STLS_VERSION). */
class AdbConnectionTest {
    class FakeAdbd(vararg replies: AdbMessage) : AdbTransport {
        private val incoming = replies.fold(ByteArray(0)) { a, m -> a + m.encode() }
        private var pos = 0
        val sent = ByteArrayOutputStream()
        var tlsAt = -1

        override fun write(bytes: ByteArray) = sent.writeBytes(bytes)
        override fun readFully(n: Int): ByteArray {
            if (pos + n > incoming.size) throw EOFException()
            return incoming.copyOfRange(pos, pos + n).also { pos += n }
        }
        override fun startTls() { tlsAt = sent.size() }
    }

    @Test
    fun `opens with CNXN version 0x01000001, 1 MiB max payload, host banner`() {
        val adbd = FakeAdbd()
        assertFailsWith<EOFException> { AdbConnection(adbd).connect() }
        assertEquals(AdbMessage(Command.CNXN, 0x01000001, 1024 * 1024, "host::".toByteArray()).encode().hex(), adbd.sent.toByteArray().hex())
    }

    @Test
    fun `answers STLS with STLS version 0x01000000 then upgrades to TLS`() {
        val adbd = FakeAdbd(AdbMessage(Command.STLS, 0x01000000, 0, ByteArray(0)))
        assertFailsWith<EOFException> { AdbConnection(adbd).connect() }
        val sent = adbd.sent.toByteArray()
        val stls = AdbMessage(Command.STLS, 0x01000000, 0, ByteArray(0)).encode()
        assertEquals(stls.hex(), sent.copyOfRange(sent.size - stls.size, sent.size).hex())
        assertEquals(sent.size, adbd.tlsAt)
    }

    @Test
    fun `connect fails if adbd does not finish with CNXN`() {
        val adbd = FakeAdbd(AdbMessage(Command.STLS, 0x01000000, 0, ByteArray(0)), AdbMessage(Command.AUTH, 1, 0, ByteArray(20)))
        assertFailsWith<ProtocolException> { AdbConnection(adbd).connect() }
    }

    @Test
    fun `shell opens a NUL-terminated service, acks each WRTE and returns output at CLSE`() {
        fun m(c: Command, a0: Int, a1: Int, p: String = "") = AdbMessage(c, a0, a1, p.toByteArray())
        val adbd = FakeAdbd(
            m(Command.STLS, 0x01000000, 0), m(Command.CNXN, 0x01000001, 4096, "device::"),
            m(Command.OKAY, 7, 1), m(Command.WRTE, 7, 1, "h"), m(Command.WRTE, 7, 1, "i\n"), m(Command.CLSE, 7, 1),
        )
        val conn = AdbConnection(adbd).apply { connect() }
        val before = adbd.sent.size()

        assertEquals("hi\n", conn.shell("echo hi"))

        val expected = listOf(m(Command.OPEN, 1, 0, "shell:echo hi\u0000"), m(Command.OKAY, 1, 7), m(Command.OKAY, 1, 7), m(Command.CLSE, 1, 7))
            .fold(ByteArray(0)) { a, x -> a + x.encode() }
        assertEquals(expected.hex(), adbd.sent.toByteArray().copyOfRange(before, adbd.sent.size()).hex())
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}
