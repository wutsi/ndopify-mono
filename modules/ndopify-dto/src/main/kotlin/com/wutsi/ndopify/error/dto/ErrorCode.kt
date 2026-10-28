package com.wutsi.ndopify.error.dto

object ErrorCode {
    private val PREFIX = "urn:wutsi:ndopify:error"

    val AGENT_NOT_FOUND = "$PREFIX:agent:not-found"
    val AGENT_ALREADY_EXISTS = "$PREFIX:agent:already-exists"
    val AGENT_VERSION_CONFLICT = "$PREFIX:agent:version-conflict"

    val APPLICATION_NOT_FOUND = "$PREFIX:application:not-found"

    val ASSIGNMENT_NOT_FOUND = "$PREFIX:assignment:not-found"
    val ASSIGNMENT_NOT_PENDING = "$PREFIX:assignment:not-pending"
    val ASSIGNMENT_ACCESS_DENIED = "$PREFIX:assignment:access-denied"

    val AUTH_ACCESS_DENIED = "$PREFIX:auth:access-denied"
    val AUTH_CREDENTIALS_EXPIRED = "$PREFIX:auth:credentials-expired"
    val AUTH_CREDENTIALS_ALREADY_USED = "$PREFIX:auth:credentials-already-used"
    val AUTH_FACTOR_NOT_FOUND = "$PREFIX:auth:factor-not-found"
    val AUTH_INVALID_CREDENTIALS = "$PREFIX:auth:invalid-credentials"
    val AUTH_MISSING_PAYLOAD = "$PREFIX:auth:missing-payload"
    val AUTH_MISSING_SECRET = "$PREFIX:auth:missing-secret"
    val AUTH_TYPE_NOT_SUPPORTED = "$PREFIX:auth:auth-type-not-supported"
    val AUTH_UNAUTHORIZED = "$PREFIX:auth:unauthorized"

    val CONTRACT_ACCESS_DENIED = "$PREFIX:contract:access-denied"
    val CONTRACT_NOT_FOUND = "$PREFIX:contract:not-found"
    val CONTRACT_NOT_DRAFT = "$PREFIX:contract:not-draft"
    val CONTRACT_NOT_PENDING = "$PREFIX:contract:not-pending"
    val CONTRACT_CANCELLED = "$PREFIX:contract:cancelled"
    val CONTRACT_CONTENT_MISMATCH = "$PREFIX:contract:content-mismatch"
    val CONTRACT_ALREADY_SIGNED = "$PREFIX:contract:already-signed"
    val CONTRACT_EXPIRED = "$PREFIX:contract:expired"
    val CONTRACT_PARTY_NOT_FOUND = "$PREFIX:contract:party-not-found"
    val CONTRACT_PARTY_MEMBERSHIP_CONFLICT = "$PREFIX:contract:party-membership-conflict"
    val CONTRACT_PARTY_ALREADY_SIGNED = "$PREFIX:contract:party-already-signed"
    val CONTRACT_PARTY_SIGNATURE_NOT_REQUIRED = "$PREFIX:contract:party-signature-not-required"
    val CONTRACT_SIGNATURE_NOT_FOUND = "$PREFIX:contract:signature-not-found"
    val CONTRACT_VERSION_CONFLICT = "$PREFIX:contract:version-conflict"

    val CUSTOMER_NOT_FOUND = "$PREFIX:customer:not-found"

    val HTTP_MISSING_PARAMETER = "$PREFIX:http:missing-parameter"
    val HTTP_INVALID_PARAMETER = "$PREFIX:http:invalid-parameter"
    val HTTP_INTERNAL = "$PREFIX:http:unexpected-error"
    val HTTP_METHOD_NOT_SUPPORTED = "$PREFIX:http:method-not-supported"
    val HTTP_NOT_FOUND = "$PREFIX:http:not-found"
    val HTTP_ACCESS_DENIED = "$PREFIX:http:access-denied"
    val HTTP_AUTHENTICATION_FAILED = "$PREFIX:http:authentication-failed"
    val HTTP_DOWNSTREAM_ERROR = "$PREFIX:http:downstream-error"
    val HTTP_VERSION_CONFLICT = "$PREFIX:http:version-conflict"

    val IDENTIFICATION_NOT_FOUND = "$PREFIX:identification:not-found"

    val KYC_CASE_NOT_FOUND = "$PREFIX:kyc-case:not-found"
    val KYC_CASE_NOT_PENDING = "$PREFIX:kyc-case:not-pending"
    val KYC_CASE_NOT_SUBMITTED = "$PREFIX:kyc-case:not-submitted"
    val KYC_CASE_MISSING_IDENTIFICATION_IMAGE = "$PREFIX:kyc-case:missing-identification-image"
    val KYC_CASE_VERSION_CONFLICT = "$PREFIX:kyc-case:version-conflict"
    val KYC_IMAGE_INVALID_MIME_TYPE = "$PREFIX:kyc-image:invalid-mime-type"
    val KYC_IMAGE_NOT_FOUND = "$PREFIX:kyc-image:not-found"
    val KYC_IMAGE_NO_CONTENT = "$PREFIX:kyc-image:image-no-content"

    val LISTING_NOT_FOUND = "$PREFIX:listing:not-found"
    val LISTING_ALREADY_EXISTS = "$PREFIX:listing:already-exists"
    val LISTING_VERSION_CONFLICT = "$PREFIX:listing:version-conflict"
    val LISTING_NOT_DRAFT = "$PREFIX:listing:not-draft"
    val LISTING_NOT_VERIFIED = "$PREFIX:listing:not-verified"
    val LISTING_INCOMPLETE = "$PREFIX:listing:incomplete"
    val LISTING_IMAGE_NOT_FOUND = "$PREFIX:listing-image:not-found"
    val LISTING_NOT_ENOUGH_IMAGES = "$PREFIX:listing:not-enough-images"
    val LISTING_MISSING_LAT_LONG = "$PREFIX:listing:missing-lat-long"
    val LISTING_MISSING_NEIGHBORHOOD = "$PREFIX:listing:missing-neighborhood"
    val LISTING_OUTSIDE_NEIGHBORHOOD = "$PREFIX:listing:outside-neighborhood"
    val LISTING_INVALID_NEIGHBORHOOD = "$PREFIX:listing:invalid-neighborhood"

    val LOCATION_NOT_FOUND = "$PREFIX:location:not-found"
    val LOCATION_FEED_NOT_FOUND = "$PREFIX:location:feed-not-found"

    val MANDATE_ACCESS_DENIED = "$PREFIX:mandate:access-denied"
    val MANDATE_NOT_FOUND = "$PREFIX:mandate:not-found"
    val MANDATE_NOT_ASSIGNED = "$PREFIX:mandate:not-assigned"
    val MANDATE_NOT_APPROVED = "$PREFIX:mandate:not-approved"
    val MANDATE_CANNOT_BE_APPROVED = "$PREFIX:mandate:cannot-be-approved"
    val MANDATE_ALREADY_APPROVED = "$PREFIX:mandate:already-approved"
    val MANDATE_ALREADY_CLOSED = "$PREFIX:mandate:already-closed"
    val MANDATE_VERSION_CONFLICT = "$PREFIX:mandate:version-conflict"

    val PARTY_NOT_FOUND = "$PREFIX:party:not-found"
    val PARTY_EMAIL_ALREADY_EXISTS = "$PREFIX:party:email-already-exists"

    val PAYMENT_METHOD_NOT_FOUND = "$PREFIX:payment-method:not-found"
    val PAYMENT_METHOD_ALREADY_EXISTS = "$PREFIX:payment-method:already-exists"
    val PAYMENT_METHOD_EOL = "$PREFIX:payment-method:eol"
    val PAYMENT_METHOD_NUMBER_NOT_VALID = "$PREFIX:payment-method:number-not-valid"

    val TENANT_NOT_FOUND: String = "$PREFIX:tenant:not-found"
    val TENANT_MISSING_HEADER: String = "$PREFIX:tenant:missing-header"
    val TENANT_NOT_ACTIVE: String = "$PREFIX:tenant:not-active"
    val TENANT_ID_NOT_VALID: String = "$PREFIX:tenant:id-not-valid"

    val USER_NOT_FOUND: String = "$PREFIX:user:not-found"
    val USER_EMAIL_ALREADY_EXISTS: String = "$PREFIX:user:email-already-exists"

    val INTERNAL_ERROR: String = "$PREFIX:internal-error"
}
