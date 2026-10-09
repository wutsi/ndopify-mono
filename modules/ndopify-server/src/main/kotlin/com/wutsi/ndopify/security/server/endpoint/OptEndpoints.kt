package com.wutsi.ndopify.security.server.endpoint

import com.wutsi.ndopify.security.dto.CreateOtpRequest
import com.wutsi.ndopify.security.server.service.OtpService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/otp")
class OptEndpoints(private val service: OtpService) {
    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateOtpRequest
    ) {
        service.create(request)
    }
}
