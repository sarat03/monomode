package phonewarden.adb.pairing

import java.math.BigInteger

// Edwards25519 arithmetic after the RFC 8032 reference code. Not constant-time; see ADR 0001.
private val P: BigInteger = BigInteger.TWO.pow(255) - BigInteger.valueOf(19)
private val D: BigInteger = BigInteger.valueOf(-121665) * BigInteger.valueOf(121666).modInverse(P) % P
private val SQRT_M1: BigInteger = BigInteger.TWO.modPow((P - BigInteger.ONE) / BigInteger.valueOf(4), P)

/** Order of the prime-order subgroup. */
val L: BigInteger = BigInteger.TWO.pow(252) + BigInteger("27742317777372353535851937790883648493")

fun leInt(bytes: ByteArray) = BigInteger(1, bytes.reversedArray())
fun le32(n: BigInteger): ByteArray = n.toByteArray().reversedArray().copyOf(32)

class Point(val x: BigInteger, val y: BigInteger) {
    operator fun plus(o: Point): Point {
        val t = D * x * o.x % P * y % P * o.y % P
        return Point(
            (x * o.y + o.x * y) * (BigInteger.ONE + t).modInverse(P) % P,
            (y * o.y + x * o.x) * (BigInteger.ONE - t).mod(P).modInverse(P) % P,
        )
    }

    operator fun unaryMinus() = Point((P - x).mod(P), y)

    /** Double-and-add; `k` is a little-endian scalar. */
    fun times(k: ByteArray): Point {
        val n = leInt(k)
        var r = IDENTITY
        for (i in n.bitLength() - 1 downTo 0) {
            r += r
            if (n.testBit(i)) r += this
        }
        return r
    }

    /** 32 bytes little-endian y, with the low bit of x in the top bit. */
    fun encode(): ByteArray {
        val out = y.toByteArray().reversedArray().copyOf(32)
        if (x.testBit(0)) out[31] = (out[31].toInt() or 0x80).toByte()
        return out
    }

    companion object {
        val IDENTITY = Point(BigInteger.ZERO, BigInteger.ONE)
        /** Base point: y = 4/5, x even. */
        val BASE = decode(le32(BigInteger.valueOf(4) * BigInteger.valueOf(5).modInverse(P) % P))!!

        /** Returns null when the bytes are not a point on the curve. */
        fun decode(bytes: ByteArray): Point? {
            if (bytes.size != 32) return null
            val sign = bytes[31].toInt() ushr 7 and 1
            val y = BigInteger(1, bytes.reversedArray()).clearBit(255)
            if (y >= P) return null
            val x2 = (y * y - BigInteger.ONE) * (D * y * y + BigInteger.ONE).modInverse(P) % P
            var x = x2.modPow((P + BigInteger.valueOf(3)) / BigInteger.valueOf(8), P)
            if ((x * x - x2).mod(P).signum() != 0) x = x * SQRT_M1 % P
            if ((x * x - x2).mod(P).signum() != 0) return null
            if (x.signum() == 0 && sign == 1) return null
            if (x.testBit(0) != (sign == 1)) x = P - x
            return Point(x, y)
        }
    }
}
