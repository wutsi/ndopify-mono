package com.wutsi.ndopify.error.dto

object ErrorCode {
    private val PREFIX = "urn:wutsi:ndopify:error"

    val AGENT_NOT_FOUND = "$PREFIX:agent:not-found"
    val AGENT_MOBILE_MONEY_NUMBER_ALREADY_ASSIGNED = "$PREFIX:agent:mobile-money-number-already-assigned"

    val APPLICATION_NOT_FOUND = "$PREFIX:application:not-found"

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
    val HTTP_AUTHENTICATION_FAILED = "$PREFIX:http:authetication-failed"
    val HTTP_DOWNSTREAM_ERROR = "$PREFIX:http:downstream-error"

    val IMPORT_ERROR = "$PREFIX:import-error"

    val LOCATION_NOT_FOUND = "$PREFIX:location:not-found"
    val LOCATION_FEED_NOT_FOUND = "$PREFIX:location:feed-not-found"

    val MOBILE_CHANGE_REQUEST_NOT_FOUND = "$PREFIX:mobile-change-request:not-found"
    val MOBILE_CHANGE_REQUEST_ALREADY_PROCEEDED = "$PREFIX:mobile-change-request:already-proceeded"
    val MOBILE_CHANGE_REQUEST_NOT_FOR_MANUAL_REVIEW = "$PREFIX:mobile-change-request:not-for-manual-review"

    val TENANT_NOT_FOUND: String = "$PREFIX:tenant:not-found"

    val USER_NOT_FOUND: String = "$PREFIX:user:not-found"
}
