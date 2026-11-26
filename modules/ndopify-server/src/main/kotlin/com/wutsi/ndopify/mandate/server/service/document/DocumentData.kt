package com.wutsi.ndopify.mandate.server.service.document

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate
import java.time.YearMonth

/**
 * What the vision model read on a mandate document, typed by the document's [com.wutsi.ndopify.mandate.dto.DocumentType]
 * and stored as the document's `data` JSON ([DocumentDataCodec]). The version is [VERSION]; the reading is additive, so
 * an older payload reads through today's class (a missing field is null, an unknown one ignored) and a field is never
 * renamed.
 *
 * Every property is nullable: the model may not find a field, and an older payload may not have it. [detectedType],
 * [valid], [invalidityReason], [confidence], [fieldConfidences] and [sidesPresent] are the model's assessment, kept for
 * the checks and never exposed as a document field ([toFields]).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
sealed class DocumentData {
    companion object {
        /** The schema version of the JSON stored in `data`; bump it when the reading changes, never rename a field. */
        const val VERSION = 1

        /** The display date of a value the model returned as `yyyy-MM-dd`; null when blank or unparseable. */
        internal fun date(value: String?): LocalDate? =
            value?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
                try {
                    LocalDate.parse(text)
                } catch (ex: Exception) {
                    null
                }
            }

        /** The first day of a value the model returned as `yyyy-MM`; null when blank or unparseable. */
        internal fun monthStart(value: String?): LocalDate? =
            value?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
                try {
                    YearMonth.parse(text).atDay(1)
                } catch (ex: Exception) {
                    null
                }
            }
    }

    /** The type the model recognized, an [com.wutsi.ndopify.mandate.dto.DocumentType] name; compared to the declared one. */
    abstract val detectedType: String?

    /** False when the model couldn't read the document; its detection and fields are then not trusted. */
    abstract val valid: Boolean

    /** The model's overall confidence, 0..1. */
    abstract val confidence: Double?

    /** One 0..1 confidence per extracted field key; a missing key counts as 0. */
    abstract val fieldConfidences: Map<String, Double>?

    /** The sides visible among the pages, for an identity document (`FRONT`, `BACK`); null for other types. */
    open val sidesPresent: List<String>? get() = null

    /** The display-ready values read, keyed and ordered as the type's contract with the document reader. */
    abstract fun toFields(): Map<String, String?>

    /** The date printed as the issue date (or a procuration's date), when the type has one. */
    open fun issuedAt(): LocalDate? = null

    /** The expiry the document prints (identity, procuration), when the type has one. */
    open fun expiresAt(): LocalDate? = null
}

/** The fields of an identity document, shared by [NationalId] and [Passport]. */
sealed class IdentityData : DocumentData() {
    abstract val number: String?
    abstract val firstName: String?
    abstract val lastName: String?
    abstract val dateOfBirth: String?
    abstract val issueDate: String?
    abstract val expiryDate: String?
    abstract val countryCode: String?

    override fun toFields(): Map<String, String?> = linkedMapOf(
        "number" to number,
        "firstName" to firstName,
        "lastName" to lastName,
        "dateOfBirth" to dateOfBirth,
        "issueDate" to issueDate,
        "expiryDate" to expiryDate,
        "countryCode" to countryCode,
    )

    override fun issuedAt(): LocalDate? = date(issueDate)

    override fun expiresAt(): LocalDate? = date(expiryDate)
}

/** A national identity card: its front and back are both needed (`sidesPresent`). */
data class NationalId(
    override val detectedType: String? = null,
    override val number: String? = null,
    override val firstName: String? = null,
    override val lastName: String? = null,
    override val dateOfBirth: String? = null,
    override val issueDate: String? = null,
    override val expiryDate: String? = null,
    override val countryCode: String? = null,
    override val sidesPresent: List<String>? = null,
    override val valid: Boolean = false,
    val invalidityReason: String? = null,
    override val confidence: Double? = null,
    override val fieldConfidences: Map<String, Double>? = null,
) : IdentityData()

/** A passport: its data page is needed (`sidesPresent` holds `FRONT`). */
data class Passport(
    override val detectedType: String? = null,
    override val number: String? = null,
    override val firstName: String? = null,
    override val lastName: String? = null,
    override val dateOfBirth: String? = null,
    override val issueDate: String? = null,
    override val expiryDate: String? = null,
    override val countryCode: String? = null,
    override val sidesPresent: List<String>? = null,
    override val valid: Boolean = false,
    val invalidityReason: String? = null,
    override val confidence: Double? = null,
    override val fieldConfidences: Map<String, Double>? = null,
) : IdentityData()

/** The fields of a proof of ownership, shared by [LandTitle] and [CertificateOfOwnership]. */
sealed class TitleData : DocumentData() {
    abstract val titleNumber: String?
    abstract val owners: List<String>?
    abstract val department: String?
    abstract val arrondissement: String?
    abstract val locality: String?
    abstract val surface: String?
    abstract val issueDate: String?

    override fun toFields(): Map<String, String?> = linkedMapOf(
        "titleNumber" to titleNumber,
        "owners" to owners?.joinToString(", "),
        "department" to department,
        "arrondissement" to arrondissement,
        "locality" to locality,
        "surface" to surface,
        "issueDate" to issueDate,
    )

    override fun issuedAt(): LocalDate? = date(issueDate)
}

/** A land title, the strongest proof of ownership. */
data class LandTitle(
    override val detectedType: String? = null,
    override val titleNumber: String? = null,
    override val owners: List<String>? = null,
    override val department: String? = null,
    override val arrondissement: String? = null,
    override val locality: String? = null,
    override val surface: String? = null,
    override val issueDate: String? = null,
    override val valid: Boolean = false,
    val invalidityReason: String? = null,
    override val confidence: Double? = null,
    override val fieldConfidences: Map<String, Double>? = null,
) : TitleData()

/** A certificate of ownership, the customary or administrative proof of a plot. */
data class CertificateOfOwnership(
    override val detectedType: String? = null,
    override val titleNumber: String? = null,
    override val owners: List<String>? = null,
    override val department: String? = null,
    override val arrondissement: String? = null,
    override val locality: String? = null,
    override val surface: String? = null,
    override val issueDate: String? = null,
    override val valid: Boolean = false,
    val invalidityReason: String? = null,
    override val confidence: Double? = null,
    override val fieldConfidences: Map<String, Double>? = null,
) : TitleData()

/** A survey plan (*plan de bornage*) of a plot. */
data class SurveyPlan(
    override val detectedType: String? = null,
    val parcelReference: String? = null,
    val locality: String? = null,
    val surface: String? = null,
    val coordinates: String? = null,
    override val valid: Boolean = false,
    val invalidityReason: String? = null,
    override val confidence: Double? = null,
    override val fieldConfidences: Map<String, Double>? = null,
) : DocumentData() {
    override fun toFields(): Map<String, String?> = linkedMapOf(
        "parcelReference" to parcelReference,
        "locality" to locality,
        "surface" to surface,
        "coordinates" to coordinates,
    )
}

/** A power of attorney (*procuration*): who gave it, to whom, for what. */
data class PowerOfAttorney(
    override val detectedType: String? = null,
    val grantor: String? = null,
    val attorney: String? = null,
    val purpose: String? = null,
    val notary: String? = null,
    val issueDate: String? = null,
    val expiryDate: String? = null,
    override val valid: Boolean = false,
    val invalidityReason: String? = null,
    override val confidence: Double? = null,
    override val fieldConfidences: Map<String, Double>? = null,
) : DocumentData() {
    override fun toFields(): Map<String, String?> = linkedMapOf(
        "grantor" to grantor,
        "attorney" to attorney,
        "purpose" to purpose,
        "notary" to notary,
        "issueDate" to issueDate,
        "expiryDate" to expiryDate,
    )

    override fun issuedAt(): LocalDate? = date(issueDate)

    override fun expiresAt(): LocalDate? = date(expiryDate)
}

/** The fields of an occupancy document, shared by [LeaseContract], [UtilityBill] and [TaxReceipt]. */
sealed class OccupancyData : DocumentData() {
    abstract val holderName: String?
    abstract val address: String?
    abstract val locality: String?
    abstract val provider: String?
    abstract val issueDate: String?

    override fun toFields(): Map<String, String?> = linkedMapOf(
        "holderName" to holderName,
        "address" to address,
        "locality" to locality,
        "provider" to provider,
        "issueDate" to issueDate,
    )

    override fun issuedAt(): LocalDate? = date(issueDate)
}

/** A lease contract: its holder's name and address prove where the owner lives and rents. */
data class LeaseContract(
    override val detectedType: String? = null,
    override val holderName: String? = null,
    override val address: String? = null,
    override val locality: String? = null,
    override val provider: String? = null,
    override val issueDate: String? = null,
    override val valid: Boolean = false,
    val invalidityReason: String? = null,
    override val confidence: Double? = null,
    override val fieldConfidences: Map<String, Double>? = null,
) : OccupancyData()

/** A utility bill: its holder's name and address prove where the owner lives. */
data class UtilityBill(
    override val detectedType: String? = null,
    override val holderName: String? = null,
    override val address: String? = null,
    override val locality: String? = null,
    override val provider: String? = null,
    override val issueDate: String? = null,
    override val valid: Boolean = false,
    val invalidityReason: String? = null,
    override val confidence: Double? = null,
    override val fieldConfidences: Map<String, Double>? = null,
) : OccupancyData()

/** A tax receipt: its holder's name and address prove where the owner lives. */
data class TaxReceipt(
    override val detectedType: String? = null,
    override val holderName: String? = null,
    override val address: String? = null,
    override val locality: String? = null,
    override val provider: String? = null,
    override val issueDate: String? = null,
    override val valid: Boolean = false,
    val invalidityReason: String? = null,
    override val confidence: Double? = null,
    override val fieldConfidences: Map<String, Double>? = null,
) : OccupancyData()

/** A document of no known type: what the model could title and summarize. */
data class Other(
    override val detectedType: String? = null,
    val title: String? = null,
    val summary: String? = null,
    override val valid: Boolean = false,
    val invalidityReason: String? = null,
    override val confidence: Double? = null,
    override val fieldConfidences: Map<String, Double>? = null,
) : DocumentData() {
    override fun toFields(): Map<String, String?> = linkedMapOf(
        "title" to title,
        "summary" to summary,
    )
}

/**
 * The fields of a financial document — a pay slip, a bank pre-approval or a proof of funds. Amounts are decimals and
 * the currency its own ISO 4217 field, so the client formats them; the account number is never stored whole.
 */
sealed class FinancialData : DocumentData() {
    /**
     * This reading with every text field masked by [AccountNumberSanitizer], which keeps only the last four digits of
     * an account number or an IBAN; the confidences are kept. It is applied to the model's answer before it's stored:
     * a prompt can ask for the last four digits, it can't be trusted with them.
     */
    abstract fun sanitized(): FinancialData
}
