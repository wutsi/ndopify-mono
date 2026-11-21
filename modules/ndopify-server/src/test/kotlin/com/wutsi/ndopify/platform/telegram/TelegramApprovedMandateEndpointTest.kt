package com.wutsi.ndopify.platform.telegram

import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.contract.dto.SignContractRequest
import com.wutsi.ndopify.contract.server.service.SignedContractService
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Sql(
    value = [
        "/db/test/clean.sql",
        "/db/test/contract/SignContractEndpoint.sql",
        "/db/test/telegram/TelegramApprovedMandateEndpoint.sql",
    ],
)
class TelegramApprovedMandateEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    companion object {
        private const val CONTENT_HASH =
            "1111111111111111111111111111111111111111111111111111111111111111" // every contract but c-10
    }

    override val application = ApplicationCode.E_SIGNATURE

    @MockitoBean
    private lateinit var client: TelegramClient

    // The signed PDF isn't this test's subject
    @MockitoBean
    private lateinit var signedContractService: SignedContractService

    @Autowired
    private lateinit var mandateDao: MandateRepository

    @Test
    fun `the last signature messages the linked agent instead of e-mailing it`() {
        userId = 12L // party 101 signs cp-22, the last required party of c-2

        val body = SignContractRequest(
            partyId = "cp-22",
            ipAddress = "192.168.1.10",
            userAgent = "Mozilla/5.0 (iPhone)",
            contentHash = CONTENT_HASH,
        )
        assertEquals(
            HttpStatus.OK,
            rest.postForEntity("/v1/contracts/c-2/sign", body, Any::class.java).statusCode,
        )
        assertEquals(MandateStatus.APPROVED, mandateDao.findById(2L).get().status)

        awaitAsyncTasks()

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertTrue(
            text.firstValue.startsWith(
                "✅ Le client a signé le mandat. Il va commencer à recevoir les offres.",
            ),
        )
        assertTrue(text.firstValue.contains("Recherche : Location — 150 000 FCFA / mois"), text.firstValue)
        assertEquals(
            listOf(TelegramButton("Voir le mandat", url = "https://partner.ndopify.com/mandates/2")),
            buttons.firstValue,
        )

        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no approval e-mail for a linked agent")
    }
}
