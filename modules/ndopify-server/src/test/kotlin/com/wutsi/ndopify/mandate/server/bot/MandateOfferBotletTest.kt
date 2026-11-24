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
    private val botlet = MandateOfferBotlet(tenantService, locationService, client)

    private val tenant = TenantEntity(id = 1L, name = "Ndopify", timeZone = "Africa/Douala")
    private val agent = AgentEntity(id = 20L, party = PartyEntity(firstName = "Kim", lastName = "Possible"))

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 111L, name = "Bastos")).whenever(locationService).findByIdOrNull(111L)
    }

    @Test
    fun `a search offer carries its criteria, its deadline and the two buttons`() {
        val assignment = AssignmentEntity(
            id = 99L,
            mandate = MandateEntity(
                id = 1L, tenantId = 1L, cityId = 2370201L, neighborhoodIds = listOf(111L),
                listingType = ListingType.RENTAL, propertyType = PropertyType.APARTMENT, rooms = "2",
                price = 150000, currencyCode = "XAF", pricePeriodicityType = PeriodicityType.MONTHLY,
            ),
            agentId = 20L, status = AssignmentStatus.PENDING, expiresAt = Date(1_800_000_000_000L),
        )

        botlet.send(assignment, agent, 1000L)

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertEquals(
            """
            📋 Nouveau mandat : Location - Appartement (Bastos) — 150 000 XAF / mois
            Expire le 15/01/2027 à 09:00.
            """.trimIndent(),
            text.firstValue,
        )
        assertEquals(listOf("Accepter", "Refuser"), buttons.firstValue.map { it.text })
        assertEquals(listOf("accept:99", "decline:99"), buttons.firstValue.map { it.callbackData })
    }

    @Test
    fun `a land purchase offer carries its financing and its documents`() {
        val assignment = AssignmentEntity(
            id = 99L,
            mandate = MandateEntity(
                id = 5L, tenantId = 1L, cityId = 2370201L, neighborhoodIds = listOf(111L),
                listingType = ListingType.SALE, propertyType = PropertyType.LAND,
                price = 10_000_000, currencyCode = "XAF",
                financingType = FinancingType.CASH,
                titleDocuments = listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN),
            ),
            agentId = 20L, status = AssignmentStatus.PENDING, expiresAt = Date(1_800_000_000_000L),
        )

        botlet.send(assignment, agent, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertEquals(
            """
            📋 Nouveau mandat : Achat - Terrain (Bastos) — Budget : 10 000 000 XAF · Financement : Comptant · Documents : Titre foncier · Plan de bornage
            Expire le 15/01/2027 à 09:00.
            """.trimIndent(),
            text.firstValue,
        )
    }

    @Test
    fun `a sale search's message names its budget and never a rental`() {
        val assignment = AssignmentEntity(
            id = 96L,
            mandate = MandateEntity(
                id = 6L, tenantId = 1L, cityId = 2370201L, neighborhoodIds = listOf(111L),
                listingType = ListingType.SALE, propertyType = PropertyType.APARTMENT,
                price = 250_000_000, currencyCode = "XAF", mandateType = MandateType.SEARCH,
            ),
            agentId = 20L, status = AssignmentStatus.PENDING, expiresAt = Date(1_800_000_000_000L),
        )

        botlet.send(assignment, agent, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.startsWith("📋 Nouveau mandat : Achat - Appartement"))
        assertTrue(text.firstValue.contains(" — Budget : 250 000 000 XAF"))
        assertNoRentalWord(text.firstValue)
    }

    @Test
    fun `a sale listing's message names its price and never a rental`() {
        val assignment = AssignmentEntity(
            id = 97L,
            mandate = MandateEntity(
                id = 7L, tenantId = 1L, cityId = 2370201L, neighborhoodIds = listOf(111L),
                listingType = ListingType.SALE, propertyType = PropertyType.VILLA,
                price = 250_000_000, currencyCode = "XAF", mandateType = MandateType.LISTING,
            ),
            agentId = 20L, status = AssignmentStatus.PENDING, expiresAt = Date(1_800_000_000_000L),
        )

        botlet.send(assignment, agent, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.startsWith("📋 Nouveau mandat : Vente - Villa"))
        assertTrue(text.firstValue.contains(" — Prix : 250 000 000 XAF"))
        assertNoRentalWord(text.firstValue)
    }

    private fun assertNoRentalWord(text: String) {
        listOf("location", "loyer", "logement").forEach { word ->
            assertFalse(text.lowercase().contains(word), "no '$word' in a sale message: $text")
        }
    }

    @Test
    fun `a listing offer carries the price, the area and the availability`() {
        val assignment = AssignmentEntity(
            id = 99L,
            mandate = MandateEntity(
                id = 2L, tenantId = 1L, cityId = 2370201L, neighborhoodIds = listOf(111L),
                listingType = ListingType.SALE, propertyType = PropertyType.VILLA, rooms = "4",
                price = 25_000_000, currencyCode = "XAF",
                area = 250, availableFrom = LocalDate.of(2026, 12, 1), mandateType = MandateType.LISTING,
            ),
            agentId = 20L, status = AssignmentStatus.PENDING, expiresAt = Date(1_800_000_000_000L),
        )

        botlet.send(assignment, agent, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertEquals(
            """
            📋 Nouveau mandat : Vente - Villa (Bastos) — Prix : 25 000 000 XAF
            4 pièces · 250 m² · disponible le 01/12/2026
            Expire le 15/01/2027 à 09:00.
            """.trimIndent(),
            text.firstValue,
        )
    }

    @Test
    fun `a land sale listing offer carries its ownership documents`() {
        val assignment = AssignmentEntity(
            id = 99L,
            mandate = MandateEntity(
                id = 2L, tenantId = 1L, cityId = 2370201L, neighborhoodIds = listOf(111L),
                listingType = ListingType.SALE, propertyType = PropertyType.LAND,
                price = 45_000_000, currencyCode = "XAF", area = 800,
                availableFrom = LocalDate.of(2026, 12, 1), mandateType = MandateType.LISTING,
                ownershipDocuments = listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN),
            ),
            agentId = 20L, status = AssignmentStatus.PENDING, expiresAt = Date(1_800_000_000_000L),
        )

        botlet.send(assignment, agent, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertEquals(
            """
            📋 Nouveau mandat : Vente - Terrain (Bastos) — Prix : 45 000 000 XAF
            800 m² · disponible le 01/12/2026 · document de propriété : Titre foncier · Plan de bornage
            Expire le 15/01/2027 à 09:00.
            """.trimIndent(),
            text.firstValue,
        )
    }

    @Test
    fun `a villa sale has no ownership document line`() {
        val assignment = AssignmentEntity(
            id = 99L,
            mandate = MandateEntity(
                id = 2L, tenantId = 1L, cityId = 2370201L, neighborhoodIds = listOf(111L),
                listingType = ListingType.SALE, propertyType = PropertyType.VILLA,
                price = 25_000_000, currencyCode = "XAF", mandateType = MandateType.LISTING,
                ownershipDocuments = listOf(TitleDocumentType.LAND_TITLE),
            ),
            agentId = 20L, status = AssignmentStatus.PENDING, expiresAt = Date(1_800_000_000_000L),
        )

        botlet.send(assignment, agent, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertFalse(text.firstValue.contains("document de propriété"))
    }

    @Test
    fun `a 5+ rooms listing keeps its rooms in the details line`() {
        val assignment = AssignmentEntity(
            id = 99L,
            mandate = MandateEntity(
                id = 4L, tenantId = 1L, cityId = 2370201L, neighborhoodIds = listOf(111L),
                listingType = ListingType.SALE, propertyType = PropertyType.VILLA, rooms = "5+",
                price = 25_000_000, currencyCode = "XAF", mandateType = MandateType.LISTING,
            ),
            agentId = 20L, status = AssignmentStatus.PENDING, expiresAt = Date(1_800_000_000_000L),
        )

        botlet.send(assignment, agent, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertEquals(
            """
            📋 Nouveau mandat : Vente - Villa (Bastos) — Prix : 25 000 000 XAF
            5+ pièces
            Expire le 15/01/2027 à 09:00.
            """.trimIndent(),
            text.firstValue,
        )
    }

    @Test
    fun `a building sale offer words its units and its levels`() {
        val assignment = AssignmentEntity(
            id = 99L,
            mandate = MandateEntity(
                id = 8L, tenantId = 1L, cityId = 2370201L, neighborhoodIds = listOf(111L),
                listingType = ListingType.SALE, propertyType = PropertyType.BUILDING,
                levels = 3, units = 12, area = 850,
                price = 240_000_000, currencyCode = "XAF", mandateType = MandateType.LISTING,
                availableFrom = LocalDate.of(2026, 12, 1),
            ),
            agentId = 20L, status = AssignmentStatus.PENDING, expiresAt = Date(1_800_000_000_000L),
        )

        botlet.send(assignment, agent, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertEquals(
            """
            📋 Nouveau mandat : Vente - Immeuble (Bastos) — Prix : 240 000 000 XAF
            3 étages · 12 unités · 850 m² · disponible le 01/12/2026
            Expire le 15/01/2027 à 09:00.
            """.trimIndent(),
            text.firstValue,
        )
        assertFalse(text.firstValue.contains("pièce"))
    }

    @Test
    fun `a one level one unit building is singular`() {
        val assignment = AssignmentEntity(
            id = 99L,
            mandate = MandateEntity(
                id = 8L, tenantId = 1L, cityId = 2370201L, neighborhoodIds = listOf(111L),
                listingType = ListingType.SALE, propertyType = PropertyType.BUILDING,
                levels = 1, units = 1, area = 85,
                price = 240_000_000, currencyCode = "XAF", mandateType = MandateType.LISTING,
                availableFrom = LocalDate.of(2026, 12, 1),
            ),
            agentId = 20L, status = AssignmentStatus.PENDING, expiresAt = Date(1_800_000_000_000L),
        )

        botlet.send(assignment, agent, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.contains("1 étage · 1 unité · 85 m²"))
    }

    @Test
    fun `a mandate without neighbourhoods or a city has no dangling place`() {
        val assignment = AssignmentEntity(
            id = 98L,
            mandate = MandateEntity(
                id = 3L, tenantId = 1L, cityId = -1L, listingType = ListingType.RENTAL,
                propertyType = PropertyType.APARTMENT, price = 150000, currencyCode = "XAF",
                pricePeriodicityType = PeriodicityType.MONTHLY, mandateType = MandateType.SEARCH,
            ),
            agentId = 20L, status = AssignmentStatus.PENDING, expiresAt = Date(1_800_000_000_000L),
        )

        botlet.send(assignment, agent, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.startsWith("📋 Nouveau mandat : Location"))
        assertFalse(text.firstValue.lines().first().contains(" à "), "no dangling city on the property line")
        assertFalse(text.firstValue.contains("()"))
    }
}
