package com.wutsi.ndopify.visit.server.service

import org.springframework.stereotype.Service
import java.security.SecureRandom

/** The 4-digit code the customer reads to its agent at check-in. Never logged, never returned to anybody but it. */
@Service
class VisitCodeGenerator {
    private val random = SecureRandom()

    fun generate(): String = String.format("%04d", random.nextInt(10000))
}
