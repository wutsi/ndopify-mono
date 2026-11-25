package com.wutsi.ndopify.mandate.server.bot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.FinancingType
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.TitleDocumentType
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import java.time.LocalDate
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MandateOfferBotletTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val client = mock<TelegramClient>()
    private val labels = MandateTelegramLabels(locationService)
    private val botlet = MandateOfferBotlet(tenantService, labels, client)

    private val tenant = TenantEntity(id = 1L, name = "Ndopify", timeZone = "Africa/Douala")
    private val agent = AgentEntity(id = 20L, party = PartyEntity(firstName = "Kim", lastName = "Possible"))
    private val expiresAt = Date(1_800_000_000_000L)

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 111L, name = "Bastos")).whenever(locationService).findByIdOrNull(111L)
    }

    @Test
    fun `a search offer carries its criteria, its deadline and the two callbacks`() {
        val assignment = assignment(
            99L,
            MandateEntity(
                id = 1L, tenantId = 1L, cityId = 2370201L, neighborhoodIds = listOf(111L),
                listingType = ListingType.RENTAL, propertyType = PropertyType.APARTMENT, rooms = "2",
                price = 150000, currencyCode = "XAF", pricePeriodicityType = PeriodicityType.MONTHLY,
            ),
        )

        botlet.send(assignment, agent, 1000L)

        assertEquals(
            """
            📋 Nouveau mandat : Location - Appartement (Bastos) - 150K/mois
            Expire le 15/01/2027 à 09:00.
            """.trimIndent(),
            text(),
        )
        assertEquals(listOf(listOf("Accepter", "Refuser")), rows().map { row -> row.map { it.text } })
        assertEquals(listOf(listOf("accept:99", "decline:99")), rows().map { row -> row.map { it.callbackData } })
    }

    @Test
    fun `the offer's third button opens its card on Partner Central`() {
        doReturn(tenant.copy(partnerCentralUrl = "https://partner.ndopify.com/")).whenever(tenantService).findById(1L)
        val assignment = assignment(99L, MandateEntity(id = 1L, tenantId = 1L, listingType = ListingType.RENTAL))

        botlet.send(assignment, agent, 1000L)

        assertEquals(
            listOf(
                listOf("Accepter", "Refuser"),
                listOf("Voir détails"),
            ),
            rows().map { row -> row.map { it.text } },
        )
        assertEquals("https://partner.ndopify.com/#assignment-99", rows()[1][0].url)
    }

    @Test
    fun `a land purchase offer carries its financing and its documents`() {
        val assignment = assignment(
            99L,
            MandateEntity(
                id = 5L, tenantId = 1L, cityId = 2370201L, neighborhoodIds = listOf(111L),
                listingType = ListingType.SALE, propertyType = PropertyType.LAND,
                price = 10_000_000, currencyCode = "XAF",
                financingType = FinancingType.CASH,
                titleDocuments = listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN),
            ),
        )

        botlet.send(assignment, agent, 1000L)

        assertEquals(
            """
            📋 Nouveau mandat : Achat - Terrain (Bastos) - 10M · Financement : Comptant · Documents : Titre foncier · Plan de bornage
            Expire le 15/01/2027 à 09:00.
            """.trimIndent(),
            text(),
        )
    }

    @Test
    fun `a sale search's message names its budget and never a rental`() {
        val assignment = assignment(
            96L,
            MandateEntity(
                id = 6L, tenantId = 1L, cityId = 2370201L, neighborhoodIds = listOf(111L),
                listingType = ListingType.SALE, propertyType = PropertyType.APARTMENT,
                price = 250_000_000, currencyCode = "XAF", mandateType = MandateType.SEARCH,
            ),
        )

        botlet.send(assignment, agent, 1000L)

        val text = text()
        assertTrue(text.startsWith("📋 Nouveau mandat : Achat - Appartement"))
        assertTrue(text.contains(" - 250M"))
        assertNoRentalWord(text)
    }

    @Test
    fun `a sale listing's message names its price and never a rental`() {
        val assignment = assignment(
            97L,
            MandateEntity(
                id = 7L, tenantId = 1L, cityId = 2370201L, neighborhoodIds = listOf(111L),
                listingType = ListingType.SALE, propertyType = PropertyType.VILLA,
                price = 250_000_000, currencyCode = "XAF", mandateType = MandateType.LISTING,
            ),
        )

        botlet.send(assignment, agent, 1000L)

        val text = text()
        assertTrue(text.startsWith("📋 Nouveau mandat : Vente - Villa"))
        assertTrue(text.contains(" - 250M"))
        assertNoRentalWord(text)
    }

    private fun assertNoRentalWord(text: String) {
        listOf("location", "loyer", "logement").forEach { word ->
            assertFalse(text.lowercase().contains(word), "no '$word' in a sale message: $text")
        }
    }

    @Test
    fun `a listing offer is short - no details line and no ownership documents`() {
        val assignment = assignment(
            99L,
            MandateEntity(
                id = 2L, tenantId = 1L, cityId = 2370201L, neighborhoodIds = listOf(111L),
                listingType = ListingType.SALE, propertyType = PropertyType.VILLA, rooms = "4",
                price = 25_000_000, currencyCode = "XAF", area = 250,
                availableFrom = LocalDate.of(2026, 12, 1), mandateType = MandateType.LISTING,
                ownershipDocuments = listOf(TitleDocumentType.LAND_TITLE),
            ),
        )

        botlet.send(assignment, agent, 1000L)

        assertEquals(
            """
            📋 Nouveau mandat : Vente - Villa (Bastos) - 25M
            Expire le 15/01/2027 à 09:00.
            """.trimIndent(),
            text(),
        )
        val text = text()
        listOf("pièces", "m²", "disponible le", "document de propriété").forEach { detail ->
            assertFalse(text.contains(detail), "no '$detail' in the short offer: $text")
        }
    }

    @Test
    fun `a building sale offer is short too`() {
        val assignment = assignment(
            99L,
            MandateEntity(
                id = 8L, tenantId = 1L, cityId = 2370201L, neighborhoodIds = listOf(111L),
                listingType = ListingType.SALE, propertyType = PropertyType.BUILDING,
                levels = 3, units = 12, area = 850,
                price = 240_000_000, currencyCode = "XAF", mandateType = MandateType.LISTING,
                availableFrom = LocalDate.of(2026, 12, 1),
            ),
        )

        botlet.send(assignment, agent, 1000L)

        assertEquals(
            """
            📋 Nouveau mandat : Vente - Immeuble (Bastos) - 240M
            Expire le 15/01/2027 à 09:00.
            """.trimIndent(),
            text(),
        )
        assertFalse(text().contains("étages"))
    }

    @Test
    fun `a mandate without neighbourhoods or a city has no dangling place`() {
        val assignment = assignment(
            98L,
            MandateEntity(
                id = 3L, tenantId = 1L, cityId = -1L, listingType = ListingType.RENTAL,
                propertyType = PropertyType.APARTMENT, price = 150000, currencyCode = "XAF",
                pricePeriodicityType = PeriodicityType.MONTHLY, mandateType = MandateType.SEARCH,
            ),
        )

        botlet.send(assignment, agent, 1000L)

        assertTrue(text().startsWith("📋 Nouveau mandat : Location - Appartement - 150K/mois"))
        assertFalse(text().contains("()"))
    }

    private fun text(): String {
        val text = argumentCaptor<String>()
        verify(client).sendMessageRows(eq(1000L), text.capture(), any())
        return text.firstValue
    }

    private fun rows(): List<List<TelegramButton>> {
        val rows = argumentCaptor<List<List<TelegramButton>>>()
        verify(client).sendMessageRows(eq(1000L), any(), rows.capture())
        return rows.firstValue
    }

    private fun assignment(id: Long, mandate: MandateEntity) = AssignmentEntity(
        id = id,
        mandate = mandate,
        agentId = 20L,
        status = AssignmentStatus.PENDING,
        expiresAt = expiresAt,
    )
}
