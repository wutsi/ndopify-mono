package com.wutsi.ndopify.platform.momo.mtn

import org.slf4j.LoggerFactory
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

class MtnProductHealthIndicator(
    private val environment: String,
    private val product: MtnProduct,
) : HealthIndicator {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MtnProductHealthIndicator::class.java)
    }

    override fun health(): Health {
        val now = System.currentTimeMillis()
        try {
            product.authenticate()
            return Health.up()
                .withDetail("environment", environment)
                .withDetail("durationMillis", System.currentTimeMillis() - now)
                .build()
        } catch (ex: Exception) {
            LOGGER.warn("Healthcheck error", ex)
            return Health.down()
                .withDetail("environment", environment)
                .withDetail("durationMillis", System.currentTimeMillis() - now)
                .withException(ex)
                .build()
        }
    }
}
