package com.uday.auth.otp

import org.junit.Assert.assertEquals
import org.junit.Test

class OtpAuthParserTest {

    @Test
    fun parsesFullUri() {
        val account = OtpAuthParser.parse(
            "otpauth://totp/ACME%20Co:john.doe@email.com?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ" +
                "&issuer=ACME%20Co&algorithm=SHA256&digits=8&period=60"
        )
        assertEquals("ACME Co", account.issuer)
        assertEquals("john.doe@email.com", account.label)
        assertEquals("HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ", account.secret)
        assertEquals(Algorithm.SHA256, account.algorithm)
        assertEquals(8, account.digits)
        assertEquals(60, account.period)
    }

    @Test
    fun appliesDefaults() {
        val account = OtpAuthParser.parse("otpauth://totp/alice@example.com?secret=JBSWY3DPEHPK3PXP")
        assertEquals("", account.issuer)
        assertEquals("alice@example.com", account.label)
        assertEquals(Algorithm.SHA1, account.algorithm)
        assertEquals(6, account.digits)
        assertEquals(30, account.period)
    }

    @Test
    fun issuerParameterWinsOverLabelPrefix() {
        val account = OtpAuthParser.parse("otpauth://totp/Old:bob?secret=JBSWY3DPEHPK3PXP&issuer=New")
        assertEquals("New", account.issuer)
        assertEquals("bob", account.label)
    }

    @Test
    fun keepsPlusSignInLabel() {
        val account = OtpAuthParser.parse("otpauth://totp/Google:me+work@gmail.com?secret=JBSWY3DPEHPK3PXP")
        assertEquals("me+work@gmail.com", account.label)
    }

    @Test
    fun normalizesLowercaseSecret() {
        val account = OtpAuthParser.parse("otpauth://TOTP/x?secret=jbswy3dpehpk3pxp")
        assertEquals("JBSWY3DPEHPK3PXP", account.secret)
    }

    @Test(expected = InvalidOtpUriException::class)
    fun rejectsNonOtpUri() {
        OtpAuthParser.parse("https://example.com")
    }

    @Test(expected = InvalidOtpUriException::class)
    fun rejectsHotp() {
        OtpAuthParser.parse("otpauth://hotp/x?secret=JBSWY3DPEHPK3PXP&counter=0")
    }

    @Test(expected = InvalidOtpUriException::class)
    fun rejectsMissingSecret() {
        OtpAuthParser.parse("otpauth://totp/x?issuer=y")
    }

    @Test(expected = InvalidOtpUriException::class)
    fun rejectsBadDigits() {
        OtpAuthParser.parse("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&digits=4")
    }
}
