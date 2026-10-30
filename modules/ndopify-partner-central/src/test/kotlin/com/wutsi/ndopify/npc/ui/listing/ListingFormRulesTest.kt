package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingSection
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.npc.service.TenantFixtures.TENANT
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest.Companion.LISTING
import com.wutsi.ndopify.web.core.service.TenantFormatter
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The section forms' rules of [ListingRules]: the prototype's `LISTING_RULES` and `LISTING_APPLIES`. */
class ListingFormRulesTest {
    private val formatter = TenantFormatter(TENANT)
    private val today = LocalDate.of(2026, 10, 26)

    private fun errors(section: ListingSection, form: ListingSectionForm) =
        ListingRules.errors(section, form, today, formatter)

    // A valid answer for each section
    private val location = ListingSectionForm(neighborhoodId = "3001", landmark = "Derrière l'école")
    private val gps = ListingSectionForm(latitude = "4.0335", longitude = "9.6942")
    private val property = ListingSectionForm(
        propertyCategory = "RESIDENTIAL",
        propertyType = "APARTMENT",
        furnitureType = "UNFURNISHED",
        rooms = "2",
        bathrooms = "1",
        area = "85",
    )
    private val amenities = ListingSectionForm(roadAccess = "roadPaved", waterMeter = true)
    private val terms = ListingSectionForm(price = "150 000", securityDeposit = "3", availableFrom = "2026-11-15")

    @Test
    fun `valid answers`() {
        assertEquals(emptyMap(), errors(ListingSection.LOCATION, location))
        assertEquals(emptyMap(), errors(ListingSection.GPS, gps))
        assertEquals(emptyMap(), errors(ListingSection.PROPERTY, property))
        assertEquals(emptyMap(), errors(ListingSection.AMENITIES, amenities))
        assertEquals(emptyMap(), errors(ListingSection.TERMS, terms))
        assertEquals(emptyMap(), errors(ListingSection.REMARKS, ListingSectionForm()))
    }

    @Test
    fun location() {
        assertEquals(
            mapOf(
                "neighborhoodId" to "Choisissez le quartier du bien.",
                "landmark" to "Indiquez un point de repère connu : école, carrefour, commerce…",
            ),
            errors(ListingSection.LOCATION, ListingSectionForm(neighborhoodId = "abc", landmark = "  ")),
        )
        assertEquals(
            setOf("landmark", "accessDirections"),
            errors(ListingSection.LOCATION, location.copy(landmark = "x".repeat(101), accessDirections = "x".repeat(501))).keys,
        )
    }

    @Test
    fun gps() {
        val message = mapOf("latitude" to "Relevez la position GPS depuis le bien.")
        assertEquals(message, errors(ListingSection.GPS, ListingSectionForm()))
        assertEquals(message, errors(ListingSection.GPS, gps.copy(latitude = "200")))
        assertEquals(message, errors(ListingSection.GPS, gps.copy(longitude = "abc")))
        assertEquals(emptyMap(), errors(ListingSection.GPS, ListingSectionForm(latitude = "4,0335", longitude = " 9,6942 ")))
    }

    @Test
    fun `property - category and type`() {
        assertEquals(
            mapOf("propertyCategory" to "Choisissez l'usage du bien.", "propertyType" to "Choisissez le type de bien."),
            errors(ListingSection.PROPERTY, ListingSectionForm(area = "85")),
        )
        assertEquals(
            mapOf("propertyType" to "Choisissez le type de bien."),
            errors(ListingSection.PROPERTY, property.copy(propertyCategory = "COMMERCIAL")),
        )
        assertEquals(
            mapOf("propertyCategory" to "Choisissez l'usage du bien."),
            errors(ListingSection.PROPERTY, property.copy(propertyCategory = "UNKNOWN")),
        )
    }

    @Test
    fun `property - counts`() {
        assertEquals(
            mapOf(
                "furnitureType" to "Indiquez si le bien est meublé.",
                "rooms" to "Indiquez ce nombre, compté sur place.",
                "bathrooms" to "Indiquez un nombre entier.",
                "kitchens" to "Indiquez un nombre entier.",
                "area" to "Indiquez la surface en m².",
            ),
            errors(
                ListingSection.PROPERTY,
                property.copy(furnitureType = "NOPE", rooms = " ", bathrooms = "-1", kitchens = "1.5", area = "0"),
            ),
        )
        assertEquals(
            mapOf("area" to "Indiquez ce nombre, compté sur place."),
            errors(ListingSection.PROPERTY, property.copy(area = null)),
        )
    }

    @Test
    fun `property - questions that don't apply are ignored`() {
        // A studio: no rooms, living rooms; a ROOM: no kitchens, parkings; neither has levels
        val studio = property.copy(propertyType = "STUDIO", rooms = "abc", livingRooms = "abc", levels = "abc")

        assertEquals(emptyMap(), errors(ListingSection.PROPERTY, studio))
        val request = ListingRules.request(ListingSection.PROPERTY, studio.copy(floorLevel = "2"))
        assertNull(request.rooms)
        assertNull(request.livingRooms)
        assertNull(request.levels)
        assertEquals(2, request.floorLevel)
    }

    @Test
    fun `property - request`() {
        val request = ListingRules.request(ListingSection.PROPERTY, property.copy(version = 4, parkings = "1", kitchens = ""))

        assertEquals(4L, request.version)
        assertEquals(ListingSection.PROPERTY, request.section)
        assertEquals(PropertyCategory.RESIDENTIAL, request.propertyCategory)
        assertEquals(PropertyType.APARTMENT, request.propertyType)
        assertEquals(FurnitureType.UNFURNISHED, request.furnitureType)
        assertEquals(2, request.rooms)
        assertEquals(1, request.bathrooms)
        assertEquals(1, request.parkings)
        assertNull(request.kitchens)
        assertEquals(85, request.area)
    }

    @Test
    fun applies() {
        val residential = PropertyCategory.RESIDENTIAL
        val commercial = PropertyCategory.COMMERCIAL
        assertTrue(ListingRules.applies("furniture", residential, PropertyType.APARTMENT))
        assertFalse(ListingRules.applies("furniture", residential, PropertyType.BUILDING))
        assertFalse(ListingRules.applies("furniture", commercial, PropertyType.OFFICE))
        assertFalse(ListingRules.applies("rooms", residential, PropertyType.STUDIO))
        assertFalse(ListingRules.applies("bathrooms", commercial, PropertyType.LAND))
        assertFalse(ListingRules.applies("kitchens", residential, PropertyType.ROOM))
        assertFalse(ListingRules.applies("parkings", residential, PropertyType.ROOM))
        assertTrue(ListingRules.applies("levels", residential, PropertyType.DUPLEX))
        assertFalse(ListingRules.applies("levels", residential, PropertyType.APARTMENT))
        assertTrue(ListingRules.applies("floorLevel", commercial, PropertyType.RETAIL))
        assertFalse(ListingRules.applies("rooms", residential, null))
    }

    @Test
    fun `type fits the category`() {
        assertTrue(ListingRules.typeFits(PropertyCategory.RESIDENTIAL, PropertyType.BUILDING))
        assertTrue(ListingRules.typeFits(PropertyCategory.COMMERCIAL, PropertyType.LAND))
        assertFalse(ListingRules.typeFits(PropertyCategory.RESIDENTIAL, PropertyType.LAND))
        assertFalse(ListingRules.typeFits(null, PropertyType.APARTMENT))
    }

    @Test
    fun amenities() {
        assertEquals(
            mapOf("roadAccess" to "Indiquez l'accès routier au bien."),
            errors(ListingSection.AMENITIES, ListingSectionForm(waterMeter = true)),
        )
        assertEquals(
            mapOf("roadAccess" to "Indiquez l'accès routier au bien."),
            errors(ListingSection.AMENITIES, ListingSectionForm(roadAccess = "helicopter")),
        )

        val request = ListingRules.request(ListingSection.AMENITIES, ListingSectionForm(roadAccess = "trackAccessibleByCar", solarPanel = true))
        assertEquals(false, request.roadPaved)
        assertEquals(true, request.trackAccessibleByCar)
        assertEquals(false, request.trackInaccessibleByCar)
        assertEquals(true, request.solarPanel)
        assertEquals(false, request.waterMeter)
    }

    @Test
    fun terms() {
        assertEquals(
            mapOf(
                "price" to "Indiquez le loyer mensuel.",
                "securityDeposit" to "Choisissez la caution demandée.",
                "availableFrom" to "Indiquez la date à laquelle le bien est libre.",
            ),
            errors(ListingSection.TERMS, ListingSectionForm()),
        )
        assertEquals(
            mapOf("price" to "Indiquez le loyer en chiffres, en FCFA par mois."),
            errors(ListingSection.TERMS, terms.copy(price = "150k")),
        )
        assertEquals(
            mapOf("price" to "Indiquez le loyer mensuel."),
            errors(ListingSection.TERMS, terms.copy(price = "0")),
        )
        assertEquals(
            mapOf("availableFrom" to "Choisissez une date dans les 12 prochains mois."),
            errors(ListingSection.TERMS, terms.copy(availableFrom = "2027-10-27")),
        )
        assertEquals(emptyMap(), errors(ListingSection.TERMS, terms.copy(availableFrom = "2027-10-26")))
        assertEquals(
            mapOf("availableFrom" to "Indiquez la date à laquelle le bien est libre."),
            errors(ListingSection.TERMS, terms.copy(availableFrom = "2026-02-31")),
        )
        assertEquals(
            setOf("securityDeposit", "advanceRent", "leaseTerm", "noticePeriod"),
            errors(ListingSection.TERMS, terms.copy(securityDeposit = "7", advanceRent = "13", leaseTerm = "5", noticePeriod = "x")).keys,
        )
    }

    @Test
    fun `terms - request`() {
        val request = ListingRules.request(
            ListingSection.TERMS,
            terms.copy(price = "150 000", advanceRent = "2", leaseTerm = "", noticePeriod = "1"),
        )

        assertEquals(150_000L, request.price)
        assertEquals(3, request.securityDeposit)
        assertEquals(2, request.advanceRent)
        assertNull(request.leaseTerm)
        assertEquals(1, request.noticePeriod)
        assertEquals(LocalDate.of(2026, 11, 15), request.availableFrom)
    }

    @Test
    fun remarks() {
        assertEquals(setOf("agentRemarks"), errors(ListingSection.REMARKS, ListingSectionForm(agentRemarks = "x".repeat(1001))).keys)
        assertNull(ListingRules.request(ListingSection.REMARKS, ListingSectionForm(agentRemarks = "  ")).agentRemarks)
        assertEquals("À revoir", ListingRules.request(ListingSection.REMARKS, ListingSectionForm(agentRemarks = " À revoir ")).agentRemarks)
    }

    @Test
    fun `location and gps - request`() {
        val request = ListingRules.request(ListingSection.LOCATION, location.copy(accessDirections = " "))
        assertEquals(3001L, request.neighborhoodId)
        assertEquals("Derrière l'école", request.landmark)
        assertNull(request.accessDirections)

        val position = ListingRules.request(ListingSection.GPS, ListingSectionForm(latitude = "4,0335", longitude = "9.6942"))
        assertEquals(4.0335, position.latitude)
        assertEquals(9.6942, position.longitude)
    }

    @Test
    fun `form of a listing`() {
        val form = ListingSectionForm.of(LISTING, formatter)

        assertEquals(2L, form.version)
        assertEquals("3001", form.neighborhoodId)
        assertEquals("RESIDENTIAL", form.propertyCategory)
        assertEquals("roadPaved", form.roadAccess)
        assertEquals("150 000", form.price)
        assertEquals(setOf("roadPaved", "waterMeter", "generatorSet"), form.amenities())
        assertEquals(emptyMap(), errors(ListingSection.TERMS, form.copy(availableFrom = "2026-11-15")))
    }
}
