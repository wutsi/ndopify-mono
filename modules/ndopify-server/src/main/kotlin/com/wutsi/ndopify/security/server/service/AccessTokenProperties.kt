package com.wutsi.ndopify.security.server.service

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.time.Instant

/**
 * `ndopify.security.access-token`: how long a token issued for each application lives, keyed by `ApplicationCode`
 * (an environment can override one), or by a purpose key (`match-link`, the customer links of the match proposal and
 * the visit e-mails). The web apps keep their cookie as long as the token, from its `exp` claim, so this is also how
 * long a user stays signed in. [maxLifetime] caps a whole session, whatever it renews.
 */
@ConfigurationProperties("ndopify.security.access-token")
data class AccessTokenProperties(
    val ttl: Map<String, Duration> = emptyMap(),
    val defaultTtl: Duration = Duration.ofHours(1),
    val maxLifetime: Duration = Duration.ofDays(90),
) {
    /** The TTL of [applicationCode]'s tokens, [defaultTtl] for an application not listed. */
    fun ttl(applicationCode: String): Duration = ttl[applicationCode] ?: defaultTtl

    /**
     * The life a renewal of a token of [lifetime] gives at [now], `null` once the session started [maxLifetime] ago
     * or more (the cap is checked first, so a session past it is never renewed). A token whose lifetime class is
     * longer than [maxLifetime] (a match link) keeps its original expiry: [expiresAt] never moves, neither earlier nor
     * later.
     */
    fun renewal(lifetime: Duration, now: Instant, expiresAt: Instant, sessionStartedAt: Instant): Duration? {
        val cap = sessionStartedAt + maxLifetime
        if (!now.isBefore(cap)) {
            return null
        }
        return if (lifetime > maxLifetime) {
            Duration.between(now, expiresAt)
        } else {
            minOf(lifetime, Duration.between(now, cap))
        }
    }
}
