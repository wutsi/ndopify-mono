package com.wutsi.ndopify.mandate.server.service.document

/**
 * Keeps a financial document's account number to its last four digits before anything is stored.
 *
 * The masking is enforced here, never trusted to the model: the prompts ask for the last four digits, but a vision
 * model may still return the full account number or the IBAN it read, and a stored payload, an e-mail or a client
 * would then hold it. [scrub] is applied to every free-text field of a [FinancialData] — a name, a bank, an account
 * holder — and [last4] to its account suffix, so no stored field holds more than the last four digits of an account
 * number. A date or the currency is kept as read: [ACCOUNT_NUMBER] accepts dashes, so a `yyyy-MM-dd` value would be
 * masked into nonsense.
 */
object AccountNumberSanitizer {
    /** The masked form of an account number: a marker, then its last four digits. */
    private const val MASK = "•••• "

    // An IBAN: two country letters, two check digits, then letters, digits and spaces. The plan's pattern, kept as is.
    private val IBAN = Regex("""\b[A-Z]{2}\d{2}[A-Z0-9 ]{10,30}""")

    // An account or card number: at least eight digits, single spaces or dashes allowed between them
    private val ACCOUNT_NUMBER = Regex("""\d(?:[ -]?\d){7,}""")

    /** The last four digits of [value]'s digits; null when it has fewer. */
    fun last4(value: String?): String? {
        val digits = value.orEmpty().filter { it.isDigit() }
        return if (digits.length >= 4) digits.takeLast(4) else null
    }

    /** [text] with every IBAN and every run of eight digits or more replaced by `•••• ` and its last four digits. */
    fun scrub(text: String?): String? = text?.let { value ->
        ACCOUNT_NUMBER.replace(IBAN.replace(value) { match -> mask(match.value) }) { match -> mask(match.value) }
    }

    private fun mask(value: String): String = MASK + last4(value).orEmpty()
}
