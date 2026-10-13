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
import com.wutsi.ndopify.mandate.dto.GetMandateResponse
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/GetMandateEndpoint.sql"])
class GetMandateEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Test
    fun get() {
        val response = rest.getForEntity("/v1/mandates/1", GetMandateResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val mandate = response.body!!.mandate
        assertEquals(1L, mandate.id)
        assertEquals(10L, mandate.customerId)
        assertNull(mandate.agentId)
        assertEquals(MandateStatus.PENDING_ASSIGNMENT, mandate.status)
        assertEquals(MandateType.TENANT, mandate.mandateType)
        assertEquals(ListingType.RENTAL, mandate.listingType)
        assertEquals(2370201L, mandate.cityId)
        assertEquals(listOf(111L, 222L), mandate.neighborhoodIds.sorted())
        assertEquals(listOf(PropertyType.APARTMENT, PropertyType.STUDIO), mandate.propertyTypes)
        assertEquals(FurnitureType.entries[1], mandate.furnitureType)
        assertEquals("2", mandate.rooms)
        assertEquals("1", mandate.parkings)
        assertEquals(50, mandate.minArea)
        assertEquals(100, mandate.maxArea)
        assertEquals(WaterAccessType.entries[1], mandate.waterAccessType)
        assertTrue(mandate.waterTank)
        assertTrue(mandate.waterWell)
        assertEquals(ElectricityAccessType.entries[1], mandate.electricityAccessType)
        assertTrue(mandate.generatorSet)
        assertTrue(mandate.solarPanel)
        assertEquals(RoadAccessType.entries[1], mandate.roadAccessType)
        assertTrue(mandate.securedFenced)
        assertTrue(mandate.securityGuard)
        assertTrue(mandate.videoSurveillance)
        assertEquals(150000L, mandate.price.amount)
        assertEquals("XAF", mandate.price.currency)
        assertEquals(PeriodicityType.MONTHLY, mandate.price.periodicity)
        assertEquals(2, mandate.securityDeposit)
        assertEquals(3, mandate.advanceRent)
        assertEquals(12, mandate.leaseTerm)
        assertEquals(1, mandate.noticePeriod)
        assertEquals(ContactPeriodType.entries[1], mandate.preferredContactPeriod)
        assertEquals("Close to the market", mandate.comments)
    }

    @Test
    fun `mandate not found`() {
        val response = rest.getForEntity("/v1/mandates/999", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `mandate from another tenant is not found`() {
        val response = rest.getForEntity("/v1/mandates/2", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }
}
