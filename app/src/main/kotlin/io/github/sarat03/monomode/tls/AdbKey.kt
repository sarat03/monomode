package io.github.sarat03.monomode.tls

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore

/** Our RSA-2048 identity in AndroidKeyStore, used as the TLS client certificate for adbd. */
object AdbKey {
    const val ALIAS = "monomode-adb"

    fun getOrCreate(alias: String = ALIAS): KeyStore.PrivateKeyEntry {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(alias)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
                initialize(
                    // Conscrypt signs TLS 1.3 RSA-PSS by doing the raw RSA private operation itself,
                    // which keystore only allows as DECRYPT with no padding.
                    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_DECRYPT)
                        .setKeySize(2048)
                        .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256)
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build(),
                )
            }.generateKeyPair()
        }
        return ks.getEntry(alias, null) as KeyStore.PrivateKeyEntry
    }
}
