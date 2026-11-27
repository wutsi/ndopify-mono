package com.wutsi.ndopify.party.server.endpoint

import com.wutsi.ndopify.common.dto.AttachUploadRequest
import com.wutsi.ndopify.common.dto.PresignUploadRequest
import com.wutsi.ndopify.common.dto.PresignUploadResponse
import com.wutsi.ndopify.party.dto.SearchPartyRequest
import com.wutsi.ndopify.party.dto.SearchPartyResponse
import com.wutsi.ndopify.party.server.mapper.PartyMapper
import com.wutsi.ndopify.party.server.service.PartyAccessService
import com.wutsi.ndopify.party.server.service.PartyService
import com.wutsi.ndopify.util.jpa.OffsetLimitPageRequest
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
@RequestMapping("/v1/parties")
class PartyEndpoints(
    private val service: PartyService,
    private val mapper: PartyMapper,
    private val accessService: PartyAccessService,
) {
    @GetMapping
    fun search(
        @ModelAttribute request: SearchPartyRequest,
    ): SearchPartyResponse {
        accessService.ensureCanSearch()
        val parties = service.search(request.copy(limit = OffsetLimitPageRequest.limit(request.limit)))
        return SearchPartyResponse(
            parties = parties.map { party -> mapper.toPartySummary(party) },
        )
    }

    /** Uploads the party's profile photo (multipart, a JPEG, PNG or WEBP image), replacing its current one. */
    @PostMapping("/{id}/photo")
    fun photo(
        @PathVariable id: Long,
        @RequestParam multipartFile: MultipartFile,
    ) {
        service.uploadPhoto(id, multipartFile)
    }

    /** A short-lived URL to upload the party's profile photo straight to the storage; no url with local storage. */
    @PostMapping("/{id}/photo/presign")
    fun presignPhoto(
        @PathVariable id: Long,
        @RequestBody request: PresignUploadRequest,
    ): PresignUploadResponse = service.presign(id, request.contentType)

    /** Attaches the profile photo uploaded straight to the storage, at the path `/photo/presign` returned. */
    @PostMapping("/{id}/photo/attach")
    fun attachPhoto(
        @PathVariable id: Long,
        @RequestBody request: AttachUploadRequest,
    ) {
        service.attach(id, request.path)
    }
}
