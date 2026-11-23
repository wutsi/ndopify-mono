package com.wutsi.ndopify.web.ui.listing

/**
 * Checks the "Être contacté par un agent" form's fields, which the server would otherwise trust (the public endpoint
 * only requires the contact to be non-empty): each invalid field gets its French message, by form field name.
 *
 * The moving-period message depends on the listing: a renter picks a moving date, a buyer a purchase horizon.
 */
object ListingInterestRules {
    private val EMAIL = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$".toRegex()

    // The intl-tel-input script posts the number in E.164 (+237 6 99 00 00 01, without its spaces)
    private val WHATSAPP = "^\\+[1-9]\\d{6,14}$".toRegex()

    private val MOVING_PERIODS = setOf(
        "WITHIN_1_MONTH",
        "WITHIN_3_MONTHS",
        "AFTER_3_MONTHS",
    )

    /** "Choisissez quand vous souhaitez acheter." for a sale, today's moving-date message otherwise. */
    fun movingPeriodError(sale: Boolean): String =
        if (sale) "Choisissez quand vous souhaitez acheter." else "Choisissez votre date d'emménagement."

    /** Empty when the form is valid; otherwise the message of each invalid field, keyed by field name. */
    fun validate(form: ListingInterestForm, sale: Boolean = false): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (form.firstName.isNullOrBlank()) {
            errors["firstName"] = "Indiquez votre prénom."
        }
        if (form.lastName.isNullOrBlank()) {
            errors["lastName"] = "Indiquez votre nom."
        }
        if (!EMAIL.matches(form.email?.trim().orEmpty())) {
            errors["email"] = "Saisissez une adresse email valide."
        }
        if (!WHATSAPP.matches(form.whatsappNumber?.trim().orEmpty())) {
            errors["whatsappNumber"] = "Indiquez un numéro WhatsApp valide."
        }
        if (form.movingPeriod !in MOVING_PERIODS) {
            errors["movingPeriod"] = movingPeriodError(sale)
        }
        return errors
    }
}
