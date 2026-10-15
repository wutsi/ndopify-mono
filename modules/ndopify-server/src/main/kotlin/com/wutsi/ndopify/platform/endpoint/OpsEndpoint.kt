package com.wutsi.ndopify.platform.endpoint

/**
 * Marks an operational endpoint (ex: dispatching a mandate, expiring an offer by hand): only mapped when
 * `ndopify.endpoints.ops` is `true` (see [OpsEndpointHandlerMapping]). Otherwise, as in prod, the endpoint doesn't
 * exist. They're unauthenticated: never turn them on in prod before they're restricted to admins.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class OpsEndpoint
