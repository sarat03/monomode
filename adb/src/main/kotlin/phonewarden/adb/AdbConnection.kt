package phonewarden.adb

/** A byte stream to adbd's connect port that can be upgraded to TLS in place. */
interface AdbTransport {
    fun write(bytes: ByteArray)
    fun readFully(n: Int): ByteArray
    fun startTls()
}

class AdbConnection(private val transport: AdbTransport) {
    fun connect() {
        send(AdbMessage(Command.CNXN, A_VERSION, MAX_PAYLOAD, "host::".toByteArray()))
        if (receive().command == Command.STLS) {
            send(AdbMessage(Command.STLS, A_STLS_VERSION, 0, ByteArray(0)))
            transport.startTls()
        }
        val reply = receive()
        if (reply.command != Command.CNXN) throw ProtocolException("expected CNXN after TLS, got ${reply.command}")
    }

    /** Runs `shell:` without shell_v2, so stdout and stderr arrive as one raw stream. */
    fun shell(command: String): String = readStream("shell:$command").toString(Charsets.UTF_8)

    /** Runs `shell,v2,raw:`; the stream carries shell_protocol.h packets [id:1][len:4 LE][data]. */
    fun shellV2(command: String): ShellResult {
        val buf = java.nio.ByteBuffer.wrap(readStream("shell,v2,raw:$command")).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val out = java.io.ByteArrayOutputStream()
        val err = java.io.ByteArrayOutputStream()
        var exit: Int? = null
        while (buf.hasRemaining()) {
            val id = buf.get().toInt()
            val data = ByteArray(buf.int).also { buf.get(it) }
            when (id) {
                ID_STDOUT -> out.writeBytes(data)
                ID_STDERR -> err.writeBytes(data)
                ID_EXIT -> exit = data[0].toInt() and 0xff
            }
        }
        return ShellResult(out.toString(Charsets.UTF_8), err.toString(Charsets.UTF_8), exit ?: throw ProtocolException("shell_v2 stream closed without exit code"))
    }

    // ponytail: buffers the whole stream before parsing; stream it when we run long or chatty commands.
    private fun readStream(service: String): ByteArray {
        // ponytail: one stream at a time with fixed local id 1; multiplex when we need concurrent commands.
        send(AdbMessage(Command.OPEN, LOCAL_ID, 0, "$service\u0000".toByteArray()))
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val m = receive()
            when (m.command) {
                Command.OKAY -> Unit
                Command.WRTE -> { out.writeBytes(m.payload); send(AdbMessage(Command.OKAY, LOCAL_ID, m.arg0, ByteArray(0))) }
                Command.CLSE -> { send(AdbMessage(Command.CLSE, LOCAL_ID, m.arg0, ByteArray(0))); return out.toByteArray() }
                else -> throw ProtocolException("unexpected ${m.command} on shell stream")
            }
        }
    }

    private fun send(m: AdbMessage) = transport.write(m.encode())

    private fun receive(): AdbMessage {
        val header = transport.readFully(AdbMessage.HEADER)
        val len = java.nio.ByteBuffer.wrap(header, 12, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
        return AdbMessage.decode(header + transport.readFully(len))
    }

    companion object {
        const val A_VERSION = 0x01000001
        const val MAX_PAYLOAD = 1024 * 1024
        const val A_STLS_VERSION = 0x01000000
        private const val LOCAL_ID = 1
        private const val ID_STDOUT = 1
        private const val ID_STDERR = 2
        private const val ID_EXIT = 3
    }
}

data class ShellResult(val stdout: String, val stderr: String, val exitCode: Int)
