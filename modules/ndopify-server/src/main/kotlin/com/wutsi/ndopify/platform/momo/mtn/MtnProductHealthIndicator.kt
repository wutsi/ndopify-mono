package com.wutsi.platform.payment.provider.mtn.spring

import com.wutsi.platform.payment.provider.mtn.product.Product
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import java.util.UUID

class MTNProductHealthIndicator(
    private val environment: String,
    private val product: Product,
) : HealthIndicator {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MTNProductHealthIndicator::class.java)
    }

    override fun health(): Health {
        val now = System.currentTimeMillis()
        try {
            product.token(UUID.randomUUID().toString())
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
