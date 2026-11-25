package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingSection
import com.wutsi.ndopify.listing.dto.ListingSectionStatus
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.npc.service.TenantFixtures.FORMATTER
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListingRulesTest {
    private val today = LocalDate.of(2026, 10, 26)

    /** A seller's villa: a SALE listing, with rental terms left over from an earlier answer. */
    private val sale = ListingViewsTest.LISTING.copy(
        listingType = ListingType.SALE,
        price = 45_000_000,
        furnitureType = FurnitureType.FULLY_FURNISHED,
        securityDeposit = 3,
        advanceRent = 2,
        leaseTerm = 12,
        noticePeriod = 1,
    )

    private fun request(section: ListingSection, form: ListingSectionForm) = ListingRules.request(section, form, sale)

    private fun errors(section: ListingSection, form: ListingSectionForm) =
        ListingRules.errors(section, form, sale, today, FORMATTER)

    @Test
    fun `a sale's form shows no rental term`() {
        val form = ListingSectionForm.of(sale, FORMATTER)

        assertEquals("45 000 000", form.price!!.replace('\u00a0', ' ').replace('\u202f', ' '))
        assertNull(form.furnitureType)
        assertNull(form.securityDeposit)
        assertNull(form.advanceRent)
        assertNull(form.leaseTerm)
        assertNull(form.noticePeriod)
        assertEquals("2", form.rooms)
    }

    @Test
    fun `a sale's terms section is the price and conditions`() {
        assertEquals("Prix et conditions", ListingRules.label(sale, ListingSection.TERMS))
        assertEquals("Prix et conditions enregistrés.", ListingRules.saved(sale, ListingSection.TERMS))
        assertEquals("Loyer et conditions", ListingRules.label(ListingViewsTest.LISTING, ListingSection.TERMS))
        assertEquals("Loyer et conditions enregistrés.", ListingRules.saved(ListingViewsTest.LISTING, ListingSection.TERMS))
        assertEquals("Emplacement", ListingRules.label(sale, ListingSection.LOCATION))
    }

    @Test
    fun `a sale's terms ask for the price only`() {
        val errors = errors(
            ListingSection.TERMS,
            ListingSectionForm(
                price = "",
                securityDeposit = "2",
                advanceRent = "3",
                leaseTerm = "12",
                noticePeriod = "1",
                availableFrom = "2026-11-15",
            ),
        )

        assertEquals(setOf("price"), errors.keys)
        assertEquals("Indiquez le prix de vente.", errors["price"])
        assertEquals(
            "Indiquez le prix de vente en chiffres, en FCFA.",
            errors(ListingSection.TERMS, ListingSectionForm(price = "45M", availableFrom = "2026-11-15"))["price"],
        )
    }

    @Test
    fun `a sale's terms request clears the rental-only terms`() {
        val request = request(
            ListingSection.TERMS,
            ListingSectionForm(
                price = "250 000 000",
                securityDeposit = "2",
                advanceRent = "3",
                leaseTerm = "12",
                noticePeriod = "1",
                availableFrom = "2026-12-01",
            ),
        )

        assertEquals(250_000_000L, request.price)
        assertNull(request.securityDeposit)
        assertNull(request.advanceRent)
        assertNull(request.leaseTerm)
        assertNull(request.noticePeriod)
        assertEquals(LocalDate.of(2026, 12, 1), request.availableFrom)
    }

    @Test
    fun `a sale listing's furniture is never asked`() {
        val form = ListingSectionForm(
            propertyCategory = "RESIDENTIAL",
            propertyType = "VILLA",
            rooms = "3",
            bathrooms = "2",
            area = "200",
        )

        assertFalse("furnitureType" in errors(ListingSection.PROPERTY, form).keys)
        assertNull(request(ListingSection.PROPERTY, form.copy(furnitureType = "FULLY_FURNISHED")).furnitureType)
    }

    @Test
    fun `a rental's terms are unchanged`() {
        val rental = ListingViewsTest.LISTING
        val form = ListingSectionForm(price = "", availableFrom = "2026-11-15")

        assertEquals(
            "Indiquez le loyer mensuel.",
            ListingRules.errors(ListingSection.TERMS, form, rental, today, FORMATTER)["price"],
        )
        assertEquals(
            "Indiquez le loyer en chiffres, en FCFA par mois.",
            ListingRules.errors(ListingSection.TERMS, form.copy(price = "45M"), rental, today, FORMATTER)["price"],
        )
        assertEquals(
            3,
            ListingRules.request(ListingSection.TERMS, ListingSectionForm(price = "150 000", securityDeposit = "3"), rental)
                .securityDeposit,
        )
        assertTrue(
            "furnitureType" in ListingRules.errors(
                ListingSection.PROPERTY,
                ListingSectionForm(propertyCategory = "RESIDENTIAL", propertyType = "VILLA", rooms = "3", bathrooms = "2", area = "200"),
                rental,
                today,
                FORMATTER,
            ).keys,
        )
    }

    @Test
    fun sections() {
        assertEquals(
            listOf(
                ListingSection.LOCATION,
                ListingSection.GPS,
                ListingSection.PROPERTY,
                ListingSection.AMENITIES,
                ListingSection.TERMS,
                ListingSection.PHOTOS,
                ListingSection.REMARKS,
            ),
            ListingRules.SECTIONS,
        )
        assertEquals("Eau, électricité, accès", ListingRules.LABELS[ListingSection.AMENITIES])
        assertEquals(ListingRules.SECTIONS.toSet(), ListingRules.LABELS.keys)
    }

    @Test
    fun saved() {
        assertEquals(ListingRules.FORM_SECTIONS.toSet(), ListingRules.SAVED.keys)
        assertEquals("Position GPS enregistrée.", ListingRules.SAVED[ListingSection.GPS])
    }

    @Test
    fun slug() {
        assertEquals("location", ListingRules.slug(ListingSection.LOCATION))
        assertEquals("photos", ListingRules.slug(ListingSection.PHOTOS))
    }

    @Test
    fun section() {
        assertEquals(ListingSection.GPS, ListingRules.section("gps"))
        assertNull(ListingRules.section("photos"))
        assertNull(ListingRules.section("unknown"))
        assertNull(ListingRules.section("nope"))
    }

    @Test
    fun tag() {
        assertEquals(ListingRules.Tag("Brouillon", "info"), ListingRules.tag(ListingStatus.DRAFT))
        assertEquals(ListingRules.Tag("En vérification", "warning"), ListingRules.tag(ListingStatus.PENDING_VERIFICATION))
        assertEquals(ListingRules.Tag("En vérification", "warning"), ListingRules.tag(ListingStatus.UNDER_VERIFICATION))
        assertEquals(ListingRules.Tag("Vérifiée", "success"), ListingRules.tag(ListingStatus.VERIFIED))
        assertEquals(ListingRules.Tag("En vérification", "warning"), ListingRules.tag(ListingStatus.PUBLISHING))
        assertEquals(ListingRules.Tag("Publiée", "success"), ListingRules.tag(ListingStatus.PUBLISHED))
        assertEquals(ListingRules.Tag("Fermée", "neutral"), ListingRules.tag(ListingStatus.RENTED))
        assertEquals(ListingRules.Tag("Fermée", "neutral"), ListingRules.tag(ListingStatus.SOLD))
        assertEquals(ListingRules.Tag("Fermée", "neutral"), ListingRules.tag(ListingStatus.EXPIRED))
        assertEquals(ListingRules.Tag("Fermée", "neutral"), ListingRules.tag(ListingStatus.CANCELLED))
        assertEquals(ListingRules.Tag("En attente", "warning"), ListingRules.tag(ListingStatus.PENDING))
    }

    @Test
    fun editable() {
        assertTrue(ListingRules.editable(Listing(status = ListingStatus.DRAFT)))
        assertFalse(ListingRules.editable(Listing(status = ListingStatus.UNDER_VERIFICATION)))
        assertTrue(ListingRules.editable(Listing(status = ListingStatus.DRAFT, errorCodes = listOf(ErrorCode.INTERNAL_ERROR))))
    }

    @Test
    fun `tag - sent back`() {
        val listing = Listing(status = ListingStatus.DRAFT, errorCodes = listOf(ErrorCode.LISTING_MISSING_LAT_LONG))

        assertEquals(ListingRules.Tag("À corriger", "warning"), ListingRules.tag(listing))
        assertEquals(ListingRules.Tag("Brouillon", "info"), ListingRules.tag(Listing(status = ListingStatus.DRAFT)))
        assertEquals(ListingRules.Tag("Publiée", "success"), ListingRules.tag(Listing(status = ListingStatus.PUBLISHED)))
    }

    @Test
    fun rejections() {
        val listing = Listing(
            status = ListingStatus.DRAFT,
            errorCodes = listOf(
                ErrorCode.LISTING_NOT_ENOUGH_IMAGES,
                ErrorCode.LISTING_OUTSIDE_NEIGHBORHOOD,
                ErrorCode.INTERNAL_ERROR,
                "urn:unknown",
            ),
        )

        assertEquals(
            listOf(
                ListingRules.Rejection("Pas assez de photos acceptées : il en faut 5 au moins.", ListingSection.PHOTOS),
                ListingRules.Rejection("La position GPS relevée n'est pas dans le quartier indiqué.", ListingSection.GPS),
                ListingRules.Rejection("La vérification de l'annonce n'a pas abouti.", null),
            ),
            ListingRules.rejections(listing),
        )
    }

    @Test
    fun `rejections - missing photo`() {
        val listing = Listing(status = ListingStatus.DRAFT, errorCodes = listOf(ErrorCode.LISTING_MISSING_PHOTO))

        assertEquals(
            listOf(
                ListingRules.Rejection(
                    "Il faut au moins une photo du bien : un plan ou une carte ne suffisent pas.",
                    ListingSection.PHOTOS,
                ),
            ),
            ListingRules.rejections(listing),
        )
    }

    @Test
    fun `rejections - draft without code`() {
        assertTrue(ListingRules.rejections(Listing(status = ListingStatus.DRAFT)).isEmpty())
    }

    @Test
    fun `rejections - not a draft`() {
        val listing = Listing(status = ListingStatus.PUBLISHING, errorCodes = listOf(ErrorCode.INTERNAL_ERROR))

        assertTrue(ListingRules.rejections(listing).isEmpty())
    }

    @Test
    fun complete() {
        val listing = ListingViewsTest.LISTING

        assertTrue(ListingRules.done(listing, ListingSection.LOCATION))
        assertFalse(ListingRules.done(listing, ListingSection.GPS))
        assertFalse(ListingRules.complete(listing))
        assertTrue(ListingRules.complete(ListingViewsTest.COMPLETE))
    }

    @Test
    fun `complete - remarks are optional`() {
        val listing = ListingViewsTest.COMPLETE
        val sections = listing.sections.map {
            if (it.section == ListingSection.REMARKS) it.copy(status = ListingSectionStatus.TO_VERIFY) else it
        }

        assertTrue(ListingRules.complete(listing.copy(sections = sections)))
    }

    @Test
    fun `complete - a section the server says is required`() {
        val listing = ListingViewsTest.COMPLETE
        val sections = listing.sections.map {
            if (it.section == ListingSection.REMARKS) it.copy(status = ListingSectionStatus.TO_VERIFY, required = true) else it
        }

        assertFalse(ListingRules.complete(listing.copy(sections = sections)))
        assertTrue(ListingRules.required(listing.copy(sections = sections), ListingSection.REMARKS))
        assertFalse(ListingRules.required(listing, ListingSection.REMARKS))
    }

    @Test
    fun `required - a section the server doesn't list`() {
        assertTrue(ListingRules.required(Listing(), ListingSection.GPS))
    }

    @Test
    fun `complete - without checklist`() {
        assertFalse(ListingRules.complete(Listing(status = ListingStatus.DRAFT)))
    }

    @Test
    fun sent() {
        assertFalse(ListingRules.sent(ListingStatus.DRAFT))
        assertTrue(ListingRules.sent(ListingStatus.UNDER_VERIFICATION))
        assertTrue(ListingRules.sent(ListingStatus.VERIFIED))
        assertTrue(ListingRules.sent(ListingStatus.PUBLISHING))
        assertFalse(ListingRules.sent(ListingStatus.PUBLISHED))
    }

    @Test
    fun `url - without a back path`() {
        assertEquals("/listings/501", ListingRules.url(501))
    }

    @Test
    fun `url - with a back path`() {
        assertEquals(
            "/listings/501?back=/mandates/204%23matches-title",
            ListingRules.url(501, "/mandates/204#matches-title"),
        )
        assertEquals(
            "/listings/5001?back=/agent?id%3D30%26page%3D1",
            ListingRules.url(5001, "/agent?id=30&page=1"),
        )
    }

    /** A building sold as a whole: six sections (no amenities), its counts instead of rooms and parking. */
    private val building = ListingViewsTest.LISTING.copy(
        listingType = ListingType.SALE,
        propertyCategory = PropertyCategory.RESIDENTIAL,
        propertyType = PropertyType.BUILDING,
        rooms = null,
        bathrooms = null,
        parkings = null,
        levels = 3,
        units = 12,
        sections = ListingViewsTest.LISTING.sections.filter { it.section != ListingSection.AMENITIES },
    )

    private fun errors(section: ListingSection, form: ListingSectionForm, listing: Listing) =
        ListingRules.errors(section, form, listing, today, FORMATTER)

    @Test
    fun `a building sale's checklist has no amenities section`() {
        assertTrue(ListingRules.soldBuilding(building))
        assertEquals(ListingRules.SECTIONS - ListingSection.AMENITIES, ListingRules.sections(building))
        assertEquals(ListingRules.SECTIONS, ListingRules.sections(ListingViewsTest.LISTING))
        assertFalse(ListingRules.soldBuilding(ListingViewsTest.LISTING.copy(listingType = ListingType.SALE)))
        assertFalse(ListingRules.soldBuilding(building.copy(listingType = ListingType.RENTAL)))
    }

    @Test
    fun `a building sale asks units and no bathroom nor parking`() {
        val commercial = PropertyCategory.COMMERCIAL
        val residential = PropertyCategory.RESIDENTIAL

        assertTrue(ListingRules.applies("units", commercial, PropertyType.BUILDING, ListingType.SALE))
        assertFalse(ListingRules.applies("units", commercial, PropertyType.BUILDING, ListingType.RENTAL))
        assertFalse(ListingRules.applies("units", residential, PropertyType.VILLA, ListingType.SALE))
        assertFalse(ListingRules.applies("bathrooms", commercial, PropertyType.BUILDING, ListingType.SALE))
        assertTrue(ListingRules.applies("bathrooms", commercial, PropertyType.BUILDING, ListingType.RENTAL))
        assertFalse(ListingRules.applies("parkings", residential, PropertyType.BUILDING, ListingType.SALE))
        assertTrue(ListingRules.applies("parkings", residential, PropertyType.BUILDING, ListingType.RENTAL))
        assertTrue(ListingRules.applies("parkings", residential, PropertyType.HOUSE, ListingType.SALE))
    }

    @Test
    fun `a building sale's property asks its two counts`() {
        val form = ListingSectionForm(
            propertyCategory = "RESIDENTIAL",
            propertyType = "BUILDING",
            levels = "3",
            units = "12",
            area = "850",
        )

        assertEquals(emptyMap(), errors(ListingSection.PROPERTY, form, building))
        assertEquals(
            mapOf(
                "levels" to "Indiquez ce nombre, compté sur place.",
                "units" to "Indiquez ce nombre, compté sur place.",
            ),
            errors(ListingSection.PROPERTY, form.copy(levels = null, units = null), building),
        )
        assertEquals(
            "Indiquez un nombre d'étages, de 1 à 30.",
            errors(ListingSection.PROPERTY, form.copy(levels = "31"), building)["levels"],
        )
        assertEquals(
            "Indiquez un nombre d'unités, de 1 à 500.",
            errors(ListingSection.PROPERTY, form.copy(units = "501"), building)["units"],
        )
    }

    @Test
    fun `a building sale's property request sends its two counts and no bathroom nor parking`() {
        val request = ListingRules.request(
            ListingSection.PROPERTY,
            ListingSectionForm(
                propertyCategory = "RESIDENTIAL",
                propertyType = "BUILDING",
                bathrooms = "2",
                parkings = "3",
                levels = "3",
                units = "12",
                area = "850",
            ),
            building,
        )

        assertEquals(3, request.levels)
        assertEquals(12, request.units)
        assertNull(request.bathrooms)
        assertNull(request.parkings)
        assertNull(request.rooms)
    }

    @Test
    fun `a type's categories follow the sale`() {
        assertEquals(
            listOf(PropertyCategory.RESIDENTIAL, PropertyCategory.COMMERCIAL),
            ListingRules.typeCategories(PropertyType.BUILDING, sale = true),
        )
        assertEquals(
            listOf(PropertyCategory.RESIDENTIAL),
            ListingRules.typeCategories(PropertyType.BUILDING, sale = false),
        )
        assertEquals(
            listOf(PropertyCategory.RESIDENTIAL),
            ListingRules.typeCategories(PropertyType.APARTMENT, sale = true),
        )
        assertEquals(
            listOf(PropertyCategory.COMMERCIAL),
            ListingRules.typeCategories(PropertyType.OFFICE, sale = true),
        )
    }

    @Test
    fun `a sale building fits both categories, a rental only residential`() {
        assertTrue(ListingRules.typeFits(PropertyCategory.COMMERCIAL, PropertyType.BUILDING, sale = true))
        assertTrue(ListingRules.typeFits(PropertyCategory.RESIDENTIAL, PropertyType.BUILDING, sale = true))
        assertFalse(ListingRules.typeFits(PropertyCategory.COMMERCIAL, PropertyType.BUILDING, sale = false))
        assertTrue(ListingRules.typeFits(PropertyCategory.RESIDENTIAL, PropertyType.BUILDING, sale = false))
    }

    @Test
    fun `a commercial sale lists the building first and the plot of land last`() {
        assertEquals(
            listOf(PropertyType.BUILDING, PropertyType.OFFICE, PropertyType.RETAIL, PropertyType.INDUSTRIAL, PropertyType.LAND),
            ListingRules.types(PropertyCategory.COMMERCIAL, sale = true),
        )
        assertEquals(
            listOf(PropertyType.OFFICE, PropertyType.RETAIL, PropertyType.INDUSTRIAL),
            ListingRules.types(PropertyCategory.COMMERCIAL, sale = false),
        )
        assertEquals(
            ListingRules.TYPES.getValue(PropertyCategory.RESIDENTIAL),
            ListingRules.types(PropertyCategory.RESIDENTIAL, sale = true),
        )
        assertEquals(
            ListingRules.TYPES.getValue(PropertyCategory.RESIDENTIAL) - PropertyType.LAND,
            ListingRules.types(PropertyCategory.RESIDENTIAL, sale = false),
        )
    }

    @Test
    fun `a sale land fits both categories, a rental only none`() {
        assertTrue(ListingRules.typeFits(PropertyCategory.RESIDENTIAL, PropertyType.LAND, sale = true))
        assertTrue(ListingRules.typeFits(PropertyCategory.COMMERCIAL, PropertyType.LAND, sale = true))
        assertFalse(ListingRules.typeFits(PropertyCategory.RESIDENTIAL, PropertyType.LAND, sale = false))
        assertFalse(ListingRules.typeFits(PropertyCategory.COMMERCIAL, PropertyType.LAND, sale = false))
        assertEquals(
            listOf(PropertyCategory.RESIDENTIAL, PropertyCategory.COMMERCIAL),
            ListingRules.typeCategories(PropertyType.LAND, sale = true),
        )
        assertEquals(listOf(PropertyCategory.RESIDENTIAL), ListingRules.typeCategories(PropertyType.LAND, sale = false))
    }

    @Test
    fun `every type is listed once and a rental lists no land`() {
        assertEquals(
            listOf(
                PropertyType.APARTMENT,
                PropertyType.HOUSE,
                PropertyType.VILLA,
                PropertyType.DUPLEX,
                PropertyType.STUDIO,
                PropertyType.ROOM,
                PropertyType.BUILDING,
                PropertyType.OFFICE,
                PropertyType.RETAIL,
                PropertyType.INDUSTRIAL,
                PropertyType.LAND,
            ),
            ListingRules.allTypes(sale = true),
        )
        assertEquals(ListingRules.allTypes(sale = true) - PropertyType.LAND, ListingRules.allTypes(sale = false))
    }

    @Test
    fun `a commercial building sale keeps its category`() {
        val commercial = building.copy(propertyCategory = PropertyCategory.COMMERCIAL)
        val form = ListingSectionForm(
            propertyCategory = "COMMERCIAL",
            propertyType = "BUILDING",
            levels = "3",
            units = "12",
            area = "850",
        )

        assertEquals(emptyMap(), errors(ListingSection.PROPERTY, form, commercial))
        val request = ListingRules.request(ListingSection.PROPERTY, form, commercial)
        assertEquals(PropertyCategory.COMMERCIAL, request.propertyCategory)
        assertEquals(PropertyType.BUILDING, request.propertyType)
        assertEquals(3, request.levels)
        assertEquals(12, request.units)
    }
}
