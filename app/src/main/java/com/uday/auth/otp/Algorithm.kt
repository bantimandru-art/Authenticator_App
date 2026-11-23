package com.uday.auth.otp

enum class Algorithm(val macName: String) {
    SHA1("HmacSHA1"),
    SHA256("HmacSHA256"),
    SHA512("HmacSHA512");

    companion object {
        fun fromName(name: String): Algorithm? =
            entries.firstOrNull { it.name.equals(name.replace("-", ""), ignoreCase = true) }
    }
}
