package com.wutsi.ndopify.mandate.dto

/**
 * How a listing mandate's ownership document compares to its city (#376): `UNKNOWN` when nothing was compared (no
 * customer-naming title, or the ownership check is missing / in flight / unusable), `MATCH` when the title's city is
 * the mandate's, `MISMATCH` when it is a known other city, `UNRESOLVED` when the locality can't be matched or one
 * side is missing — the last two never decide a verdict by themselves beyond `MISMATCH`'s reason.
 */
enum class OwnershipLocationStatus {
    UNKNOWN,
    MATCH,
    MISMATCH,
    UNRESOLVED,
}
