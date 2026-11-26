package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.common.dto.AttachUploadRequest
import com.wutsi.ndopify.common.dto.PresignUploadRequest
import com.wutsi.ndopify.common.dto.PresignUploadResponse
import com.wutsi.ndopify.mandate.dto.AttachMandateDocumentPageResponse
import com.wutsi.ndopify.mandate.dto.CreateMandateDocumentRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateDocumentResponse
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.dto.GetMandateDocumentResponse
import com.wutsi.ndopify.mandate.dto.SearchMandateDocumentResponse
import com.wutsi.ndopify.mandate.dto.UpdateMandateDocumentRequest
import com.wutsi.ndopify.mandate.server.mapper.MandateDocumentMapper
import com.wutsi.ndopify.mandate.server.service.MandateAccessService
import com.wutsi.ndopify.mandate.server.service.MandateDocumentService
import com.wutsi.ndopify.mandate.server.service.MandateService
import com.wutsi.ndopify.mandate.server.service.document.MandateDocumentExtractionService
import com.wutsi.ndopify.platform.endpoint.OpsEndpoint
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
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
    private val extractionService: MandateDocumentExtractionService,
) {
    @GetMapping
    fun search(@PathVariable id: Long): SearchMandateDocumentResponse {
        val mandate = mandateService.findById(id)
        access.ensureCanUpdate(mandate)
        val documents = service.search(id)
        val pages = service.findPages(documents.map { it.id!! })
        return SearchMandateDocumentResponse(
            documents = documents.map { document ->
                mapper.toMandateDocument(document, pages[document.id!!] ?: emptyList())
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
            document = mapper.toMandateDocument(document, pages),
        )
    }

    /**
     * The page's authenticated preview: the resized, watermarked JPEG, generated now if it isn't stored yet. Same
     * checks and order as the document reads; a CLOSED / CANCELLED mandate still serves its pages. A generation or
     * storage failure is a 500, never the original bytes.
     */
    @GetMapping("/{documentId}/pages/{pageId}/preview")
    fun preview(
        @PathVariable id: Long,
        @PathVariable documentId: Long,
        @PathVariable pageId: Long,
    ): ResponseEntity<ByteArray> {
        val mandate = mandateService.findById(id)
        access.ensureCanUpdate(mandate)
        val document = service.findDocument(mandate, documentId)
        val page = service.findPage(document, pageId)
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .body(service.preview(mandate, page))
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

    /**
     * The ops re-run of one document, synchronously (off in prod): whatever its status, it's read again and the
     * following GET shows the new fields and codes. No access check: it's operational only.
     */
    @OpsEndpoint
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/{documentId}/extract")
    fun extract(@PathVariable id: Long, @PathVariable documentId: Long) {
        extractionService.extract(id, documentId)
    }

    @PostMapping("/{documentId}")
    fun rename(
        @PathVariable id: Long,
        @PathVariable documentId: Long,
        @RequestBody request: UpdateMandateDocumentRequest,
    ) {
        service.rename(id, documentId, request)
    }

    @PostMapping("/{documentId}/pages")
    fun addPage(
        @PathVariable id: Long,
        @PathVariable documentId: Long,
        @RequestBody request: AttachUploadRequest,
    ): AttachMandateDocumentPageResponse {
        val page = service.addPage(id, documentId, request.path)
        return AttachMandateDocumentPageResponse(pageId = page.id ?: -1)
    }

    @PostMapping("/{documentId}/pages/upload")
    fun uploadPage(
        @PathVariable id: Long,
        @PathVariable documentId: Long,
        @RequestParam file: MultipartFile,
    ): AttachMandateDocumentPageResponse {
        val page = service.uploadPage(id, documentId, file)
        return AttachMandateDocumentPageResponse(pageId = page.id ?: -1)
    }

    @PostMapping("/{documentId}/pages/{pageId}")
    fun replacePage(
        @PathVariable id: Long,
        @PathVariable documentId: Long,
        @PathVariable pageId: Long,
        @RequestBody request: AttachUploadRequest,
    ): AttachMandateDocumentPageResponse {
        val page = service.replacePage(id, documentId, pageId, request.path)
        return AttachMandateDocumentPageResponse(pageId = page.id ?: -1)
    }

    @PostMapping("/{documentId}/pages/{pageId}/upload")
    fun uploadReplacementPage(
        @PathVariable id: Long,
        @PathVariable documentId: Long,
        @PathVariable pageId: Long,
        @RequestParam file: MultipartFile,
    ): AttachMandateDocumentPageResponse {
        val page = service.uploadReplacementPage(id, documentId, pageId, file)
        return AttachMandateDocumentPageResponse(pageId = page.id ?: -1)
    }

    @DeleteMapping("/{documentId}/pages/{pageId}")
    fun deletePage(
        @PathVariable id: Long,
        @PathVariable documentId: Long,
        @PathVariable pageId: Long,
    ) {
        service.deletePage(id, documentId, pageId)
    }

    @DeleteMapping("/{documentId}")
    fun delete(
        @PathVariable id: Long,
        @PathVariable documentId: Long,
    ) {
        service.delete(id, documentId)
    }
}
