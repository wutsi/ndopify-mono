package com.wutsi.ndopify.mandate.server.service.document

import com.wutsi.ndopify.mandate.dto.DocumentType
import org.springframework.stereotype.Service

/**
 * The [DocumentDataExtractor] of a [DocumentType]. A type without one (the financial documents #333 adds,
 * [DocumentType.UNKNOWN]) is never read: the extraction job leaves its documents `UPLOADED`.
 */
@Service
class DocumentDataExtractorProvider(extractors: List<DocumentDataExtractor>) {
    private val byType: Map<DocumentType, DocumentDataExtractor> =
        extractors.flatMap { extractor -> extractor.types.map { type -> type to extractor } }.toMap()

    /** The extractor of [type], or null when the type has none. */
    fun get(type: DocumentType): DocumentDataExtractor? = byType[type]

    fun supports(type: DocumentType): Boolean = byType.containsKey(type)

    /** Every type an extractor handles: the extraction job's batch only looks at those. */
    fun types(): Set<DocumentType> = byType.keys
}
