package com.wutsi.ndopify.npc.ui.login

import java.time.Instant
import kotlin.math.ceil
import kotlin.math.max

/**
 * The two sentences the sign-in page adds for the OTP account lock, built from the error's `data` (never from the
 * server's message). The copy lives in the app, like [LoginController]'s other messages: the e-signature portal words
 * its own page, so `ndopify-web-core` gains nothing.
 */
object OtpLockMessage {
    /**
     * How long the agent waits: the minutes left until `lockedUntil` (ISO-8601 instant), rounded up and never under
     * one, or a vaguer sentence when the server didn't say (unreadable data, a server that isn't this one).
     */
    fun locked(data: Map<String, Any>?, now: Long): String {
        val lockedUntil = (data?.get("lockedUntil") as? String)
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: return "Trop de tentatives. Réessayez plus tard."

        val minutes = max(1L, ceil((lockedUntil.toEpochMilli() - now).toDouble() / 60_000).toLong())
        val unit = if (minutes > 1) "minutes." else "minute."
        return "Trop de tentatives. Réessayez dans $minutes $unit"
    }

    /** How many attempts the lock leaves, from `remainingAttempts`; [fallback] when the server didn't say. */
    fun invalid(data: Map<String, Any>?, fallback: String): String {
        val remaining = (data?.get("remainingAttempts") as? Number)?.toInt() ?: return fallback
        if (remaining < 1) return fallback

        val unit = if (remaining > 1) "tentatives" else "tentative"
        return "Code incorrect. Encore $remaining $unit avant le blocage de votre compte."
    }
}
