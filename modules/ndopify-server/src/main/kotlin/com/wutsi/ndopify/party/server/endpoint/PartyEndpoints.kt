package com.wutsi.ndopify.party.server.endpoint

import com.wutsi.ndopify.party.dto.UpdatePhotoRequest
import com.wutsi.ndopify.party.server.service.PartyService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/parties")
class PartyEndpoints(
    private val service: PartyService,
) {
    @PostMapping("/{id}/photo")
    fun photo(
        @PathVariable id: Long,
        @RequestBody @Valid request: UpdatePhotoRequest,
    ) {
        service.updatePhoto(id, request)
    }
}
