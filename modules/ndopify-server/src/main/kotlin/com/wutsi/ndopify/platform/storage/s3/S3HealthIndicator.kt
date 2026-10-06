package com.wutsi.ndopify.platform.storage.s3

import com.amazonaws.services.s3.AmazonS3
import org.slf4j.LoggerFactory
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

open class S3HealthIndicator(
    private val bucket: String,
    private val s3: AmazonS3,
) : HealthIndicator {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(S3HealthIndicator::class.java)
    }

    override fun health(): Health {
        val start = System.currentTimeMillis()
        try {
            val location = s3.getBucketLocation(bucket)
            return Health.up()
                .withDetail("bucket", this.bucket)
                .withDetail("location", location)
                .withDetail("latency", System.currentTimeMillis() - start)
                .build()
        } catch (ex: Exception) {
            LOGGER.warn("Healthcheck error", ex)
            return Health.down()
                .withDetail("bucket", this.bucket)
                .withDetail("latency", System.currentTimeMillis() - start)
                .withException(ex)
                .build()
        }
    }
}
