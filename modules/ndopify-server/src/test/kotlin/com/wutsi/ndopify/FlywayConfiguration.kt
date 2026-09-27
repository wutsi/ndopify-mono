package com.wutsi.ndopify

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FlywayConfiguration {
    @Bean
    fun flywayMigrationStrategy(): FlywayMigrationStrategy = FlywayMigrationStrategy { flyway ->
        if (!cleaned) {
            flyway.clean()
            cleaned = true
        }
        flyway.migrate()
    }

    companion object {
        var cleaned: Boolean = false
    }
}
