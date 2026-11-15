package com.wutsi.ndopify.security.server.service

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * `ndopify.security.otp`: how many wrong one-time codes lock an account, and for how long. Policy, not an invariant:
 * an environment may tune them without a rebuild.
 */
@ConfigurationProperties("ndopify.security.otp")
data class OtpProperties(
    /** Wrong codes after which the account is locked. */
    val maxFailedAttempts: Int = 5,
    /** How long the account stays locked. */
    val lockDuration: Duration = Duration.ofHours(1),
)
