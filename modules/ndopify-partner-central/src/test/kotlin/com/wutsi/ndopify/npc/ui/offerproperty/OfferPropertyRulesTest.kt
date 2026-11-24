package com.wutsi.ndopify.npc.ui.offerproperty

import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.TitleDocumentType
import com.wutsi.ndopify.npc.service.TenantFixtures.FORMATTER
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfferPropertyRulesTest {
    private val today = LocalDate.of(2026, 10, 21)
    private val residential = PropertyCategory.RESIDENTIAL
    private val commercial = PropertyCategory.COMMERCIAL

    @Test
    fun categoriesByTypeInFormOrder() {
        assertEquals(
            listOf(
                PropertyType.APARTMENT, PropertyType.HOUSE, PropertyType.ROOM, PropertyType.STUDIO,
                PropertyType.DUPLEX, PropertyType.BUILDING, PropertyType.OFFICE, PropertyType.RETAIL,
                PropertyType.INDUSTRIAL,
            ),
            OfferPropertyRules.CATEGORIES_BY_TYPE.keys.toList(),
        )
        assertEquals(residential, OfferPropertyRules.CATEGORIES_BY_TYPE[PropertyType.BUILDING])
        assertNull(OfferPropertyRules.CATEGORIES_BY_TYPE[PropertyType.LAND])
    }

    @Test
    fun amenitiesAndChoices() {
        assertEquals(11, OfferPropertyRules.AMENITIES.size)
        assertEquals("waterMeter", OfferPropertyRules.AMENITIES.first())
        assertEquals("securedFenced", OfferPropertyRules.AMENITIES.last())
        assertEquals(listOf("1", "2", "3", "4", "5+"), OfferPropertyRules.ROOMS)
        assertEquals(listOf("1", "2", "3+"), OfferPropertyRules.PARKINGS)
        assertEquals("0", OfferPropertyRules.NO_PARKING)
    }

    @Test
    fun propertyCategory() {
        assertNull(OfferPropertyRules.propertyCategory(residential))
        val message = "Indiquez si le bien est pour y habiter ou pour une activité."
        assertEquals(message, OfferPropertyRules.propertyCategory(null))
        assertEquals(message, OfferPropertyRules.propertyCategory(PropertyCategory.UNKNOWN))
    }

    @Test
    fun cityAndNeighborhood() {
        assertNull(OfferPropertyRules.cityId(1))
        assertEquals("Choisissez la ville.", OfferPropertyRules.cityId(null))
        assertEquals("Choisissez la ville.", OfferPropertyRules.cityId(0))
        assertNull(OfferPropertyRules.neighborhoodId(1001))
        assertEquals("Choisissez le quartier.", OfferPropertyRules.neighborhoodId(null))
        assertEquals("Choisissez le quartier.", OfferPropertyRules.neighborhoodId(-1))
    }

    @Test
    fun priceAmount() {
        assertEquals("Indiquez le loyer mensuel souhaité.", OfferPropertyRules.priceAmount(null, FORMATTER, null))
        assertEquals("Indiquez le loyer mensuel souhaité.", OfferPropertyRules.priceAmount(0, FORMATTER, null))
        assertEquals(
            "Indiquez un loyer d'au moins ${FORMATTER.money(5000)} par mois.",
            OfferPropertyRules.priceAmount(4999, FORMATTER, null),
        )
        assertNull(OfferPropertyRules.priceAmount(5000, FORMATTER, null))
    }

    @Test
    fun advanceRent() {
        val message = "Choisissez une avance proposée."
        assertNull(OfferPropertyRules.advanceRent(null))
        assertEquals(message, OfferPropertyRules.advanceRent(0))
        assertNull(OfferPropertyRules.advanceRent(1))
        assertNull(OfferPropertyRules.advanceRent(24))
        assertEquals(message, OfferPropertyRules.advanceRent(25))
    }

    @Test
    fun securityDeposit() {
        val message = "Indiquez la caution demandée, en mois de loyer."
        assertNull(OfferPropertyRules.securityDeposit(null))
        assertEquals(message, OfferPropertyRules.securityDeposit(0))
        assertNull(OfferPropertyRules.securityDeposit(1))
        assertNull(OfferPropertyRules.securityDeposit(24))
        assertEquals(message, OfferPropertyRules.securityDeposit(25))
    }

    @Test
    fun availableFrom() {
        assertEquals(
            "Indiquez à partir de quand le bien est libre.",
            OfferPropertyRules.availableFrom(null, today),
        )
        assertNull(OfferPropertyRules.availableFrom(today, today))
        assertNull(OfferPropertyRules.availableFrom(today.plusDays(365), today))
        val range = "Choisissez une date entre aujourd'hui et dans 365 jours."
        assertEquals(range, OfferPropertyRules.availableFrom(today.plusDays(366), today))
        assertEquals(range, OfferPropertyRules.availableFrom(today.minusDays(1), today))
    }

    @Test
    fun parseDate() {
        assertEquals(LocalDate.of(2026, 11, 3), OfferPropertyRules.parseDate("2026-11-03"))
        assertEquals(LocalDate.of(2026, 11, 3), OfferPropertyRules.parseDate(" 2026-11-03 "))
        assertNull(OfferPropertyRules.parseDate("2026-02-31"))
        assertNull(OfferPropertyRules.parseDate("03/11/2026"))
        assertNull(OfferPropertyRules.parseDate("2026-1-3"))
        assertNull(OfferPropertyRules.parseDate(""))
        assertNull(OfferPropertyRules.parseDate(null))
    }

    @Test
    fun propertyType() {
        val message = "Choisissez le type de bien."
        assertNull(OfferPropertyRules.propertyType(residential, "BUILDING", null))
        assertEquals(message, OfferPropertyRules.propertyType(commercial, "LAND", null))
        assertEquals(message, OfferPropertyRules.propertyType(commercial, "BUILDING", null))
        assertEquals(message, OfferPropertyRules.propertyType(residential, "VILLA", null))
        assertEquals(message, OfferPropertyRules.propertyType(residential, null, null))
        assertEquals(message, OfferPropertyRules.propertyType(null, "APARTMENT", null))
    }

    @Test
    fun needs() {
        assertFalse(OfferPropertyRules.needsFurniture(residential, PropertyType.BUILDING, null))
        assertTrue(OfferPropertyRules.needsFurniture(residential, PropertyType.ROOM, null))
        assertFalse(OfferPropertyRules.needsFurniture(commercial, PropertyType.OFFICE, null))
        assertFalse(OfferPropertyRules.needsRooms(residential, PropertyType.STUDIO, null))
        assertFalse(OfferPropertyRules.needsRooms(residential, PropertyType.ROOM, null))
        assertFalse(OfferPropertyRules.needsRooms(residential, PropertyType.BUILDING, null))
        assertTrue(OfferPropertyRules.needsRooms(residential, PropertyType.APARTMENT, null))
        assertFalse(OfferPropertyRules.needsRooms(commercial, PropertyType.OFFICE, null))
        assertFalse(OfferPropertyRules.needsParking(residential, PropertyType.ROOM, null))
        assertTrue(OfferPropertyRules.needsParking(residential, PropertyType.BUILDING, null))
        assertFalse(OfferPropertyRules.needsParking(commercial, PropertyType.OFFICE, null))
        assertTrue(OfferPropertyRules.needsArea(commercial, PropertyType.OFFICE, null))
        assertFalse(OfferPropertyRules.needsArea(residential, PropertyType.APARTMENT, null))
        assertFalse(OfferPropertyRules.needsArea(null, null, null))
    }

    @Test
    fun `a sale offers residential land and no room`() {
        assertEquals(
            listOf(
                PropertyType.APARTMENT, PropertyType.HOUSE, PropertyType.STUDIO, PropertyType.DUPLEX,
                PropertyType.LAND,
            ),
            OfferPropertyRules.propertyTypes(PropertyCategory.RESIDENTIAL, ListingType.SALE),
        )
        assertEquals(
            listOf(
                PropertyType.BUILDING, PropertyType.OFFICE, PropertyType.RETAIL, PropertyType.INDUSTRIAL,
            ),
            OfferPropertyRules.propertyTypes(PropertyCategory.COMMERCIAL, ListingType.SALE),
        )
        assertEquals(
            listOf(PropertyType.APARTMENT, PropertyType.HOUSE, PropertyType.ROOM, PropertyType.STUDIO,
                PropertyType.DUPLEX, PropertyType.BUILDING),
            OfferPropertyRules.propertyTypes(PropertyCategory.RESIDENTIAL, ListingType.RENTAL),
        )
        assertEquals(
            listOf(PropertyType.OFFICE, PropertyType.RETAIL, PropertyType.INDUSTRIAL),
            OfferPropertyRules.propertyTypes(PropertyCategory.COMMERCIAL, ListingType.RENTAL),
        )
        assertFalse(OfferPropertyRules.typeFits(PropertyCategory.RESIDENTIAL, "ROOM", ListingType.SALE))
        assertTrue(OfferPropertyRules.typeFits(PropertyCategory.RESIDENTIAL, "LAND", ListingType.SALE))
        assertFalse(OfferPropertyRules.typeFits(PropertyCategory.COMMERCIAL, "LAND", ListingType.SALE))
        assertFalse(OfferPropertyRules.typeFits(PropertyCategory.RESIDENTIAL, "LAND", ListingType.RENTAL))
        assertFalse(OfferPropertyRules.typeFits(PropertyCategory.COMMERCIAL, "LAND", ListingType.RENTAL))
    }

    @Test
    fun `a building is sold under commercial only`() {
        assertTrue(OfferPropertyRules.propertyTypes(PropertyCategory.COMMERCIAL, ListingType.SALE).contains(PropertyType.BUILDING))
        assertFalse(OfferPropertyRules.propertyTypes(PropertyCategory.RESIDENTIAL, ListingType.SALE).contains(PropertyType.BUILDING))
        assertTrue(OfferPropertyRules.typeFits(PropertyCategory.COMMERCIAL, "BUILDING", ListingType.SALE))
        assertFalse(OfferPropertyRules.typeFits(PropertyCategory.RESIDENTIAL, "BUILDING", ListingType.SALE))
        // A rented building keeps today's category.
        assertTrue(OfferPropertyRules.typeFits(PropertyCategory.RESIDENTIAL, "BUILDING", ListingType.RENTAL))
        assertFalse(OfferPropertyRules.typeFits(PropertyCategory.COMMERCIAL, "BUILDING", ListingType.RENTAL))
    }

    @Test
    fun `a building sale asks its counts, no parking and no amenities step`() {
        assertTrue(OfferPropertyRules.buildingSale(ListingType.SALE, PropertyType.BUILDING))
        assertFalse(OfferPropertyRules.buildingSale(ListingType.RENTAL, PropertyType.BUILDING))
        assertFalse(OfferPropertyRules.buildingSale(ListingType.SALE, PropertyType.VILLA))
        assertFalse(OfferPropertyRules.buildingSale(null, null))

        assertFalse(OfferPropertyRules.needsParking(commercial, PropertyType.BUILDING, ListingType.SALE))
        assertTrue(OfferPropertyRules.needsParking(residential, PropertyType.BUILDING, ListingType.RENTAL))
        assertFalse(OfferPropertyRules.needsAmenities(commercial, PropertyType.BUILDING, ListingType.SALE))
        assertTrue(OfferPropertyRules.needsAmenities(residential, PropertyType.HOUSE, ListingType.SALE))
        assertTrue(OfferPropertyRules.needsAmenities(commercial, PropertyType.BUILDING, ListingType.RENTAL))
    }

    @Test
    fun `a building's counts are whole numbers within their bounds`() {
        assertEquals(30, OfferPropertyRules.MAX_LEVELS)
        assertEquals(500, OfferPropertyRules.MAX_UNITS)

        assertNull(OfferPropertyRules.levels("1"))
        assertNull(OfferPropertyRules.levels("30"))
        assertEquals("Indiquez le nombre d'étages.", OfferPropertyRules.levels(null))
        assertEquals("Indiquez le nombre d'étages.", OfferPropertyRules.levels(" "))
        assertEquals("Indiquez un nombre entier, de 1 à 30.", OfferPropertyRules.levels("31"))
        assertEquals("Indiquez un nombre entier, de 1 à 30.", OfferPropertyRules.levels("3,5"))

        assertNull(OfferPropertyRules.units("500"))
        assertEquals("Indiquez le nombre d'unités.", OfferPropertyRules.units(""))
        assertEquals("Indiquez un nombre entier, de 1 à 500.", OfferPropertyRules.units("501"))
    }

    @Test
    fun `a sale asks no furniture, no rooms nor parking for land`() {
        assertFalse(OfferPropertyRules.needsFurniture(PropertyCategory.RESIDENTIAL, PropertyType.APARTMENT, ListingType.SALE))
        assertTrue(OfferPropertyRules.needsFurniture(PropertyCategory.RESIDENTIAL, PropertyType.APARTMENT, ListingType.RENTAL))
        assertFalse(OfferPropertyRules.needsRooms(PropertyCategory.RESIDENTIAL, PropertyType.LAND, ListingType.SALE))
        assertFalse(OfferPropertyRules.needsParking(PropertyCategory.RESIDENTIAL, PropertyType.LAND, ListingType.SALE))
    }

    @Test
    fun `only a land sale requires its surface`() {
        assertTrue(OfferPropertyRules.needsArea(PropertyCategory.RESIDENTIAL, PropertyType.LAND, ListingType.SALE))
        assertFalse(OfferPropertyRules.needsArea(PropertyCategory.RESIDENTIAL, PropertyType.LAND, ListingType.RENTAL))
        assertTrue(OfferPropertyRules.needsArea(PropertyCategory.COMMERCIAL, PropertyType.OFFICE, ListingType.RENTAL))
        assertEquals("Indiquez la surface du terrain.", OfferPropertyRules.area(null, required = true))
        assertEquals("Indiquez une surface d'au moins 5 m².", OfferPropertyRules.area(4, required = true))
    }

    @Test
    fun `the documents are a land sale's only`() {
        assertEquals(
            listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN, TitleDocumentType.CERTIFICATE_OF_OWNERSHIP),
            OfferPropertyRules.ownershipDocumentTypes(ListingType.SALE, PropertyType.LAND),
        )
        assertEquals(emptyList(), OfferPropertyRules.ownershipDocumentTypes(ListingType.SALE, PropertyType.VILLA))
        assertEquals(emptyList(), OfferPropertyRules.ownershipDocumentTypes(ListingType.RENTAL, PropertyType.LAND))
    }

    @Test
    fun `a sale has its own price floor`() {
        assertNull(OfferPropertyRules.priceAmount(1_000_000L, FORMATTER, ListingType.SALE))
        assertEquals(
            "Indiquez un prix d'au moins ${FORMATTER.money(1_000_000)}.",
            OfferPropertyRules.priceAmount(900_000L, FORMATTER, ListingType.SALE),
        )
        assertEquals("Indiquez le prix de vente souhaité.", OfferPropertyRules.priceAmount(null, FORMATTER, ListingType.SALE))
        assertNull(OfferPropertyRules.priceAmount(5_000L, FORMATTER, ListingType.RENTAL))
        assertTrue(OfferPropertyRules.isPriceAmount(1_000_000L, ListingType.SALE))
        assertFalse(OfferPropertyRules.isPriceAmount(999_999L, ListingType.SALE))
        assertTrue(OfferPropertyRules.isPriceAmount(5_000L, ListingType.RENTAL))
    }

    @Test
    fun `the project is required`() {
        val message = "Indiquez si vous souhaitez mettre votre bien en location ou le vendre."
        assertEquals(message, OfferPropertyRules.listingType(null))
        assertEquals(message, OfferPropertyRules.listingType(ListingType.UNKNOWN))
        assertNull(OfferPropertyRules.listingType(ListingType.RENTAL))
        assertNull(OfferPropertyRules.listingType(ListingType.SALE))
    }

    @Test
    fun furnitureType() {
        assertNull(OfferPropertyRules.furnitureType("FULLY_FURNISHED"))
        assertNull(OfferPropertyRules.furnitureType("UNFURNISHED"))
        assertEquals("Indiquez si le bien est meublé.", OfferPropertyRules.furnitureType("UNKNOWN"))
        assertEquals("Indiquez si le bien est meublé.", OfferPropertyRules.furnitureType(null))
    }

    @Test
    fun rooms() {
        assertNull(OfferPropertyRules.rooms("5+"))
        assertEquals("Indiquez le nombre de chambres.", OfferPropertyRules.rooms("6"))
        assertEquals("Indiquez le nombre de chambres.", OfferPropertyRules.rooms(null))
    }

    @Test
    fun parkings() {
        assertNull(OfferPropertyRules.parkings("0"))
        assertNull(OfferPropertyRules.parkings("3+"))
        assertEquals("Indiquez le nombre de places.", OfferPropertyRules.parkings("4"))
        assertEquals("Indiquez si le bien a un parking ou un garage.", OfferPropertyRules.parkings(null))
        assertEquals("Indiquez si le bien a un parking ou un garage.", OfferPropertyRules.parkings(""))
    }

    @Test
    fun area() {
        assertEquals("Indiquez la surface, même approximative.", OfferPropertyRules.area(null, required = false))
        assertEquals("Indiquez une surface d'au moins 5 m².", OfferPropertyRules.area(4, required = false))
        assertNull(OfferPropertyRules.area(5, required = false))
        assertNull(OfferPropertyRules.area(1_000_000, required = false))
        assertEquals("Indiquez une surface d'au moins 5 m².", OfferPropertyRules.area(1_000_001, required = false))
    }

    @Test
    fun roadAccess() {
        assertNull(OfferPropertyRules.roadAccess("roadPaved"))
        assertEquals("Indiquez comment on accède au bien.", OfferPropertyRules.roadAccess("x"))
        assertEquals("Indiquez comment on accède au bien.", OfferPropertyRules.roadAccess(null))
    }

    @Test
    fun preferredContactPeriod() {
        assertNull(OfferPropertyRules.preferredContactPeriod(ContactPeriodType.MORNING))
        val message = "Indiquez quand votre agent peut vous contacter."
        assertEquals(message, OfferPropertyRules.preferredContactPeriod(null))
        assertEquals(message, OfferPropertyRules.preferredContactPeriod(ContactPeriodType.UNKNOWN))
    }

    @Test
    fun pricePerSqm() {
        assertEquals(56_300L, OfferPropertyRules.pricePerSqm(ListingType.SALE, PropertyType.LAND, 45_000_000L, 800))
        assertNull(OfferPropertyRules.pricePerSqm(ListingType.SALE, PropertyType.LAND, 45_000_000L, 4))
        assertNull(OfferPropertyRules.pricePerSqm(ListingType.SALE, PropertyType.LAND, null, 800))
        assertNull(OfferPropertyRules.pricePerSqm(ListingType.SALE, PropertyType.HOUSE, 45_000_000L, 800))
        assertNull(OfferPropertyRules.pricePerSqm(ListingType.RENTAL, PropertyType.LAND, 45_000_000L, 800))
        assertNull(OfferPropertyRules.pricePerSqm(ListingType.SALE, PropertyType.LAND, 1_000_000L, 1_000_000))
    }

    @Test
    fun projectLabel() {
        assertEquals("Vente", OfferPropertyRules.projectLabel(ListingType.SALE))
        assertEquals("Location", OfferPropertyRules.projectLabel(ListingType.RENTAL))
        assertEquals("Location", OfferPropertyRules.projectLabel(null))
    }
}
