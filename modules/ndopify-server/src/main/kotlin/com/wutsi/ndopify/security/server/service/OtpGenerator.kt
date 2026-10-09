package com.wutsi.ndopify.security.server.service

import org.springframework.stereotype.Service
import java.security.SecureRandom

@Service
class OtpGenerator {
    fun generate(): String {
        val secureRandom = SecureRandom()
        val otp = secureRandom.nextInt(900_000) + 100_000
        return otp.toString()
    }
}
