package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.koki.listing.dto.ListingType
import com.wutsi.koki.listing.dto.PropertyType
import com.wutsi.koki.listing.dto.RoadAccessType
import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.ElectricityAccessType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.WaterAccessType
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.UpdateMandateRequest
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/UpdateMandateEndpoint.sql"])
class UpdateMandateEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: MandateRepository

    @Autowired
    private lateinit var ds: DataSource

    private val request = UpdateMandateRequest(
        mandateType = MandateType.OWNER,
        listingType = ListingType.SALE,
        cityId = 2220957L,
        neighbourhoodIds = listOf(333L),
        propertyTypes = listOf(PropertyType.VILLA),
        furnitureType = FurnitureType.entries.last(),
        rooms = "4",
        parkings = "2",
        minArea = 200,
        maxArea = 400,
        waterAccessType = WaterAccessType.entries.last(),
        waterTank = false,
        waterWell = false,
        electricityAccessType = ElectricityAccessType.entries.last(),
        generatorSet = false,
        solarPanel = false,
        roadAccessType = RoadAccessType.entries.last(),
        securedFenced = false,
        securityGuard = false,
        videoSurveillance = false,
        price = Money(amount = 90000000, "XAF", PeriodicityType.NONE),
        securityDeposit = null,
        advanceRent = null,
        leaseTerm = null,
        noticePeriod = null,
        comments = "Ocean view",
    )

    @Test
    fun update() {
        val response = rest.postForEntity("/v1/mandates/1", request, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val mandate = dao.findById(1L).get()
        assertEquals(TENANT_ID, mandate.tenantId)
        assertEquals(10L, mandate.customerId)
        assertNull(mandate.agentId)
        assertEquals(MandateStatus.PENDING_ASSIGNMENT, mandate.status)
        assertEquals(request.mandateType, mandate.mandateType)
        assertEquals(request.listingType, mandate.listingType)
        assertEquals(request.cityId, mandate.cityId)
        assertEquals(request.neighbourhoodIds, neighborhoodIds(1L))
        assertEquals(request.propertyTypes, mandate.propertyTypes)
        assertEquals(request.furnitureType, mandate.furnitureType)
        assertEquals(request.rooms, mandate.rooms)
        assertEquals(request.parkings, mandate.parkings)
        assertEquals(request.minArea, mandate.minArea)
        assertEquals(request.maxArea, mandate.maxArea)
        assertEquals(request.waterAccessType, mandate.waterAccessType)
        assertEquals(request.waterTank, mandate.waterTank)
        assertEquals(request.waterWell, mandate.waterWell)
        assertEquals(request.electricityAccessType, mandate.electricityAccessType)
        assertEquals(request.generatorSet, mandate.generatorSet)
        assertEquals(request.solarPanel, mandate.solarPanel)
        assertEquals(request.roadAccessType, mandate.roadAccessType)
        assertEquals(request.securedFenced, mandate.securedFenced)
        assertEquals(request.securityGuard, mandate.securityGuard)
        assertEquals(request.videoSurveillance, mandate.videoSurveillance)
        assertEquals(request.price.amount, mandate.price)
        assertEquals(request.price.currency, mandate.currencyCode)
        assertEquals(request.price.periodicity, mandate.pricePeriodicityType)
        assertNull(mandate.securityDeposit)
        assertNull(mandate.advanceRent)
        assertNull(mandate.leaseTerm)
        assertNull(mandate.noticePeriod)
        assertEquals(ContactPeriodType.entries[1], mandate.preferredContactPeriod)
        assertEquals(request.comments, mandate.comments)
    }

    @Test
    fun `mandate not found`() {
        val response = rest.postForEntity("/v1/mandates/999", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `mandate from another tenant is not found`() {
        val response = rest.postForEntity("/v1/mandates/2", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }

    private fun neighborhoodIds(mandateId: Long): List<Long> {
        return ds.connection.use { conn ->
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
    }
}
