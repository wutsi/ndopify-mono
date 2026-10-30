package com.wutsi.ndopify.lead.server.endpoint

import com.wutsi.ndopify.lead.dto.CreateLeadRequest
import com.wutsi.ndopify.lead.dto.CreateLeadResponse
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/v1/leads")
class LeadEndpoints {
    @PostMapping
    fun create(@RequestBody @Valid request: CreateLeadRequest): CreateLeadResponse {
        TODO()
    }
}
