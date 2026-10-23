package monomode.adb.pairing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Expected ciphertexts come from BoringSSL (tools/spake2-vectors/gcm.cc, gcm-vectors.txt). */
class PairingCipherTest {
    private val key = "d7fbf98a9da9e4153e082b87cf8572be".unhex()
    private val plain = "2a2b2cff451233".unhex()

    @Test
    fun `each seal uses the next sequence number as the nonce`() {
        val cipher = PairingCipher(key)
        assertEquals("d1e6a8d65d4db286dd5fc6d131b42093612e10d4a78a8d", cipher.seal(plain).hex())
        assertEquals("a58c8ff934b4e4fdec1b98eda56fd757cbfc5641c35087", cipher.seal(plain).hex())
    }

    @Test
    fun `opens BoringSSL's ciphertexts in sequence`() {
        val cipher = PairingCipher(key)
        assertEquals(plain.hex(), cipher.open("d1e6a8d65d4db286dd5fc6d131b42093612e10d4a78a8d".unhex())?.hex())
        assertEquals(plain.hex(), cipher.open("a58c8ff934b4e4fdec1b98eda56fd757cbfc5641c35087".unhex())?.hex())
    }

    @Test
    fun `returns null for tampered or replayed ciphertext`() {
        val cipher = PairingCipher(key)
        val sealed = "d1e6a8d65d4db286dd5fc6d131b42093612e10d4a78a8d".unhex()
        assertNull(cipher.open(sealed.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }))
        assertNull(PairingCipher(key).also { it.open(sealed) }.open(sealed)) // seq 1 now expected
    }
}
