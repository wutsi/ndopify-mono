package com.wutsi.ndopify.party.server.endpoint

import com.wutsi.ndopify.party.dto.CreateIdentificationRequest
import com.wutsi.ndopify.party.dto.GetIdentificationImageUrlResponse
import com.wutsi.ndopify.party.dto.GetIdentificationResponse
import com.wutsi.ndopify.party.dto.IdentificationImageType
import com.wutsi.ndopify.party.dto.SearchIdentificationRequest
import com.wutsi.ndopify.party.dto.SearchIdentificationResponse
import com.wutsi.ndopify.party.server.mapper.IdentificationMapper
import com.wutsi.ndopify.party.server.service.IdentificationService
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
@RequestMapping("/v1/identifications")
class IdentificationEndpoints(
    private val service: IdentificationService,
    private val mapper: IdentificationMapper,
) {
    @PostMapping("")
    fun create(
        @RequestBody @Valid request: CreateIdentificationRequest,
    ) {
        service.create(request)
    }

    @GetMapping("")
    fun search(
        @ModelAttribute request: SearchIdentificationRequest,
    ): SearchIdentificationResponse {
        val identifications = service.search(request)
        return SearchIdentificationResponse(
            identifications = identifications.map { identification -> mapper.toIdentificationSummary(identification) },
        )
    }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: String,
    ): GetIdentificationResponse {
        val identification = service.findById(id)
        return GetIdentificationResponse(identification = mapper.toIdentification(identification))
    }

    @PostMapping("/{id}/images/upload")
    fun upload(
        @PathVariable id: String,
        @RequestParam imageType: IdentificationImageType,
        @RequestParam(required = false) multipartFile: MultipartFile,
    ) {
        service.upload(id, imageType, multipartFile)
    }

    @GetMapping("/images/{id}/url")
    fun getImageUrl(
        @PathVariable id: String,
    ): GetIdentificationImageUrlResponse {
        val url = service.getImageUrl(id)
        return GetIdentificationImageUrlResponse(url.toString())
    }
}
