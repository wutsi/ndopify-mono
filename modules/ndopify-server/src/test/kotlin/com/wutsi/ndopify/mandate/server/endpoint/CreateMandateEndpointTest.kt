package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.customer.server.dao.CustomerRepository
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
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.SearchAssignmentResponse
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.party.server.service.PartyService
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/CreateMandateEndpoint.sql"])
class CreateMandateEndpointTest : TenantAwareEndpointIntegrationTest() {
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
        mandateType = MandateType.TENANT,
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
        noticePeriod = 1,
        firstName = "Ray",
        lastName = "Sponsible",
        email = "Ray.Sponsible@gmail.com",
        whatsappNumber = "+237671234567",
        preferredContactPeriod = ContactPeriodType.MORNING,
        comments = "Close to the market",
    )

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
                "(200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible', 4)", // 4=VERIFIED
            "INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES (20, 1, 200, 2370201)",
        ],
    )
    fun `the new mandate is offered to an agent`() {
        val response = rest.postForEntity("/v1/mandates", request, CreateMandateResponse::class.java)
        awaitAsyncTasks() // Dispatched after commit

        val mandateId = response.body!!.mandateId
        val assignments = rest.getForEntity("/v1/assignments?mandate-id=$mandateId", SearchAssignmentResponse::class.java)
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
