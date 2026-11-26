package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.server.service.document.MandateDocumentExtractionService
import com.wutsi.ndopify.platform.endpoint.OpsEndpoint
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * The extraction's operational endpoints (off in prod). `POST /v1/mandate-documents/extract?type=` sends a whole type
 * back to `UPLOADED` so the next job run re-reads it, after a prompt or schema change; a type without an extractor is
 * a 400.
 */
@RestController
@RequestMapping("/v1/mandate-documents")
class MandateDocumentOpsEndpoints(
    private val service: MandateDocumentExtractionService,
) {
    @OpsEndpoint
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/extract")
    fun extract(@RequestParam type: DocumentType) {
        service.requeueByType(type)
    }
}
