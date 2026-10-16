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
        assertEquals("Locataire", MandateLabels.mandateType(MandateType.TENANT))
        assertEquals("Bailleur", MandateLabels.mandateType(MandateType.OWNER))
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
            "150\u00A0000 FCFA par mois",
            MandateLabels.price(Money(amount = 150000, currency = "XAF", periodicity = PeriodicityType.MONTHLY)),
        )
        assertEquals("2\u00A0500 CAD", MandateLabels.price(Money(amount = 2500, currency = "CAD")))
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
