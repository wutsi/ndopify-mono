package com.wutsi.ndopify.mandate.dto

/**
 * The type of a document of a mandate. [LAND_TITLE], [SURVEY_PLAN] and [CERTIFICATE_OF_OWNERSHIP] reuse
 * [TitleDocumentType]'s names on purpose (#294): a title document the buyer requires and a land document the agent
 * uploads are the same kind of paper.
 */
enum class DocumentType {
    UNKNOWN,
    NATIONAL_ID,
    PASSPORT,
    LAND_TITLE,
    CERTIFICATE_OF_OWNERSHIP,
    SURVEY_PLAN,
    POWER_OF_ATTORNEY,
    LEASE_CONTRACT,
    UTILITY_BILL,
    TAX_RECEIPT,
    PAY_SLIP,
    BANK_PRE_APPROVAL,
    PROOF_OF_FUNDS,
    OTHER,
}
