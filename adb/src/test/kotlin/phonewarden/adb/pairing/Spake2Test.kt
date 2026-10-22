package phonewarden.adb.pairing

import kotlin.test.Test
import kotlin.test.assertEquals

/** Expected values come from BoringSSL (spake25519.cc comments, tools/spake2-vectors/vectors.txt). */
class Spake2Test {
    @Test
    fun `N and M are generated from their published seeds`() {
        assertEquals("10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778", Spake2.N.encode().hex())
        assertEquals("5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e", Spake2.M.encode().hex())
    }
}

fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
fun String.unhex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
