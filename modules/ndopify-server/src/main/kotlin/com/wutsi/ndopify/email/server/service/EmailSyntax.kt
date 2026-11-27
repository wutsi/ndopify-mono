package com.wutsi.ndopify.email.server.service

import org.apache.commons.validator.routines.EmailValidator

/**
 * A local syntax check before any paid call: it only saves an obvious call, SES still decides. No DNS lookup and no
 * TLD check, so it costs nothing and never refuses on a network miss.
 */
object EmailSyntax {
    private val VALIDATOR = EmailValidator.getInstance(false, false)

    fun isValid(email: String): Boolean = VALIDATOR.isValid(email)
}
