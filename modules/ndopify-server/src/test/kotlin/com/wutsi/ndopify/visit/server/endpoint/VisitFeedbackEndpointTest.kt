package com.wutsi.ndopify.visit.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.match.dto.MatchRejectionReason
import com.wutsi.ndopify.visit.dto.VisitFeedbackRequest
import com.wutsi.ndopify.visit.dto.VisitInterest
import com.wutsi.ndopify.visit.server.dao.VisitRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/VisitFeedbackEndpoint.sql"])
class VisitFeedbackEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: VisitRepository

    @Test
    fun interested() {
        userId = 13L

        val response = rest.postForEntity(
            "/v1/visits/1/feedback",
            VisitFeedbackRequest(interest = VisitInterest.INTERESTED),
            Any::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val visit = dao.findById(1L).get()
        assertEquals(VisitInterest.INTERESTED, visit.interest)
        assertNull(visit.feedbackReasonCode)
        assertNull(visit.feedbackComment)
    }

    @Test
    fun `not interested, with a reason and a comment`() {
        userId = 13L

        val response = rest.postForEntity(
            "/v1/visits/1/feedback",
            VisitFeedbackRequest(
                interest = VisitInterest.NOT_INTERESTED,
                reasonCode = MatchRejectionReason.PRICE,
                comment = "Trop cher pour moi",
            ),
            Any::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val visit = dao.findById(1L).get()
        assertEquals(VisitInterest.NOT_INTERESTED, visit.interest)
        assertEquals(MatchRejectionReason.PRICE, visit.feedbackReasonCode)
        assertEquals("Trop cher pour moi", visit.feedbackComment)
    }

    @Test
    fun `already sent`() {
        userId = 13L

        val response = rest.postForEntity(
            "/v1/visits/2/feedback",
            VisitFeedbackRequest(interest = VisitInterest.NOT_INTERESTED),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.VISIT_FEEDBACK_ALREADY_SENT, response.body?.error?.code)
        assertEquals(VisitInterest.INTERESTED, dao.findById(2L).get().interest)
    }

    @Test
    fun `not completed`() {
        userId = 13L

        val response = rest.postForEntity(
            "/v1/visits/3/feedback",
            VisitFeedbackRequest(interest = VisitInterest.INTERESTED),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.VISIT_NOT_COMPLETED, response.body?.error?.code)
    }

    @Test
    fun `as the agent`() {
        val response = rest.postForEntity(
            "/v1/visits/1/feedback",
            VisitFeedbackRequest(interest = VisitInterest.INTERESTED),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `another customer`() {
        userId = 15L

        val response = rest.postForEntity(
            "/v1/visits/1/feedback",
            VisitFeedbackRequest(interest = VisitInterest.INTERESTED),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `visit not found`() {
        userId = 13L

        val response = rest.postForEntity(
            "/v1/visits/999/feedback",
            VisitFeedbackRequest(interest = VisitInterest.INTERESTED),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.VISIT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.postForEntity(
            "/v1/visits/1/feedback",
            VisitFeedbackRequest(interest = VisitInterest.INTERESTED),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }
}
