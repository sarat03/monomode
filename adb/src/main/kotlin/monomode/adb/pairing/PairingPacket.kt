package monomode.adb.pairing

import java.nio.ByteBuffer
import monomode.adb.ProtocolException

/** adb's PeerInfo struct: 1 type byte + data, always this many bytes. */
const val PeerInfoSize = 8192

/** Ordinal is the wire value (adb's pairing.proto: SPAKE2_MSG = 0, PEER_INFO = 1). */
enum class PacketType { SPAKE2_MSG, PEER_INFO }

/** Packed {u8 version; u8 type; u32 payload} with the payload size big-endian, as adb sends it. */
class PacketHeader(val type: PacketType, val payloadSize: Int) {
    fun encode(): ByteArray = ByteBuffer.allocate(SIZE)
        .put(VERSION.toByte()).put(type.ordinal.toByte()).putInt(payloadSize).array()

    companion object {
        const val SIZE = 6
        const val VERSION = 1

        /** Max payload is 2 x PeerInfo, the limit adbd enforces. */
        const val MAX_PAYLOAD = 2 * PeerInfoSize

        fun decode(bytes: ByteArray): PacketHeader {
            if (bytes.size != SIZE) throw ProtocolException("pairing header is ${bytes.size} bytes, want $SIZE")
            val b = ByteBuffer.wrap(bytes)
            val version = b.get().toInt() and 0xff
            if (version != VERSION) throw ProtocolException("unsupported pairing header version $version")
            val type = PacketType.entries.getOrNull(b.get().toInt() and 0xff) ?: throw ProtocolException("unknown pairing packet type")
            val size = b.int
            if (size !in 1..MAX_PAYLOAD) throw ProtocolException("pairing payload size $size out of range")
            return PacketHeader(type, size)
        }
    }
}

/** adb's PeerInfo: our public key (or a device GUID) sent encrypted after SPAKE2. Data is a C string. */
class PeerInfo(val type: Type, val data: ByteArray) {
    /** Ordinal is the wire value. */
    enum class Type { ADB_RSA_PUB_KEY, ADB_DEVICE_GUID }

    init {
        require(data.size < PeerInfoSize - 1) { "peer info data is ${data.size} bytes; must leave room for the NUL" }
    }

    fun encode(): ByteArray = ByteArray(PeerInfoSize).also {
        it[0] = type.ordinal.toByte()
        data.copyInto(it, 1)
    }

    companion object {
        fun decode(bytes: ByteArray): PeerInfo {
            if (bytes.size != PeerInfoSize) throw ProtocolException("peer info is ${bytes.size} bytes, want $PeerInfoSize")
            val type = Type.entries.getOrNull(bytes[0].toInt() and 0xff) ?: throw ProtocolException("unknown peer info type")
            val end = (1 until bytes.size).firstOrNull { bytes[it] == 0.toByte() } ?: bytes.size
            return PeerInfo(type, bytes.copyOfRange(1, end))
        }
    }
}
