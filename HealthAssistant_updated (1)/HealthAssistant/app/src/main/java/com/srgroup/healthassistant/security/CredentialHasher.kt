package com.srgroup.healthassistant.security

import java.security.MessageDigest

/**
 * Local device auth only — no server, no account recovery flow.
 * SHA-256 with a static app-level salt. This stops a casual "picked up
 * the phone" viewer, not a serious attacker with the APK — real auth
 * needs a backend + proper credential storage. Flagging this honestly
 * rather than presenting it as production-grade security.
 */
object CredentialHasher {
    private const val SALT = "health-assistant-local-v1"

    fun hash(secret: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((SALT + secret).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun matches(secret: String, storedHash: String): Boolean = hash(secret) == storedHash
}
