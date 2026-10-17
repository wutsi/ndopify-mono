package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MandateLabelsTest {
    @Test
    fun mandateType() {
        assertEquals("Recherche de Location", MandateLabels.mandateType(MandateType.SEARCH_RENTAL))
        assertEquals("Location", MandateLabels.mandateType(MandateType.RENTAL))
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
            "150K FCFA par mois",
            MandateLabels.price(Money(amount = 150000, currency = "XAF", periodicity = PeriodicityType.MONTHLY)),
        )
        assertEquals("2.5K CAD", MandateLabels.price(Money(amount = 2500, currency = "CAD")))
    }

    @Test
    fun formatAmount() {
        assertEquals("500", MandateLabels.formatAmount(500))
        assertEquals("5K", MandateLabels.formatAmount(5_000))
        assertEquals("12.5K", MandateLabels.formatAmount(12_500))
        assertEquals("800K", MandateLabels.formatAmount(800_000))
        assertEquals("1M", MandateLabels.formatAmount(1_000_000))
        assertEquals("1.5M", MandateLabels.formatAmount(1_500_000))
        assertEquals("1.23M", MandateLabels.formatAmount(1_234_567))
    }

    @Test
    fun amenityGroups() {
        val mandate = Mandate(waterMeter = true, waterWell = true, securityGuard = true)

        assertEquals(
            listOf("Accès à l'eau" to "Compteur d'eau, Forage", "Sécurité" to "Gardien"),
            MandateLabels.amenityGroups(mandate),
        )
        assertEquals(emptyList(), MandateLabels.amenityGroups(Mandate()))
    }
}
