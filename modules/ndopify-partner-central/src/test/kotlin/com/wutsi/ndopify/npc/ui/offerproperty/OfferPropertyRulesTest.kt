package com.wutsi.ndopify.npc.ui.offerproperty

import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
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
                PropertyType.INDUSTRIAL, PropertyType.LAND,
            ),
            OfferPropertyRules.CATEGORIES_BY_TYPE.keys.toList(),
        )
        assertEquals(residential, OfferPropertyRules.CATEGORIES_BY_TYPE[PropertyType.BUILDING])
        assertEquals(commercial, OfferPropertyRules.CATEGORIES_BY_TYPE[PropertyType.LAND])
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
        assertEquals("Indiquez le loyer mensuel souhaité.", OfferPropertyRules.priceAmount(null, FORMATTER))
        assertEquals("Indiquez le loyer mensuel souhaité.", OfferPropertyRules.priceAmount(0, FORMATTER))
        assertEquals(
            "Indiquez un loyer d'au moins ${FORMATTER.money(5000)} par mois.",
            OfferPropertyRules.priceAmount(4999, FORMATTER),
        )
        assertNull(OfferPropertyRules.priceAmount(5000, FORMATTER))
    }

    @Test
    fun advanceRent() {
        val message = "Choisissez une avance proposée."
        assertNull(OfferPropertyRules.advanceRent(null))
        assertEquals(message, OfferPropertyRules.advanceRent(0))
        assertNull(OfferPropertyRules.advanceRent(1))
        assertNull(OfferPropertyRules.advanceRent(12))
        assertEquals(message, OfferPropertyRules.advanceRent(13))
    }

    @Test
    fun securityDeposit() {
        val message = "Indiquez la caution demandée, en mois de loyer."
        assertEquals(message, OfferPropertyRules.securityDeposit(null))
        assertEquals(message, OfferPropertyRules.securityDeposit(0))
        assertNull(OfferPropertyRules.securityDeposit(1))
        assertNull(OfferPropertyRules.securityDeposit(6))
        assertEquals(message, OfferPropertyRules.securityDeposit(7))
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
        assertNull(OfferPropertyRules.propertyType(residential, "BUILDING"))
        assertNull(OfferPropertyRules.propertyType(commercial, "LAND"))
        assertEquals(message, OfferPropertyRules.propertyType(commercial, "BUILDING"))
        assertEquals(message, OfferPropertyRules.propertyType(residential, "VILLA"))
        assertEquals(message, OfferPropertyRules.propertyType(residential, null))
        assertEquals(message, OfferPropertyRules.propertyType(null, "APARTMENT"))
    }

    @Test
    fun needs() {
        assertFalse(OfferPropertyRules.needsFurniture(residential, PropertyType.BUILDING))
        assertTrue(OfferPropertyRules.needsFurniture(residential, PropertyType.ROOM))
        assertFalse(OfferPropertyRules.needsFurniture(commercial, PropertyType.OFFICE))
        assertFalse(OfferPropertyRules.needsRooms(residential, PropertyType.STUDIO))
        assertFalse(OfferPropertyRules.needsRooms(residential, PropertyType.ROOM))
        assertFalse(OfferPropertyRules.needsRooms(residential, PropertyType.BUILDING))
        assertTrue(OfferPropertyRules.needsRooms(residential, PropertyType.APARTMENT))
        assertFalse(OfferPropertyRules.needsRooms(commercial, PropertyType.OFFICE))
        assertFalse(OfferPropertyRules.needsParking(residential, PropertyType.ROOM))
        assertTrue(OfferPropertyRules.needsParking(residential, PropertyType.BUILDING))
        assertFalse(OfferPropertyRules.needsParking(commercial, PropertyType.OFFICE))
        assertTrue(OfferPropertyRules.needsArea(commercial))
        assertFalse(OfferPropertyRules.needsArea(residential))
        assertFalse(OfferPropertyRules.needsArea(null))
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
        assertEquals("Indiquez la surface, même approximative.", OfferPropertyRules.area(null))
        assertEquals("Indiquez une surface d'au moins 5 m².", OfferPropertyRules.area(4))
        assertNull(OfferPropertyRules.area(5))
        assertNull(OfferPropertyRules.area(1_000_000))
        assertEquals("Indiquez une surface d'au moins 5 m².", OfferPropertyRules.area(1_000_001))
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
}
