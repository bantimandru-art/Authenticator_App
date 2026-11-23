package com.uday.auth.otp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TotpGeneratorTest {

    // Test vectors from RFC 6238, Appendix B
    private val sha1Key = "12345678901234567890".toByteArray()
    private val sha256Key = "12345678901234567890123456789012".toByteArray()
    private val sha512Key = "1234567890123456789012345678901234567890123456789012345678901234".toByteArray()

    private val vectors = listOf(
        Triple(59L, "94287082", Algorithm.SHA1),
        Triple(59L, "46119246", Algorithm.SHA256),
        Triple(59L, "90693936", Algorithm.SHA512),
        Triple(1111111109L, "07081804", Algorithm.SHA1),
        Triple(1111111109L, "68084774", Algorithm.SHA256),
        Triple(1111111109L, "25091201", Algorithm.SHA512),
        Triple(1234567890L, "89005924", Algorithm.SHA1),
        Triple(1234567890L, "91819424", Algorithm.SHA256),
        Triple(1234567890L, "93441116", Algorithm.SHA512),
        Triple(20000000000L, "65353130", Algorithm.SHA1),
        Triple(20000000000L, "77737706", Algorithm.SHA256),
        Triple(20000000000L, "47863826", Algorithm.SHA512),
    )

    @Test
    fun matchesRfc6238Vectors() {
        for ((seconds, expected, algorithm) in vectors) {
            val key = when (algorithm) {
                Algorithm.SHA1 -> sha1Key
                Algorithm.SHA256 -> sha256Key
                Algorithm.SHA512 -> sha512Key
            }
            val code = TotpGenerator.generate(key, seconds * 1000, digits = 8, algorithm = algorithm)
            assertEquals("$algorithm at $seconds", expected, code)
        }
    }

    @Test
    fun sixDigitCodesAreTheLastSixDigits() {
        assertEquals("287082", TotpGenerator.generate(sha1Key, 59_000))
    }

    @Test
    fun secondsRemainingCountsDownWithinPeriod() {
        assertEquals(30, TotpGenerator.secondsRemaining(30, 60_000))
        assertEquals(29, TotpGenerator.secondsRemaining(30, 61_500))
        assertEquals(1, TotpGenerator.secondsRemaining(30, 89_999))
    }

    @Test
    fun base32DecodesKnownSecret() {
        assertArrayEquals(sha1Key, Base32.decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"))
    }

    @Test
    fun base32IgnoresCaseSpacesAndPadding() {
        assertArrayEquals(
            Base32.decode("JBSWY3DPEHPK3PXP"),
            Base32.decode("jbsw y3dp-ehpk 3pxp===="),
        )
    }

    @Test
    fun base32RejectsInvalidCharacters() {
        assertEquals(false, Base32.isValid("JBSWY3DP1"))
        assertEquals(false, Base32.isValid(""))
    }
}
