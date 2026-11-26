package com.wutsi.ndopify.party.server.service.kyc

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import com.wutsi.ndopify.party.dto.IdentificationType
import java.util.Date

@JsonIgnoreProperties(ignoreUnknown = true)
data class KycInfo(
    val type: IdentificationType = IdentificationType.UNKNOWN,

    @param:JsonSetter(nulls = Nulls.AS_EMPTY)
    val number: String = "",

    @param:JsonSetter(nulls = Nulls.AS_EMPTY)
    val firstName: String = "",

    @param:JsonSetter(nulls = Nulls.AS_EMPTY)
    val lastName: String = "",

    @param:JsonSetter(nulls = Nulls.AS_EMPTY)
    val countryCode: String = "",

    val valid: Boolean = false,
    val invalidityReason: String? = null,

    @param:JsonSetter(nulls = Nulls.AS_EMPTY)
    val confidence: Double = 0.0,

    @get:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val expiryDate: Date? = null,

    /**
     * The holder's birth date, `yyyy-MM-dd` as printed; null when the model didn't read it. Read by the mandate
     * document extraction, ignored by the KYC verifier.
     */
    val dateOfBirth: String? = null,

    /** The document's issue date, `yyyy-MM-dd` as printed; null when absent. */
    val issueDate: String? = null,

    /** The sides visible among the images provided (`FRONT`, `BACK`), for a two-sided identity document. */
    @param:JsonSetter(nulls = Nulls.AS_EMPTY)
    val sidesPresent: List<String> = emptyList(),

    /** One 0..1 confidence per extracted field key, for the mandate document extraction. */
    @param:JsonSetter(nulls = Nulls.AS_EMPTY)
    val fieldConfidences: Map<String, Double> = emptyMap(),
)
