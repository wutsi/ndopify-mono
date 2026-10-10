package com.wutsi.ndopify.party.server.endpoint

import com.wutsi.ndopify.party.dto.SearchPartyRequest
import com.wutsi.ndopify.party.dto.SearchPartyResponse
import com.wutsi.ndopify.party.dto.UpdatePhotoRequest
import com.wutsi.ndopify.party.server.mapper.PartyMapper
import com.wutsi.ndopify.party.server.service.PartyService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/parties")
class PartyEndpoints(
    private val service: PartyService,
    private val mapper: PartyMapper,
) {
    @GetMapping
    fun search(
        @ModelAttribute request: SearchPartyRequest,
    ): SearchPartyResponse {
        val parties = service.search(request)
        return SearchPartyResponse(
            parties = parties.map { party -> mapper.toPartySummary(party) },
        )
    }

    @PostMapping("/{id}/photo")
    fun photo(
        @PathVariable id: Long,
        @RequestBody @Valid request: UpdatePhotoRequest,
    ) {
        service.updatePhoto(id, request)
    }
}
