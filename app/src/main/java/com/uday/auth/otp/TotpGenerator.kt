package com.uday.auth.otp

import com.uday.auth.data.Account
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Time-based one-time passwords as described in RFC 6238 (built on HOTP, RFC 4226).
 */
object TotpGenerator {

    private val POWERS_OF_TEN = intArrayOf(1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000)

    fun generate(account: Account, timeMillis: Long = System.currentTimeMillis()): String =
        generate(
            key = Base32.decode(account.secret),
            timeMillis = timeMillis,
            period = account.period,
            digits = account.digits,
            algorithm = account.algorithm,
        )

    fun generate(
        key: ByteArray,
        timeMillis: Long,
        period: Int = 30,
        digits: Int = 6,
        algorithm: Algorithm = Algorithm.SHA1,
    ): String {
        val counter = timeMillis / 1000 / period
        return hotp(key, counter, digits, algorithm)
    }

    /** Whole seconds left before the current code rolls over, in the range 1..period. */
    fun secondsRemaining(period: Int, timeMillis: Long = System.currentTimeMillis()): Int =
        period - ((timeMillis / 1000) % period).toInt()

    private fun hotp(key: ByteArray, counter: Long, digits: Int, algorithm: Algorithm): String {
        val mac = Mac.getInstance(algorithm.macName)
        mac.init(SecretKeySpec(key, algorithm.macName))
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())

        // Dynamic truncation: the low nibble of the last byte picks where to read 4 bytes from
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)

        val otp = binary % POWERS_OF_TEN[digits]
        return otp.toString().padStart(digits, '0')
    }
}
