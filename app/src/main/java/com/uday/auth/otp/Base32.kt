package com.uday.auth.otp

/**
 * RFC 4648 Base32 decoder. Authenticator secrets are almost always handed out in
 * this format, sometimes lowercase, with spaces or dashes, and with or without padding,
 * so the input is normalised before decoding.
 */
object Base32 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun normalize(input: String): String =
        input.uppercase()
            .filterNot { it.isWhitespace() || it == '-' }
            .trimEnd('=')

    fun isValid(input: String): Boolean {
        val clean = normalize(input)
        return clean.isNotEmpty() && clean.all { it in ALPHABET }
    }

    fun decode(input: String): ByteArray {
        val clean = normalize(input)
        val output = ByteArray(clean.length * 5 / 8)

        var buffer = 0
        var bitsLeft = 0
        var index = 0

        for (c in clean) {
            val value = ALPHABET.indexOf(c)
            require(value >= 0) { "Invalid Base32 character '$c'" }

            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output[index++] = (buffer shr bitsLeft).toByte()
            }
        }
        return output
    }
}
