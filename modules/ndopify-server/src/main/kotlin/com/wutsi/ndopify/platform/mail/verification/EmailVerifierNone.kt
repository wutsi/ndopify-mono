package com.wutsi.ndopify.platform.mail.verification

/**
 * The default verifier, selected when `ndopify.mail.verification.provider=none`: it accepts every address and never
 * reaches AWS. This is why local, QA and the server's own tests make no SES call.
 */
class EmailVerifierNone : EmailVerifier {
    override fun verify(email: String): EmailVerification =
        EmailVerification(score = 1.0, reason = VerificationReason.NONE)
}
