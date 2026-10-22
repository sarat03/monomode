package phonewarden.adb

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/** Expected output is a real `adb keygen` adbkey.pub; the modulus came from `openssl rsa -modulus` on its private key. */
class AdbPublicKeyTest {
    @Test
    fun `encodes an RSA public key exactly like adb keygen`() {
        val modulus = BigInteger(MODULUS, 16)
        assertEquals("$PUB test@phonewarden", AdbPublicKey.encode(modulus, BigInteger.valueOf(65537), "test@phonewarden"))
    }

    private companion object {
        const val MODULUS = "b2736afdf9319110fd471b3385d57cb805931433f8113d489410f506f31a3bc665360ddf9e2d623b709ebff5f6712c0489daac51a730e1a903c7c06a5d7eb7cc" +
            "f87e6433adc5930b41df0efe00852577a0b4c881bf0f7c1f72304cc9da34c1f7465799fa064751ad9eed06d0736f6fc350a41d974129d2dc8bf671178ddd4b0e" +
            "9367aefb4d34896939f51ef20d0ffbd4f0279613b842c6527f95301c3818ff18cd908433473a4236675a517802831ab4b94f89d94eee024632b620e8dd89e43f" +
            "afba9e423ff1d43e229078dbccc7da23bf5ea9eab2428efcaef211097ba251764fae3e56646163b748b4a81640a33a047c76a7e426363cd63b9e210b1cc76b7b"
        const val PUB = "QAAAAE1E6Gh7a8ccCyGeO9Y8Nibkp3Z8BDqjQBaotEi3Y2FkVj6uT3ZRonsJEfKu/I5CsuqpXr8j2sfM23iQIj7U8T9CnrqvP+SJ3eggtjJGAu5O2YlPubQagwJ4" +
            "UVpnNkI6RzOEkM0Y/xg4HDCVf1LGQrgTlifw1PsPDfIe9TlpiTRN+65nkw5L3Y0XcfaL3NIpQZcdpFDDb29z0Abtnq1RRwb6mVdG98E02slMMHIffA+/gci0oHcl" +
            "hQD+Dt9BC5PFrTNkfvjMt35dasDHA6nhMKdRrNqJBCxx9vW/nnA7Yi2e3w02ZcY7GvMG9RCUSD0R+DMUkwW4fNWFMxtH/RCRMfn9anOybFDIUcSQBxijeYxUMfFY" +
            "ftTemlNEu8K5EZlQcrdNg9/g0IYg8GekX1+a85tC7hbcWsh2zW+cmyqOprfw7ShQpDEfcplEj5zIn+swclfPyT787ZLdrtCS4PAyYxzXMirf2MzWj7cbZ8STq0SM" +
            "bmj1WtBjsFTxJGi6L12jcyrGVX6xpa+rhP9qVCLhq0JdSlwOYfPqx//gVf6P+sMdr1z71Vj01ETq6+wk6j0VV9YpZnujK1NY8FqlPgeRB8Ct1kTWHlAOBxhkWdu1" +
            "Ox6e6/Tn8If3tXNoJoU93PuPSKk5A0J/UxNzE3sCzf48HXj/GGKOG5G8o68zqRaBfispfyRBiwEAAQA="
    }
}
