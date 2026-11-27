package com.wutsi.ndopify.email.server.service

/**
 * The e-mail verification port: the only provider-specific code is its adapter (see `EmailVerifierSES`). It throws
 * when the provider fails; fail-open and the caching are the policy's (`EmailVerificationService`).
 */
fun interface EmailVerifier {
    fun verify(email: String): EmailVerification
}
