package com.wutsi.ndopify.config.platform

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.MustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.filter.HtmlEscapeFilter
import dev.jcputney.mjml.ClasspathIncludeResolver
import dev.jcputney.mjml.MjmlConfiguration
import dev.jcputney.mjml.MjmlRenderer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender

@Configuration
open class MailConfiguration(
    private val mail: JavaMailSender,
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
    fun mustacheFactory(): MustacheFactory {
        // ClasspathResolver: the mail templates share partials (the agent's signature footer)
        return DefaultMustacheFactory(ClasspathResolver())
    }

    @Bean
    fun mailBodyResolver(): MailBodyResolver {
        return MailBodyResolver(mjmlRenderer(), mustacheFactory(), listOf(HtmlEscapeFilter()))
    }
}
