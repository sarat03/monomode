package phonewarden.adb.pairing

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-128-GCM as adb's pairing_auth uses it: 12-byte nonce = 64-bit little-endian
 * sequence number then zeros, no AAD, 16-byte tag. Send and receive keep separate counters.
 */
class PairingCipher(aesKey: ByteArray) {
    private val key = SecretKeySpec(aesKey, "AES")
    private var sealSeq = 0L
    private var openSeq = 0L

    fun seal(plain: ByteArray): ByteArray = run(Cipher.ENCRYPT_MODE, sealSeq++, plain)

    /** Null when authentication fails. Like adb, the counter only advances on success. */
    fun open(sealed: ByteArray): ByteArray? =
        try { run(Cipher.DECRYPT_MODE, openSeq, sealed).also { openSeq++ } } catch (_: AEADBadTagException) { null }

    private fun run(mode: Int, seq: Long, input: ByteArray): ByteArray {
        val nonce = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putLong(seq).array()
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(mode, key, GCMParameterSpec(128, nonce)) }.doFinal(input)
    }
}
