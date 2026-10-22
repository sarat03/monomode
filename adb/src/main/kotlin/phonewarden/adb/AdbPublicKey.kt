package phonewarden.adb

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/**
 * adb's public key format (android_pubkey.c): base64 of a little-endian struct
 * {u32 words = 64; u32 n0inv; u32 n[64]; u32 rr[64]; u32 e}, then a space and a name.
 * n0inv = -1/n mod 2^32 and rr = (2^2048)^2 mod n let adbd do Montgomery math directly.
 */
object AdbPublicKey {
    private const val WORDS = 64 // RSA-2048

    fun encode(modulus: BigInteger, exponent: BigInteger, name: String): String {
        require(modulus.bitLength() == WORDS * 32) { "adb keys are RSA-2048" }
        val r32 = BigInteger.ONE.shiftLeft(32)
        val n0inv = r32 - modulus.mod(r32).modInverse(r32)
        val rr = BigInteger.ONE.shiftLeft(WORDS * 32 * 2).mod(modulus)
        val b = ByteBuffer.allocate(4 * (3 + 2 * WORDS)).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(WORDS).putInt(n0inv.toInt())
            .put(le(modulus)).put(le(rr)).putInt(exponent.toInt())
        return Base64.getEncoder().encodeToString(b.array()) + " " + name
    }

    private fun le(n: BigInteger): ByteArray = n.toByteArray().reversedArray().copyOf(WORDS * 4)
}
