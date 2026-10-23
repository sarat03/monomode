package monomode.adb.pairing

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** SPAKE2 over Edwards25519, wire-compatible with BoringSSL's spake25519 (what adbd uses). */
object Spake2 {
    val N: Point = pointFromSeed("edwards25519 point generation seed (N)")
    val M: Point = pointFromSeed("edwards25519 point generation seed (M)")

    /**
     * SHA-512(password) mod L, then BoringSSL's fix for its missing cofactor clear:
     * add L, 2L, 4L as needed so the low three bits are zero. Peers must match this exactly.
     */
    fun passwordScalar(password: ByteArray): ByteArray {
        var s = leInt(MessageDigest.getInstance("SHA-512").digest(password)) % L
        for (bit in 0..2) if (s.testBit(bit)) s += L.shiftLeft(bit)
        return le32(s)
    }

    enum class Role { ALICE, BOB }

    /** Like BoringSSL: reduce mod L, then ×8 so the peer's small-order component is cleared. */
    fun privateKeyFrom(random64: ByteArray): ByteArray {
        require(random64.size == 64) { "need 64 random bytes" }
        return le32((leInt(random64) % L).shiftLeft(3))
    }

    /** HKDF-SHA256 (RFC 5869), empty salt, adb's info string, 16 bytes: one expand block is enough. */
    fun aesKey(sharedKey: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(ByteArray(32), "HmacSHA256"))
        val prk = mac.doFinal(sharedKey)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        return mac.doFinal("adb pairing_auth aes-128-gcm key".toByteArray() + 1).copyOf(16)
    }

    // adb sends sizeof() of C string literals, so the trailing NUL is part of each name.
    private val CLIENT = "adb pair client\u0000".toByteArray()
    private val SERVER = "adb pair server\u0000".toByteArray()

    /** Alice is the pairing client (us), Bob is adbd. */
    class Party(
        val role: Role,
        password: ByteArray,
        private val privateKey: ByteArray = privateKeyFrom(ByteArray(64).also(SecureRandom()::nextBytes)),
    ) {
        private val pwHash = MessageDigest.getInstance("SHA-512").digest(password)
        private val pwScalar = passwordScalar(password)

        /** privateKey·B + pw·(M for alice, N for bob). The private key must already be a multiple of 8. */
        val message: ByteArray = (Point.BASE.times(privateKey) + (if (role == Role.ALICE) M else N).times(pwScalar)).encode()

        /** 64-byte key. Throws if their message is not a curve point. */
        fun sharedKey(theirMessage: ByteArray): ByteArray {
            val q = Point.decode(theirMessage) ?: throw IllegalArgumentException("peer message is not a curve point")
            val dh = (q + -(if (role == Role.ALICE) N else M).times(pwScalar)).times(privateKey).encode()
            val sha = MessageDigest.getInstance("SHA-512")
            fun put(b: ByteArray) {
                sha.update(le32(b.size.toBigInteger()).copyOf(8))
                sha.update(b)
            }
            if (role == Role.ALICE) listOf(CLIENT, SERVER, message, theirMessage).forEach(::put)
            else listOf(CLIENT, SERVER, theirMessage, message).forEach(::put)
            put(dh)
            put(pwHash)
            return sha.digest()
        }
    }

    // BoringSSL's generator: SHA-256 the seed, then re-hash until the digest decodes to a point.
    private fun pointFromSeed(seed: String): Point {
        val sha = MessageDigest.getInstance("SHA-256")
        var v = sha.digest(seed.toByteArray())
        while (true) Point.decode(v)?.let { return it } ?: run { v = sha.digest(v) }
    }
}
