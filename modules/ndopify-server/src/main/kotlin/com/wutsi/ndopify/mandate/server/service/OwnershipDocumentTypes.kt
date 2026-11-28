package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.mandate.dto.DocumentType

/** The document types that back an owner verdict (#297) and an override of it (#377). */
object OwnershipDocumentTypes {
    val IDENTITY: Set<DocumentType> = setOf(DocumentType.NATIONAL_ID, DocumentType.PASSPORT)
    val OWNERSHIP: Set<DocumentType> = setOf(DocumentType.LAND_TITLE, DocumentType.CERTIFICATE_OF_OWNERSHIP)
}
