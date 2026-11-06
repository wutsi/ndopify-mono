package com.wutsi.ndopify.npc.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** The executor behind a page's parallel REST calls (see [Parallel]). */
@Configuration
class ParallelConfiguration {
    @Bean(destroyMethod = "close")
    fun parallel(): Parallel = Parallel()
}
