package phonewarden.adb

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** ADB wire commands; the value is the 4 ASCII letters read as a little-endian int. */
enum class Command(val value: Int) { CNXN(0x4e584e43) }

class AdbMessage(val command: Command, val arg0: Int, val arg1: Int, val payload: ByteArray) {
    fun encode(): ByteArray = ByteBuffer.allocate(HEADER + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        .putInt(command.value).putInt(arg0).putInt(arg1)
        .putInt(payload.size).putInt(payload.sumOf { it.toInt() and 0xff })
        .putInt(command.value.inv())
        .put(payload).array()

    companion object {
        const val HEADER = 24

        fun decode(bytes: ByteArray): AdbMessage {
            if (bytes.size < HEADER) throw ProtocolException("short header: ${bytes.size} bytes")
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val cmd = b.int; val arg0 = b.int; val arg1 = b.int; val len = b.int
            b.int // byte-sum checksum: ignored, as adbd does since protocol version 0x01000001
            if (b.int != cmd.inv()) throw ProtocolException("bad magic")
            val command = Command.entries.find { it.value == cmd } ?: throw ProtocolException("unknown command 0x%08x".format(cmd))
            if (len < 0 || bytes.size - HEADER != len) throw ProtocolException("payload length $len, got ${bytes.size - HEADER}")
            return AdbMessage(command, arg0, arg1, bytes.copyOfRange(HEADER, bytes.size))
        }
    }
}

class ProtocolException(msg: String) : Exception(msg)
