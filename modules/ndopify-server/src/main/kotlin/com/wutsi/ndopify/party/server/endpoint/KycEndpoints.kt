package com.wutsi.ndopify.party.server.endpoint

import com.wutsi.ndopify.party.dto.CreateKycCaseRequest
import com.wutsi.ndopify.party.dto.CreateKycCaseResponse
import com.wutsi.ndopify.party.dto.GetKycCaseResponse
import com.wutsi.ndopify.party.dto.SearchKycCaseRequest
import com.wutsi.ndopify.party.dto.SearchKycCaseResponse
import com.wutsi.ndopify.party.server.mapper.KycCaseMapper
import com.wutsi.ndopify.party.server.service.KycService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/kyc/cases")
class KycEndpoints(
    private val service: KycService,
    private val mapper: KycCaseMapper,
) {
    @PostMapping
    fun create(@RequestBody @Valid request: CreateKycCaseRequest): CreateKycCaseResponse {
        val case = service.create(request)
        return CreateKycCaseResponse(caseId = case.id)
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): GetKycCaseResponse {
        val case = service.findById(id)
        return GetKycCaseResponse(case = mapper.toKycCase(case))
    }

    @GetMapping
    fun search(@ModelAttribute request: SearchKycCaseRequest): SearchKycCaseResponse {
        val cases = service.search(request)
        return SearchKycCaseResponse(
            cases = cases.map { case -> mapper.toKycCaseSummary(case) },
        )
    }

    @PostMapping("/{id}/verify")
    fun verify(@PathVariable id: String) {
        service.verify(id)
    }
}
