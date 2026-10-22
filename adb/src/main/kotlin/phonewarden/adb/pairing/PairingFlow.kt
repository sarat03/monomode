package phonewarden.adb.pairing

import phonewarden.adb.ProtocolException

/** The TLS 1.3 connection to adbd's pairing port, already handshaken. */
interface PairingTransport {
    /** 64 bytes from the TLS exporter, label "adb-label\u0000", no context. */
    fun exportedKey(): ByteArray
    fun write(bytes: ByteArray)
    fun readFully(n: Int): ByteArray
}

class PairingFlow(
    private val transport: PairingTransport,
    private val code: String,
    private val ourInfo: PeerInfo,
    private val privateKey: ByteArray? = null,
) {
    /** Client side of adb pairing: returns adbd's PeerInfo. */
    fun run(): PeerInfo {
        // Binding the password to this TLS session stops a man-in-the-middle relaying the exchange.
        val password = code.toByteArray() + transport.exportedKey()
        val party = privateKey?.let { Spake2.Party(Spake2.Role.ALICE, password, it) } ?: Spake2.Party(Spake2.Role.ALICE, password)
        send(PacketType.SPAKE2_MSG, party.message)
        val cipher = PairingCipher(Spake2.aesKey(party.sharedKey(receive(PacketType.SPAKE2_MSG))))
        send(PacketType.PEER_INFO, cipher.seal(ourInfo.encode()))
        // A wrong code gives each side a different key, so adbd's peer info fails authentication.
        val theirs = cipher.open(receive(PacketType.PEER_INFO)) ?: throw WrongCodeException()
        return PeerInfo.decode(theirs)
    }

    private fun send(type: PacketType, payload: ByteArray) =
        transport.write(PacketHeader(type, payload.size).encode() + payload)

    private fun receive(type: PacketType): ByteArray {
        val header = PacketHeader.decode(transport.readFully(PacketHeader.SIZE))
        if (header.type != type) throw ProtocolException("expected $type packet, got ${header.type}")
        return transport.readFully(header.payloadSize)
    }
}

class WrongCodeException : Exception("pairing code did not match")
