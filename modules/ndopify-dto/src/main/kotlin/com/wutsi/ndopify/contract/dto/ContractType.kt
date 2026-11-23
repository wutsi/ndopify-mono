package com.wutsi.ndopify.contract.dto

enum class ContractType {
    UNKNOWN,
    SEARCH,
    LISTING,

    // A SALE mandate: the buyer's SEARCH (SALE_SEARCH) or the seller's LISTING (SALE_LISTING). Both keep the MNDT number.
    SALE_SEARCH,
    SALE_LISTING,
    LEASE_AGREEMENT,
}
