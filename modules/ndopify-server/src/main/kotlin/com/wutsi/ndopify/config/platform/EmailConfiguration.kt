package com.wutsi.ndopify.config.platform

import com.wutsi.ndopify.platform.email.EmailVerifier
import com.wutsi.ndopify.platform.email.EmailVerifierNone
import com.wutsi.ndopify.platform.email.EmailVerifierSES
import com.wutsi.ndopify.platform.email.SesEmailVerifierHealthIndicator
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sesv2.SesV2Client
import java.time.Clock
import java.time.Duration

/**
 * Wires the e-mail verification: the `none` verifier by default, the SES client and its verifier when
 * `ndopify.mail.verification.provider=ses`. The SDK's own call timeout bounds a slow SES, so it never holds a
 * submission past `ndopify.mail.verification.timeout-seconds`.
 */
@Configuration
class EmailConfiguration(
    @param:Value("\${ndopify.mail.verification.ses.region}") private val region: String,
    @param:Value("\${ndopify.mail.verification.timeout-seconds}") private val timeoutSeconds: Long,
    @param:Value("\${ndopify.mail.verification.health.up-ttl-minutes}") private val upTtlMinutes: Long,
    @param:Value("\${ndopify.mail.verification.health.down-ttl-seconds}") private val downTtlSeconds: Long,
    private val clock: Clock,
) {
    @Bean
    @ConditionalOnProperty(name = ["ndopify.mail.verification.provider"], havingValue = "none", matchIfMissing = true)
    fun emailVerifierNone(): EmailVerifier = EmailVerifierNone()

    @Bean
    @ConditionalOnProperty(name = ["ndopify.mail.verification.provider"], havingValue = "ses")
    fun sesV2Client(): SesV2Client =
        SesV2Client.builder()
            .region(Region.of(region))
            .overrideConfiguration { it.apiCallTimeout(Duration.ofSeconds(timeoutSeconds)) }
            .build()

    @Bean
    @ConditionalOnProperty(name = ["ndopify.mail.verification.provider"], havingValue = "ses")
    fun emailVerifierSes(client: SesV2Client): EmailVerifier = EmailVerifierSES(client)

    /**
     * Registered here, not by `management.health.<id>.enabled`: Spring Boot registers every `HealthIndicator` bean
     * whatever that property says. With `none` there is no component and no AWS call at all (see
     * `TelegramConfiguration`).
     */
    @Bean
    @ConditionalOnProperty(name = ["ndopify.mail.verification.provider"], havingValue = "ses")
    fun sesEmailVerifierHealthIndicator(client: SesV2Client): HealthIndicator =
        SesEmailVerifierHealthIndicator(
            client,
            clock,
            Duration.ofMinutes(upTtlMinutes),
            Duration.ofSeconds(downTtlSeconds),
        )
}
