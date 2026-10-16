package com.wutsi.ndopify.party.server.endpoint

import com.wutsi.ndopify.party.dto.CreateKycCaseRequest
import com.wutsi.ndopify.party.dto.CreateKycCaseResponse
import com.wutsi.ndopify.party.dto.GetKycCaseResponse
import com.wutsi.ndopify.party.dto.GetKycImageUrlResponse
import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.dto.SearchKycCaseRequest
import com.wutsi.ndopify.party.dto.SearchKycCaseResponse
import com.wutsi.ndopify.party.server.mapper.KycCaseMapper
import com.wutsi.ndopify.party.server.service.KycService
import com.wutsi.ndopify.platform.endpoint.OpsEndpoint
import com.wutsi.ndopify.util.jpa.OffsetLimitPageRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

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
        val cases = service.search(request.copy(limit = OffsetLimitPageRequest.limit(request.limit)))
        return SearchKycCaseResponse(
            cases = cases.map { case -> mapper.toKycCaseSummary(case) },
        )
    }

    @PostMapping("/{id}/submit")
    fun submit(@PathVariable id: String) {
        service.submit(id)
    }

    // Cases are verified offline, by KycJobs: by hand only outside prod
    @OpsEndpoint
    @PostMapping("/{id}/verify")
    fun verify(@PathVariable id: String) {
        service.verify(id)
    }

    @PostMapping("/{id}/images/upload")
    fun upload(
        @PathVariable id: String,
        @RequestParam identificationType: IdentificationType,
        @RequestParam imageType: KycImageType,
        @RequestParam(required = false) multipartFile: MultipartFile,
    ) {
        service.upload(id, identificationType, imageType, multipartFile)
    }

    @GetMapping("/images/{id}/url")
    fun imageUrl(
        @PathVariable id: String,
    ): GetKycImageUrlResponse {
        val url = service.getImageUrl(id)
        return GetKycImageUrlResponse(url.toString())
    }
}
