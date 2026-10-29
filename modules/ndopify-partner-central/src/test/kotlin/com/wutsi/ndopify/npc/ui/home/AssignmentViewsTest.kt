package com.wutsi.ndopify.npc.ui.home

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.AssignmentSummary
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.service.TenantFixtures.FORMATTER
import com.wutsi.ndopify.npc.ui.home.AssignmentViewsTest.Companion.MANDATE
import com.wutsi.ndopify.npc.ui.home.AssignmentViewsTest.Companion.NOW
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AKWA
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.BONAPRISO
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.DOUALA
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.LOCATIONS
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssignmentViewsTest {
    companion object {
        const val NOW = 1_800_000_000_000L
        private const val MINUTE = 60_000L

        /** An offer of [MANDATE] to the agent of ProfileFixtures, 52 min left at [NOW]. */
        val ASSIGNMENT = AssignmentSummary(
            id = 7,
            mandateId = 101,
            agentId = 20,
            status = AssignmentStatus.PENDING,
            expiresAt = Date(NOW + 52 * MINUTE),
        )

        val MANDATE = Mandate(
            id = 101,
            customerId = 555,
            mandateType = MandateType.SEARCH,
            propertyCategory = PropertyCategory.RESIDENTIAL,
            propertyType = PropertyType.APARTMENT,
            furnitureType = FurnitureType.UNFURNISHED,
            rooms = "2",
            parkings = "1",
            area = 80,
            cityId = DOUALA,
            neighborhoodIds = listOf(BONAPRISO, AKWA),
            waterMeter = true,
            waterWell = true,
            securityGuard = true,
            price = Money(amount = 150000, currency = "XAF", periodicity = PeriodicityType.MONTHLY),
            movingPeriod = MovingPeriod.WITHIN_1_MONTH,
            comments = "Appelez-moi au 677 12 34 56",
        )

        val NAMES = LOCATIONS.associateBy { it.id }
    }

    @Test
    fun card() {
        val view = AssignmentViews.of(ASSIGNMENT, MANDATE, NAMES, NOW, FORMATTER)

        assertEquals(7L, view.id)
        assertEquals("Recherche", view.title)
        assertEquals("Secteurs", view.sectorTerm)
        assertEquals("Bonapriso, Akwa", view.sector)
        assertEquals("Budget", view.priceTerm)
        assertEquals("150K\u00a0FCFA par mois", view.price)
        assertEquals("Emménagement", view.movingTerm)
        assertEquals("Dans moins d'1 mois", view.moving)
        assertEquals(NOW + 52 * MINUTE, view.expiresAt)
        assertEquals("52 min", view.remaining)
    }

    @Test
    fun details() {
        val view = AssignmentViews.of(ASSIGNMENT, MANDATE, NAMES, NOW, FORMATTER)

        assertEquals(
            listOf(
                "Catégorie" to "Résidentiel",
                "Type de propriété" to "Appartement",
                "Meublé" to "Non",
                "Chambres" to "2",
                "Parking" to "1",
                "Surface souhaitée" to "80 m²",
                "Accès à l'eau" to "Compteur d'eau, Forage",
                "Sécurité" to "Gardien de sécurité",
            ),
            view.details,
        )
    }

    @Test
    fun `owner - one neighbourhood`() {
        val view = AssignmentViews.of(
            ASSIGNMENT,
            MANDATE.copy(mandateType = MandateType.LISTING, neighborhoodIds = listOf(BONAPRISO)),
            NAMES,
            NOW,
            FORMATTER,
        )

        assertEquals("Affichage", view.title)
        assertEquals("Secteur", view.sectorTerm)
        assertEquals("Bonapriso", view.sector)
        assertEquals("Loyer", view.priceTerm)
        assertEquals("Disponibilité", view.movingTerm)
        assertEquals("80 m²", view.details.toMap()["Surface"])
    }

    @Test
    fun `no neighbourhood - the whole city`() {
        val view = AssignmentViews.of(ASSIGNMENT, MANDATE.copy(neighborhoodIds = emptyList()), NAMES, NOW, FORMATTER)

        assertEquals("Secteur", view.sectorTerm)
        assertEquals("Tous les quartiers", view.sector)
    }

    @Test
    fun `questions that don't apply to the type are left out`() {
        val view = AssignmentViews.of(
            ASSIGNMENT,
            MANDATE.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = PropertyType.LAND),
            NAMES,
            NOW,
            FORMATTER
        )

        val terms = view.details.map { it.first }
        assertFalse("Chambres" in terms)
        assertFalse("Parking" in terms)
        assertFalse("Meublé" in terms)
    }

    @Test
    fun `comments and customer are never shown`() {
        val view = AssignmentViews.of(ASSIGNMENT, MANDATE, NAMES, NOW, FORMATTER)

        assertFalse(view.toString().contains("677"))
        assertFalse(view.toString().contains("555"))
    }

    @Test
    fun `location names unavailable`() {
        val view = AssignmentViews.of(ASSIGNMENT, MANDATE, emptyMap(), NOW, FORMATTER)

        assertNull(view.sector)
    }

    @Test
    fun `expired offers are left out`() {
        assertTrue(AssignmentViews.isOpen(ASSIGNMENT, NOW))
        assertFalse(AssignmentViews.isOpen(ASSIGNMENT.copy(expiresAt = Date(NOW)), NOW))
        assertFalse(AssignmentViews.isOpen(ASSIGNMENT.copy(status = AssignmentStatus.ACCEPTED), NOW))
    }

    @Test
    fun remaining() {
        assertEquals("1 h 05 min", AssignmentViews.remaining(65 * MINUTE))
        assertEquals("52 min", AssignmentViews.remaining(52 * MINUTE + 30_000L))
        assertEquals("moins d'1 min", AssignmentViews.remaining(30_000L))
    }
}
