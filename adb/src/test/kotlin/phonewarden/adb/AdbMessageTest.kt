package phonewarden.adb

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdbMessageTest {
    private val cnxnBytes = byteArrayOf(
            0x43, 0x4e, 0x58, 0x4e,                      // "CNXN"
            0x01, 0x00, 0x00, 0x01,                      // version
            0x00, 0x10, 0x00, 0x00,                      // max payload 4096
            0x07, 0x00, 0x00, 0x00,                      // payload length
            0x32, 0x02, 0x00, 0x00,                      // byte sum 562
            0xbc.toByte(), 0xb1.toByte(), 0xa7.toByte(), 0xb1.toByte(), // command xor 0xffffffff
        ) + "host::\u0000".toByteArray()

    @Test
    fun `encodes a CNXN message as a little-endian header followed by the payload`() {
        val msg = AdbMessage(Command.CNXN, 0x01000001, 4096, "host::\u0000".toByteArray())
        assertContentEquals(cnxnBytes, msg.encode())
    }

    @Test
    fun `decodes a CNXN message from the wire`() {
        val msg = AdbMessage.decode(cnxnBytes)
        assertEquals(Command.CNXN, msg.command)
        assertEquals(0x01000001, msg.arg0)
        assertEquals(4096, msg.arg1)
        assertContentEquals("host::\u0000".toByteArray(), msg.payload)
    }

    @Test
    fun `rejects a header whose magic does not match the command`() {
        val corrupt = cnxnBytes.copyOf().also { it[20] = 0 }
        assertFailsWith<ProtocolException> { AdbMessage.decode(corrupt) }
    }

    @Test
    fun `rejects a payload shorter than the declared length`() {
        assertFailsWith<ProtocolException> { AdbMessage.decode(cnxnBytes.copyOf(cnxnBytes.size - 1)) }
    }
}
