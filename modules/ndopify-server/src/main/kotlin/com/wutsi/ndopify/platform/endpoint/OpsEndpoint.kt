package com.wutsi.ndopify.platform.endpoint

/**
 * Marks an operational endpoint (ex: dispatching a mandate, expiring an offer by hand): only mapped when
 * `ndopify.endpoints.ops` is `true` (see [OpsEndpointHandlerMapping]). Otherwise, as in prod, the endpoint doesn't
 * exist. Only an admin may call it: an `admin-console` token with the `admin` role (`CallerService.isAdmin`), else
 * 403 `HTTP_ACCESS_DENIED` ([OpsEndpointAuthorizationInterceptor]).
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class OpsEndpoint
