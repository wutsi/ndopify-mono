package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.service.TenantFixtures.FORMATTER
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MandateLabelsTest {
    @Test
    fun mandateType() {
        assertEquals("Recherche", MandateLabels.mandateType(MandateType.SEARCH))
        assertEquals("Affichage", MandateLabels.mandateType(MandateType.LISTING))
        assertEquals("Mandat", MandateLabels.mandateType(MandateType.UNKNOWN))
    }

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
            "150\u00a0000\u00a0FCFA par mois",
            MandateLabels.price(
                Money(amount = 150000, currency = "XAF", periodicity = PeriodicityType.MONTHLY),
                FORMATTER
            ),
        )
        assertEquals("2\u00a0500,00\u00a0CAD", MandateLabels.price(Money(amount = 2500, currency = "CAD"), FORMATTER))
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
    fun party() {
        assertEquals("Bailleur", MandateLabels.party(MandateType.LISTING))
        assertEquals("Locataire", MandateLabels.party(MandateType.SEARCH))
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
            "Bailleur · Appartement",
            MandateLabels.title(Mandate(mandateType = MandateType.LISTING, propertyType = PropertyType.APARTMENT)),
        )
        assertEquals("Locataire", MandateLabels.title(Mandate(mandateType = MandateType.SEARCH)))
    }

    @Test
    fun rent() {
        assertEquals(
            "150\u00a0000\u00a0FCFA / mois",
            MandateLabels.rent(Money(150000, "XAF", PeriodicityType.MONTHLY), FORMATTER)
        )
        assertEquals(
            "152\u00a0500\u00a0FCFA / mois",
            MandateLabels.rent(Money(152500, "XAF", PeriodicityType.MONTHLY), FORMATTER)
        )
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
                "Emménagement" to "Dans moins d'1 mois",
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
                "Disponibilité" to "Dans plus de 3 mois",
                "Catégorie" to "Commercial",
                "Type de propriété" to "Terrain",
            ),
            MandateLabels.criteria(mandate, LOCATIONS, FORMATTER),
        )
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
        assertEquals("Disponibilité" to "Dans plus de 3 mois", MandateLabels.criteria(without, LOCATIONS, FORMATTER)[2])
    }

    @Test
    fun `criteria - client ignores the availability date`() {
        val mandate = Mandate(
            mandateType = MandateType.SEARCH,
            cityId = 1,
            availableFrom = LocalDate.of(2026, 11, 3),
            movingPeriod = MovingPeriod.AFTER_3_MONTHS
        )

        assertEquals("Emménagement" to "Dans plus de 3 mois", MandateLabels.criteria(mandate, LOCATIONS, FORMATTER)[2])
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
}
