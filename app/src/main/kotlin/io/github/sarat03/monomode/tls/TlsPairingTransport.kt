package io.github.sarat03.monomode.tls

import java.io.Closeable
import java.io.DataInputStream
import java.net.InetAddress
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import org.conscrypt.Conscrypt
import monomode.adb.pairing.PairingTransport

/** TLS 1.3 to adbd's pairing port via bundled Conscrypt, presenting our AndroidKeyStore certificate. */
class TlsPairingTransport private constructor(private val socket: SSLSocket) : PairingTransport, Closeable {
    // adbd passes sizeof("adb-label") to BoringSSL, so the label includes the trailing NUL.
    override fun exportedKey(): ByteArray = Conscrypt.exportKeyingMaterial(socket, "adb-label\u0000", null, 64)
    override fun write(bytes: ByteArray) = socket.outputStream.run { write(bytes); flush() }

    override fun readFully(n: Int): ByteArray =
        DataInputStream(socket.inputStream).let { input -> ByteArray(n).also(input::readFully) }
    override fun close() = socket.close()

    companion object {
        private val provider = Conscrypt.newProvider()

        fun connect(host: InetAddress, port: Int, clientKey: KeyStore.PrivateKeyEntry): TlsPairingTransport {
            val ctx = SSLContext.getInstance("TLSv1.3", provider).apply { init(arrayOf(FixedKeyManager(clientKey)), arrayOf(TrustAll), null) }
            val socket = (ctx.socketFactory.createSocket(host, port) as SSLSocket).apply {
                enabledProtocols = arrayOf("TLSv1.3")
                startHandshake()
            }
            return TlsPairingTransport(socket)
        }
    }
}

/**
 * Accepts any certificate. Safe ONLY for the pairing port: adbd's certificate is not known in
 * advance, and a man-in-the-middle is defeated because the SPAKE2 password includes key material
 * exported from this exact TLS session, so a relayed session yields a different key.
 */
@android.annotation.SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
object TrustAll : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
