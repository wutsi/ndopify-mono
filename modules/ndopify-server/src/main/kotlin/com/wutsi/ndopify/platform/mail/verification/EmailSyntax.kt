package com.wutsi.ndopify.platform.mail.verification

import org.apache.commons.validator.routines.EmailValidator

/**
 * A local syntax check before any paid call: it only saves an obvious call, SES still decides.
 *
 * It is offline — no DNS lookup, no MX probe — but the validator still checks the domain's TLD against
 * commons-validator's bundled list, so an address at a TLD missing from that list is refused without a provider call.
 */
object EmailSyntax {
    private val VALIDATOR = EmailValidator.getInstance(false, false)

    fun isValid(email: String): Boolean = VALIDATOR.isValid(email)
}
