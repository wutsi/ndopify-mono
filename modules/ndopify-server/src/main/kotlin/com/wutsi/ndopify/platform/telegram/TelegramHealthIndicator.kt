package com.wutsi.ndopify.platform.telegram

import org.slf4j.LoggerFactory
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.health.contributor.Status
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Checks the bot with `getMe`: UP and the bot's username when Telegram answers, DOWN when it's unreachable or the
 * token is invalid. The result is cached, for [upTtl] when UP and [downTtl] (shorter, to see a recovery sooner) when
 * DOWN, so that a `/actuator/health` poll never hammers Telegram. [health] is synchronized, so concurrent probes wait
 * for the call in flight instead of each making one.
 *
 * A DOWN shows the error's class only, never its message: the token is part of every Telegram URL, so neither an
 * exception's text nor a detail may carry it.
 */
class TelegramHealthIndicator(
    private val client: TelegramClient,
    private val clock: Clock,
    private val upTtl: Duration,
    private val downTtl: Duration,
) : HealthIndicator {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(TelegramHealthIndicator::class.java)
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

    private fun check(): Health =
        try {
            val user = client.getMe()
            Health.up()
                .withDetail("username", user.username ?: "-")
                .build()
        } catch (ex: Exception) {
            LOGGER.warn("Telegram healthcheck error", ex)
            Health.down()
                .withDetail("error", ex.javaClass.simpleName)
                .build()
        }
}
