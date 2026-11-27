package com.wutsi.ndopify.config.platform

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.MustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.filter.HtmlEscapeFilter
import com.wutsi.ndopify.platform.mail.verification.EmailVerifier
import com.wutsi.ndopify.platform.mail.verification.EmailVerifierNone
import com.wutsi.ndopify.platform.mail.verification.EmailVerifierSES
import com.wutsi.ndopify.platform.mail.verification.SesEmailVerifierHealthIndicator
import dev.jcputney.mjml.ClasspathIncludeResolver
import dev.jcputney.mjml.MjmlConfiguration
import dev.jcputney.mjml.MjmlRenderer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sesv2.SesV2Client
import java.time.Clock
import java.time.Duration

/**
 * Wires e-mail: sending (`MailService`, `MjmlRenderer`, `MailBodyResolver`) and, behind
 * `ndopify.mail.verification.provider`, verification — the `none` verifier by default, the SES client, its verifier
 * and its health indicator when `ses`. The SDK's own call timeout bounds a slow SES, so it never holds a submission
 * past `ndopify.mail.verification.timeout-seconds`.
 */
@Configuration
open class MailConfiguration(
    private val mail: JavaMailSender,
    @param:Value("\${ndopify.mail.verification.ses.region}") private val region: String,
    @param:Value("\${ndopify.mail.verification.ses.access-key}") private val accessKey: String,
    @param:Value("\${ndopify.mail.verification.ses.secret-key}") private val secretKey: String,
    @param:Value("\${ndopify.mail.verification.timeout-seconds}") private val timeoutSeconds: Long,
    @param:Value("\${ndopify.mail.verification.health.up-ttl-minutes}") private val upTtlMinutes: Long,
    @param:Value("\${ndopify.mail.verification.health.down-ttl-seconds}") private val downTtlSeconds: Long,
    private val clock: Clock,
) {
    @Bean
    open fun mailService(
        @Value("\${spring.mail.properties.mail.smtp.from:\${spring.mail.username:no-reply@wutsi.com}}") from: String,
    ): MailService {
        return MailService(mail, from)
    }

    @Bean
    open fun mjmlRenderer(): MjmlRenderer {
        val config = MjmlConfiguration.builder()
            .direction("ltr")
            .includeResolver(ClasspathIncludeResolver())
            .sanitizeOutput(true)
            .build()
        return MjmlRenderer.create(config)
    }

    @Bean
    open fun mustacheFactory(): MustacheFactory {
        // ClasspathResolver: the mail templates share partials (the agent's signature footer)
        return DefaultMustacheFactory(ClasspathResolver())
    }

    @Bean
    open fun mailBodyResolver(): MailBodyResolver {
        return MailBodyResolver(mjmlRenderer(), mustacheFactory(), listOf(HtmlEscapeFilter()))
    }

    @Bean
    @ConditionalOnProperty(name = ["ndopify.mail.verification.provider"], havingValue = "none", matchIfMissing = true)
    open fun emailVerifierNone(): EmailVerifier = EmailVerifierNone()

    @Bean
    @ConditionalOnProperty(name = ["ndopify.mail.verification.provider"], havingValue = "ses")
    open fun sesV2Client(): SesV2Client {
        val builder = SesV2Client.builder()
            .region(Region.of(region))
            .overrideConfiguration { it.apiCallTimeout(Duration.ofSeconds(timeoutSeconds)) }
        if (accessKey.isNotBlank() && secretKey.isNotBlank()) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)),
            )
        }
        return builder.build()
    }

    @Bean
    @ConditionalOnProperty(name = ["ndopify.mail.verification.provider"], havingValue = "ses")
    open fun emailVerifierSes(client: SesV2Client): EmailVerifier = EmailVerifierSES(client)

    /**
     * Registered here, not by `management.health.<id>.enabled`: Spring Boot registers every `HealthIndicator` bean
     * whatever that property says. With `none` there is no component and no AWS call at all (see
     * `TelegramConfiguration`).
     */
    @Bean
    @ConditionalOnProperty(name = ["ndopify.mail.verification.provider"], havingValue = "ses")
    open fun sesEmailVerifierHealthIndicator(client: SesV2Client): HealthIndicator =
        SesEmailVerifierHealthIndicator(
            client,
            clock,
            Duration.ofMinutes(upTtlMinutes),
            Duration.ofSeconds(downTtlSeconds),
        )
}
