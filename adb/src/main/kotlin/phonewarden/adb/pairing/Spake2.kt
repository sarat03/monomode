package phonewarden.adb.pairing

import java.security.MessageDigest

/** SPAKE2 over Edwards25519, wire-compatible with BoringSSL's spake25519 (what adbd uses). */
object Spake2 {
    val N: Point = pointFromSeed("edwards25519 point generation seed (N)")
    val M: Point = pointFromSeed("edwards25519 point generation seed (M)")

    // BoringSSL's generator: SHA-256 the seed, then re-hash until the digest decodes to a point.
    private fun pointFromSeed(seed: String): Point {
        val sha = MessageDigest.getInstance("SHA-256")
        var v = sha.digest(seed.toByteArray())
        while (true) Point.decode(v)?.let { return it } ?: run { v = sha.digest(v) }
    }
}
