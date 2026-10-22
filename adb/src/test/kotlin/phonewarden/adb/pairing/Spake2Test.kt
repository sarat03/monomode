package phonewarden.adb.pairing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/** Expected values come from BoringSSL (spake25519.cc comments, tools/spake2-vectors/vectors.txt). */
class Spake2Test {
    @Test
    fun `N and M are generated from their published seeds`() {
        assertEquals("10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778", Spake2.N.encode().hex())
        assertEquals("5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e", Spake2.M.encode().hex())
    }

    @Test
    fun `password becomes a cofactor-cleared scalar like BoringSSL's`() {
        val scalar = Spake2.passwordScalar("4f5a0146".unhex())
        assertEquals("b00ac4aaeb006718476bd6077811e1c957b16b556437f587c983df901ec5ae20", scalar.hex())
    }

    @Test
    fun `alice's message matches BoringSSL for the same private key and password`() {
        val alice = Spake2.Party(Spake2.Role.ALICE, PW, "489c3ec20630727e868f336be234133524754e0a3e053202e44ec32851d1ae2a".unhex())
        assertEquals("c01e4c90b706ad5f506db898e6b21ed8e94adc95abb6cae6ba989279644b5714", alice.message.hex())
    }

    @Test
    fun `both sides derive BoringSSL's shared key`() {
        val alice = Spake2.Party(Spake2.Role.ALICE, PW, ALICE_PRIVATE.unhex())
        val bob = Spake2.Party(Spake2.Role.BOB, PW, "d8ba19588b6b94cf2fb922ea9797eb27e417723ee87b891f1db359d011d0d55c".unhex())
        assertEquals(KEY, alice.sharedKey("a3b2cbad399eb0cbf1be3345f88fcc530a483ea5e6e6983191656c76961ef3d1".unhex()).hex())
        assertEquals(KEY, bob.sharedKey(ALICE_MSG.unhex()).hex())
    }

    @Test
    fun `a wrong password gives BoringSSL's mismatched key, not the shared one`() {
        val bob = Spake2.Party(Spake2.Role.BOB, "4f5a0147".unhex(), "58fb7bc2f3ce49eed1074bba357a0e19da57ad622c45a3fb8380486cfcb48854".unhex())
        val key = bob.sharedKey(ALICE_MSG.unhex()).hex()
        assertEquals("dc47344d50851b735dfabd8f22923f16ecc5ba342aebdb40f90830ed58e45a67" +
            "a5108f003b7899950eb7b9a46f08c13c0fd461b40cbe6bf5f395f1ecfebe3d25", key)
    }

    @Test
    fun `rejects a peer message that is not a curve point`() {
        val alice = Spake2.Party(Spake2.Role.ALICE, PW, ALICE_PRIVATE.unhex())
        // all-ones y is 2^255 - 1, which is >= p: not a valid encoding
        assertFailsWith<IllegalArgumentException> { alice.sharedKey(ByteArray(32) { 0xff.toByte() }.also { it[31] = 0x7f }) }
    }

    @Test
    fun `derives adb's AES-128-GCM key from the shared key`() {
        assertEquals("d7fbf98a9da9e4153e082b87cf8572be", Spake2.aesKey(KEY.unhex()).hex())
    }

    @Test
    fun `private key is 64 random bytes reduced mod L then multiplied by 8`() {
        // expected values computed independently in Python: (int.from_bytes(b, 'little') % L) * 8
        assertEquals("08" + "00".repeat(31), Spake2.privateKeyFrom(byteArrayOf(1) + ByteArray(63)).hex())
        assertEquals("0078e0241a8f30203d9a2c443bdd70802ef3adbf909e6377eed184e1db08ca1c",
            Spake2.privateKeyFrom(ByteArray(64) { 0xff.toByte() }).hex())
    }

    @Test
    fun `parties with fresh random keys agree only when the passwords match`() {
        val alice = Spake2.Party(Spake2.Role.ALICE, PW)
        val bob = Spake2.Party(Spake2.Role.BOB, PW)
        val eve = Spake2.Party(Spake2.Role.BOB, "4f5a0147".unhex())
        assertEquals(alice.sharedKey(bob.message).hex(), bob.sharedKey(alice.message).hex())
        assertNotEquals(alice.sharedKey(eve.message).hex(), eve.sharedKey(alice.message).hex())
    }

    companion object {
        val PW = "4f5a0146".unhex()
        const val ALICE_PRIVATE = "489c3ec20630727e868f336be234133524754e0a3e053202e44ec32851d1ae2a"
        const val ALICE_MSG = "c01e4c90b706ad5f506db898e6b21ed8e94adc95abb6cae6ba989279644b5714"
        const val KEY = "04f626e5de25e43e69bde6600d225bf68dc13dadc442764241749442f6e374e1" +
            "e86c94d1fbd023200258d00a43844e7db77b451a212c108c6a966c4a18446dde"
    }
}

fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
fun String.unhex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
