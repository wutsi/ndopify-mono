package com.wutsi.ndopify.mandate.dto

/**
 * The city comparison of a listing mandate's owner verdict (#376): the mandate's city and the ownership document's
 * locality, with how they compare. Display names only — never a document id, a page, a path or a storage URL.
 */
data class OwnershipLocation(
    val mandateCity: String = "",
    val documentLocality: String = "",
    val status: OwnershipLocationStatus = OwnershipLocationStatus.UNKNOWN,
)
