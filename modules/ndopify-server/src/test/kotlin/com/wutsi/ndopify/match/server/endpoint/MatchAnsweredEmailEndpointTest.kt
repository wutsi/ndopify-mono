package com.wutsi.ndopify.match.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/match/ProposeMatchEndpoint.sql"])
class MatchAnsweredEmailEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val application = ApplicationCode.E_SIGNATURE

    @MockitoBean
    private lateinit var client: TelegramClient

    @BeforeTest
    fun asCustomer() {
        userId = 13L
    }

    @Test
    fun `an unlinked agent is e-mailed, Telegram is not called`() {
        val response = rest.postForEntity("/v1/matches/2/accept", null, Any::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)

        awaitAsyncTasks()

        assertTrue(smtp.waitForIncomingEmail(5000, 1), "one e-mail expected")
        verifyNoInteractions(client)
    }
}
