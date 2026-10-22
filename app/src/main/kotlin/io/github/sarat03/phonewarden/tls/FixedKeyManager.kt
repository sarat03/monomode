package io.github.sarat03.phonewarden.tls

import java.net.Socket
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager

/** Always presents one AndroidKeyStore entry; the private key never leaves the keystore. */
class FixedKeyManager(private val entry: KeyStore.PrivateKeyEntry) : X509ExtendedKeyManager() {
    private val chain = entry.certificateChain.map { it as X509Certificate }.toTypedArray()

    override fun chooseClientAlias(keyType: Array<String>?, issuers: Array<Principal>?, socket: Socket?) = ALIAS
    override fun chooseServerAlias(keyType: String?, issuers: Array<Principal>?, socket: Socket?) = ALIAS
    override fun chooseEngineClientAlias(keyType: Array<String>?, issuers: Array<Principal>?, engine: SSLEngine?) = ALIAS
    override fun chooseEngineServerAlias(keyType: String?, issuers: Array<Principal>?, engine: SSLEngine?) = ALIAS
    override fun getClientAliases(keyType: String?, issuers: Array<Principal>?) = arrayOf(ALIAS)
    override fun getServerAliases(keyType: String?, issuers: Array<Principal>?) = arrayOf(ALIAS)
    override fun getCertificateChain(alias: String?): Array<X509Certificate> = chain
    override fun getPrivateKey(alias: String?): PrivateKey = entry.privateKey

    private companion object { const val ALIAS = "phonewarden" }
}
