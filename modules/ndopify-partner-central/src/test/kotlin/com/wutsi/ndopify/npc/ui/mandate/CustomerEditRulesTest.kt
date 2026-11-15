package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.party.dto.Party
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CustomerEditRulesTest {
    companion object {
        val VALID = CustomerEditForm("Ray", "Sponsible", "ray@x.com", "+237699123456")
    }

    @Test
    fun `of - the customer's details`() {
        val form = CustomerEditForm.of(
            Customer(
                party = Party(firstName = "Ray", lastName = "Sponsible", email = "ray@gmail.com"),
                whatsappNumber = "+237699123456",
            ),
        )

        assertEquals(VALID.copy(email = "ray@gmail.com"), form)
    }

    @Test
    fun `of - no stored phone`() {
        val form = CustomerEditForm.of(Customer(party = Party(firstName = "Ray"), whatsappNumber = null))

        assertEquals("", form.whatsappNumber)
    }

    @Test
    fun errors() {
        assertEquals(emptyMap(), CustomerEditRules.errors(VALID))
        assertEquals("Saisissez le prénom.", CustomerEditRules.errors(VALID.copy(firstName = " "))["firstName"])
        assertEquals("Saisissez le nom.", CustomerEditRules.errors(VALID.copy(lastName = ""))["lastName"])
        assertEquals(
            "Cette adresse email semble incomplète. Exemple : nom@exemple.com",
            CustomerEditRules.errors(VALID.copy(email = "ray@"))["email"],
        )
        assertEquals(
            "Saisissez le numéro WhatsApp.",
            CustomerEditRules.errors(VALID.copy(whatsappNumber = ""))["whatsappNumber"],
        )
        assertNotNull(CustomerEditRules.errors(VALID.copy(whatsappNumber = "6 77 12 34 56"))["whatsappNumber"])
    }

    @Test
    fun `errors - names with a digit`() {
        assertNotNull(CustomerEditRules.errors(VALID.copy(firstName = "Ray2"))["firstName"])
        assertNotNull(CustomerEditRules.errors(VALID.copy(lastName = "<script>"))["lastName"])
    }

    @Test
    fun request() {
        val request = CustomerEditRules.request(
            CustomerEditForm(" Ray ", " Sponsible ", " Ray@X.com ", "00237 699 123 456"),
        )

        assertEquals("Ray", request.firstName)
        assertEquals("Sponsible", request.lastName)
        assertEquals("ray@x.com", request.email)
        assertEquals("+237699123456", request.whatsappNumber)
    }
}
