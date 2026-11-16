package com.wutsi.ndopify.web.ui.listing

import com.wutsi.ndopify.listing.dto.MovingPeriod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListingInterestRulesTest {
    private val valid = ListingInterestForm(
        firstName = "Awa",
        lastName = "Ndiaye",
        email = "awa.ndiaye@gmail.com",
        whatsappNumber = "+237650000001",
        movingPeriod = "WITHIN_1_MONTH",
    )

    @Test
    fun `a valid form has no error`() {
        assertTrue(ListingInterestRules.validate(valid).isEmpty())
    }

    @Test
    fun `every field missing`() {
        assertEquals(
            mapOf(
                "firstName" to "Indiquez votre prénom.",
                "lastName" to "Indiquez votre nom.",
                "email" to "Saisissez une adresse email valide.",
                "whatsappNumber" to "Indiquez un numéro WhatsApp valide.",
                "movingPeriod" to "Choisissez votre date d'emménagement.",
            ),
            ListingInterestRules.validate(ListingInterestForm()),
        )
    }

    @Test
    fun `an invalid e-mail is refused`() {
        assertEquals(
            mapOf("email" to "Saisissez une adresse email valide."),
            ListingInterestRules.validate(valid.copy(email = "awa.ndiaye@gmail")),
        )
    }

    @Test
    fun `a WhatsApp number that is not E164 is refused`() {
        listOf("650000001", "+237 6 50 00 00 01", "+0123456789").forEach { number ->
            assertTrue("whatsappNumber" in ListingInterestRules.validate(valid.copy(whatsappNumber = number)), number)
        }
    }

    @Test
    fun `an unknown moving period is refused`() {
        assertEquals(
            mapOf("movingPeriod" to "Choisissez votre date d'emménagement."),
            ListingInterestRules.validate(valid.copy(movingPeriod = "UNKNOWN")),
        )
    }

    @Test
    fun `the three moving periods are accepted`() {
        listOf(
            MovingPeriod.WITHIN_1_MONTH,
            MovingPeriod.WITHIN_3_MONTHS,
            MovingPeriod.AFTER_3_MONTHS,
        ).forEach { period ->
            assertTrue(ListingInterestRules.validate(valid.copy(movingPeriod = period.name)).isEmpty(), period.name)
        }
    }

    @Test
    fun `the honeypot is not a field of the rules`() {
        assertTrue(ListingInterestRules.validate(valid.copy(website = "https://spam.example")).isEmpty())
    }
}
