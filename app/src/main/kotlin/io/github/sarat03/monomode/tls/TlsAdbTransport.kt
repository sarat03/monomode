package io.github.sarat03.monomode.tls

import java.io.Closeable
import java.io.DataInputStream
import java.net.InetAddress
import java.net.Socket
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import org.conscrypt.Conscrypt
import monomode.adb.AdbTransport

/** adbd's connect port: plain TCP until STLS, then TLS 1.3 in place presenting our paired key. */
class TlsAdbTransport(private val host: InetAddress, private val port: Int, private val clientKey: KeyStore.PrivateKeyEntry) : AdbTransport, Closeable {
    private var socket: Socket = Socket(host, port)

    override fun write(bytes: ByteArray) = socket.outputStream.run { write(bytes); flush() }
    override fun readFully(n: Int): ByteArray =
        DataInputStream(socket.inputStream).let { input -> ByteArray(n).also(input::readFully) }

    // Trust-all, as host adb does: adbd's certificate is self-signed and unknown; adbd authenticates us.
    override fun startTls() {
        val ctx = SSLContext.getInstance("TLSv1.3", provider).apply { init(arrayOf(FixedKeyManager(clientKey)), arrayOf(TrustAll), null) }
        socket = (ctx.socketFactory.createSocket(socket, host.hostAddress, port, true) as SSLSocket).apply {
            enabledProtocols = arrayOf("TLSv1.3")
            startHandshake()
        }
    }

    override fun close() = socket.close()

    private companion object { val provider = Conscrypt.newProvider() }
}
