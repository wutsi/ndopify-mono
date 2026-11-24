package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.FinancingType
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.TitleDocumentType
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

        /** An approved buyer's search for a house: a one-off budget, no furniture. */
        val SALE_SEARCH = MANDATE.copy(
            id = 204,
            listingType = ListingType.SALE,
            price = Money(15_000_000, "XAF", PeriodicityType.NONE),
            furnitureType = FurnitureType.UNFURNISHED,
        )

        /** A seller's villa: a one-off price, no furniture. */
        val SALE_LISTING = SALE_SEARCH.copy(mandateType = MandateType.LISTING)

        /** A buyer's search for a building sold as a whole. */
        val BUILDING_SEARCH = SALE_SEARCH.copy(
            propertyCategory = PropertyCategory.COMMERCIAL,
            propertyType = PropertyType.BUILDING,
            rooms = null,
            levels = 3,
            units = 12,
            area = 850,
            price = Money(250_000_000, "XAF", PeriodicityType.NONE),
        )

        /** The same building, offered by its seller. */
        val BUILDING_LISTING = BUILDING_SEARCH.copy(mandateType = MandateType.LISTING)

        /** The building's answers; the rooms and parking it could carry are left over from an earlier type. */
        val BUILDING_FORM = FORM.copy(
            propertyCategory = PropertyCategory.COMMERCIAL,
            propertyType = "BUILDING",
            rooms = "3",
            furnitureType = null,
            parkings = "2",
            levels = "3",
            units = "12",
            area = "850",
            priceAmount = "250 000 000",
            amenities = listOf("waterWell", "securityGuard"),
        )
    }

    private fun text(value: String?): String = value!!.replace('\u00a0', ' ').replace('\u202f', ' ')

    @Test
    fun `a sale search's budget has no monthly period`() {
        assertEquals(
            "Indiquez le budget, en FCFA.",
            MandateEditRules.errors(FORM.copy(priceAmount = ""), SALE_SEARCH, FORMATTER)["priceAmount"],
        )
        assertEquals(
            "Indiquez le budget, en FCFA.",
            MandateEditRules.errors(FORM.copy(priceAmount = "0"), SALE_SEARCH, FORMATTER)["priceAmount"],
        )
        assertEquals(
            "Indiquez quand vous souhaitez acheter.",
            MandateEditRules.errors(FORM.copy(movingPeriod = null), SALE_SEARCH, FORMATTER)["movingPeriod"],
        )
    }

    @Test
    fun `a sale listing's price is asked, not its rent`() {
        assertEquals(
            "Indiquez le prix, en FCFA.",
            MandateEditRules.errors(FORM.copy(priceAmount = ""), SALE_LISTING, FORMATTER)["priceAmount"],
        )
        assertEquals(
            "Indiquez quand le bien est disponible.",
            MandateEditRules.errors(FORM.copy(movingPeriod = null), SALE_LISTING, FORMATTER)["movingPeriod"],
        )
    }

    @Test
    fun `a sale listing's floor is a price, a sale search's a budget`() {
        assertEquals(
            "Indiquez un prix d'au moins 1M FCFA.",
            text(MandateEditRules.errors(FORM.copy(priceAmount = "1 000"), SALE_LISTING, FORMATTER)["priceAmount"]),
        )
        assertEquals(
            "Indiquez un budget d'au moins 1M FCFA.",
            text(MandateEditRules.errors(FORM.copy(priceAmount = "1 000"), SALE_SEARCH, FORMATTER)["priceAmount"]),
        )
        assertEquals(
            emptyMap(),
            MandateEditRules.errors(
                FORM.copy(priceAmount = "15 000 000", furnitureType = "UNFURNISHED"),
                SALE_LISTING,
                FORMATTER,
            ),
        )
    }

    @Test
    fun `a sale asks its furniture and no rental term`() {
        val form = FORM.copy(priceAmount = "15 000 000", furnitureType = null)

        assertEquals("Indiquez si le bien est meublé.", MandateEditRules.errors(form, SALE_SEARCH, FORMATTER)["furnitureType"])
        assertEquals("Indiquez si le bien est meublé.", MandateEditRules.errors(form, SALE_LISTING, FORMATTER)["furnitureType"])
        val furnished = form.copy(furnitureType = "UNFURNISHED")
        assertEquals(emptyMap(), MandateEditRules.errors(furnished, SALE_SEARCH, FORMATTER))
        assertEquals(emptyMap(), MandateEditRules.errors(furnished, SALE_LISTING, FORMATTER))

        assertEquals(
            "15M FCFA · Dans moins d'1 mois",
            text(MandateEditRules.summaries(form, SALE_SEARCH, listOf("Bonapriso"), FORMATTER)["budget"]),
        )
        assertFalse(MandateEditRules.summaries(form, SALE_SEARCH, listOf("Bonapriso"), FORMATTER)["budget"]!!.contains("/ mois"))
    }

    @Test
    fun `the moving legend words a purchase`() {
        assertEquals("Date d'emménagement souhaitée", MandateEditRules.movingLegend(MANDATE))
        assertEquals("Disponibilité du bien", MandateEditRules.movingLegend(MANDATE.copy(mandateType = MandateType.LISTING)))
        assertEquals("Quand souhaitez-vous acheter ?", MandateEditRules.movingLegend(SALE_SEARCH))
        assertEquals("Disponibilité du bien", MandateEditRules.movingLegend(SALE_LISTING))
    }

    @Test
    fun `a rental's edit rules are unchanged`() {
        assertEquals(
            "Indiquez le budget, en FCFA par mois.",
            MandateEditRules.errors(FORM.copy(priceAmount = ""), MANDATE, FORMATTER)["priceAmount"],
        )
        assertEquals(
            "Indiquez le loyer, en FCFA par mois.",
            MandateEditRules.errors(FORM.copy(priceAmount = ""), MANDATE.copy(mandateType = MandateType.LISTING), FORMATTER)["priceAmount"],
        )
        assertEquals(
            "Indiquez la date d'emménagement.",
            MandateEditRules.errors(FORM.copy(movingPeriod = null), MANDATE, FORMATTER)["movingPeriod"],
        )
        assertEquals(
            "150K FCFA / mois · Dans moins d'1 mois",
            text(MandateEditRules.summaries(FORM, MANDATE, listOf("Bonapriso"), FORMATTER)["budget"]),
        )
    }

    @Test
    fun `a building sale's edit page asks its two counts`() {
        assertEquals(emptyMap(), MandateEditRules.errors(BUILDING_FORM, BUILDING_SEARCH, FORMATTER))
        assertEquals(emptyMap(), MandateEditRules.errors(BUILDING_FORM.copy(rooms = null, parkings = null), BUILDING_LISTING, FORMATTER))

        assertEquals(
            "Indiquez le nombre d'étages.",
            MandateEditRules.errors(BUILDING_FORM.copy(levels = null), BUILDING_SEARCH, FORMATTER)["levels"],
        )
        assertEquals(
            "Indiquez le nombre d'unités.",
            MandateEditRules.errors(BUILDING_FORM.copy(units = ""), BUILDING_SEARCH, FORMATTER)["units"],
        )
        assertEquals(
            "Indiquez un nombre entier, de 1 à 30.",
            MandateEditRules.errors(BUILDING_FORM.copy(levels = "31"), BUILDING_SEARCH, FORMATTER)["levels"],
        )
        assertEquals(
            "Indiquez un nombre entier, de 1 à 30.",
            MandateEditRules.errors(BUILDING_FORM.copy(levels = "3,5"), BUILDING_SEARCH, FORMATTER)["levels"],
        )
        assertEquals(
            "Indiquez un nombre entier, de 1 à 500.",
            MandateEditRules.errors(BUILDING_FORM.copy(units = "501"), BUILDING_SEARCH, FORMATTER)["units"],
        )
    }

    @Test
    fun `a building sale's request sends its counts and drops the rest`() {
        val request = MandateEditRules.request(BUILDING_FORM, BUILDING_SEARCH)

        assertEquals(PropertyType.BUILDING, request.propertyType)
        assertEquals(3, request.levels)
        assertEquals(12, request.units)
        assertNull(request.rooms)
        assertNull(request.parkings)
        assertFalse(request.waterWell)
        assertFalse(request.securityGuard)
    }

    @Test
    fun `another type sends no count`() {
        val request = MandateEditRules.request(FORM.copy(levels = "3", units = "12"), MANDATE)

        assertNull(request.levels)
        assertNull(request.units)
    }

    @Test
    fun `a building sale's group summary words its counts`() {
        assertEquals(
            "Immeuble · 3 étages · 12 unités · 850 m²",
            text(MandateEditRules.summaries(BUILDING_FORM, BUILDING_SEARCH, listOf("Bonapriso"), FORMATTER)["property"]),
        )
        assertEquals(
            "Immeuble · 1 étage · 1 unité · 850 m²",
            text(
                MandateEditRules.summaries(
                    BUILDING_FORM.copy(levels = "1", units = "1"),
                    BUILDING_SEARCH,
                    listOf("Bonapriso"),
                    FORMATTER,
                )["property"],
            ),
        )
    }

    @Test
    fun `of - a building sale`() {
        val form = MandateEditForm.of(BUILDING_SEARCH)

        assertEquals("BUILDING", form.propertyType)
        assertEquals("3", form.levels)
        assertEquals("12", form.units)
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
    fun `request - the purchase financing travels, its documents are the page's`() {
        val sale = MANDATE.copy(
            listingType = ListingType.SALE,
            financingType = FinancingType.CASH,
            ownershipDocuments = listOf(TitleDocumentType.LAND_TITLE),
        )

        val request = MandateEditRules.request(FORM.copy(documents = listOf("SURVEY_PLAN")), sale)

        assertEquals(ListingType.SALE, request.listingType)
        assertEquals(FinancingType.CASH, request.financingType)
        // A search's page edits the documents it requires; its other list travels untouched.
        assertEquals(listOf(TitleDocumentType.SURVEY_PLAN), request.titleDocuments)
        assertEquals(listOf(TitleDocumentType.LAND_TITLE), request.ownershipDocuments)
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
        assertEquals(emptyMap(), MandateEditRules.errors(FORM, MANDATE, FORMATTER))
        assertEquals(emptyMap(), MandateEditRules.errors(FORM.copy(propertyType = "ANY"), MANDATE, FORMATTER))
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
            MandateEditRules.errors(form, MANDATE.copy(mandateType = MandateType.LISTING), FORMATTER),
        )
        assertEquals(
            "Choisissez un type de bien proposé.",
            MandateEditRules.errors(form.copy(propertyType = null), MANDATE.copy(mandateType = MandateType.LISTING), FORMATTER)["propertyType"],
        )
    }

    @Test
    fun `errors - landlord`() {
        val form = FORM.copy(furnitureType = "UNFURNISHED")
        val landlord = MANDATE.copy(mandateType = MandateType.LISTING)

        assertEquals(emptyMap(), MandateEditRules.errors(form, landlord, FORMATTER))
        assertEquals(
            "Indiquez le loyer, en FCFA par mois.",
            MandateEditRules.errors(form.copy(priceAmount = ""), landlord, FORMATTER)["priceAmount"],
        )
        assertEquals(
            "Indiquez quand le bien est disponible.",
            MandateEditRules.errors(form.copy(movingPeriod = null), landlord, FORMATTER)["movingPeriod"],
        )
    }

    @Test
    fun `errors - rooms required for a home only`() {
        assertEquals(
            "Indiquez le nombre de chambres.",
            MandateEditRules.errors(FORM.copy(rooms = null), MANDATE, FORMATTER)["rooms"],
        )
        assertEquals(
            "Indiquez le nombre de chambres.",
            MandateEditRules.errors(FORM.copy(propertyType = "ANY", rooms = null), MANDATE, FORMATTER)["rooms"],
        )
        assertEquals(emptyMap(), MandateEditRules.errors(FORM.copy(propertyType = "STUDIO", rooms = null), MANDATE, FORMATTER))
        val storedLand = MANDATE.copy(propertyCategory = PropertyCategory.RESIDENTIAL, propertyType = PropertyType.LAND)
        val landForm = FORM.copy(
            propertyCategory = PropertyCategory.RESIDENTIAL,
            propertyType = "LAND",
            rooms = null,
            furnitureType = null,
            parkings = null,
        )
        assertEquals(emptyMap(), MandateEditRules.errors(landForm, storedLand, FORMATTER))
        assertEquals(
            "Choisissez un type de bien proposé.",
            MandateEditRules.errors(landForm, MANDATE, FORMATTER)["propertyType"],
        )
    }

    @Test
    fun `errors - a sale land is accepted`() {
        val sale = MANDATE.copy(
            listingType = ListingType.SALE,
            propertyCategory = PropertyCategory.RESIDENTIAL,
            propertyType = PropertyType.LAND,
            rooms = null,
            furnitureType = null,
            parkings = null,
            price = Money(15_000_000, "XAF", PeriodicityType.NONE),
        )
        val form = FORM.copy(
            propertyCategory = PropertyCategory.RESIDENTIAL,
            propertyType = "LAND",
            rooms = null,
            furnitureType = null,
            parkings = null,
            area = "300",
            priceAmount = "15 000 000",
        )

        assertNull(MandateEditRules.errors(form, sale, FORMATTER)["propertyType"])
    }

    @Test
    fun `errors - a sale land saves under its commercial usage`() {
        // #293: the usage the agent picks is the one stored, both being valid for a sold plot of land
        val sale = MANDATE.copy(
            listingType = ListingType.SALE,
            propertyCategory = PropertyCategory.RESIDENTIAL,
            propertyType = PropertyType.LAND,
            rooms = null,
            furnitureType = null,
            parkings = null,
            price = Money(15_000_000, "XAF", PeriodicityType.NONE),
        )
        val form = FORM.copy(
            propertyCategory = PropertyCategory.COMMERCIAL,
            propertyType = "LAND",
            rooms = null,
            furnitureType = null,
            parkings = null,
            area = "300",
            priceAmount = "15 000 000",
        )

        assertEquals(emptyMap(), MandateEditRules.errors(form, sale, FORMATTER))
        val request = MandateEditRules.request(form, sale)
        assertEquals(PropertyCategory.COMMERCIAL, request.propertyCategory)
        assertEquals(PropertyType.LAND, request.propertyType)
    }

    @Test
    fun `errors - a stored rental land on a landlord's listing is accepted`() {
        val storedLand = MANDATE.copy(
            mandateType = MandateType.LISTING,
            propertyCategory = PropertyCategory.RESIDENTIAL,
            propertyType = PropertyType.LAND,
        )
        val form = FORM.copy(
            propertyCategory = PropertyCategory.RESIDENTIAL,
            propertyType = "LAND",
            rooms = null,
            furnitureType = null,
            parkings = null,
        )

        assertEquals(emptyMap(), MandateEditRules.errors(form, storedLand, FORMATTER))
        assertEquals(PropertyType.LAND, MandateEditRules.request(form, storedLand).propertyType)
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

        // A commercial type asks neither the furniture, the parking nor, under an unanswered usage, the surface.
        assertEquals(
            mapOf(
                "neighborhoodIds" to "Choisissez au plus 3 quartiers.",
                "propertyCategory" to "Indiquez si le bien est pour y habiter ou pour une activité.",
                "propertyType" to "Choisissez un type de bien proposé.",
                "priceAmount" to "Indiquez le budget, en FCFA par mois.",
                "movingPeriod" to "Indiquez la date d'emménagement.",
                "comments" to "Votre message ne doit pas dépasser 500 caractères.",
            ),
            MandateEditRules.errors(form, MANDATE, FORMATTER),
        )
    }

    @Test
    fun `errors - type of the other category`() {
        assertEquals(
            "Choisissez un type de bien proposé.",
            MandateEditRules.errors(FORM.copy(propertyCategory = PropertyCategory.COMMERCIAL), MANDATE, FORMATTER)["propertyType"],
        )
    }

    @Test
    fun `errors - amounts`() {
        assertEquals(
            "Indiquez un loyer d'au moins 5K\u00a0FCFA par mois.",
            MandateEditRules.errors(FORM.copy(priceAmount = "1 000"), MANDATE, FORMATTER)["priceAmount"],
        )
        // A commercial property is measured; a residential one is not asked its surface any more (#304).
        val office = MANDATE.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = PropertyType.OFFICE)
        val officeForm = FORM.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = "OFFICE", area = "2")
        assertEquals(
            "Indiquez la surface en m², ou laissez le champ vide.",
            MandateEditRules.errors(officeForm, office, FORMATTER)["area"],
        )
        assertEquals(emptyMap(), MandateEditRules.errors(officeForm.copy(area = "  "), office, FORMATTER))
        assertEquals(emptyMap(), MandateEditRules.errors(FORM.copy(area = "  "), MANDATE, FORMATTER))
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
                // A residential property is not asked its surface (#304): the mandate's stored one travels.
                area = null,
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
    fun `request - the follow-ups of a type the page doesn't ask stay as stored`() {
        val storedLand = MANDATE.copy(
            propertyCategory = PropertyCategory.RESIDENTIAL,
            propertyType = PropertyType.LAND,
            rooms = "3",
            furnitureType = FurnitureType.UNFURNISHED,
            parkings = "2",
        )
        val request = MandateEditRules.request(
            FORM.copy(
                propertyCategory = PropertyCategory.RESIDENTIAL,
                propertyType = "LAND",
                rooms = "1",
                furnitureType = "FULLY_FURNISHED",
                parkings = "1",
            ),
            storedLand,
        )

        assertEquals(PropertyType.LAND, request.propertyType)
        assertEquals("3", request.rooms)
        assertEquals(FurnitureType.UNFURNISHED, request.furnitureType)
        assertEquals("2", request.parkings)
    }

    @Test
    fun `a sale's edit page saves the documents on the mandate's own list`() {
        val listing = MANDATE.copy(
            mandateType = MandateType.LISTING,
            listingType = ListingType.SALE,
            propertyType = PropertyType.HOUSE,
            titleDocuments = listOf(TitleDocumentType.SURVEY_PLAN),
        )
        val request = MandateEditRules.request(
            FORM.copy(propertyType = "HOUSE", area = "120 000", documents = listOf("LAND_TITLE", "UNKNOWN")),
            listing,
        )
        assertEquals(listOf(TitleDocumentType.LAND_TITLE), request.ownershipDocuments)
        assertEquals(listing.titleDocuments, request.titleDocuments)

        val search = MANDATE.copy(
            mandateType = MandateType.SEARCH,
            listingType = ListingType.SALE,
            propertyType = PropertyType.HOUSE,
            ownershipDocuments = listOf(TitleDocumentType.CERTIFICATE_OF_OWNERSHIP),
        )
        val searchRequest = MandateEditRules.request(
            FORM.copy(propertyType = "HOUSE", documents = listOf("SURVEY_PLAN", "LAND_TITLE", "SURVEY_PLAN")),
            search,
        )
        assertEquals(listOf(TitleDocumentType.SURVEY_PLAN, TitleDocumentType.LAND_TITLE), searchRequest.titleDocuments)
        assertEquals(search.ownershipDocuments, searchRequest.ownershipDocuments)
    }

    @Test
    fun `a question the page doesn't ask keeps its stored answer`() {
        val rental = MANDATE.copy(listingType = ListingType.RENTAL, propertyType = PropertyType.APARTMENT, area = 150)
        assertEquals(150, MandateEditRules.request(FORM.copy(propertyType = "APARTMENT", area = ""), rental).area)
        val office = MANDATE.copy(
            listingType = ListingType.RENTAL,
            propertyCategory = PropertyCategory.COMMERCIAL,
            propertyType = PropertyType.OFFICE,
            furnitureType = FurnitureType.UNFURNISHED,
        )
        assertEquals(
            FurnitureType.UNFURNISHED,
            MandateEditRules.request(
                FORM.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = "OFFICE", furnitureType = null),
                office,
            ).furnitureType,
        )
    }

    @Test
    fun `a hidden question keeps its stored counts and its stored documents`() {
        val stored = MANDATE.copy(rooms = "3", parkings = "2", levels = 2, units = 4)
        val request = MandateEditRules.request(
            FORM.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = "OFFICE", rooms = "", parkings = ""),
            stored,
        )

        assertEquals("3", request.rooms)
        assertEquals("2", request.parkings)
        assertEquals(2, request.levels)
        assertEquals(4, request.units)

        val rental = MANDATE.copy(titleDocuments = listOf(TitleDocumentType.LAND_TITLE))
        val rentalRequest = MandateEditRules.request(FORM.copy(documents = listOf("SURVEY_PLAN")), rental)
        assertEquals(rental.titleDocuments, rentalRequest.titleDocuments)
    }

    @Test
    fun `a hidden amenities group keeps the mandate's stored answers`() {
        val building = BUILDING_SEARCH.copy(waterWell = true, securityGuard = true)

        val request = MandateEditRules.request(BUILDING_FORM.copy(amenities = emptyList()), building)

        assertTrue(request.waterWell)
        assertTrue(request.securityGuard)
    }

    @Test
    fun `a stored rental building keeps its type and its rental answers`() {
        val building = MANDATE.copy(
            listingType = ListingType.RENTAL,
            propertyCategory = PropertyCategory.COMMERCIAL,
            propertyType = PropertyType.BUILDING,
            area = 300,
            furnitureType = FurnitureType.UNFURNISHED,
        )
        val request = MandateEditRules.request(
            FORM.copy(
                propertyCategory = PropertyCategory.COMMERCIAL,
                propertyType = "BUILDING",
                area = "300",
                furnitureType = "UNFURNISHED",
            ),
            building,
        )
        assertEquals(PropertyType.BUILDING, request.propertyType)
        assertEquals(FurnitureType.UNFURNISHED, request.furnitureType)
        assertNull(request.levels)
    }

    @Test
    fun `a stored rental building is asked its surface and its furniture`() {
        val building = MANDATE.copy(
            listingType = ListingType.RENTAL,
            propertyCategory = PropertyCategory.COMMERCIAL,
            propertyType = PropertyType.BUILDING,
            area = 300,
            furnitureType = FurnitureType.UNFURNISHED,
        )
        val form = FORM.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = "BUILDING")

        assertTrue(MandateEditRules.showsArea(form, building))
        assertTrue(MandateEditRules.showsFurniture(form, building))
    }

    @Test
    fun `of - a sale's documents and a grouped surface`() {
        val sale = MANDATE.copy(
            mandateType = MandateType.LISTING,
            listingType = ListingType.SALE,
            area = 120_000,
            ownershipDocuments = listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN),
        )

        val form = MandateEditForm.of(sale)

        assertEquals(listOf("LAND_TITLE", "SURVEY_PLAN"), form.documents)
        assertEquals(MandateLabels.formatNumber(120_000), form.area)
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
