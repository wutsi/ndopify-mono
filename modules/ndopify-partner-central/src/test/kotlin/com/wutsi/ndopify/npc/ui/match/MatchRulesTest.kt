package com.wutsi.ndopify.npc.ui.match

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.service.TenantFixtures.FORMATTER
import com.wutsi.ndopify.npc.ui.mandate.MandateRules
import com.wutsi.ndopify.npc.ui.match.MatchFixtures.LISTING_A
import com.wutsi.ndopify.npc.ui.match.MatchFixtures.LISTING_B
import com.wutsi.ndopify.npc.ui.match.MatchFixtures.LISTING_C
import com.wutsi.ndopify.npc.ui.match.MatchFixtures.MANDATE
import com.wutsi.ndopify.npc.ui.match.MatchFixtures.MATCH_A
import com.wutsi.ndopify.npc.ui.match.MatchFixtures.MATCH_B
import com.wutsi.ndopify.npc.ui.match.MatchFixtures.MATCH_C
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchRulesTest {
    private val sentAt = date(2026, 10, 28)
    private val answeredAt = date(2026, 10, 29)

    @Test
    fun matched() {
        assertTrue(MatchRules.matched(MANDATE))
        assertFalse(MatchRules.matched(MANDATE.copy(mandateType = MandateType.LISTING)))
        assertFalse(MatchRules.matched(MANDATE.copy(status = MandateStatus.ASSIGNED)))
        assertFalse(MatchRules.matched(MANDATE.copy(status = MandateStatus.CLOSED)))
    }

    @Test
    fun `fit - listing A`() {
        val fit = MatchRules.fit(MATCH_A, MANDATE, LISTING_A)

        assertEquals(
            listOf(
                FitLine(true, "Dans le secteur recherché"),
                FitLine(true, "Dans le budget"),
                FitLine(true, "3 chambres, comme demandé"),
                FitLine(true, "Appartement, comme demandé"),
                FitLine(false, "2 équipements sur 3"),
                FitLine(true, "Disponible avant la date d'emménagement"),
                FitLine(true, "Non meublé, comme demandé"),
            ),
            fit,
        )
    }

    @Test
    fun `fit - listing B`() {
        val fit = MatchRules.fit(MATCH_B, MANDATE, LISTING_B).map { it.text }

        assertEquals(
            listOf(
                "Près du secteur recherché",
                "6\u00a0% au-dessus du budget",
                "4 chambres, une de plus que demandé",
                "Appartement, comme demandé",
                "Les 3 équipements demandés",
                "Disponible avant la date d'emménagement",
                "Non meublé, comme demandé",
            ),
            fit,
        )
    }

    @Test
    fun `fit - listing C`() {
        val fit = MatchRules.fit(MATCH_C, MANDATE, LISTING_C)

        assertEquals(FitLine(false, "2 chambres, une de moins que demandé"), fit[2])
        assertEquals(FitLine(true, "Appartement, comme demandé"), fit[3])
        assertEquals(FitLine(false, "1 équipement sur 3"), fit[4])
        assertEquals(FitLine(false, "Disponible moins d'un mois après la date d'emménagement"), fit[5])
    }

    @Test
    fun `fit - out of the sector, late, wrong rooms, furnished`() {
        val match = MATCH_A.copy(neighborhoodScore = 0.0, roomsScore = 0.0, availabilityScore = 0.0, furnitureScore = 0.0)
        val listing = LISTING_A.copy(rooms = 1, furnitureType = FurnitureType.FULLY_FURNISHED)

        val fit = MatchRules.fit(match, MANDATE, listing).map { it.text }

        assertEquals("Hors du secteur recherché", fit[0])
        assertEquals("1 chambre au lieu de 3", fit[2])
        assertEquals("Disponible plus d'un mois après la date d'emménagement", fit[5])
        assertEquals("Meublé : le client le veut non meublé", fit[6])
    }

    @Test
    fun `fit - wants furnished`() {
        val mandate = MANDATE.copy(furnitureType = FurnitureType.FULLY_FURNISHED)
        val match = MATCH_A.copy(furnitureScore = 0.0)

        assertEquals("Non meublé : le client le veut meublé", MatchRules.fit(match, mandate, LISTING_A).last().text)
        assertEquals(
            "Meublé, comme demandé",
            MatchRules.fit(MATCH_A, mandate, LISTING_A.copy(furnitureType = FurnitureType.FULLY_FURNISHED)).last().text,
        )
    }

    @Test
    fun `fit - studio without rooms`() {
        val listing = LISTING_A.copy(propertyType = PropertyType.STUDIO, rooms = null)
        val match = MATCH_A.copy(roomsScore = 0.0, typeScore = 0.0)

        val fit = MatchRules.fit(match, MANDATE, listing)

        assertEquals(FitLine(false, "Nombre de chambres non précisé"), fit[2])
    }

    @Test
    fun `fit - no rooms searched`() {
        val fit = MatchRules.fit(MATCH_A, MANDATE.copy(rooms = null), LISTING_A)

        assertEquals(FitLine(true, "3 chambres"), fit[2])
    }

    @Test
    fun `fit - no rooms searched nor listed`() {
        val fit = MatchRules.fit(MATCH_A, MANDATE.copy(rooms = null), LISTING_A.copy(rooms = null)).map { it.text }

        assertEquals(6, fit.size)
        assertFalse(fit.any { it.contains("chambre") })
    }

    @Test
    fun `fit - one amenity asked`() {
        val mandate = MANDATE.copy(generatorSet = false, securedFenced = false)

        assertEquals("L'équipement demandé", MatchRules.fit(MATCH_A.copy(amenitiesScore = 1.0), mandate, LISTING_A)[4].text)
    }

    @Test
    fun `fit - track on foot asked, not an amenity`() {
        val mandate = MANDATE.copy(securedFenced = false, trackInaccessibleByCar = true)
        val match = MATCH_A.copy(amenitiesScore = 0.5)

        assertEquals(FitLine(false, "1 équipement sur 2"), MatchRules.fit(match, mandate, LISTING_A)[4])
    }

    @Test
    fun `fit - only the track on foot asked`() {
        val mandate = MANDATE.copy(generatorSet = false, securedFenced = false, waterTank = false, trackInaccessibleByCar = true)

        val fit = MatchRules.fit(MATCH_A.copy(amenitiesScore = 1.0), mandate, LISTING_A).map { it.text }

        assertEquals(6, fit.size)
        assertFalse(fit.any { it.contains("équipement") })
    }

    @Test
    fun `fit - sparse search`() {
        val mandate = MANDATE.copy(
            generatorSet = false,
            securedFenced = false,
            waterTank = false,
            furnitureType = null,
            neighborhoodIds = emptyList(),
            price = MANDATE.price.copy(amount = 0),
        )

        val fit = MatchRules.fit(MATCH_A.copy(amenitiesScore = 1.0), mandate, LISTING_A).map { it.text }

        assertEquals(
            listOf(
                "Dans le secteur recherché",
                "Dans le budget",
                "3 chambres, comme demandé",
                "Appartement, comme demandé",
                "Disponible avant la date d'emménagement",
            ),
            fit,
        )
    }

    @Test
    fun `fit - unknown furniture searched`() {
        val fit = MatchRules.fit(MATCH_A, MANDATE.copy(furnitureType = FurnitureType.UNKNOWN), LISTING_A)

        assertEquals(6, fit.size)
    }

    @Test
    fun `fit - commercial sibling`() {
        val mandate = MANDATE.copy(
            propertyCategory = PropertyCategory.COMMERCIAL,
            propertyType = PropertyType.OFFICE,
            rooms = null,
        )
        val listing = LISTING_A.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = PropertyType.RETAIL)

        val fit = MatchRules.fit(MATCH_A.copy(typeScore = 0.9), mandate, listing).map { it.text }

        assertTrue("Boutique au lieu de bureau" in fit)
    }

    @Test
    fun `fit - housing sibling elides before a vowel`() {
        val listing = LISTING_A.copy(propertyType = PropertyType.HOUSE)

        val fit = MatchRules.fit(MATCH_A.copy(typeScore = 0.8), MANDATE, listing).map { it.text }

        assertTrue("Maison au lieu d'appartement" in fit)
    }

    @Test
    fun `fit - sibling of an entrepot elides`() {
        val mandate = MANDATE.copy(
            propertyCategory = PropertyCategory.COMMERCIAL,
            propertyType = PropertyType.INDUSTRIAL,
            rooms = null,
        )
        val listing = LISTING_A.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = PropertyType.OFFICE)

        val fit = MatchRules.fit(MATCH_A.copy(typeScore = 0.8), mandate, listing).map { it.text }

        assertTrue("Bureau au lieu d'entrepôt" in fit)
    }

    @Test
    fun `fit - no type in the payload`() {
        // A Match deserialized from an older server has typeScore = 1.0 by default
        val match = jacksonObjectMapper()
            .readValue("""{"mandateId":${MANDATE.id},"listingId":${LISTING_A.id}}""", Match::class.java)

        assertEquals(1.0, match.typeScore)
        assertTrue("Appartement, comme demandé" in MatchRules.fit(match, MANDATE, LISTING_A).map { it.text })
    }

    @Test
    fun tag() {
        assertEquals(MandateRules.Tag("Nouveau", "info"), MatchRules.tag(MATCH_A))
        assertEquals(MandateRules.Tag("En attente", "neutral"), MatchRules.tag(MATCH_A.copy(status = MatchStatus.PROPOSED)))
        assertEquals(MandateRules.Tag("Intéressé", "success"), MatchRules.tag(MATCH_A.copy(status = MatchStatus.ACCEPTED)))
        assertEquals(MandateRules.Tag("Expirée", "warning"), MatchRules.tag(MATCH_A.copy(status = MatchStatus.EXPIRED)))
    }

    @Test
    fun answer() {
        val proposed = MATCH_A.copy(status = MatchStatus.PROPOSED, proposedAt = sentAt)

        assertNull(MatchRules.answer(MATCH_A, FORMATTER))
        assertEquals("Envoyé le 28 oct. 2026", MatchRules.answer(proposed, FORMATTER))
        assertEquals(
            "Intéressé le 29 oct. 2026",
            MatchRules.answer(proposed.copy(status = MatchStatus.ACCEPTED, answeredAt = answeredAt), FORMATTER),
        )
        assertEquals("Envoyé le 28 oct. 2026", MatchRules.answer(proposed.copy(status = MatchStatus.ACCEPTED), FORMATTER))
        assertNull(MatchRules.answer(proposed.copy(status = MatchStatus.REJECTED, answeredAt = answeredAt), FORMATTER))
        assertEquals(
            "Envoyé le 28 oct. 2026, sans réponse",
            MatchRules.answer(proposed.copy(status = MatchStatus.EXPIRED), FORMATTER),
        )
    }

    @Test
    fun sort() {
        val proposed = MATCH_C.copy(id = 9004, status = MatchStatus.PROPOSED, score = 0.99)
        val rejected = MATCH_C.copy(id = 9005, status = MatchStatus.REJECTED, score = 0.99)
        val accepted = MATCH_C.copy(id = 9006, status = MatchStatus.ACCEPTED, score = 0.1)
        val dismissed = MATCH_C.copy(id = 9007, status = MatchStatus.DISMISSED)
        val expired = MATCH_C.copy(id = 9008, status = MatchStatus.EXPIRED, score = 0.99)

        val sorted = MatchRules.sort(listOf(rejected, expired, MATCH_B, proposed, dismissed, MATCH_A, accepted))

        // The refused one isn't shown; the expired ones last
        assertEquals(listOf(9006L, 9001L, 9003L, 9004L, 9008L), sorted.map { it.id })
    }

    @Test
    fun proposals() {
        val matches = listOf(
            MATCH_A,
            MATCH_A.copy(id = 1, status = MatchStatus.PROPOSED),
            MATCH_A.copy(id = 2, status = MatchStatus.ACCEPTED),
            MATCH_A.copy(id = 3, status = MatchStatus.REJECTED),
        )

        assertEquals("1 intéressé · 1 refusé · 1 en attente", MatchRules.proposals(matches))
        assertEquals(
            "2 intéressés · 2 refusés",
            MatchRules.proposals(
                listOf(MatchStatus.ACCEPTED, MatchStatus.ACCEPTED, MatchStatus.REJECTED, MatchStatus.REJECTED)
                    .map { MATCH_A.copy(status = it) },
            ),
        )
        assertEquals("1 en attente", MatchRules.proposals(listOf(MATCH_A.copy(status = MatchStatus.PROPOSED))))
        assertEquals(
            "1 refusé · 2 sans réponse",
            MatchRules.proposals(
                listOf(MatchStatus.EXPIRED, MatchStatus.REJECTED, MatchStatus.EXPIRED).map { MATCH_A.copy(status = it) },
            ),
        )
        assertNull(MatchRules.proposals(listOf(MATCH_A)))
    }

    @Test
    fun interest() {
        assertEquals("Aucune personne intéressée pour l'instant", MatchRules.interest(0))
        assertEquals("1 personne intéressée", MatchRules.interest(1))
        assertEquals("3 personnes intéressées", MatchRules.interest(3))
    }

    private fun date(year: Int, month: Int, day: Int): Date =
        Date.from(ZonedDateTime.of(year, month, day, 12, 0, 0, 0, ZoneId.of("Africa/Douala")).toInstant())
}
