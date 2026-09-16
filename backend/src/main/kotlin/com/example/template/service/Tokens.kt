package com.example.template.service

import com.example.template.config.AppProperties
import java.security.SecureRandom
import java.util.HexFormat
import org.springframework.stereotype.Service

/** The random values the auth flow hands out. */
@Service
class Tokens(private val properties: AppProperties) {

    private val random = SecureRandom()

    /** 32 random bytes, hex encoded — verification, reset and pending-login tokens. */
    fun randomToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return HexFormat.of().formatHex(bytes)
    }

    /**
     * A 4-digit login code. In development it is always 1234 so testing needs
     * no mail server; otherwise a random code.
     */
    fun randomCode(): String {
        if (properties.isDevelopment()) {
            return "1234"
        }
        val bytes = ByteArray(2)
        random.nextBytes(bytes)
        val value = (((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)) % 10000
        return "%04d".format(value)
    }

    companion object {
        private const val TOKEN_BYTES = 32
    }
}
