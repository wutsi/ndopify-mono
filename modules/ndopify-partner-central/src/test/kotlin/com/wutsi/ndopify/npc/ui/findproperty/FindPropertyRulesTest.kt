package com.wutsi.ndopify.npc.ui.findproperty

import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.FinancingType
import com.wutsi.ndopify.mandate.dto.TitleDocumentType
import com.wutsi.ndopify.npc.service.TenantFixtures.FORMATTER
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FindPropertyRulesTest {
    private val residential = PropertyCategory.RESIDENTIAL
    private val commercial = PropertyCategory.COMMERCIAL

    @Test
    fun propertyCategory() {
        assertNull(FindPropertyRules.propertyCategory(PropertyCategory.RESIDENTIAL))
        assertEquals(
            "Indiquez si le bien est pour y habiter ou pour une activité.",
            FindPropertyRules.propertyCategory(PropertyCategory.UNKNOWN),
        )
        assertEquals(
            "Indiquez si le bien est pour y habiter ou pour une activité.",
            FindPropertyRules.propertyCategory(null),
        )
    }

    @Test
    fun cityId() {
        assertNull(FindPropertyRules.cityId(2220957))
        assertEquals("Choisissez la ville.", FindPropertyRules.cityId(null))
        assertEquals("Choisissez la ville.", FindPropertyRules.cityId(-1))
    }

    @Test
    fun neighborhoodIds() {
        assertNull(FindPropertyRules.neighborhoodIds(listOf(1L, 2L, 3L)))
        assertNull(FindPropertyRules.neighborhoodIds(emptyList()))
        assertEquals("Choisissez au plus 3 quartiers.", FindPropertyRules.neighborhoodIds(listOf(1L, 2L, 3L, 4L)))
    }

    @Test
    fun propertyTypeIsOptionalButMustFitCategory() {
        assertNull(FindPropertyRules.propertyType(PropertyCategory.RESIDENTIAL, null, null))
        assertNull(FindPropertyRules.propertyType(PropertyCategory.RESIDENTIAL, "ANY", null))
        assertNull(FindPropertyRules.propertyType(PropertyCategory.RESIDENTIAL, "APARTMENT", null))
        assertNull(FindPropertyRules.propertyType(PropertyCategory.COMMERCIAL, "OFFICE", null))
        assertEquals(
            "Choisissez un type de bien proposé.",
            FindPropertyRules.propertyType(PropertyCategory.COMMERCIAL, "APARTMENT", null),
        )
        assertEquals(
            "Choisissez un type de bien proposé.",
            FindPropertyRules.propertyType(PropertyCategory.RESIDENTIAL, "LAND", null),
        )
        assertEquals(
            "Choisissez un type de bien proposé.",
            FindPropertyRules.propertyType(PropertyCategory.RESIDENTIAL, "CASTLE", null),
        )
        assertEquals(
            "Choisissez un type de bien proposé.",
            FindPropertyRules.propertyType(PropertyCategory.RESIDENTIAL, "UNKNOWN", null),
        )
    }

    @Test
    fun `a sale offers land under both usages and no room`() {
        assertEquals(
            listOf(
                PropertyType.STUDIO,
                PropertyType.APARTMENT,
                PropertyType.HOUSE,
                PropertyType.VILLA,
                PropertyType.DUPLEX,
                PropertyType.LAND,
            ),
            FindPropertyRules.propertyTypes(PropertyCategory.RESIDENTIAL, ListingType.SALE),
        )
        assertEquals(
            listOf(
                PropertyType.RETAIL,
                PropertyType.OFFICE,
                PropertyType.INDUSTRIAL,
                PropertyType.LAND,
                PropertyType.BUILDING,
            ),
            FindPropertyRules.propertyTypes(PropertyCategory.COMMERCIAL, ListingType.SALE),
        )
        assertEquals(
            listOf(
                PropertyType.ROOM,
                PropertyType.STUDIO,
                PropertyType.APARTMENT,
                PropertyType.HOUSE,
                PropertyType.VILLA,
                PropertyType.DUPLEX,
            ),
            FindPropertyRules.propertyTypes(PropertyCategory.RESIDENTIAL, ListingType.RENTAL),
        )
        assertEquals(
            listOf(PropertyType.RETAIL, PropertyType.OFFICE, PropertyType.INDUSTRIAL),
            FindPropertyRules.propertyTypes(PropertyCategory.COMMERCIAL, ListingType.RENTAL),
        )
        assertFalse(FindPropertyRules.typeFits(PropertyCategory.RESIDENTIAL, "ROOM", ListingType.SALE))
        assertTrue(FindPropertyRules.typeFits(PropertyCategory.RESIDENTIAL, "LAND", ListingType.SALE))
        assertTrue(FindPropertyRules.typeFits(PropertyCategory.COMMERCIAL, "LAND", ListingType.SALE))
        assertFalse(FindPropertyRules.typeFits(PropertyCategory.RESIDENTIAL, "LAND", ListingType.RENTAL))
        assertFalse(FindPropertyRules.typeFits(PropertyCategory.COMMERCIAL, "LAND", ListingType.RENTAL))
    }

    @Test
    fun `a commercial purchase accepts a plot of land`() {
        assertNull(FindPropertyRules.propertyType(PropertyCategory.COMMERCIAL, "LAND", ListingType.SALE))
        assertEquals(
            listOf(
                PropertyType.RETAIL,
                PropertyType.OFFICE,
                PropertyType.INDUSTRIAL,
                PropertyType.LAND,
                PropertyType.BUILDING,
            ),
            FindPropertyRules.propertyTypes(PropertyCategory.COMMERCIAL, ListingType.SALE),
        )
        assertFalse(FindPropertyRules.needsRooms(PropertyCategory.COMMERCIAL, PropertyType.LAND, ListingType.SALE))
        assertFalse(FindPropertyRules.asksParkings(PropertyCategory.COMMERCIAL, PropertyType.LAND, ListingType.SALE))
        assertTrue(FindPropertyRules.needsArea(PropertyCategory.COMMERCIAL, PropertyType.LAND, ListingType.SALE))
        assertFalse(FindPropertyRules.needsFurniture(PropertyCategory.RESIDENTIAL, PropertyType.LAND, ListingType.SALE))
    }

    @Test
    fun followUps() {
        assertTrue(FindPropertyRules.needsRooms(PropertyCategory.RESIDENTIAL, PropertyType.APARTMENT, null))
        assertTrue(FindPropertyRules.needsRooms(PropertyCategory.RESIDENTIAL, null, null))
        assertFalse(FindPropertyRules.needsRooms(PropertyCategory.RESIDENTIAL, PropertyType.STUDIO, null))
        assertFalse(FindPropertyRules.needsRooms(PropertyCategory.RESIDENTIAL, PropertyType.ROOM, null))
        assertFalse(FindPropertyRules.needsRooms(PropertyCategory.COMMERCIAL, null, null))
        assertFalse(FindPropertyRules.needsRooms(PropertyCategory.COMMERCIAL, PropertyType.OFFICE, null))
        assertFalse(FindPropertyRules.needsRooms(null, null, null))
        assertFalse(FindPropertyRules.needsFurniture(PropertyCategory.RESIDENTIAL, PropertyType.LAND, null))
        assertTrue(FindPropertyRules.needsFurniture(PropertyCategory.RESIDENTIAL, null, null))
        assertFalse(FindPropertyRules.needsFurniture(PropertyCategory.COMMERCIAL, null, null))
        assertTrue(FindPropertyRules.asksParkings(PropertyCategory.RESIDENTIAL, PropertyType.ROOM, null))
        assertTrue(FindPropertyRules.asksParkings(PropertyCategory.RESIDENTIAL, null, null))
        assertFalse(FindPropertyRules.asksParkings(PropertyCategory.COMMERCIAL, null, null))
    }

    @Test
    fun `a building sale asks its counts, no parking and no amenities step`() {
        assertTrue(FindPropertyRules.buildingSale(ListingType.SALE, PropertyType.BUILDING))
        assertFalse(FindPropertyRules.buildingSale(ListingType.RENTAL, PropertyType.BUILDING))
        assertFalse(FindPropertyRules.buildingSale(ListingType.SALE, PropertyType.VILLA))
        assertFalse(FindPropertyRules.buildingSale(null, null))

        assertFalse(
            FindPropertyRules.needsAmenities(PropertyCategory.COMMERCIAL, PropertyType.BUILDING, ListingType.SALE),
        )
        assertTrue(
            FindPropertyRules.needsAmenities(PropertyCategory.COMMERCIAL, PropertyType.BUILDING, ListingType.RENTAL),
        )
        assertTrue(FindPropertyRules.needsAmenities(PropertyCategory.RESIDENTIAL, PropertyType.VILLA, ListingType.SALE))
        assertTrue(FindPropertyRules.needsAmenities(PropertyCategory.COMMERCIAL, null, ListingType.SALE))
        assertTrue(FindPropertyRules.needsAmenities(PropertyCategory.RESIDENTIAL, null, null))

        assertFalse(FindPropertyRules.asksParkings(PropertyCategory.COMMERCIAL, PropertyType.BUILDING, ListingType.SALE))
        assertFalse(FindPropertyRules.asksParkings(PropertyCategory.COMMERCIAL, PropertyType.BUILDING, ListingType.RENTAL))
        assertTrue(FindPropertyRules.asksParkings(PropertyCategory.RESIDENTIAL, PropertyType.VILLA, ListingType.SALE))
    }

    @Test
    fun `a building sale's counts are whole numbers within their bounds, or indifferent`() {
        assertEquals(30, FindPropertyRules.MAX_LEVELS)
        assertEquals(500, FindPropertyRules.MAX_UNITS)

        assertNull(FindPropertyRules.levels("1", false))
        assertNull(FindPropertyRules.levels("3", false))
        assertNull(FindPropertyRules.levels("30", false))
        assertEquals(
            "Indiquez le nombre d'étages, ou cochez Indifférent.",
            FindPropertyRules.levels(null, false),
        )
        assertEquals(
            "Indiquez le nombre d'étages, ou cochez Indifférent.",
            FindPropertyRules.levels("", false),
        )
        assertEquals("Indiquez un nombre entier, de 1 à 30.", FindPropertyRules.levels("31", false))
        assertEquals("Indiquez un nombre entier, de 1 à 30.", FindPropertyRules.levels("3,5", false))
        assertEquals("Indiquez un nombre entier, de 1 à 30.", FindPropertyRules.levels("0", false))

        // "Indifférent": a UI answer, sent to the server as null.
        assertNull(FindPropertyRules.levels("", true))
        assertNull(FindPropertyRules.levels("31", true))

        assertNull(FindPropertyRules.units("12", false))
        assertNull(FindPropertyRules.units("500", false))
        assertEquals(
            "Indiquez le nombre d'unités, ou cochez Indifférent.",
            FindPropertyRules.units(null, false),
        )
        assertEquals("Indiquez un nombre entier, de 1 à 500.", FindPropertyRules.units("501", false))
        assertNull(FindPropertyRules.units("501", true))
    }

    @Test
    fun `a residential purchase asks furniture, a room asks its parkings, a commercial type asks none`() {
        assertTrue(FindPropertyRules.needsFurniture(residential, PropertyType.HOUSE, ListingType.SALE))
        assertTrue(FindPropertyRules.needsFurniture(residential, null, ListingType.SALE)) // "Indifférent"
        assertFalse(FindPropertyRules.needsFurniture(residential, PropertyType.BUILDING, ListingType.SALE))
        assertFalse(FindPropertyRules.needsFurniture(commercial, PropertyType.OFFICE, ListingType.RENTAL))
        assertTrue(FindPropertyRules.asksParkings(residential, PropertyType.ROOM, ListingType.RENTAL))
        assertFalse(FindPropertyRules.asksParkings(commercial, PropertyType.OFFICE, ListingType.RENTAL))
        assertFalse(FindPropertyRules.asksParkings(residential, PropertyType.LAND, ListingType.SALE))
        assertFalse(FindPropertyRules.asksParkings(commercial, PropertyType.BUILDING, ListingType.SALE))
    }

    @Test
    fun `a purchase asks no rooms for a plot, a studio or a room`() {
        assertFalse(FindPropertyRules.needsRooms(PropertyCategory.RESIDENTIAL, PropertyType.LAND, ListingType.SALE))
        assertTrue(FindPropertyRules.needsRooms(PropertyCategory.RESIDENTIAL, PropertyType.HOUSE, ListingType.SALE))
        assertTrue(FindPropertyRules.needsRooms(PropertyCategory.RESIDENTIAL, PropertyType.VILLA, ListingType.SALE))
        assertTrue(FindPropertyRules.needsRooms(PropertyCategory.RESIDENTIAL, null, ListingType.SALE))
        assertFalse(FindPropertyRules.needsRooms(PropertyCategory.RESIDENTIAL, PropertyType.STUDIO, ListingType.SALE))
        assertFalse(FindPropertyRules.needsRooms(PropertyCategory.RESIDENTIAL, PropertyType.ROOM, ListingType.SALE))
    }

    @Test
    fun `a residential non-land purchase asks no surface`() {
        assertFalse(FindPropertyRules.needsArea(residential, PropertyType.VILLA, ListingType.SALE))
        assertTrue(FindPropertyRules.needsArea(commercial, PropertyType.OFFICE, ListingType.SALE))
        assertTrue(FindPropertyRules.needsArea(commercial, PropertyType.OFFICE, ListingType.RENTAL))
        assertTrue(FindPropertyRules.needsArea(residential, PropertyType.LAND, ListingType.SALE))
    }

    @Test
    fun `a rental never offers a building`() {
        assertFalse(
            FindPropertyRules.propertyTypes(PropertyCategory.COMMERCIAL, ListingType.RENTAL)
                .contains(PropertyType.BUILDING),
        )
        assertFalse(
            FindPropertyRules.propertyTypes(PropertyCategory.RESIDENTIAL, ListingType.RENTAL)
                .contains(PropertyType.BUILDING),
        )
        assertTrue(
            FindPropertyRules.propertyTypes(PropertyCategory.COMMERCIAL, ListingType.SALE)
                .contains(PropertyType.BUILDING),
        )
        assertFalse(FindPropertyRules.typeFits(PropertyCategory.COMMERCIAL, "BUILDING", ListingType.RENTAL))
    }

    @Test
    fun parseListingType() {
        assertEquals(ListingType.SALE, FindPropertyRules.parseListingType("SALE"))
        assertEquals(ListingType.RENTAL, FindPropertyRules.parseListingType("RENTAL"))
        assertNull(FindPropertyRules.parseListingType("sale"))
        assertNull(FindPropertyRules.parseListingType("ANY"))
        assertNull(FindPropertyRules.parseListingType(null))
        assertNull(FindPropertyRules.parseListingType(""))
    }

    @Test
    fun listingType() {
        assertNull(FindPropertyRules.listingType(ListingType.SALE))
        assertNull(FindPropertyRules.listingType(ListingType.RENTAL))
        assertEquals(
            "Indiquez si vous souhaitez louer ou acheter.",
            FindPropertyRules.listingType(ListingType.UNKNOWN),
        )
        assertEquals("Indiquez si vous souhaitez louer ou acheter.", FindPropertyRules.listingType(null))
    }

    @Test
    fun parsePropertyType() {
        assertEquals(PropertyType.APARTMENT, FindPropertyRules.parsePropertyType("APARTMENT"))
        assertNull(FindPropertyRules.parsePropertyType(null))
        assertNull(FindPropertyRules.parsePropertyType(""))
        assertNull(FindPropertyRules.parsePropertyType(FindPropertyRules.ANY))
        assertNull(FindPropertyRules.parsePropertyType("UNKNOWN"))
        assertNull(FindPropertyRules.parsePropertyType("apartment"))
    }

    @Test
    fun rooms() {
        assertNull(FindPropertyRules.rooms("5+"))
        assertEquals("Indiquez le nombre de chambres.", FindPropertyRules.rooms(null))
        assertEquals("Indiquez le nombre de chambres.", FindPropertyRules.rooms("7"))
    }

    @Test
    fun furnitureType() {
        assertNull(FindPropertyRules.furnitureType("ANY"))
        assertNull(FindPropertyRules.furnitureType("FULLY_FURNISHED"))
        assertEquals("Indiquez si le bien doit être meublé.", FindPropertyRules.furnitureType(null))
        assertEquals("Indiquez si le bien doit être meublé.", FindPropertyRules.furnitureType("UNKNOWN"))
    }

    @Test
    fun parkings() {
        assertNull(FindPropertyRules.parkings(null))
        assertNull(FindPropertyRules.parkings("3+"))
        assertNull(FindPropertyRules.parkings("ANY"))
        assertEquals("Choisissez un nombre de places proposé.", FindPropertyRules.parkings("9"))
    }

    @Test
    fun area() {
        assertNull(FindPropertyRules.area(null, false))
        assertNull(FindPropertyRules.area(5, false))
        assertNull(FindPropertyRules.area(1_000_000, false))
        assertEquals(
            "Indiquez une surface d'au moins 5 m², ou laissez ce champ vide.",
            FindPropertyRules.area(4, false),
        )
        assertEquals(
            "Indiquez une surface d'au moins 5 m², ou laissez ce champ vide.",
            FindPropertyRules.area(1_000_001, false),
        )
    }

    @Test
    fun `a land purchase requires its surface`() {
        assertNull(FindPropertyRules.area(400, true))
        assertEquals("Indiquez la surface du terrain.", FindPropertyRules.area(null, true))
        assertEquals("Indiquez une surface d'au moins 5 m².", FindPropertyRules.area(4, true))
        assertTrue(FindPropertyRules.needsArea(PropertyCategory.RESIDENTIAL, PropertyType.LAND, ListingType.SALE))
        assertFalse(FindPropertyRules.needsArea(PropertyCategory.RESIDENTIAL, PropertyType.LAND, ListingType.RENTAL))
        assertFalse(FindPropertyRules.needsArea(PropertyCategory.RESIDENTIAL, PropertyType.VILLA, ListingType.SALE))
    }

    @Test
    fun priceAmount() {
        assertNull(FindPropertyRules.priceAmount(5000L, FORMATTER, null))
        assertEquals("Indiquez votre budget mensuel.", FindPropertyRules.priceAmount(null, FORMATTER, null))
        assertEquals(
            "Indiquez un loyer d'au moins 5K\u00a0FCFA par mois.",
            FindPropertyRules.priceAmount(4999L, FORMATTER, null),
        )
    }

    @Test
    fun `a sale has its own price floor`() {
        assertNull(FindPropertyRules.priceAmount(1_000_000L, FORMATTER, ListingType.SALE))
        assertEquals("Indiquez votre budget total.", FindPropertyRules.priceAmount(null, FORMATTER, ListingType.SALE))
        assertEquals(
            "Indiquez un budget d'au moins 1M\u00a0FCFA.",
            FindPropertyRules.priceAmount(999_999L, FORMATTER, ListingType.SALE),
        )
        assertNull(FindPropertyRules.priceAmount(5_000L, FORMATTER, ListingType.RENTAL))
    }

    @Test
    fun isPriceAmount() {
        assertTrue(FindPropertyRules.isPriceAmount(5000L, null))
        assertFalse(FindPropertyRules.isPriceAmount(4999L, null))
        assertFalse(FindPropertyRules.isPriceAmount(null, null))
        assertTrue(FindPropertyRules.isPriceAmount(1_000_000L, ListingType.SALE))
        assertFalse(FindPropertyRules.isPriceAmount(999_999L, ListingType.SALE))
    }

    @Test
    fun parseAmount() {
        assertEquals(150000L, FindPropertyRules.parseAmount("150 000"))
        assertEquals(150000L, FindPropertyRules.parseAmount("150 000")) // fr-FR toLocaleString
        assertEquals(150000L, FindPropertyRules.parseAmount("150.000"))
        assertNull(FindPropertyRules.parseAmount(""))
        assertNull(FindPropertyRules.parseAmount(null))
        assertNull(FindPropertyRules.parseAmount("abc"))
        assertNull(FindPropertyRules.parseAmount("99999999999999999999")) // overflow, not a crash
    }

    @Test
    fun movingPeriod() {
        assertNull(FindPropertyRules.movingPeriod(MovingPeriod.WITHIN_1_MONTH, null))
        assertEquals(
            "Indiquez quand vous souhaitez emménager.",
            FindPropertyRules.movingPeriod(MovingPeriod.UNKNOWN, null),
        )
        assertEquals("Indiquez quand vous souhaitez emménager.", FindPropertyRules.movingPeriod(null, null))
        assertNull(FindPropertyRules.movingPeriod(MovingPeriod.WITHIN_1_MONTH, ListingType.SALE))
        assertEquals(
            "Indiquez quand vous souhaitez acheter.",
            FindPropertyRules.movingPeriod(null, ListingType.SALE),
        )
    }

    @Test
    fun `the financing is required for a sale only`() {
        assertNotNull(FindPropertyRules.financingType(null, ListingType.SALE))
        assertNotNull(FindPropertyRules.financingType(FinancingType.UNKNOWN, ListingType.SALE))
        assertNull(FindPropertyRules.financingType(FinancingType.CASH, ListingType.SALE))
        assertNull(FindPropertyRules.financingType(null, ListingType.RENTAL))
    }

    @Test
    fun `every purchase may require its documents`() {
        val three = listOf(
            TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN, TitleDocumentType.CERTIFICATE_OF_OWNERSHIP,
        )
        assertEquals(three, FindPropertyRules.titleDocumentTypes(ListingType.SALE, PropertyType.LAND))
        assertEquals(three, FindPropertyRules.titleDocumentTypes(ListingType.SALE, PropertyType.APARTMENT))
        assertEquals(emptyList(), FindPropertyRules.titleDocumentTypes(ListingType.RENTAL, PropertyType.APARTMENT))
        assertEquals(emptyList(), FindPropertyRules.titleDocumentTypes(null, null))
    }

    @Test
    fun preferredContactPeriod() {
        assertNull(FindPropertyRules.preferredContactPeriod(ContactPeriodType.EVENING))
        assertEquals(
            "Indiquez quand votre agent peut vous contacter.",
            FindPropertyRules.preferredContactPeriod(ContactPeriodType.UNKNOWN),
        )
    }

    @Test
    fun comments() {
        assertNull(FindPropertyRules.comments(null))
        assertNull(FindPropertyRules.comments("a".repeat(500)))
        assertEquals("Votre message ne doit pas dépasser 500 caractères.", FindPropertyRules.comments("a".repeat(501)))
    }

    @Test
    fun projectLabel() {
        assertEquals("Achat", FindPropertyRules.projectLabel(ListingType.SALE))
        assertEquals("Location", FindPropertyRules.projectLabel(ListingType.RENTAL))
        assertEquals("Location", FindPropertyRules.projectLabel(null))
    }
}
