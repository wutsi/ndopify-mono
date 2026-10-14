package com.wutsi.ndopify.refdata.dto

object KycErrorCode {
    val INACTIVE = "INACTIVE"
    val GATEWAY_ERROR = "GATEWAY_ERROR"
    val INTERRUPTED = "INTERRUPTED" // the verification stopped before completing (e.g. the server restarted)
    val EXPIRED = "EXPIRED"
    val SUSPENDED = "SUSPENDED"
    val INVALID = "INVALID"
    val TYPE_MISMATCH = "TYPE_MISMATCH"
    val LOW_SCORE = "LOW_SCORE"
    val SELFIE_REJECTED = "SELFIE_REJECTED"
    val IDENTIFICATION_REJECTED = "IDENTIFICATION_REJECTED"
}
