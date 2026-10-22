package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.UpdateMandateRequest
import com.wutsi.ndopify.npc.service.TenantFixtures.FORMATTER
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.MANDATE
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AKWA
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.BONAPRISO
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.DOUALA
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import kotlin.test.Test
import kotlin.test.assertEquals

class MandateEditRulesTest {
    companion object {
        /** A client's valid answers: an apartment in Bonapriso. */
        val FORM = MandateEditForm(
            neighborhoodIds = listOf(BONAPRISO),
            propertyCategory = PropertyCategory.RESIDENTIAL,
            propertyType = "APARTMENT",
            rooms = "3",
            furnitureType = "ANY",
            parkings = "1",
            area = "80",
            amenities = listOf("waterWell", "securityGuard"),
            priceAmount = "150 000",
            movingPeriod = MovingPeriod.WITHIN_1_MONTH,
            comments = "  Près d'une école  ",
        )
    }

    @Test
    fun `of - client`() {
        val form = MandateEditForm.of(MANDATE.copy(waterWell = true, area = 120, version = 3))

        assertEquals(listOf(BONAPRISO, AKWA), form.neighborhoodIds)
        assertEquals(PropertyCategory.RESIDENTIAL, form.propertyCategory)
        assertEquals("HOUSE", form.propertyType)
        assertEquals("3", form.rooms)
        assertEquals("ANY", form.furnitureType)
        assertEquals("ANY", form.parkings)
        assertEquals("120", form.area)
        assertEquals(listOf("waterWell"), form.amenities)
        assertEquals("250 000", form.priceAmount)
        assertEquals(MovingPeriod.WITHIN_3_MONTHS, form.movingPeriod)
        assertEquals("Près d'une école", form.comments)
        assertEquals(3L, form.version)
    }

    @Test
    fun `request - the version the form was loaded with, not the mandate's`() {
        val request = MandateEditRules.request(FORM.copy(version = 3), MANDATE.copy(version = 4))

        assertEquals(3L, request.version)
    }

    @Test
    fun `of - client looking for any type`() {
        assertEquals("ANY", MandateEditForm.of(MANDATE.copy(propertyType = null)).propertyType)
    }

    @Test
    fun `of - landlord`() {
        val form = MandateEditForm.of(
            MANDATE.copy(mandateType = MandateType.LISTING, furnitureType = FurnitureType.UNFURNISHED, parkings = null),
        )

        assertEquals("UNFURNISHED", form.furnitureType)
        assertEquals(null, form.parkings)
    }

    @Test
    fun `errors - valid`() {
        assertEquals(emptyMap(), MandateEditRules.errors(FORM, owner = false, FORMATTER))
        assertEquals(emptyMap(), MandateEditRules.errors(FORM.copy(propertyType = "ANY"), owner = false, FORMATTER))
    }

    @Test
    fun `errors - landlord can't answer any`() {
        val form = FORM.copy(propertyType = "ANY", furnitureType = "ANY", parkings = "ANY")

        assertEquals(
            mapOf(
                "propertyType" to "Choisissez un type de bien proposé.",
                "furnitureType" to "Indiquez si le bien est meublé.",
                "parkings" to "Choisissez un nombre de places proposé.",
            ),
            MandateEditRules.errors(form, owner = true, FORMATTER),
        )
        assertEquals("Choisissez un type de bien proposé.", MandateEditRules.errors(form.copy(propertyType = null), owner = true, FORMATTER)["propertyType"])
    }

    @Test
    fun `errors - landlord`() {
        val form = FORM.copy(furnitureType = "UNFURNISHED")

        assertEquals(emptyMap(), MandateEditRules.errors(form, owner = true, FORMATTER))
        assertEquals(
            "Indiquez le loyer, en FCFA par mois.",
            MandateEditRules.errors(form.copy(priceAmount = ""), owner = true, FORMATTER)["priceAmount"],
        )
        assertEquals(
            "Indiquez quand le bien est disponible.",
            MandateEditRules.errors(form.copy(movingPeriod = null), owner = true, FORMATTER)["movingPeriod"],
        )
    }

    @Test
    fun `errors - rooms required for a home only`() {
        assertEquals(
            "Indiquez le nombre de chambres.",
            MandateEditRules.errors(FORM.copy(rooms = null), owner = false, FORMATTER)["rooms"],
        )
        assertEquals(
            "Indiquez le nombre de chambres.",
            MandateEditRules.errors(FORM.copy(propertyType = "ANY", rooms = null), owner = false, FORMATTER)["rooms"],
        )
        assertEquals(emptyMap(), MandateEditRules.errors(FORM.copy(propertyType = "STUDIO", rooms = null), owner = false, FORMATTER))
        assertEquals(
            emptyMap(),
            MandateEditRules.errors(FORM.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = "LAND", rooms = null, furnitureType = null), owner = false, FORMATTER),
        )
    }

    @Test
    fun `errors - each field`() {
        val form = FORM.copy(
            neighborhoodIds = listOf(1, 2, 3, 4),
            propertyCategory = null,
            propertyType = "RETAIL",
            furnitureType = "XYZ",
            parkings = "9",
            area = "abc",
            priceAmount = "abc",
            movingPeriod = MovingPeriod.UNKNOWN,
            comments = "x".repeat(501),
        )

        assertEquals(
            mapOf(
                "neighborhoodIds" to "Choisissez au plus 3 quartiers.",
                "propertyCategory" to "Indiquez si le bien est pour y habiter ou pour une activité.",
                "propertyType" to "Choisissez un type de bien proposé.",
                "furnitureType" to "Indiquez si le bien est meublé.",
                "parkings" to "Choisissez un nombre de places proposé.",
                "area" to "Indiquez la surface en m², ou laissez le champ vide.",
                "priceAmount" to "Indiquez le budget, en FCFA par mois.",
                "movingPeriod" to "Indiquez la date d'emménagement.",
                "comments" to "Votre message ne doit pas dépasser 500 caractères.",
            ),
            MandateEditRules.errors(form, owner = false, FORMATTER),
        )
    }

    @Test
    fun `errors - type of the other category`() {
        assertEquals(
            "Choisissez un type de bien proposé.",
            MandateEditRules.errors(FORM.copy(propertyCategory = PropertyCategory.COMMERCIAL), owner = false, FORMATTER)["propertyType"],
        )
    }

    @Test
    fun `errors - amounts`() {
        assertEquals(
            "Indiquez un loyer d'au moins 5\u00a0000\u00a0FCFA par mois.",
            MandateEditRules.errors(FORM.copy(priceAmount = "1 000"), owner = false, FORMATTER)["priceAmount"],
        )
        assertEquals(
            "Indiquez la surface en m², ou laissez le champ vide.",
            MandateEditRules.errors(FORM.copy(area = "2"), owner = false, FORMATTER)["area"],
        )
        assertEquals(emptyMap(), MandateEditRules.errors(FORM.copy(area = "  "), owner = false, FORMATTER))
    }

    @Test
    fun request() {
        val mandate = MANDATE.copy(listingType = ListingType.RENTAL, securityDeposit = 2, leaseTerm = 12)

        assertEquals(
            UpdateMandateRequest(
                propertyCategory = PropertyCategory.RESIDENTIAL,
                listingType = ListingType.RENTAL,
                cityId = DOUALA,
                neighbourhoodIds = listOf(BONAPRISO),
                propertyType = PropertyType.APARTMENT,
                furnitureType = null,
                rooms = "3",
                parkings = "1",
                area = 80,
                waterWell = true,
                securityGuard = true,
                price = Money(150000, "XAF", PeriodicityType.MONTHLY),
                securityDeposit = 2,
                leaseTerm = 12,
                movingPeriod = MovingPeriod.WITHIN_1_MONTH,
                comments = "Près d'une école",
            ),
            MandateEditRules.request(FORM, mandate),
        )
    }

    @Test
    fun `request - keeps the availability date of the mandate`() {
        val date = java.time.LocalDate.of(2026, 11, 3)

        assertEquals(date, MandateEditRules.request(FORM, MANDATE.copy(availableFrom = date)).availableFrom)
    }

    @Test
    fun `request - drops follow-ups that don't apply`() {
        val request = MandateEditRules.request(
            FORM.copy(
                propertyCategory = PropertyCategory.COMMERCIAL,
                propertyType = "LAND",
                rooms = "3",
                furnitureType = "FULLY_FURNISHED",
                parkings = "2",
            ),
            MANDATE,
        )

        assertEquals(PropertyType.LAND, request.propertyType)
        assertEquals(null, request.rooms)
        assertEquals(null, request.furnitureType)
        assertEquals(null, request.parkings)
    }

    @Test
    fun `request - any`() {
        val request = MandateEditRules.request(
            FORM.copy(propertyType = "ANY", furnitureType = "ANY", parkings = "ANY", area = "", comments = " "),
            MANDATE,
        )

        assertEquals(null, request.propertyType)
        assertEquals(null, request.furnitureType)
        assertEquals(null, request.parkings)
        assertEquals(FORM.rooms, request.rooms)
        assertEquals(null, request.area)
        assertEquals(null, request.comments)
    }

    @Test
    fun `request - city never taken from the form`() {
        assertEquals(DOUALA, MandateEditRules.request(FORM, MANDATE).cityId)
    }
}
