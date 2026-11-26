package com.wutsi.ndopify.mandate.server.mapper

import com.wutsi.ndopify.mandate.dto.MandateDocument
import com.wutsi.ndopify.mandate.dto.MandateDocumentPage
import com.wutsi.ndopify.mandate.server.domain.MandateDocumentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateDocumentPageEntity
import org.springframework.stereotype.Service

/**
 * Maps a [MandateDocumentEntity] and its pages to the DTO. The page's storage path is never handed out: only
 * [urlOf]'s presigned URL, so the page DTO carries no path. The extracted fields stay empty until Task 4.
 */
@Service
class MandateDocumentMapper {
    fun toMandateDocument(
        entity: MandateDocumentEntity,
        pages: List<MandateDocumentPageEntity>,
        urlOf: (MandateDocumentPageEntity) -> String,
    ): MandateDocument {
        return MandateDocument(
            id = entity.id ?: -1,
            mandateId = entity.mandateId,
            type = entity.type,
            title = entity.title,
            status = entity.status,
            problemCodes = entity.problemCodes,
            fields = emptyList(),
            issuedAt = entity.issuedAt,
            expiresAt = entity.expiresAt,
            pages = pages.map { page -> toMandateDocumentPage(page, urlOf) },
            version = entity.version ?: 0,
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
        )
    }

    private fun toMandateDocumentPage(
        page: MandateDocumentPageEntity,
        urlOf: (MandateDocumentPageEntity) -> String,
    ): MandateDocumentPage = MandateDocumentPage(
        id = page.id ?: -1,
        index = page.pageIndex,
        contentType = page.mimeType,
        url = urlOf(page),
        createdAt = page.createdAt,
        modifiedAt = page.modifiedAt,
    )
}
