package com.wutsi.ndopify.mandate.dto

/**
 * The land documents a buyer requires, in the buyer's words: *Titre foncier* (land title), *Plan de bornage*
 * (survey plan) and *Certificat de propriété* (certificate of ownership). Optional, and only for a land purchase.
 */
enum class TitleDocumentType {
    UNKNOWN,
    LAND_TITLE,
    SURVEY_PLAN,
    CERTIFICATE_OF_OWNERSHIP,
}
