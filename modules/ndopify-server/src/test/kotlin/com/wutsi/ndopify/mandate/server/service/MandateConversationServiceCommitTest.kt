package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.jdbc.Sql
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(scripts = ["/db/test/clean.sql", "/db/test/mandate/AcceptAssignmentEndpoint.sql"])
class MandateConversationServiceCommitTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var service: MandateConversationService

    @Autowired
    private lateinit var dao: MandateRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    /**
     * The e-mail is handed to SMTP from inside the sender's transaction, and the SMTP server delivers it before that
     * transaction commits: `MailService.send` has returned by the time a client receives the message. The subject must
     * therefore be committed before `of` returns, not with the sender's transaction (see
     * `AcceptAssignmentEndpointTest.accept`).
     */
    @Test
    fun `the conversation subject is committed before the e-mail is sent`() {
        val outer = TransactionTemplate(transactionManager)
        val other = TransactionTemplate(transactionManager).apply { propagationBehavior = PROPAGATION_REQUIRES_NEW }

        outer.executeWithoutResult {
            val conversation = service.of(1L)

            assertEquals("Votre recherche : appartement à Yaounde · mandat 1", conversation.subject)
            // The caller's transaction is still open: only a subject committed by of() is readable from another one
            assertEquals(
                "Votre recherche : appartement à Yaounde · mandat 1",
                other.execute { dao.findConversationSubjectById(1L) },
            )
        }
    }
}
