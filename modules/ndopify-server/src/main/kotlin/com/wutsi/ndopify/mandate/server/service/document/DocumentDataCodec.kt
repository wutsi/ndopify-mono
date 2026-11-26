package com.wutsi.ndopify.mandate.server.service.document

import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.util.jpa.JsonHelper
import org.springframework.stereotype.Service

/**
 * The typed [DocumentData] of a mandate document, as stored in its `data` JSON column. The subclass comes from the
 * document's [DocumentType] column, never from the payload; a type without a data class (the financial documents #333
 * adds, [DocumentType.UNKNOWN]) has none. Reading is additive and versioned ([DocumentData.VERSION]): an older or
 * partial payload reads with null fields, an unknown one is ignored, and a malformed payload is null — never an error.
 */
@Service
class DocumentDataCodec(private val json: JsonHelper) {
    /** The current [DocumentData] version, stored as the document's `data_version`. */
    fun version(): Int = DocumentData.VERSION

    /** The JSON of [data]. */
    fun write(data: DocumentData): String = json.write(data)
        ?: throw IllegalArgumentException("The document data cannot be serialized")

    /**
     * The data of [type] in [json]; null for a type without a data class, a blank payload or a malformed one.
     * [dataVersion] is the version the payload was written with: the reading is additive, so today's class reads any
     * older version.
     */
    fun read(json: String?, type: DocumentType, dataVersion: Int?): DocumentData? {
        val clazz = classOf(type) ?: return null
        return try {
            this.json.read(json, clazz)
        } catch (ex: Exception) {
            null
        }
    }

    private fun classOf(type: DocumentType): Class<out DocumentData>? = when (type) {
        DocumentType.NATIONAL_ID -> NationalId::class.java
        DocumentType.PASSPORT -> Passport::class.java
        DocumentType.LAND_TITLE -> LandTitle::class.java
        DocumentType.CERTIFICATE_OF_OWNERSHIP -> CertificateOfOwnership::class.java
        DocumentType.SURVEY_PLAN -> SurveyPlan::class.java
        DocumentType.POWER_OF_ATTORNEY -> PowerOfAttorney::class.java
        DocumentType.LEASE_CONTRACT -> LeaseContract::class.java
        DocumentType.UTILITY_BILL -> UtilityBill::class.java
        DocumentType.TAX_RECEIPT -> TaxReceipt::class.java
        DocumentType.OTHER -> Other::class.java
        DocumentType.UNKNOWN,
        DocumentType.PAY_SLIP,
        DocumentType.BANK_PRE_APPROVAL,
        DocumentType.PROOF_OF_FUNDS -> null
    }
}
