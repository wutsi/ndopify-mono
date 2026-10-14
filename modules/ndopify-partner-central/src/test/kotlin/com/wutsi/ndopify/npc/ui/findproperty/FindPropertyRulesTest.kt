package com.wutsi.ndopify.npc.ui.findproperty

import com.wutsi.koki.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FindPropertyRulesTest {
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
        assertEquals("Choisissez au moins un quartier.", FindPropertyRules.neighborhoodIds(emptyList()))
        assertEquals("Choisissez au plus 3 quartiers.", FindPropertyRules.neighborhoodIds(listOf(1L, 2L, 3L, 4L)))
    }

    @Test
    fun propertyTypeIsOptionalButMustFitCategory() {
        assertNull(FindPropertyRules.propertyType(PropertyCategory.RESIDENTIAL, null))
        assertNull(FindPropertyRules.propertyType(PropertyCategory.RESIDENTIAL, "ANY"))
        assertNull(FindPropertyRules.propertyType(PropertyCategory.RESIDENTIAL, "APARTMENT"))
        assertNull(FindPropertyRules.propertyType(PropertyCategory.COMMERCIAL, "LAND"))
        assertEquals(
            "Choisissez un type de bien proposé.",
            FindPropertyRules.propertyType(PropertyCategory.COMMERCIAL, "APARTMENT"),
        )
        assertEquals(
            "Choisissez un type de bien proposé.",
            FindPropertyRules.propertyType(PropertyCategory.RESIDENTIAL, "CASTLE"),
        )
        assertEquals(
            "Choisissez un type de bien proposé.",
            FindPropertyRules.propertyType(PropertyCategory.RESIDENTIAL, "UNKNOWN"),
        )
    }

    @Test
    fun followUps() {
        assertTrue(FindPropertyRules.needsRooms("APARTMENT"))
        assertFalse(FindPropertyRules.needsRooms("STUDIO"))
        assertFalse(FindPropertyRules.needsRooms(null))
        assertFalse(FindPropertyRules.needsFurniture("LAND"))
        assertTrue(FindPropertyRules.needsFurniture(null))
        assertTrue(FindPropertyRules.needsFurniture("ANY"))
        assertFalse(FindPropertyRules.asksParkings("ROOM"))
        assertTrue(FindPropertyRules.asksParkings(null))
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
        assertNull(FindPropertyRules.area(null))
        assertNull(FindPropertyRules.area(5))
        assertNull(FindPropertyRules.area(1_000_000))
        assertEquals("Indiquez une surface d'au moins 5 m², ou laissez ce champ vide.", FindPropertyRules.area(4))
        assertEquals(
            "Indiquez une surface d'au moins 5 m², ou laissez ce champ vide.",
            FindPropertyRules.area(1_000_001),
        )
    }

    @Test
    fun priceAmount() {
        assertNull(FindPropertyRules.priceAmount(5000L))
        assertEquals("Indiquez votre budget mensuel.", FindPropertyRules.priceAmount(null))
        assertEquals("Indiquez un loyer d'au moins 5 000 FCFA par mois.", FindPropertyRules.priceAmount(4999L))
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
        assertNull(FindPropertyRules.movingPeriod(MovingPeriod.WITHIN_1_MONTH))
        assertEquals("Indiquez quand vous souhaitez emménager.", FindPropertyRules.movingPeriod(MovingPeriod.UNKNOWN))
        assertEquals("Indiquez quand vous souhaitez emménager.", FindPropertyRules.movingPeriod(null))
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
}
