package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.party.dto.CreateKycCaseRequest
import com.wutsi.ndopify.party.dto.CreateKycCaseResponse
import com.wutsi.ndopify.party.dto.GetKycCaseResponse
import com.wutsi.ndopify.party.dto.GetKycImageUrlResponse
import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.dto.KycCase
import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.dto.SearchKycCaseResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client for the ndopify-server KYC case endpoints (`/v1/kyc/cases`). Cases are tenant-scoped (`X-Tenant-ID`, see
 * `RestConfiguration`). Agents always verify with their national ID card, as `AgentService.create` opens their
 * first case with it.
 */
@Service
class NdKycClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,

    builder: RestClient.Builder,
) {
    companion object {
        val IDENTIFICATION_TYPE = IdentificationType.NATIONAL_ID
    }

    private val rest: RestClient = builder
        .baseUrl(baseUrl)
        .build()

    /** The party's current case, the one it opened last, with its images; null when it has none. */
    fun current(partyId: Long): KycCase? {
        val summary = rest.get()
            .uri { uri -> uri.path("/v1/kyc/cases").queryParam("partyId", partyId).queryParam("limit", 1).build() }
            .retrieve()
            .body<SearchKycCaseResponse>()
            ?.cases
            ?.firstOrNull()
            ?: return null
        return get(summary.id)
    }

    fun get(id: String): KycCase =
        (
            rest.get()
                .uri("/v1/kyc/cases/{id}", id)
                .retrieve()
                .body<GetKycCaseResponse>()
                ?: GetKycCaseResponse()
            ).case

    /** Opens a new PENDING_VERIFICATION case for [partyId] and returns its id. */
    fun create(partyId: Long): String =
        rest.post()
            .uri("/v1/kyc/cases")
            .body(CreateKycCaseRequest(partyId = partyId, identificationType = IDENTIFICATION_TYPE))
            .retrieve()
            .body<CreateKycCaseResponse>()
            ?.caseId
            ?: ""

    /**
     * Adds (or replaces) the [imageType] photo of a PENDING_VERIFICATION case. [mimeType] is sent as the part's content type: the
     * server rejects anything that isn't `image/` with `IDENTIFICATION_IMAGE_INVALID_MIME_TYPE`.
     */
    fun upload(caseId: String, imageType: KycImageType, content: ByteArray, mimeType: String) {
        val file = object : ByteArrayResource(content) {
            // A file name makes the part a file upload (a `MultipartFile`) on the server side.
            override fun getFilename(): String = imageType.name.lowercase()
        }
        val partHeaders = HttpHeaders().apply { contentType = MediaType.parseMediaType(mimeType) }
        val body = LinkedMultiValueMap<String, Any>().apply { add("multipartFile", HttpEntity(file, partHeaders)) }

        rest.post()
            .uri { uri ->
                uri.path("/v1/kyc/cases/{id}/images/upload")
                    .queryParam("identificationType", IDENTIFICATION_TYPE)
                    .queryParam("imageType", imageType)
                    .build(caseId)
            }
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body)
            .retrieve()
            .toBodilessEntity()
    }

    /** PENDING_VERIFICATION → SUBMITTED. Fails with `KYC_CASE_NOT_PENDING` or `KYC_CASE_MISSING_IDENTIFICATION_IMAGE`. */
    fun submit(caseId: String) {
        rest.post()
            .uri("/v1/kyc/cases/{id}/submit", caseId)
            .retrieve()
            .toBodilessEntity()
    }

    /** A short-lived URL of the image, for an `<img src>`. */
    fun imageUrl(imageId: String): String =
        rest.get()
            .uri("/v1/kyc/cases/images/{id}/url", imageId)
            .retrieve()
            .body<GetKycImageUrlResponse>()
            ?.url
            ?: ""
}
