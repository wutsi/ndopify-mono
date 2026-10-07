package com.wutsi.ndopify.error.dto

object ErrorCode {
    private val PREFIX = "urn:wutsi:ndopify:error"

    val AGENT_NOT_FOUND = "$PREFIX:agent:not-found"
    val AGENT_ALREADY_EXISTS = "$PREFIX:agent:already-exists"

    val AUTH_MISSING_SECRET = "$PREFIX:auth:missing-secret"
    val AUTH_MISSING_PAYLOAD = "$PREFIX:auth:missing-payload"
    val AUTH_INVALID_CREDENTIALS = "$PREFIX:auth:invalid-credentials"
    val AUTH_CREDENTIALS_EXPIRED = "$PREFIX:auth:credentials-expired"
    val AUTH_ACCESS_DENIED = "$PREFIX:auth:access-denied"
    val AUTH_TYPE_NOT_SUPPORTED = "$PREFIX:auth:auth-type-not-supported"
    val AUTH_UNAUTHORIZED = "$PREFIX:auth:unauthorized"

    val HTTP_MISSING_PARAMETER = "$PREFIX:http:missing-parameter"
    val HTTP_INVALID_PARAMETER = "$PREFIX:http:invalid-parameter"
    val HTTP_INTERNAL = "$PREFIX:http:unexpected-error"
    val HTTP_METHOD_NOT_SUPPORTED = "$PREFIX:http:method-not-supported"
    val HTTP_ACCESS_DENIED = "$PREFIX:http:access-denied"
    val HTTP_AUTHENTICATION_FAILED = "$PREFIX:http:authentication-failed"
    val HTTP_DOWNSTREAM_ERROR = "$PREFIX:http:downstream-error"

    val IDENTIFICATION_IMAGE_ALREADY_UPLOADED = "$PREFIX:identification-image:already-uploaded"
    val IDENTIFICATION_IMAGE_INVALID_MIME_TYPE = "$PREFIX:identification-image:invalid-mime-type"
    val IDENTIFICATION_IMAGE_NO_CONTENT = "$PREFIX:identification-image:image-no-content"
    val IDENTIFICATION_IMAGE_NOT_FOUND = "$PREFIX:identification-image:not-found"
    val IDENTIFICATION_NOT_FOUND = "$PREFIX:identification:not-found"

    val KYC_CASE_NOT_FOUND = "$PREFIX:kyc-case:not-found"
    val KYC_CASE_PARTY_MISMATCH = "$PREFIX:kyc-case:party-mismatch"
    val KYC_CASE_NOT_PENDING = "$PREFIX:kyc-case:not-pending"
    val KYC_CASE_MISSING_IDENTIFICATION_IMAGE = "$PREFIX:kyc-case:missing-identification-image"

    val LOCATION_NOT_FOUND = "$PREFIX:location:not-found"
    val LOCATION_FEED_NOT_FOUND = "$PREFIX:location:feed-not-found"

    val PARTY_NOT_FOUND = "$PREFIX:party:not-found"
    val PARTY_EMAIL_ALREADY_EXISTS = "$PREFIX:party:email-already-exists"

    val PAYMENT_METHOD_NOT_FOUND = "$PREFIX:payment-method:not-found"
    val PAYMENT_METHOD_ALREADY_EXISTS = "$PREFIX:payment-method:already-exists"
    val PAYMENT_METHOD_EOL = "$PREFIX:payment-method:eol"
    val PAYMENT_METHOD_NUMBER_NOT_VALID = "$PREFIX:payment-method:number-not-valid"

    val TENANT_NOT_FOUND: String = "$PREFIX:tenant:not-found"

    val USER_NOT_FOUND: String = "$PREFIX:user:not-found"
    val USER_EMAIL_ALREADY_EXISTS: String = "$PREFIX:user:email-already-exists"
}
