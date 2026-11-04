package com.wutsi.ndopify.visit.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.visit.dto.CheckInVisitRequest
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.server.dao.VisitRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/CheckInVisitEndpoint.sql"])
class CheckInVisitEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: VisitRepository

    @Test
    fun checkIn() {
        val response = rest.postForEntity(
            "/v1/visits/1/check-in",
            CheckInVisitRequest(code = "1234", latitude = 4.051550, longitude = 9.767900),
            Any::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val visit = dao.findById(1L).get()
        assertEquals(VisitStatus.CHECKED_IN, visit.status)
        assertNotNull(visit.checkedInAt)
        assertEquals(50, visit.checkInDistanceMeters)
        assertFalse(visit.checkInFlagged)
        assertEquals(4.051550, visit.checkInLatitude)
        assertEquals(9.767900, visit.checkInLongitude)
    }

    @Test
    fun `more than 300 m away is flagged`() {
        val response = rest.postForEntity(
            "/v1/visits/1/check-in",
            CheckInVisitRequest(code = "1234", latitude = 4.058300, longitude = 9.767900),
            Any::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val visit = dao.findById(1L).get()
        assertEquals(VisitStatus.CHECKED_IN, visit.status)
        assertTrue(visit.checkInDistanceMeters!! in 790..810, "distance ${visit.checkInDistanceMeters}")
        assertTrue(visit.checkInFlagged)
    }

    @Test
    fun `without position`() {
        val response = rest.postForEntity("/v1/visits/1/check-in", CheckInVisitRequest(code = "1234"), Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val visit = dao.findById(1L).get()
        assertNull(visit.checkInDistanceMeters)
        assertFalse(visit.checkInFlagged)
    }

    @Test
    fun `wrong code`() {
        val response = rest.postForEntity("/v1/visits/1/check-in", CheckInVisitRequest(code = "0000"), ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.VISIT_CODE_INVALID, response.body?.error?.code)
        val visit = dao.findById(1L).get()
        assertEquals(VisitStatus.CONFIRMED, visit.status)
        assertEquals(1, visit.codeAttempts)
    }

    @Test
    fun `the fifth wrong code locks the check-in for good`() {
        val wrong = rest.postForEntity("/v1/visits/6/check-in", CheckInVisitRequest(code = "0000"), ErrorResponse::class.java)
        assertEquals(HttpStatus.CONFLICT, wrong.statusCode)
        assertEquals(ErrorCode.VISIT_CODE_LOCKED, wrong.body?.error?.code)

        val right = rest.postForEntity("/v1/visits/6/check-in", CheckInVisitRequest(code = "4444"), ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, right.statusCode)
        assertEquals(ErrorCode.VISIT_CODE_LOCKED, right.body?.error?.code)
        val visit = dao.findById(6L).get()
        assertEquals(VisitStatus.CONFIRMED, visit.status)
        assertEquals(5, visit.codeAttempts)
    }

    @Test
    fun `too early`() {
        val response = rest.postForEntity("/v1/visits/7/check-in", CheckInVisitRequest(code = "5555"), ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.VISIT_OUTSIDE_WINDOW, response.body?.error?.code)
    }

    @Test
    fun `too late`() {
        val response = rest.postForEntity("/v1/visits/3/check-in", CheckInVisitRequest(code = "1111"), ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.VISIT_OUTSIDE_WINDOW, response.body?.error?.code)
    }

    @Test
    fun `a cancelled visit stays cancelled`() {
        val response = rest.postForEntity("/v1/visits/4/check-in", CheckInVisitRequest(code = "2222"), ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.VISIT_NOT_CONFIRMED, response.body?.error?.code)
        assertEquals(VisitStatus.CANCELLED, dao.findById(4L).get().status)
    }

    @Test
    fun `missing code`() {
        val response = rest.postForEntity("/v1/visits/1/check-in", CheckInVisitRequest(code = ""), ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
    }

    @Test
    fun `visit of another agent`() {
        val response = rest.postForEntity("/v1/visits/5/check-in", CheckInVisitRequest(code = "3333"), ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `as the customer`() {
        userId = 13L

        val response = rest.postForEntity("/v1/visits/1/check-in", CheckInVisitRequest(code = "1234"), ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `visit not found`() {
        val response = rest.postForEntity("/v1/visits/999/check-in", CheckInVisitRequest(code = "1234"), ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.VISIT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.postForEntity("/v1/visits/1/check-in", CheckInVisitRequest(code = "1234"), ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }
}
