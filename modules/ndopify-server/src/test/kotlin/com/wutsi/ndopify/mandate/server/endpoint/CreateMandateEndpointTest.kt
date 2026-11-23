package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.customer.server.dao.CustomerRepository
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateResponse
import com.wutsi.ndopify.mandate.dto.FinancingType
import com.wutsi.ndopify.mandate.dto.GetMandateResponse
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.SearchAssignmentResponse
import com.wutsi.ndopify.mandate.dto.TitleDocumentType
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.party.server.service.PartyService
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/CreateMandateEndpoint.sql"])
class CreateMandateEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Autowired
    private lateinit var dao: MandateRepository

    @Autowired
    private lateinit var customerDao: CustomerRepository

    @Autowired
    private lateinit var partyService: PartyService

    @Autowired
    private lateinit var ds: DataSource

    private val request = CreateMandateRequest(
        propertyCategory = PropertyCategory.RESIDENTIAL,
        mandateType = MandateType.LISTING,
        listingType = ListingType.RENTAL,
        cityId = 2370201L,
        neighbourhoodIds = listOf(111L, 222L),
        propertyType = PropertyType.APARTMENT,
        furnitureType = FurnitureType.FULLY_FURNISHED,
        rooms = "2",
        parkings = "1",
        area = 75,
        waterMeter = true,
        dedicatedWaterMeter = true,
        waterTank = true,
        waterWell = true,
        electricityMeter = true,
        dedicatedElectricityMeter = false,
        generatorSet = true,
        solarPanel = true,
        roadPaved = true,
        trackAccessibleByCar = false,
        trackInaccessibleByCar = false,
        securedFenced = true,
        securityGuard = true,
        videoSurveillance = true,
        price = Money(amount = 150000, "XAF", PeriodicityType.MONTHLY),
        securityDeposit = 2,
        advanceRent = 3,
        leaseTerm = 12,
        movingPeriod = MovingPeriod.WITHIN_1_MONTH,
        availableFrom = LocalDate.of(2026, 11, 3),
        noticePeriod = 1,
        firstName = "Ray",
        lastName = "Sponsible",
        email = "Ray.Sponsible@gmail.com",
        whatsappNumber = "+237671234567",
        preferredContactPeriod = ContactPeriodType.MORNING,
        comments = "Close to the market",
    )

    @Test
    fun `security deposit over 24 months - 400`() {
        val response = rest.postForEntity(
            "/v1/mandates",
            request.copy(securityDeposit = 25),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertEquals("securityDeposit", response.body?.error?.parameter?.name)
    }

    @Test
    fun `advance rent over 24 months - 400`() {
        val response = rest.postForEntity(
            "/v1/mandates",
            request.copy(advanceRent = 25),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertEquals("advanceRent", response.body?.error?.parameter?.name)
    }

    @Test
    fun create() {
        val response = rest.postForEntity("/v1/mandates", request, CreateMandateResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val mandate = dao.findById(response.body!!.mandateId).get()
        assertEquals(TENANT_ID, mandate.tenantId)
        assertEquals(MandateStatus.PENDING_ASSIGNMENT, mandate.status)
        assertNull(mandate.agentId)
        assertEquals(request.propertyCategory, mandate.propertyCategory)
        assertEquals(request.mandateType, mandate.mandateType)
        assertEquals(request.listingType, mandate.listingType)
        assertEquals(request.cityId, mandate.cityId)
        assertNeighborhoods(mandate.id!!, request.neighbourhoodIds)
        assertEquals(request.propertyType, mandate.propertyType)
        assertEquals(request.furnitureType, mandate.furnitureType)
        assertEquals(request.rooms, mandate.rooms)
        assertEquals(request.parkings, mandate.parkings)
        assertEquals(request.area, mandate.area)
        assertEquals(request.waterMeter, mandate.waterMeter)
        assertEquals(request.dedicatedWaterMeter, mandate.dedicatedWaterMeter)
        assertEquals(request.waterTank, mandate.waterTank)
        assertEquals(request.waterWell, mandate.waterWell)
        assertEquals(request.electricityMeter, mandate.electricityMeter)
        assertEquals(request.dedicatedElectricityMeter, mandate.dedicatedElectricityMeter)
        assertEquals(request.generatorSet, mandate.generatorSet)
        assertEquals(request.solarPanel, mandate.solarPanel)
        assertEquals(request.roadPaved, mandate.roadPaved)
        assertEquals(request.trackAccessibleByCar, mandate.trackAccessibleByCar)
        assertEquals(request.trackInaccessibleByCar, mandate.trackInaccessibleByCar)
        assertEquals(request.securedFenced, mandate.securedFenced)
        assertEquals(request.securityGuard, mandate.securityGuard)
        assertEquals(request.videoSurveillance, mandate.videoSurveillance)
        assertEquals(request.price.amount, mandate.price)
        assertEquals(request.price.currency, mandate.currencyCode)
        assertEquals(request.price.periodicity, mandate.pricePeriodicityType)
        assertEquals(request.securityDeposit, mandate.securityDeposit)
        assertEquals(request.advanceRent, mandate.advanceRent)
        assertEquals(request.leaseTerm, mandate.leaseTerm)
        assertEquals(request.noticePeriod, mandate.noticePeriod)
        assertEquals(request.movingPeriod, mandate.movingPeriod)
        assertEquals(request.availableFrom, mandate.availableFrom)
        assertEquals(request.preferredContactPeriod, mandate.preferredContactPeriod)
        assertEquals(request.comments, mandate.comments)

        val party = partyService.findByEmailOrNull(request.email)!!
        assertEquals(TENANT_ID, party.tenantId)
        assertEquals(request.firstName, party.firstName)
        assertEquals(request.lastName, party.lastName)
        assertEquals(request.email.lowercase(), party.email)

        val customer = customerDao.findById(mandate.customerId).get()
        assertEquals(TENANT_ID, customer.tenantId)
        assertEquals(party.id, customer.party.id)
    }

    @Test
    @Sql(
        scripts = ["/db/test/clean.sql", "/db/test/mandate/CreateMandateEndpoint.sql"],
        statements = [
            "INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, kyc_status) VALUES " +
                "(200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible', 'VERIFIED')",
            "INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES (20, 1, 200, 2370201)",
        ],
    )
    fun `the new mandate is offered to an agent`() {
        val response = rest.postForEntity("/v1/mandates", request, CreateMandateResponse::class.java)
        awaitAsyncTasks() // Dispatched after commit

        val mandateId = response.body!!.mandateId
        val assignments =
            rest.getForEntity("/v1/assignments?mandate-id=$mandateId", SearchAssignmentResponse::class.java)
                .body!!.assignments
        assertEquals(20L, assignments.single().agentId)
        assertEquals(AssignmentStatus.PENDING, assignments.single().status)
    }

    @Test
    fun `create for existing party`() {
        val response = rest.postForEntity(
            "/v1/mandates",
            request.copy(email = "007@gmail.com", whatsappNumber = "+237650000001"),
            CreateMandateResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val mandate = dao.findById(response.body!!.mandateId).get()
        val customer = customerDao.findById(mandate.customerId).get()
        assertEquals(100L, customer.party.id)
    }

    @Test
    fun `create for existing customer`() {
        val response = rest.postForEntity(
            "/v1/mandates",
            request.copy(email = "omam.mbiyick@gmail.com", whatsappNumber = "+237650000044"),
            CreateMandateResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val mandate = dao.findById(response.body!!.mandateId).get()
        assertEquals(101L, mandate.customerId)
    }

    @Test
    fun `no first name`() {
        val response = rest.postForEntity("/v1/mandates", request.copy(firstName = ""), ErrorResponse::class.java)
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `no last name`() {
        val response = rest.postForEntity("/v1/mandates", request.copy(lastName = ""), ErrorResponse::class.java)
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `no email`() {
        val response = rest.postForEntity("/v1/mandates", request.copy(email = ""), ErrorResponse::class.java)
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `no mobile money number`() {
        val response = rest.postForEntity(
            "/v1/mandates",
            request.copy(whatsappNumber = ""),
            ErrorResponse::class.java,
        )
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    // What the public listing page posts: the contact, the moving period and the origin listing, no criterion
    private val visitor = CreateMandateRequest(
        propertyCategory = PropertyCategory.COMMERCIAL, // tampered
        mandateType = MandateType.SEARCH,
        listingType = ListingType.SALE, // tampered
        cityId = 2220957L, // tampered
        neighbourhoodIds = listOf(222L), // tampered
        rooms = "9", // tampered
        price = Money(amount = 1, currency = "EUR", periodicity = PeriodicityType.YEARLY), // tampered
        movingPeriod = MovingPeriod.WITHIN_3_MONTHS,
        firstName = "Awa",
        lastName = "Ndiaye",
        email = "awa.ndiaye@gmail.com",
        whatsappNumber = "+237650000002",
        originListingId = 500L,
    )

    @Test
    fun `a mandate created from a listing takes the listing's criteria`() {
        val response = rest.postForEntity("/v1/mandates", visitor, CreateMandateResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val mandate = dao.findById(response.body!!.mandateId).get()
        assertEquals(PropertyCategory.RESIDENTIAL, mandate.propertyCategory)
        assertEquals(MandateType.SEARCH, mandate.mandateType)
        assertEquals(ListingType.RENTAL, mandate.listingType)
        assertEquals(2370201L, mandate.cityId)
        assertNeighborhoods(mandate.id!!, listOf(111L))
        assertEquals("2", mandate.rooms)
        assertEquals("1", mandate.parkings)
        assertEquals(75, mandate.area)
        assertTrue(mandate.waterTank)
        assertEquals(175000L, mandate.price)
        assertEquals("XAF", mandate.currencyCode)
        assertEquals(PeriodicityType.MONTHLY, mandate.pricePeriodicityType)
        assertEquals(MovingPeriod.WITHIN_3_MONTHS, mandate.movingPeriod) // the visitor's
        assertEquals(500L, mandate.originListingId)
    }

    @Test
    fun `a sold listing seeds the criteria without origin`() {
        val response = rest.postForEntity(
            "/v1/mandates",
            visitor.copy(originListingId = 501L),
            CreateMandateResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val mandate = dao.findById(response.body!!.mandateId).get()
        assertNull(mandate.originListingId)
        assertEquals(2370201L, mandate.cityId)
        assertEquals(175000L, mandate.price)
    }

    @Test
    fun `an unknown listing is 404`() {
        val response = rest.postForEntity(
            "/v1/mandates",
            visitor.copy(originListingId = 999L),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `a second request for the same search creates no second mandate`() {
        val first = rest.postForEntity("/v1/mandates", visitor, CreateMandateResponse::class.java)
        val second = rest.postForEntity("/v1/mandates", visitor, CreateMandateResponse::class.java)
        awaitAsyncTasks()

        assertEquals(HttpStatus.OK, second.statusCode)
        assertEquals(first.body!!.mandateId, second.body!!.mandateId) // the same search, not a duplicate
        assertEquals(1, countSearches(visitor.email))
    }

    @Test
    fun `an existing search of the same city and type gets the listing as a match`() {
        val response = rest.postForEntity(
            "/v1/mandates",
            visitor.copy(email = "existing.search@gmail.com"),
            CreateMandateResponse::class.java,
        )
        awaitAsyncTasks()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(402L, response.body!!.mandateId) // the search it already has
        assertEquals(1, countSearches("existing.search@gmail.com"))
        assertEquals(1, countMatches(402L, 500L))
        assertEquals("ACCEPTED", matchStatus(402L, 500L))

        // Its agent is told to contact the customer, and the customer gets its booking link
        assertTrue(smtp.waitForIncomingEmail(5000, 2), "emails not received")
        val recipients = smtp.receivedMessages.map { it.allRecipients.single().toString() }
        assertTrue(recipients.contains("Listing Agent <listing.agent@gmail.com>"), recipients.toString())
        assertTrue(recipients.any { it.contains("existing.search@gmail.com") }, recipients.toString())

        val agentMessage = smtp.receivedMessages.single { message ->
            message.allRecipients.single().toString().contains("listing.agent@gmail.com")
        }
        assertTrue(agentMessage.content.toString().contains("Awa Ndiaye"))
        assertTrue(agentMessage.content.toString().contains("Contactez-le pour organiser la visite"))
    }

    // The price per m² is never stored: only the budget and the surface travel.

    @Test
    fun `create a sale search with its financing and land documents`() {
        val sale = request.copy(
            listingType = ListingType.SALE,
            propertyCategory = PropertyCategory.COMMERCIAL,
            propertyType = PropertyType.LAND,
            rooms = null,
            furnitureType = null,
            parkings = null,
            area = 400,
            price = Money(amount = 10_000_000, currency = "XAF", periodicity = PeriodicityType.NONE),
            financingType = FinancingType.CASH,
            titleDocuments = listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN),
        )

        val created = rest.postForEntity("/v1/mandates", sale, CreateMandateResponse::class.java)

        assertEquals(HttpStatus.OK, created.statusCode)
        val mandate = rest.getForEntity("/v1/mandates/${created.body!!.mandateId}", GetMandateResponse::class.java)
            .body!!.mandate
        assertEquals(ListingType.SALE, mandate.listingType)
        assertEquals(FinancingType.CASH, mandate.financingType)
        assertEquals(listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN), mandate.titleDocuments)
        assertEquals(PeriodicityType.NONE, mandate.price.periodicity)
        assertNull(mandate.rooms)
        assertNull(mandate.furnitureType)
    }

    @Test
    fun `drop the financing of a rental`() {
        val created = rest.postForEntity(
            "/v1/mandates",
            request.copy(listingType = ListingType.RENTAL, financingType = FinancingType.CASH),
            CreateMandateResponse::class.java,
        )

        assertEquals(HttpStatus.OK, created.statusCode)
        val mandate = rest.getForEntity("/v1/mandates/${created.body!!.mandateId}", GetMandateResponse::class.java)
            .body!!.mandate
        assertNull(mandate.financingType)
    }

    @Test
    fun `drop the documents of a non-land sale`() {
        val created = rest.postForEntity(
            "/v1/mandates",
            request.copy(
                listingType = ListingType.SALE,
                propertyCategory = PropertyCategory.RESIDENTIAL,
                propertyType = PropertyType.VILLA,
                titleDocuments = listOf(TitleDocumentType.LAND_TITLE),
            ),
            CreateMandateResponse::class.java,
        )

        assertEquals(HttpStatus.OK, created.statusCode)
        val mandate = rest.getForEntity("/v1/mandates/${created.body!!.mandateId}", GetMandateResponse::class.java)
            .body!!.mandate
        assertEquals(emptyList(), mandate.titleDocuments)
    }

    @Test
    fun `keep a land sale with no document`() {
        val created = rest.postForEntity(
            "/v1/mandates",
            request.copy(
                listingType = ListingType.SALE,
                propertyCategory = PropertyCategory.COMMERCIAL,
                propertyType = PropertyType.LAND,
                titleDocuments = emptyList(),
            ),
            CreateMandateResponse::class.java,
        )

        assertEquals(HttpStatus.OK, created.statusCode)
        val mandate = rest.getForEntity("/v1/mandates/${created.body!!.mandateId}", GetMandateResponse::class.java)
            .body!!.mandate
        assertEquals(emptyList(), mandate.titleDocuments)
    }

    @Test
    fun `create a land sale with its ownership documents`() {
        val sale = request.copy(
            listingType = ListingType.SALE,
            propertyCategory = PropertyCategory.RESIDENTIAL,
            propertyType = PropertyType.LAND,
            rooms = null,
            furnitureType = null,
            parkings = null,
            area = 800,
            price = Money(amount = 45_000_000, currency = "XAF", periodicity = PeriodicityType.NONE),
            ownershipDocuments = listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN),
        )

        val created = rest.postForEntity("/v1/mandates", sale, CreateMandateResponse::class.java)

        assertEquals(HttpStatus.OK, created.statusCode)
        val mandate = rest.getForEntity("/v1/mandates/${created.body!!.mandateId}", GetMandateResponse::class.java)
            .body!!.mandate
        assertEquals(ListingType.SALE, mandate.listingType)
        assertEquals(
            listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN),
            mandate.ownershipDocuments,
        )
        assertEquals(PeriodicityType.NONE, mandate.price.periodicity)
    }

    @Test
    fun `drop the ownership documents of a non-land sale`() {
        val created = rest.postForEntity(
            "/v1/mandates",
            request.copy(
                listingType = ListingType.SALE,
                propertyCategory = PropertyCategory.RESIDENTIAL,
                propertyType = PropertyType.VILLA,
                ownershipDocuments = listOf(TitleDocumentType.LAND_TITLE),
            ),
            CreateMandateResponse::class.java,
        )

        assertEquals(HttpStatus.OK, created.statusCode)
        val mandate = rest.getForEntity("/v1/mandates/${created.body!!.mandateId}", GetMandateResponse::class.java)
            .body!!.mandate
        assertEquals(emptyList(), mandate.ownershipDocuments)
    }

    @Test
    fun `drop the ownership documents of a rental`() {
        val created = rest.postForEntity(
            "/v1/mandates",
            request.copy(
                listingType = ListingType.RENTAL,
                propertyType = PropertyType.LAND,
                ownershipDocuments = listOf(TitleDocumentType.LAND_TITLE),
            ),
            CreateMandateResponse::class.java,
        )

        assertEquals(HttpStatus.OK, created.statusCode)
        val mandate = rest.getForEntity("/v1/mandates/${created.body!!.mandateId}", GetMandateResponse::class.java)
            .body!!.mandate
        assertEquals(emptyList(), mandate.ownershipDocuments)
    }

    @Test
    fun `keep a land sale with no ownership document`() {
        val created = rest.postForEntity(
            "/v1/mandates",
            request.copy(
                listingType = ListingType.SALE,
                propertyCategory = PropertyCategory.RESIDENTIAL,
                propertyType = PropertyType.LAND,
                ownershipDocuments = emptyList(),
            ),
            CreateMandateResponse::class.java,
        )

        assertEquals(HttpStatus.OK, created.statusCode)
        val mandate = rest.getForEntity("/v1/mandates/${created.body!!.mandateId}", GetMandateResponse::class.java)
            .body!!.mandate
        assertEquals(emptyList(), mandate.ownershipDocuments)
    }

    private fun countSearches(email: String): Int = ds.connection.use { conn ->
        conn.prepareStatement(
            "SELECT COUNT(*) FROM T_MANDATE m JOIN T_CUSTOMER c ON c.id = m.customer_id " +
                "JOIN T_PARTY p ON p.id = c.party_id WHERE p.email = ? AND m.mandate_type = 'SEARCH'",
        ).use { stmt ->
            stmt.setString(1, email)
            stmt.executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
    }

    private fun countMatches(mandateId: Long, listingId: Long): Int = ds.connection.use { conn ->
        conn.prepareStatement("SELECT COUNT(*) FROM T_MATCH WHERE mandate_id = ? AND listing_id = ?").use { stmt ->
            stmt.setLong(1, mandateId)
            stmt.setLong(2, listingId)
            stmt.executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
    }

    private fun matchStatus(mandateId: Long, listingId: Long): String? = ds.connection.use { conn ->
        conn.prepareStatement("SELECT status FROM T_MATCH WHERE mandate_id = ? AND listing_id = ?").use { stmt ->
            stmt.setLong(1, mandateId)
            stmt.setLong(2, listingId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getString(1) else null
            }
        }
    }

    private fun assertNeighborhoods(mandateId: Long, expectedNeighborhoodIds: List<Long>) {
        val actualNeighborhoodIds = ds.connection.use { conn ->
            conn.prepareStatement("SELECT neighborhood_id FROM T_MANDATE_NEIGHBORHOOD WHERE mandate_id = ?")
                .use { stmt ->
                    stmt.setLong(1, mandateId)
                    stmt.executeQuery().use { rs ->
                        val ids = mutableListOf<Long>()
                        while (rs.next()) {
                            ids.add(rs.getLong("neighborhood_id"))
                        }
                        ids
                    }
                }
        }
        assertEquals(expectedNeighborhoodIds.sorted(), actualNeighborhoodIds.sorted())
    }
}
