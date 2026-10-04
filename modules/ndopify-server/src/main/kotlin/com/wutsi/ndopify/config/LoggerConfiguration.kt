package com.wutsi.ndopify.config

import com.wutsi.koki.platform.logger.servlet.KVLoggerFilter
import com.wutsi.ndopify.platform.logger.DefaultKVLogger
import com.wutsi.ndopify.platform.logger.DynamicKVLogger
import com.wutsi.ndopify.platform.logger.KVLogger
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import org.springframework.context.annotation.ScopedProxyMode
import org.springframework.core.Ordered

@Configuration
open class LoggerConfiguration(
    private val context: ApplicationContext,
) {
    @Bean
    open fun loggerFilter(): FilterRegistrationBean<KVLoggerFilter> {
        val filter = FilterRegistrationBean(KVLoggerFilter(logger()))
        filter.order = Ordered.LOWEST_PRECEDENCE
        return filter
    }

    @Bean
    open fun logger(): KVLogger =
        DynamicKVLogger(context)

    @Bean
    @Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
    open fun requestLogger(): DefaultKVLogger =
        DefaultKVLogger()
}
