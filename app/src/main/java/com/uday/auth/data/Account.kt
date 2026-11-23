package com.uday.auth.data

import com.uday.auth.otp.Algorithm

data class Account(
    val id: String,
    val issuer: String,
    val label: String,
    val secret: String,
    val algorithm: Algorithm = Algorithm.SHA1,
    val digits: Int = 6,
    val period: Int = 30,
) {
    /** What we show in bold on the list: the issuer, or the account name if there isn't one. */
    val displayName: String
        get() = issuer.ifBlank { label }

    fun isSameKeyAs(other: Account): Boolean =
        secret == other.secret && issuer == other.issuer && label == other.label
}
