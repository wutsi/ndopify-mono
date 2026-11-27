package com.wutsi.ndopify.platform.mail.verification

import org.slf4j.LoggerFactory
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.health.contributor.Status
import software.amazon.awssdk.services.sesv2.SesV2Client
import software.amazon.awssdk.services.sesv2.model.GetAccountRequest
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Checks SES with `GetAccount`: UP with the account's `sendingEnabled` and `productionAccessEnabled` flags and the
 * call's latency when it answers, DOWN with the exception's class only when it doesn't. `GetAccount` is free and
 * validates the credentials, the region and reachability, though not the `GetEmailAddressInsights` permission itself.
 *
 * `/actuator/health` is public and the SES API is throttled around one request per second, so the result is cached:
 * for [upTtl] when UP, for [downTtl] (shorter, to see a recovery sooner) when DOWN. [health] is synchronized, so
 * concurrent probes wait for the call in flight instead of each making one. A DOWN never carries the credentials, an
 * account id, an e-mail address nor the exception's message.
 */
class SesEmailVerifierHealthIndicator(
    private val client: SesV2Client,
    private val clock: Clock,
    private val upTtl: Duration,
    private val downTtl: Duration,
) : HealthIndicator {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(SesEmailVerifierHealthIndicator::class.java)
    }

    private var cached: Health? = null
    private var expiresAt: Instant = Instant.MIN

    @Synchronized
    override fun health(): Health {
        val now = clock.instant()
        cached?.let { health -> if (now.isBefore(expiresAt)) return health }

        val health = check()
        cached = health
        expiresAt = now.plus(if (health.status == Status.UP) upTtl else downTtl)
        return health
    }

    private fun check(): Health {
        val start = System.currentTimeMillis()
        return try {
            val account = client.getAccount(GetAccountRequest.builder().build())
            Health.up()
                .withDetail("sendingEnabled", account.sendingEnabled())
                .withDetail("productionAccessEnabled", account.productionAccessEnabled())
                .withDetail("latency", System.currentTimeMillis() - start)
                .build()
        } catch (ex: Exception) {
            LOGGER.warn("SES healthcheck error", ex)
            Health.down()
                .withDetail("error", ex.javaClass.simpleName)
                .build()
        }
    }
}
