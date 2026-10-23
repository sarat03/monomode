package monomode.adb

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

        override fun write(bytes: ByteArray) = sent.write(bytes)
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

    @Test
    fun `shellV2 splits stdout, stderr and exit code from packets that span WRTEs`() {
        // shell_protocol.h: [id:1][len:4 LE][data]; stdout=1, stderr=2, exit=3
        fun pkt(id: Int, data: ByteArray) = byteArrayOf(id.toByte()) +
            java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(data.size).array() + data
        val stream = pkt(1, "out\n".toByteArray()) + pkt(2, "err\n".toByteArray()) + pkt(3, byteArrayOf(2))
        fun m(c: Command, a0: Int, a1: Int, p: ByteArray = ByteArray(0)) = AdbMessage(c, a0, a1, p)
        val adbd = FakeAdbd(
            m(Command.STLS, 0x01000000, 0), m(Command.CNXN, 0x01000001, 4096, "device::".toByteArray()),
            m(Command.OKAY, 7, 1), m(Command.WRTE, 7, 1, stream.copyOfRange(0, 3)),
            m(Command.WRTE, 7, 1, stream.copyOfRange(3, stream.size)), m(Command.CLSE, 7, 1),
        )
        val conn = AdbConnection(adbd).apply { connect() }
        val before = adbd.sent.size()

        assertEquals(ShellResult("out\n", "err\n", 2), conn.shellV2("ls nope"))
        val open = m(Command.OPEN, 1, 0, "shell,v2,raw:ls nope\u0000".toByteArray()).encode()
        assertEquals(open.hex(), adbd.sent.toByteArray().copyOfRange(before, before + open.size).hex())
    }

    private fun v2Pkt(id: Int, data: ByteArray) = byteArrayOf(id.toByte()) +
        java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(data.size).array() + data

    private fun connected(vararg stream: AdbMessage): Pair<AdbConnection, FakeAdbd> {
        val adbd = FakeAdbd(AdbMessage(Command.STLS, 0x01000000, 0, ByteArray(0)), AdbMessage(Command.CNXN, 0x01000001, 4096, "device::".toByteArray()), *stream)
        return AdbConnection(adbd).apply { connect() } to adbd
    }

    @Test
    fun `disableWirelessDebugging puts adb_wifi_enabled 0 over shell_v2`() {
        val (conn, adbd) = connected(
            AdbMessage(Command.OKAY, 7, 1, ByteArray(0)), AdbMessage(Command.WRTE, 7, 1, v2Pkt(3, byteArrayOf(0))), AdbMessage(Command.CLSE, 7, 1, ByteArray(0)),
        )
        val before = adbd.sent.size()
        conn.disableWirelessDebugging()
        val open = AdbMessage(Command.OPEN, 1, 0, "shell,v2,raw:settings put global adb_wifi_enabled 0\u0000".toByteArray()).encode()
        assertEquals(open.hex(), adbd.sent.toByteArray().copyOfRange(before, before + open.size).hex())
    }

    @Test
    fun `disableWirelessDebugging treats adbd dropping the connection as success`() {
        // seen on Android 14 emulator: adbd closes the socket before any shell_v2 exit packet
        val (conn, _) = connected(AdbMessage(Command.OKAY, 7, 1, ByteArray(0)))
        conn.disableWirelessDebugging()
    }

    @Test
    fun `disableWirelessDebugging fails when settings exits non-zero`() {
        val (conn, _) = connected(
            AdbMessage(Command.OKAY, 7, 1, ByteArray(0)), AdbMessage(Command.WRTE, 7, 1, v2Pkt(3, byteArrayOf(255.toByte()))), AdbMessage(Command.CLSE, 7, 1, ByteArray(0)),
        )
        assertFailsWith<ProtocolException> { conn.disableWirelessDebugging() }
    }

    @Test
    fun `second command on one connection ignores a late CLSE for the first stream`() {
        // seen on Android 14 emulator: adbd sends a second CLSE after we answer its CLSE
        fun exit0(local: Int) = arrayOf(
            AdbMessage(Command.OKAY, 7, local, ByteArray(0)), AdbMessage(Command.WRTE, 7, local, v2Pkt(1, "x".toByteArray()) + v2Pkt(3, byteArrayOf(0))),
            AdbMessage(Command.CLSE, 7, local, ByteArray(0)),
        )
        val (conn, adbd) = connected(*exit0(1), AdbMessage(Command.CLSE, 7, 1, ByteArray(0)), *exit0(2))
        conn.shellV2("echo a")
        val before = adbd.sent.size()
        assertEquals(ShellResult("x", "", 0), conn.shellV2("echo b"))
        val open = AdbMessage(Command.OPEN, 2, 0, "shell,v2,raw:echo b\u0000".toByteArray()).encode()
        assertEquals(open.hex(), adbd.sent.toByteArray().copyOfRange(before, before + open.size).hex())
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}
