package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.customer.dto.Customer

/**
 * The customer edit page's form: the customer's four editable details, as posted, so that the page can show them
 * again with its errors. The e-mail is the party's contact e-mail, not its login.
 */
data class CustomerEditForm(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val whatsappNumber: String = "",
) {
    companion object {
        /** The customer's current details; a stored number that isn't E.164 is shown as is, to be corrected. */
        fun of(customer: Customer): CustomerEditForm = CustomerEditForm(
            firstName = customer.party.firstName,
            lastName = customer.party.lastName,
            email = customer.party.email,
            whatsappNumber = customer.whatsappNumber.orEmpty(),
        )
    }
}
