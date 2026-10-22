package com.wutsi.ndopify.mandate.server.service

import com.github.mustachejava.DefaultMustacheFactory
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import dev.jcputney.mjml.MjmlRenderer
import java.time.Instant
import java.time.LocalDate
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MandateContractGeneratorTest {
    private val tenantService = mock<TenantService>()
    private val customerService = mock<CustomerService>()
    private val agentService = mock<AgentService>()
    private val locationService = mock<LocationService>()
    private val generator = MandateContractGenerator(
        tenantService,
        customerService,
        agentService,
        locationService,
        MailBodyResolver(MjmlRenderer.create(), DefaultMustacheFactory(), listOf(MailFilterNull())),
    )

    private val mandate = MandateEntity(
        id = 1L,
        tenantId = 1L,
        customerId = 10L,
        agentId = 20L,
        mandateType = MandateType.SEARCH,
        propertyCategory = PropertyCategory.RESIDENTIAL,
        propertyType = PropertyType.APARTMENT,
        furnitureType = FurnitureType.UNFURNISHED,
        rooms = "2",
        parkings = "1",
        area = 75,
        cityId = 2370201L,
        neighborhoodIds = listOf(111L, 222L),
        dedicatedWaterMeter = true,
        waterWell = true,
        generatorSet = true,
        roadPaved = true,
        securityGuard = true,
        videoSurveillance = true,
        price = 150000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
        securityDeposit = 2,
        advanceRent = 3,
        leaseTerm = 12,
        noticePeriod = 1,
        movingPeriod = MovingPeriod.WITHIN_3_MONTHS,
        preferredContactPeriod = ContactPeriodType.MORNING,
        comments = "Proche du marché",
    )

    // 23:30 UTC: already the next day in Douala (UTC+1)
    private val contract = ContractEntity(
        id = "c-1",
        number = "MNDT-2026-000001",
        createdAt = Date.from(Instant.parse("2026-10-16T23:30:00Z")),
        durationMonths = 6,
    )

    @BeforeTest
    fun setUp() {
        doReturn(TenantEntity(id = 1L, name = "Ndopify", logoUrl = "https://cdn.ndopify.com/logo.png"))
            .whenever(tenantService).findById(1L)
        doReturn(
            CustomerEntity(
                id = 10L,
                party = PartyEntity(firstName = "Ray", lastName = "Sponsible", email = "ray.sponsible@gmail.com"),
                whatsappNumber = "+237671234567",
            )
        ).whenever(customerService).findById(10L)
        doReturn(AgentEntity(id = 20L, party = PartyEntity(firstName = "Kim", lastName = "Possible")))
            .whenever(agentService).findById(20L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaoundé")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 111L, name = "Bastos")).whenever(locationService).findByIdOrNull(111L)
        doReturn(LocationEntity(id = 222L, name = "Mvan")).whenever(locationService).findByIdOrNull(222L)
    }

    @Test
    fun `search mandate`() {
        val html = generator.generate(mandate, contract)

        listOf(
            "MANDAT DE RECHERCHE IMMOBILIÈRE (LOCATION)",
            "MNDT-2026-000001",
            "Date : 17/10/2026",
            "Ray Sponsible",
            "+237671234567",
            "ray.sponsible@gmail.com",
            "représenté par Kim Possible",
            "Résidentiel",
            "Appartement",
            "<b>Meublé</b> : Non",
            "2 chambres",
            "1 place de parking",
            "75 m²",
            "Yaoundé",
            "Bastos, Mvan",
            "Compteur d&#39;eau individuel, Forage", // Escaped by mustache
            "Groupe électrogène",
            "Route bitumée",
            "Gardien, Vidéosurveillance",
            "150 000 XAF / mois",
            "<b>Caution</b> : 2 mois",
            "<b>Avance sur loyer</b> : 3 mois",
            "<b>Durée du bail</b> : 12 mois",
            "<b>Préavis</b> : 1 mois",
            "Dans 1 à 3 mois",
            "Le matin",
            "Proche du marché",
            "17/10/2026", // contract_date
            "6 mois, à compter de la date de sa",
        ).forEach { text -> assertTrue(html.contains(text), "missing: $text") }
        assertFalse(html.contains("Panneaux solaires")) // Not asked for
    }

    @Test
    fun `optional criteria left out`() {
        doReturn(CustomerEntity(id = 10L, party = PartyEntity(firstName = "Ray", lastName = "Sponsible")))
            .whenever(customerService).findById(10L)
        val bare = MandateEntity(
            tenantId = 1L,
            customerId = 10L,
            mandateType = MandateType.SEARCH,
            cityId = 2370201L,
            price = 100000,
            currencyCode = "XAF",
            pricePeriodicityType = PeriodicityType.MONTHLY,
        )

        val html = generator.generate(bare, contract)

        assertTrue(html.contains("Tout type de bien"))
        assertTrue(html.contains("100 000 XAF / mois"))
        listOf(
            "Tél :",
            "Email :",
            "représenté par",
            "Catégorie",
            "<b>Meublé</b>",
            "Chambres",
            "Surface",
            "Quartiers ciblés",
            "Les équipements souhaités",
            "Caution",
            "Emménagement souhaité",
            "Informations complémentaires",
        ).forEach { text -> assertFalse(html.contains(text), "unexpected: $text") }
    }

    @Test
    fun `customer text is escaped`() {
        val html = generator.generate(mandate.copy(comments = "<script>alert(1)</script>"), contract)

        assertFalse(html.contains("<script>"))
    }

    @Test
    fun `listing mandate`() {
        val listing = mandate.copy(
            mandateType = MandateType.LISTING,
            neighborhoodIds = listOf(111L),
            leaseTerm = null,
            noticePeriod = null,
            availableFrom = LocalDate.parse("2026-11-01"),
            comments = null,
        )

        val html = generator.generate(listing, contract)

        listOf(
            "MANDAT EXCLUSIF DE MISE EN LOCATION",
            "MNDT-2026-000001",
            "Date : 17/10/2026",
            "<b>Le Mandant (Propriétaire)</b> : Ray Sponsible",
            "+237671234567",
            "représenté par Kim Possible",
            "rechercher un locataire",
            "Appartement",
            "2 chambres",
            "75 m²",
            "<b>Quartier</b> : Bastos",
            "Les équipements du bien",
            "Groupe électrogène",
            "<b>Loyer demandé</b> : 150 000 XAF / mois",
            "<b>Caution</b> : 2 mois",
            "<b>Avance sur loyer</b> : 3 mois",
            "<b>Disponible à partir du</b> : 01/11/2026",
            "Le matin",
            "6 mois, à compter de la date de sa",
            "EXCLUSIVITÉ",
            "1 mois du loyer stipulé dans le bail",
            "VISITE D'ÉVALUATION ET PUBLICATION",
            "sur le site de Ndopify et ses supports de diffusion",
        ).forEach { text -> assertTrue(html.contains(text), "missing: $text") }
        listOf(
            "RECHERCHE IMMOBILIÈRE",
            "Loyer maximum",
            "Durée du bail",
            "Préavis",
            "Emménagement",
            "Commentaires",
            "Frais de visites",
        ).forEach { text -> assertFalse(html.contains(text), "unexpected: $text") }
    }

    @Test
    fun `no template for the mandate type`() {
        assertFailsWith<IllegalArgumentException> {
            generator.generate(mandate.copy(mandateType = MandateType.UNKNOWN), contract)
        }
    }
}
