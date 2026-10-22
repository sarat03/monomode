package io.github.sarat03.phonewarden.tls

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import org.conscrypt.Conscrypt

/** Test-only TLS 1.3 server with its own AndroidKeyStore certificate, like adbd's pairing port. */
object TestTls {
    private val provider = Conscrypt.newProvider()

    fun clientKey() = AdbKey.getOrCreate("phonewarden-test-client-v2")

    fun server(): SSLServerSocket {
        val km = FixedKeyManager(AdbKey.getOrCreate("phonewarden-test-server-v2"))
        val ctx = SSLContext.getInstance("TLSv1.3", provider).apply { init(arrayOf(km), arrayOf(TrustAll), null) }
        return (ctx.serverSocketFactory.createServerSocket(0) as SSLServerSocket).apply {
            enabledProtocols = arrayOf("TLSv1.3")
            needClientAuth = true
        }
    }
}
