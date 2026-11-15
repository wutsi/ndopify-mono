package com.wutsi.ndopify.agent.server.service

import org.springframework.stereotype.Service
import java.security.SecureRandom

/** Generates the 6-digit code an agent sends to the bot as `/connect <code>`. */
@Service
class TelegramCodeGenerator {
    fun generate(): String = (SecureRandom().nextInt(900_000) + 100_000).toString()
}
