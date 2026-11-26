package com.wutsi.ndopify.mandate.server.service.document

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountNumberSanitizerTest {
    @Test
    fun `last4 - the last four digits of an IBAN`() {
        assertEquals("0189", AccountNumberSanitizer.last4("FR76 3000 6000 0112 3456 7890 189"))
        assertEquals("8901", AccountNumberSanitizer.last4("12345678901"))
    }

    @Test
    fun `last4 - fewer than four digits and null have none`() {
        assertNull(AccountNumberSanitizer.last4("12"))
        assertNull(AccountNumberSanitizer.last4(""))
        assertNull(AccountNumberSanitizer.last4(null))
    }

    @Test
    fun `scrub - an account number keeps only its last four digits`() {
        val scrubbed = AccountNumberSanitizer.scrub("Compte 10005 00001 12345678901 23")!!

        assertFalse(Regex("""\d{8,}""").containsMatchIn(scrubbed), scrubbed)
        assertTrue(scrubbed.endsWith("•••• 0123"), scrubbed)
    }

    @Test
    fun `scrub - an IBAN is masked`() {
        val scrubbed = AccountNumberSanitizer.scrub("CM21 10005 00001 12345678901 23")!!

        assertFalse(Regex("""[A-Z]{2}\d{2}[A-Z0-9 ]{10,30}""").containsMatchIn(scrubbed), scrubbed)
        assertEquals("•••• 0123", scrubbed)
    }

    @Test
    fun `scrub - text without a number is kept`() {
        assertEquals("Afriland First Bank", AccountNumberSanitizer.scrub("Afriland First Bank"))
        assertEquals("Jean Dupont", AccountNumberSanitizer.scrub("Jean Dupont"))
    }

    @Test
    fun `scrub - null and a blank stay as they are`() {
        assertNull(AccountNumberSanitizer.scrub(null))
        assertEquals("", AccountNumberSanitizer.scrub(""))
    }
}
