package com.wutsi.ndopify.party.server.endpoint

import com.wutsi.ndopify.party.dto.CreateIdentificationRequest
import com.wutsi.ndopify.party.dto.GetIdentificationResponse
import com.wutsi.ndopify.party.dto.SearchIdentificationResponse
import com.wutsi.ndopify.party.server.mapper.IdentificationMapper
import com.wutsi.ndopify.party.server.service.IdentificationService
import com.wutsi.ndopify.party.server.service.PartyService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1")
class IdentificationEndpoints(
    private val service: IdentificationService,
    private val partyService: PartyService,
    private val mapper: IdentificationMapper,
) {
    @PostMapping("/parties/{id}/identifications")
    fun create(
        @PathVariable id: Long,
        @RequestBody @Valid request: CreateIdentificationRequest,
    ) {
        val party = partyService.findById(id)
        service.create(party, request)
    }

    @GetMapping("/parties/{id}/identifications")
    fun getByParty(
        @PathVariable id: Long,
    ): SearchIdentificationResponse {
        val party = partyService.findById(id)
        val identifications = service.findByParty(party)
        return SearchIdentificationResponse(
            identifications = identifications.map { identification -> mapper.toIdentificationSummary(identification) },
        )
    }

    @GetMapping("/identifications/{id}")
    fun get(
        @PathVariable id: String,
    ): GetIdentificationResponse {
        val identification = service.findById(id)
        return GetIdentificationResponse(identification = mapper.toIdentification(identification))
    }
}
