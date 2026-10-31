package com.wutsi.ndopify.security.server.service

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * `ndopify.security.access-token`: how long a token issued for each application lives, keyed by `ApplicationCode`
 * (an environment can override one). The web apps keep their cookie as long as the token, from its `exp` claim, so
 * this is also how long a user stays signed in.
 */
@ConfigurationProperties("ndopify.security.access-token")
data class AccessTokenProperties(
    val ttl: Map<String, Duration> = emptyMap(),
    val defaultTtl: Duration = Duration.ofHours(1),
) {
    /** The TTL of [applicationCode]'s tokens, [defaultTtl] for an application not listed. */
    fun ttl(applicationCode: String): Duration = ttl[applicationCode] ?: defaultTtl
}
