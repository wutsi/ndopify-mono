package com.wutsi.ndopify.mandate.server.domain

import com.wutsi.ndopify.refdata.dto.StorageType
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.TenantId
import java.util.Date

/**
 * One image page of a [MandateDocumentEntity], [pageIndex] 0-based in the document. Its [path] is the storage path,
 * never handed out: the page is read through the authenticated preview route
 * (`/v1/mandates/{id}/documents/{documentId}/pages/{pageId}/preview`), which serves the derived display copy. Not
 * `@Version`-ed: pages are always written under the mandate's lock.
 */
@Entity
@Table(name = "T_MANDATE_DOCUMENT_PAGE")
data class MandateDocumentPageEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // Left unset (null) on construction so Hibernate's TenantIdGeneration can populate it from the current
    // tenant on insert — mirrors MandateEntity.tenantId exactly.
    @TenantId
    val tenantId: Long? = null,

    val documentId: Long = -1,
    val pageIndex: Int = 0,
    val path: String = "",
    val storageType: StorageType = StorageType.UNKNOWN,
    val mimeType: String? = null,

    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
