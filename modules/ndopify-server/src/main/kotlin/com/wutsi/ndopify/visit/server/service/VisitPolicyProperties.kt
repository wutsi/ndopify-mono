package com.wutsi.ndopify.visit.server.service

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * `ndopify.visit.policy`: the delays and tolerances of a visit's lifecycle. They are policy, not invariants: an
 * environment or a tenant may tune them without a rebuild.
 */
@ConfigurationProperties("ndopify.visit.policy")
data class VisitPolicyProperties(
    /** How early the agent may check the customer in. */
    val checkInBefore: Duration = Duration.ofHours(1),
    /** How late the agent may check in, report a no-show, or be marked as absent. */
    val checkInAfter: Duration = Duration.ofHours(4),
    /** The customer can't be reported absent before this delay. */
    val noShowDelay: Duration = Duration.ofMinutes(15),
    /** The minimum length of a visit, before the agent may check out. */
    val checkOutMinimum: Duration = Duration.ofMinutes(10),
    /** A visit request not scheduled within this delay expires. */
    val expireAfter: Duration = Duration.ofHours(48),
    /** A confirmed visit starting within this delay is reminded. */
    val remindBefore: Duration = Duration.ofHours(24),
    /** Wrong codes after which the visit's code is locked. */
    val maxCodeAttempts: Int = 5,
    /** A check-in farther than this from the listing is flagged. */
    val flagDistanceMeters: Int = 300,
)
