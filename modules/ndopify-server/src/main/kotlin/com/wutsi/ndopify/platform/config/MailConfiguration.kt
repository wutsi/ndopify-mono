package com.wutsi.ndopify.platform.config

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.MustacheFactory
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import dev.jcputney.mjml.ClasspathIncludeResolver
import dev.jcputney.mjml.MjmlConfiguration
import dev.jcputney.mjml.MjmlRenderer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender

@Configuration
open class MailConfiguration(
    private val mail: JavaMailSender,
) {
    @Bean
    open fun mailService(): MailService {
        return MailService(mail)
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
    fun mustacheFactory(): MustacheFactory {
        return DefaultMustacheFactory()
    }

    @Bean
    fun mailBodyResolver(): MailBodyResolver {
        return MailBodyResolver(mjmlRenderer(), mustacheFactory())
    }
}
