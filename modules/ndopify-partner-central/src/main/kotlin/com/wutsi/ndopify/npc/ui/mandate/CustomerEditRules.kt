package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.customer.dto.UpdateCustomerRequest
import com.wutsi.ndopify.npc.ui.join.JoinRules

/**
 * Field rules of the customer edit page: all four details are required. Each check returns the French message, or null
 * when valid; ndopify-server re-checks the same things (`@NotEmpty`, the same e-mail ownership) but not the formats.
 * The WhatsApp number is normalized like the join wizard's (`00` and separators) then must be E.164.
 */
object CustomerEditRules {
    private val NAME = Regex("^\\p{L}[\\p{L}' .-]*$")
    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")
    private val PHONE = Regex("^\\+[1-9]\\d{6,14}$")

    fun errors(form: CustomerEditForm): Map<String, String> = buildMap {
        firstName(form.firstName)?.let { put("firstName", it) }
        lastName(form.lastName)?.let { put("lastName", it) }
        email(form.email)?.let { put("email", it) }
        whatsappNumber(form.whatsappNumber)?.let { put("whatsappNumber", it) }
    }

    fun firstName(value: String?): String? = name(value, "Saisissez le prénom.")

    fun lastName(value: String?): String? = name(value, "Saisissez le nom.")

    fun email(value: String?): String? = when {
        value.isNullOrBlank() -> "Saisissez l'adresse email."
        !EMAIL.matches(value.trim()) -> "Cette adresse email semble incomplète. Exemple : nom@exemple.com"
        else -> null
    }

    fun whatsappNumber(value: String?): String? = when {
        value.isNullOrBlank() -> "Saisissez le numéro WhatsApp."
        !PHONE.matches(JoinRules.normalizePhone(value)) ->
            "Ce numéro n'est pas un numéro mobile valide. Exemple : 6 77 12 34 56"
        else -> null
    }

    /** What the page posts: trimmed names, the e-mail in lower case, the phone in E.164. */
    fun request(form: CustomerEditForm): UpdateCustomerRequest = UpdateCustomerRequest(
        firstName = form.firstName.trim(),
        lastName = form.lastName.trim(),
        email = form.email.trim().lowercase(),
        whatsappNumber = JoinRules.normalizePhone(form.whatsappNumber),
    )

    private fun name(value: String?, emptyMessage: String): String? = when {
        value.isNullOrBlank() -> emptyMessage
        !NAME.matches(value.trim()) -> "Utilisez uniquement des lettres, des espaces, des tirets ou des apostrophes."
        else -> null
    }
}
