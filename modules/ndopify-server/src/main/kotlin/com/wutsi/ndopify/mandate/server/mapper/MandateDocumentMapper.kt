package com.wutsi.ndopify.mandate.server.mapper

import com.wutsi.ndopify.mandate.dto.DocumentField
import com.wutsi.ndopify.mandate.dto.MandateDocument
import com.wutsi.ndopify.mandate.dto.MandateDocumentPage
import com.wutsi.ndopify.mandate.server.domain.MandateDocumentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateDocumentPageEntity
import com.wutsi.ndopify.mandate.server.service.document.DocumentDataCodec
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Maps a [MandateDocumentEntity] and its pages to the DTO. A page's file is private: the DTO carries neither its
 * storage path nor a URL, the caller builds its authenticated preview route from the ids.
 *
 * An `EXTRACTED` document's typed data becomes the generic [DocumentField] list — its display keys in the type's order,
 * each value non-blank, with its own confidence — never the raw JSON: `data` doesn't leave the server. A document not
 * extracted yet, a type whose payload is unknown, or a malformed payload gives no field, never a 500.
 */
@Service
class MandateDocumentMapper(private val codec: DocumentDataCodec) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MandateDocumentMapper::class.java)
    }

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
            source = entity.source,
            problemCodes = entity.problemCodes,
            fields = toFields(entity),
            issuedAt = entity.issuedAt,
            expiresAt = entity.expiresAt,
            pages = pages.map { page -> toMandateDocumentPage(page) },
            version = entity.version ?: 0,
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
        )
    }

    private fun toFields(entity: MandateDocumentEntity): List<DocumentField> {
        val data = codec.read(entity.data, entity.type, entity.dataVersion)
        if (data == null) {
            if (entity.data != null) {
                // The payload is logged by document id and type only, never its content
                LOGGER.warn(">>> MandateDocument#${entity.id} - unreadable extracted data (${entity.type.name})")
            }
            return emptyList()
        }
        val confidences = data.fieldConfidences.orEmpty()
        return data.toFields().mapNotNull { (key, value) ->
            val text = value?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            DocumentField(key = key, value = text, confidence = confidences[key])
        }
    }

    private fun toMandateDocumentPage(page: MandateDocumentPageEntity): MandateDocumentPage = MandateDocumentPage(
        id = page.id ?: -1,
        index = page.pageIndex,
        contentType = page.mimeType,
        createdAt = page.createdAt,
        modifiedAt = page.modifiedAt,
    )
}
