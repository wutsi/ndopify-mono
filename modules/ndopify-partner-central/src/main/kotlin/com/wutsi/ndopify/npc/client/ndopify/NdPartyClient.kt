package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.common.dto.AttachUploadRequest
import com.wutsi.ndopify.common.dto.PresignUploadRequest
import com.wutsi.ndopify.common.dto.PresignUploadResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/** Client for the ndopify-server party endpoints (`/v1/parties`). Parties are tenant-scoped (`X-Tenant-ID`). */
@Service
class NdPartyClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,

    builder: RestClient.Builder,
) {
    private val rest: RestClient = builder
        .baseUrl(baseUrl)
        .build()

    /**
     * Replaces the party's profile photo. [mimeType] is sent as the part's content type: the server only takes
     * `image/jpeg`, `image/png` and `image/webp` (400 `HTTP_INVALID_PARAMETER` otherwise).
     */
    fun uploadPhoto(partyId: Long, content: ByteArray, mimeType: String) {
        val file = object : ByteArrayResource(content) {
            // A file name makes the part a file upload (a `MultipartFile`) on the server side.
            override fun getFilename(): String = "photo"
        }
        val partHeaders = HttpHeaders().apply { contentType = MediaType.parseMediaType(mimeType) }
        val body = LinkedMultiValueMap<String, Any>().apply { add("multipartFile", HttpEntity(file, partHeaders)) }

        rest.post()
            .uri("/v1/parties/{id}/photo", partyId)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body)
            .retrieve()
            .toBodilessEntity()
    }

    /**
     * A short-lived URL to upload the party's profile photo straight to the storage. Its `url` is null when the storage
     * can't presign (local): the caller falls back to [uploadPhoto].
     */
    fun presignPhoto(partyId: Long, contentType: String): PresignUploadResponse =
        rest.post()
            .uri("/v1/parties/{id}/photo/presign", partyId)
            .body(PresignUploadRequest(contentType))
            .retrieve()
            .body<PresignUploadResponse>()
            ?: PresignUploadResponse()

    /** Makes the photo uploaded straight to the storage, at the path [presignPhoto] returned, the party's photo. */
    fun attachPhoto(partyId: Long, path: String) {
        rest.post()
            .uri("/v1/parties/{id}/photo/attach", partyId)
            .body(AttachUploadRequest(path))
            .retrieve()
            .toBodilessEntity()
    }
}
