package com.wutsi.ndopify

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
@EnableCaching
@EntityScan(
    basePackages = [
        "com.wutsi.ndopify.agent.server.domain",
        "com.wutsi.ndopify.refdata.server.domain",
        "com.wutsi.ndopify.security.server.domain",
    ],
)
@EnableJpaRepositories(
    basePackages = [
        "com.wutsi.ndopify.agent.server.dao",
        "com.wutsi.ndopify.refdata.server.dao",
        "com.wutsi.ndopify.security.server.dao",
    ],
)
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
