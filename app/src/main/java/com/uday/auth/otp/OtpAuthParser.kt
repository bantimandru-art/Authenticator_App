package com.uday.auth.otp

import com.uday.auth.data.Account
import java.net.URLDecoder
import java.util.UUID

class InvalidOtpUriException(message: String) : Exception(message)

/**
 * Parses the `otpauth://totp/Issuer:account?secret=...&issuer=...` URIs that sites
 * encode in their setup QR codes.
 *
 * Format reference: https://github.com/google/google-authenticator/wiki/Key-Uri-Format
 *
 * Written against plain strings rather than android.net.Uri so it can be unit tested
 * on the JVM.
 */
object OtpAuthParser {

    private const val SCHEME = "otpauth://"

    fun parse(text: String): Account {
        val uri = text.trim()
        if (!uri.startsWith(SCHEME, ignoreCase = true)) {
            throw InvalidOtpUriException("This QR code isn't for an authenticator app.")
        }

        val rest = uri.substring(SCHEME.length)
        val type = rest.substringBefore('/')
        when {
            type.equals("hotp", ignoreCase = true) ->
                throw InvalidOtpUriException("Counter based (HOTP) accounts aren't supported.")
            !type.equals("totp", ignoreCase = true) ->
                throw InvalidOtpUriException("Unknown account type \"$type\".")
        }

        val pathAndQuery = rest.substringAfter('/', missingDelimiterValue = "")
        val label = decodePath(pathAndQuery.substringBefore('?'))
        val params = parseQuery(pathAndQuery.substringAfter('?', missingDelimiterValue = ""))

        val (labelIssuer, accountName) = splitLabel(label)
        val issuer = params["issuer"]?.trim()?.takeIf { it.isNotEmpty() } ?: labelIssuer

        val secret = params["secret"]
        if (secret.isNullOrBlank() || !Base32.isValid(secret)) {
            throw InvalidOtpUriException("The secret key in this QR code is missing or invalid.")
        }

        val algorithm = params["algorithm"]?.let {
            Algorithm.fromName(it) ?: throw InvalidOtpUriException("Unsupported algorithm \"$it\".")
        } ?: Algorithm.SHA1

        val digits = params["digits"]?.let {
            it.toIntOrNull()?.takeIf { d -> d in 6..8 }
                ?: throw InvalidOtpUriException("Unsupported code length \"$it\".")
        } ?: 6

        val period = params["period"]?.let {
            it.toIntOrNull()?.takeIf { p -> p > 0 }
                ?: throw InvalidOtpUriException("Invalid period \"$it\".")
        } ?: 30

        if (issuer.isEmpty() && accountName.isEmpty()) {
            throw InvalidOtpUriException("This QR code doesn't include an account name.")
        }

        return Account(
            id = UUID.randomUUID().toString(),
            issuer = issuer,
            label = accountName,
            secret = Base32.normalize(secret),
            algorithm = algorithm,
            digits = digits,
            period = period,
        )
    }

    /** "GitHub:jane@example.com" -> ("GitHub", "jane@example.com"); no colon means no issuer. */
    private fun splitLabel(label: String): Pair<String, String> {
        val colon = label.indexOf(':')
        return if (colon >= 0) {
            label.substring(0, colon).trim() to label.substring(colon + 1).trim()
        } else {
            "" to label.trim()
        }
    }

    private fun parseQuery(query: String): Map<String, String> =
        query.split('&')
            .filter { it.isNotEmpty() }
            .associate { pair ->
                val key = decodeQuery(pair.substringBefore('=')).lowercase()
                val value = decodeQuery(pair.substringAfter('=', missingDelimiterValue = ""))
                key to value
            }

    // A literal '+' in the path is part of the label (think "me+work@gmail.com"),
    // while in the query string some generators use it in place of a space.
    private fun decodePath(value: String) = decode(value.replace("+", "%2B"))

    private fun decodeQuery(value: String) = decode(value)

    private fun decode(value: String): String =
        try {
            URLDecoder.decode(value, "UTF-8")
        } catch (e: IllegalArgumentException) {
            throw InvalidOtpUriException("This QR code is malformed.")
        }
}
