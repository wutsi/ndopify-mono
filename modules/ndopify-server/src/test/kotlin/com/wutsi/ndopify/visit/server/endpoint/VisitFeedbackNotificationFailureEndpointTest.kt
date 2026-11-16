package com.wutsi.ndopify.visit.server.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.visit.dto.VisitFeedbackRequest
import com.wutsi.ndopify.visit.dto.VisitInterest
import com.wutsi.ndopify.visit.server.dao.VisitRepository
import com.wutsi.ndopify.visit.server.service.VisitFeedbackNotifier
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The notification is a side effect of the answer: when it fails, `VisitEventListener` logs it and the feedback stays
 * committed, never rethrown to the customer.
 */
@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/VisitFeedbackEndpoint.sql"])
class VisitFeedbackNotificationFailureEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: VisitRepository

    @MockitoBean
    private lateinit var notifier: VisitFeedbackNotifier

    @Test
    fun `a failed notification leaves the feedback committed`() {
        userId = 13L
        doThrow(IllegalStateException("notification failed")).whenever(notifier).send(any(), any(), any(), any())

        val response = rest.postForEntity(
            "/v1/visits/1/feedback",
            VisitFeedbackRequest(interest = VisitInterest.INTERESTED),
            Any::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        awaitAsyncTasks()

        // The notification ran and threw: the feedback is still committed and the customer saw no error
        verify(notifier).send(any(), any(), any(), any())
        assertEquals(VisitInterest.INTERESTED, dao.findById(1L).get().interest)
    }
}
