package com.wutsi.ndopify.mandate.server.domain

import com.wutsi.ndopify.mandate.dto.DocumentSource
import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.util.jpa.StringListConverter
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.TenantId
import org.hibernate.type.SqlTypes
import java.time.LocalDate
import java.util.Date

/**
 * A document attached to a mandate — an identity card, a payslip, a land title... — with its pages and, once
 * extracted, the fields read on it ([data]) and the [problemCodes] the extraction reported.
 */
@Entity
@Table(name = "T_MANDATE_DOCUMENT")
data class MandateDocumentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // Left unset (null) on construction so Hibernate's TenantIdGeneration can populate it from the current
    // tenant on insert — mirrors MandateEntity.tenantId exactly.
    @TenantId
    val tenantId: Long? = null,

    val mandateId: Long = -1,
    val type: DocumentType = DocumentType.UNKNOWN,
    val title: String = "",
    val status: DocumentStatus = DocumentStatus.UNKNOWN,

    /** The kind of files it was created from; `UNKNOWN` for a row written before the kind was recorded. */
    val source: DocumentSource = DocumentSource.UNKNOWN,

    /** The codes of the problems the extraction reported; empty until then. */
    @Convert(converter = StringListConverter::class)
    val problemCodes: List<String> = emptyList(),

    /** The typed fields read on the document: the JSON of its `DocumentData`, written and read by `DocumentDataCodec`. */
    @JdbcTypeCode(SqlTypes.JSON)
    val data: String? = null,

    val dataVersion: Int? = null,
    val issuedAt: LocalDate? = null,
    val expiresAt: LocalDate? = null,

    // Optimistic locking: a write based on a stale read fails (409 MANDATE_DOCUMENT_VERSION_CONFLICT) instead of
    // silently overwriting the change
    @Version
    val version: Long? = null,

    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
