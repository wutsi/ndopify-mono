package com.wutsi.ndopify.mandate.server.mapper

import com.wutsi.ndopify.mandate.dto.MandateDocument
import com.wutsi.ndopify.mandate.dto.MandateDocumentPage
import com.wutsi.ndopify.mandate.server.domain.MandateDocumentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateDocumentPageEntity
import org.springframework.stereotype.Service

/**
 * Maps a [MandateDocumentEntity] and its pages to the DTO. A page's file is private: the DTO carries neither its
 * storage path nor a URL, the caller builds its authenticated preview route from the ids. The extracted fields stay
 * empty until the extraction (#330).
 */
@Service
class MandateDocumentMapper {
    fun toMandateDocument(
        entity: MandateDocumentEntity,
        pages: List<MandateDocumentPageEntity>,
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
            pages = pages.map { page -> toMandateDocumentPage(page) },
            version = entity.version ?: 0,
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
        )
    }

    private fun toMandateDocumentPage(page: MandateDocumentPageEntity): MandateDocumentPage = MandateDocumentPage(
        id = page.id ?: -1,
        index = page.pageIndex,
        contentType = page.mimeType,
        createdAt = page.createdAt,
        modifiedAt = page.modifiedAt,
    )
}
