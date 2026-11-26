package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.common.dto.PresignUploadRequest
import com.wutsi.ndopify.common.dto.PresignUploadResponse
import com.wutsi.ndopify.mandate.dto.CreateMandateDocumentRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateDocumentResponse
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.dto.GetMandateDocumentResponse
import com.wutsi.ndopify.mandate.dto.SearchMandateDocumentResponse
import com.wutsi.ndopify.mandate.server.mapper.MandateDocumentMapper
import com.wutsi.ndopify.mandate.server.service.MandateAccessService
import com.wutsi.ndopify.mandate.server.service.MandateDocumentService
import com.wutsi.ndopify.mandate.server.service.MandateService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * The documents of a mandate, private and only for its agent or an admin. The reads run the access check here, like
 * [MandateEndpoints]; the writes are checked by [MandateDocumentService].
 */
@RestController
@RequestMapping("/v1/mandates/{id}/documents")
class MandateDocumentEndpoints(
    private val service: MandateDocumentService,
    private val mandateService: MandateService,
    private val access: MandateAccessService,
    private val mapper: MandateDocumentMapper,
) {
    @GetMapping
    fun search(@PathVariable id: Long): SearchMandateDocumentResponse {
        val mandate = mandateService.findById(id)
        access.ensureCanUpdate(mandate)
        val documents = service.search(id)
        val pages = service.findPages(documents.map { it.id!! })
        return SearchMandateDocumentResponse(
            documents = documents.map { document ->
                mapper.toMandateDocument(document, pages[document.id!!] ?: emptyList(), service::pageUrl)
            },
        )
    }

    @GetMapping("/{documentId}")
    fun get(@PathVariable id: Long, @PathVariable documentId: Long): GetMandateDocumentResponse {
        val mandate = mandateService.findById(id)
        access.ensureCanUpdate(mandate)
        val document = service.findDocument(mandate, documentId)
        val pages = service.findPages(listOf(document.id!!))[document.id!!] ?: emptyList()
        return GetMandateDocumentResponse(
            document = mapper.toMandateDocument(document, pages, service::pageUrl),
        )
    }

    @PostMapping("/presign")
    fun presign(
        @PathVariable id: Long,
        @RequestBody request: PresignUploadRequest,
    ): PresignUploadResponse = service.presign(id, request.contentType)

    @PostMapping
    fun create(
        @PathVariable id: Long,
        @RequestBody request: CreateMandateDocumentRequest,
    ): CreateMandateDocumentResponse {
        val document = service.create(id, request)
        return CreateMandateDocumentResponse(documentId = document.id ?: -1)
    }

    @PostMapping("/upload")
    fun upload(
        @PathVariable id: Long,
        @RequestParam type: DocumentType,
        @RequestParam(required = false) title: String?,
        @RequestParam(required = false) files: List<MultipartFile>?,
    ): CreateMandateDocumentResponse {
        val document = service.upload(id, type, title, files.orEmpty())
        return CreateMandateDocumentResponse(documentId = document.id ?: -1)
    }
}
