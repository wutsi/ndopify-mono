package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.FinancingType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.TitleDocumentType
import com.wutsi.ndopify.npc.service.TenantFixtures.FORMATTER
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MandateLabelsTest {
    @Test
    fun category() {
        assertEquals("Résidentiel", MandateLabels.category(PropertyCategory.RESIDENTIAL))
        assertEquals("Commercial", MandateLabels.category(PropertyCategory.COMMERCIAL))
        assertNull(MandateLabels.category(PropertyCategory.UNKNOWN))
    }

    @Test
    fun propertyType() {
        assertEquals("Appartement", MandateLabels.propertyType(PropertyType.APARTMENT))
        assertEquals("Tout type de bien", MandateLabels.propertyType(null))
        assertEquals("Tout type de bien", MandateLabels.propertyType(PropertyType.UNKNOWN))
    }

    @Test
    fun furniture() {
        assertEquals("Oui", MandateLabels.furniture(FurnitureType.FULLY_FURNISHED))
        assertEquals("Non", MandateLabels.furniture(FurnitureType.UNFURNISHED))
        assertEquals("Indifférent", MandateLabels.furniture(null))
    }

    @Test
    fun price() {
        assertEquals(
            "150\u00a0000\u00a0FCFA / mois",
            MandateLabels.price(
                Money(amount = 150000, currency = "XAF", periodicity = PeriodicityType.MONTHLY),
                FORMATTER
            ),
        )
        assertEquals("2\u00a0500,00\u00a0CAD", MandateLabels.price(Money(amount = 2500, currency = "CAD"), FORMATTER))
        assertEquals(
            "152,5K\u00a0FCFA / mois",
            MandateLabels.price(Money(152500, "XAF", PeriodicityType.MONTHLY), FORMATTER, compact = true),
        )
        assertEquals("2,5K\u00a0CAD", MandateLabels.price(Money(2500, "CAD"), FORMATTER, compact = true))
    }

    @Test
    fun amenityGroups() {
        val mandate = Mandate(waterMeter = true, waterWell = true, securityGuard = true)

        assertEquals(
            listOf("Accès à l'eau" to "Compteur d'eau, Forage", "Sécurité" to "Gardien de sécurité"),
            MandateLabels.amenityGroups(mandate),
        )
        assertEquals(emptyList(), MandateLabels.amenityGroups(Mandate()))
    }

    @Test
    fun `project words a purchase`() {
        assertEquals("Location, usage résidentiel", MandateLabels.project(PropertyCategory.RESIDENTIAL, null))
        assertEquals("Location, usage commercial", MandateLabels.project(PropertyCategory.COMMERCIAL, ListingType.RENTAL))
        assertEquals("Achat, usage résidentiel", MandateLabels.project(PropertyCategory.RESIDENTIAL, ListingType.SALE))
        assertEquals("Achat, usage commercial", MandateLabels.project(PropertyCategory.COMMERCIAL, ListingType.SALE))
        assertNull(MandateLabels.project(null, ListingType.SALE))
    }

    @Test
    fun `the owner project words a sale`() {
        assertEquals("Location, usage résidentiel", MandateLabels.ownerProject(PropertyCategory.RESIDENTIAL, ListingType.RENTAL))
        assertEquals("Vente, usage résidentiel", MandateLabels.ownerProject(PropertyCategory.RESIDENTIAL, ListingType.SALE))
        assertEquals("Vente, usage commercial", MandateLabels.ownerProject(PropertyCategory.COMMERCIAL, ListingType.SALE))
        assertNull(MandateLabels.ownerProject(null, ListingType.SALE))
    }

    @Test
    fun `move in term words a purchase`() {
        assertEquals("Date d'emménagement", MandateLabels.moveInTerm(null, PropertyCategory.RESIDENTIAL))
        assertEquals("Date d'installation", MandateLabels.moveInTerm(ListingType.RENTAL, PropertyCategory.COMMERCIAL))
        assertEquals("Achat prévu", MandateLabels.moveInTerm(ListingType.SALE, PropertyCategory.RESIDENTIAL))
        assertEquals("Achat prévu", MandateLabels.moveInTerm(ListingType.SALE, PropertyCategory.COMMERCIAL))
    }

    @Test
    fun `ownership documents are named or none declared`() {
        assertEquals(
            "Titre foncier · Plan de bornage",
            MandateLabels.ownershipDocuments(listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN)),
        )
        assertEquals("Aucun document déclaré", MandateLabels.ownershipDocuments(emptyList()))
        assertEquals(
            "Certificat de propriété",
            MandateLabels.ownershipDocuments(listOf(TitleDocumentType.CERTIFICATE_OF_OWNERSHIP)),
        )
    }

    @Test
    fun `title documents are named or none`() {
        assertEquals("Titre foncier · Plan de bornage", MandateLabels.titleDocuments(
            listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN),
        ))
        assertEquals("Aucun document exigé", MandateLabels.titleDocuments(emptyList()))
        assertEquals(
            "Certificat de propriété",
            MandateLabels.titleDocuments(listOf(TitleDocumentType.CERTIFICATE_OF_OWNERSHIP)),
        )
    }

    @Test
    fun party() {
        assertEquals("Propriétaire", MandateLabels.party(MandateType.LISTING))
        assertEquals("Locataire", MandateLabels.party(MandateType.SEARCH))
    }

    @Test
    fun `a sale's parties are the seller and the buyer`() {
        assertEquals("Vendeur", MandateLabels.party(MandateType.LISTING, ListingType.SALE))
        assertEquals("Acheteur", MandateLabels.party(MandateType.SEARCH, ListingType.SALE))
        assertEquals("Propriétaire", MandateLabels.party(MandateType.LISTING, ListingType.RENTAL))
        assertEquals("Locataire", MandateLabels.party(MandateType.SEARCH, ListingType.RENTAL))

        assertEquals(
            "Vendeur · Terrain",
            MandateLabels.title(
                Mandate(mandateType = MandateType.LISTING, listingType = ListingType.SALE, propertyType = PropertyType.LAND),
            ),
        )
        assertEquals(
            "Acheteur · Maison",
            MandateLabels.title(
                Mandate(mandateType = MandateType.SEARCH, listingType = ListingType.SALE, propertyType = PropertyType.HOUSE),
            ),
        )
    }

    @Test
    fun `the amount and date terms follow the listing type`() {
        assertEquals("Loyer", MandateLabels.amountTerm(owner = true, listingType = ListingType.RENTAL))
        assertEquals("Prix", MandateLabels.amountTerm(owner = true, listingType = ListingType.SALE))
        assertEquals("Budget", MandateLabels.amountTerm(owner = false, listingType = ListingType.RENTAL))
        assertEquals("Budget", MandateLabels.amountTerm(owner = false, listingType = ListingType.SALE))

        assertEquals("Loyer", MandateLabels.listingPriceTerm(ListingType.RENTAL))
        assertEquals("Prix", MandateLabels.listingPriceTerm(ListingType.SALE))
        assertEquals("Loyer", MandateLabels.listingPriceTerm(null))

        assertEquals("Disponibilité", MandateLabels.dateTerm(owner = true, listingType = ListingType.SALE))
        assertEquals("Disponibilité", MandateLabels.dateTerm(owner = true, listingType = ListingType.RENTAL))
        assertEquals("Achat prévu", MandateLabels.dateTerm(owner = false, listingType = ListingType.SALE))
        assertEquals("Emménagement", MandateLabels.dateTerm(owner = false, listingType = ListingType.RENTAL))
    }

    @Test
    fun `a sale's criteria read its price and its purchase date`() {
        val listing = Mandate(
            mandateType = MandateType.LISTING,
            listingType = ListingType.SALE,
            propertyType = PropertyType.LAND,
            cityId = 1,
            price = Money(45_000_000, "XAF", PeriodicityType.NONE),
            availableFrom = LocalDate.of(2026, 12, 1),
        )
        val search = Mandate(
            mandateType = MandateType.SEARCH,
            listingType = ListingType.SALE,
            propertyType = PropertyType.HOUSE,
            cityId = 1,
            price = Money(45_000_000, "XAF", PeriodicityType.NONE),
            movingPeriod = MovingPeriod.WITHIN_3_MONTHS,
        )

        assertEquals(
            "Prix" to "45\u00a0000\u00a0000\u00a0FCFA",
            MandateLabels.criteria(listing, LOCATIONS, FORMATTER).first { it.first == "Prix" },
        )
        assertEquals(
            "Disponibilité" to "Dès le 01 déc. 2026",
            MandateLabels.criteria(listing, LOCATIONS, FORMATTER).first { it.first == "Disponibilité" },
        )
        assertEquals(
            "Budget" to "45\u00a0000\u00a0000\u00a0FCFA",
            MandateLabels.criteria(search, LOCATIONS, FORMATTER).first { it.first == "Budget" },
        )
        assertEquals(
            "Achat prévu" to "Dans 1 à 3\u00a0mois",
            MandateLabels.criteria(search, LOCATIONS, FORMATTER).first { it.first == "Achat prévu" },
        )
    }

    @Test
    fun shortPropertyType() {
        assertEquals("Boutique", MandateLabels.shortPropertyType(PropertyType.RETAIL))
        assertEquals("Entrepôt", MandateLabels.shortPropertyType(PropertyType.INDUSTRIAL))
        assertEquals("Maison", MandateLabels.shortPropertyType(PropertyType.HOUSE))
        assertNull(MandateLabels.shortPropertyType(null))
    }

    @Test
    fun title() {
        assertEquals(
            "Propriétaire · Appartement",
            MandateLabels.title(Mandate(mandateType = MandateType.LISTING, propertyType = PropertyType.APARTMENT)),
        )
        assertEquals("Locataire", MandateLabels.title(Mandate(mandateType = MandateType.SEARCH)))
    }

    @Test
    fun criteria() {
        val mandate = Mandate(
            mandateType = MandateType.SEARCH,
            propertyCategory = PropertyCategory.RESIDENTIAL,
            propertyType = PropertyType.APARTMENT,
            cityId = 1,
            neighborhoodIds = listOf(11, 12),
            rooms = "3",
            parkings = "0",
            area = 80,
            waterWell = true,
            price = Money(150000, "XAF", PeriodicityType.MONTHLY),
            movingPeriod = MovingPeriod.WITHIN_1_MONTH,
            comments = "Près d'une école",
        )

        assertEquals(
            listOf(
                "Secteurs" to "Kotto, Logpom — Douala",
                "Budget" to "150\u00a0000\u00a0FCFA / mois",
                "Emménagement" to "Dans moins d'1\u00a0mois",
                "Catégorie" to "Résidentiel",
                "Type de propriété" to "Appartement",
                "Meublé" to "Indifférent",
                "Chambres" to "3",
                "Parking" to "Aucune",
                "Surface souhaitée" to "80 m²",
                "Accès à l'eau" to "Forage",
                "Autres précisions" to "Près d'une école",
            ),
            MandateLabels.criteria(mandate, LOCATIONS, FORMATTER),
        )
    }

    @Test
    fun `criteria - landlord, whole city, follow-ups that don't apply`() {
        val mandate = Mandate(
            mandateType = MandateType.LISTING,
            propertyCategory = PropertyCategory.COMMERCIAL,
            propertyType = PropertyType.LAND,
            cityId = 1,
            rooms = "3",
            parkings = "1",
            price = Money(50000, "XAF", PeriodicityType.MONTHLY),
            movingPeriod = MovingPeriod.AFTER_3_MONTHS,
        )

        assertEquals(
            listOf(
                "Secteur" to "Toute la ville — Douala",
                "Loyer" to "50\u00a0000\u00a0FCFA / mois",
                "Disponibilité" to "Dans plus de 3\u00a0mois",
                "Catégorie" to "Commercial",
                "Type de propriété" to "Terrain",
            ),
            MandateLabels.criteria(mandate, LOCATIONS, FORMATTER),
        )
    }

    @Test
    fun `criteria - a land purchase has no furniture row and shows its financing and documents`() {
        val mandate = Mandate(
            mandateType = MandateType.SEARCH,
            listingType = ListingType.SALE,
            propertyCategory = PropertyCategory.COMMERCIAL,
            propertyType = PropertyType.LAND,
            cityId = 1,
            area = 400,
            furnitureType = FurnitureType.FULLY_FURNISHED, // left over from an earlier answer
            price = Money(10_000_000, "XAF", PeriodicityType.NONE),
            movingPeriod = MovingPeriod.WITHIN_3_MONTHS,
            financingType = FinancingType.CASH,
            titleDocuments = listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN),
        )

        val rows = MandateLabels.criteria(mandate, LOCATIONS, FORMATTER)

        assertEquals("10\u00a0000\u00a0000\u00a0FCFA", rows.single { it.first == "Budget" }.second)
        assertEquals(emptyList(), rows.filter { it.first == "Meublé" })
        assertEquals("Comptant", rows.single { it.first == "Financement" }.second)
        assertEquals(
            "Titre foncier · Plan de bornage",
            rows.single { it.first == "Documents exigés" }.second,
        )
    }

    @Test
    fun `criteria - a land purchase without documents says so`() {
        val mandate = Mandate(
            mandateType = MandateType.SEARCH,
            listingType = ListingType.SALE,
            propertyCategory = PropertyCategory.COMMERCIAL,
            propertyType = PropertyType.LAND,
            cityId = 1,
            price = Money(10_000_000, "XAF", PeriodicityType.NONE),
            financingType = FinancingType.UNDECIDED,
        )

        val rows = MandateLabels.criteria(mandate, LOCATIONS, FORMATTER)

        assertEquals("Pas encore décidé", rows.single { it.first == "Financement" }.second)
        assertEquals("Aucun document exigé", rows.single { it.first == "Documents exigés" }.second)
    }

    @Test
    fun `criteria - a rental is unchanged`() {
        val mandate = Mandate(
            mandateType = MandateType.SEARCH,
            listingType = ListingType.RENTAL,
            propertyCategory = PropertyCategory.RESIDENTIAL,
            propertyType = PropertyType.APARTMENT,
            cityId = 1,
            rooms = "3",
            price = Money(150000, "XAF", PeriodicityType.MONTHLY),
            movingPeriod = MovingPeriod.WITHIN_1_MONTH,
            financingType = FinancingType.CASH, // never kept for a rental
        )

        val rows = MandateLabels.criteria(mandate, LOCATIONS, FORMATTER)

        assertEquals("150\u00a0000\u00a0FCFA / mois", rows.single { it.first == "Budget" }.second)
        assertEquals("Indifférent", rows.single { it.first == "Meublé" }.second)
        assertEquals(emptyList(), rows.filter { it.first == "Financement" })
        assertEquals(emptyList(), rows.filter { it.first == "Documents exigés" })
    }

    @Test
    fun `criteria - landlord, availability date`() {
        val with = Mandate(
            mandateType = MandateType.LISTING,
            cityId = 1,
            availableFrom = LocalDate.of(2026, 11, 3),
            movingPeriod = MovingPeriod.AFTER_3_MONTHS
        )
        val without = with.copy(availableFrom = null)

        assertEquals("Disponibilité" to "Dès le 03 nov. 2026", MandateLabels.criteria(with, LOCATIONS, FORMATTER)[2])
        assertEquals("Disponibilité" to "Dans plus de 3\u00a0mois", MandateLabels.criteria(without, LOCATIONS, FORMATTER)[2])
    }

    @Test
    fun `criteria - client ignores the availability date`() {
        val mandate = Mandate(
            mandateType = MandateType.SEARCH,
            cityId = 1,
            availableFrom = LocalDate.of(2026, 11, 3),
            movingPeriod = MovingPeriod.AFTER_3_MONTHS
        )

        assertEquals("Emménagement" to "Dans plus de 3\u00a0mois", MandateLabels.criteria(mandate, LOCATIONS, FORMATTER)[2])
    }

    @Test
    fun `criteria - client, any type`() {
        val rows = MandateLabels.criteria(Mandate(mandateType = MandateType.SEARCH, cityId = 1), LOCATIONS, FORMATTER)

        assertEquals("Type de propriété" to "Indifférent", rows[4])
        assertEquals("Parking" to "Indifférent", rows[6])
    }

    companion object {
        private val LOCATIONS = mapOf(
            1L to Location(id = 1, name = "Douala"),
            11L to Location(id = 11, name = "Kotto"),
            12L to Location(id = 12, name = "Logpom"),
        )
    }

    @Test
    fun `a sale criteria has no furniture row and shows the documents`() {
        val mandate = Mandate(
            mandateType = MandateType.LISTING,
            listingType = ListingType.SALE,
            propertyCategory = PropertyCategory.RESIDENTIAL,
            propertyType = PropertyType.LAND,
            cityId = 1,
            area = 800,
            furnitureType = FurnitureType.FULLY_FURNISHED, // left over from an earlier answer
            price = Money(45_000_000, "XAF", PeriodicityType.NONE),
            availableFrom = LocalDate.of(2026, 12, 1),
            ownershipDocuments = listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN),
        )

        val rows = MandateLabels.criteria(mandate, LOCATIONS, FORMATTER)

        assertTrue(rows.none { it.first == "Meublé" })
        assertEquals("Titre foncier · Plan de bornage", rows.first { it.first == "Document de propriété" }.second)
    }

    @Test
    fun `a rental criteria is unchanged`() {
        val mandate = Mandate(
            mandateType = MandateType.LISTING,
            listingType = ListingType.RENTAL,
            propertyCategory = PropertyCategory.RESIDENTIAL,
            propertyType = PropertyType.APARTMENT,
            cityId = 1,
            neighborhoodIds = listOf(11, 12),
            furnitureType = FurnitureType.UNFURNISHED,
            rooms = "3",
            parkings = "0",
            area = 80,
            price = Money(150_000, "XAF", PeriodicityType.MONTHLY),
            availableFrom = LocalDate.of(2026, 12, 1),
        )

        val rows = MandateLabels.criteria(mandate, LOCATIONS, FORMATTER)

        assertTrue(rows.any { it.first == "Meublé" })
        assertTrue(rows.none { it.first == "Document de propriété" })
        assertEquals("150\u00a0000\u00a0FCFA / mois", rows.first { it.first == "Loyer" }.second)
    }
}
