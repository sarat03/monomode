package phonewarden.adb.pairing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import phonewarden.adb.ProtocolException

/** Layout from AOSP pairing_connection.cpp: packed {u8 version; u8 type; u32 payload (big-endian)}. */
class PairingPacketTest {
    @Test
    fun `header is version, type, then big-endian payload size`() {
        assertEquals("010000000020", PacketHeader(PacketType.SPAKE2_MSG, 32).encode().hex())
        assertEquals("010100002010", PacketHeader(PacketType.PEER_INFO, 8208).encode().hex())
    }

    @Test
    fun `decodes a header from the wire`() {
        val h = PacketHeader.decode("010100002010".unhex())
        assertEquals(PacketType.PEER_INFO, h.type)
        assertEquals(8208, h.payloadSize)
    }

    @Test
    fun `rejects headers adb would reject`() {
        for (bad in listOf(
            "020000000020", // unsupported version
            "010200000020", // unknown type
            "010000000000", // empty payload
            "010000004001", // 16385 > 2 x 8192
            "0100000000",   // truncated
        )) assertFailsWith<ProtocolException>(bad) { PacketHeader.decode(bad.unhex()) }
    }

    @Test
    fun `peer info is a type byte then NUL-padded data, always 8192 bytes`() {
        val wire = PeerInfo(PeerInfo.Type.ADB_RSA_PUB_KEY, "ab".toByteArray()).encode()
        assertEquals(8192, wire.size)
        assertEquals("006162" + "00".repeat(8189), wire.hex())
    }

    @Test
    fun `decodes peer info up to the first NUL`() {
        val info = PeerInfo.decode(("01" + "6775696431" + "00".repeat(8186)).unhex())
        assertEquals(PeerInfo.Type.ADB_DEVICE_GUID, info.type)
        assertEquals("guid1", String(info.data))
    }

    @Test
    fun `rejects peer info adb could not have sent`() {
        assertFailsWith<ProtocolException> { PeerInfo.decode(ByteArray(8191)) }                      // wrong size
        assertFailsWith<ProtocolException> { PeerInfo.decode(ByteArray(8192).also { it[0] = 7 }) }   // unknown type
        assertFailsWith<IllegalArgumentException> { PeerInfo(PeerInfo.Type.ADB_RSA_PUB_KEY, ByteArray(8192)) } // too long
    }
}
