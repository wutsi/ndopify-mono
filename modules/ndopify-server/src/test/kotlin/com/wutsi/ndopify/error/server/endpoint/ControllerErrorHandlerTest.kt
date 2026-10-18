package com.wutsi.ndopify.error.endpoint

import com.nhaarman.mockitokotlin2.mock
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.orm.ObjectOptimisticLockingFailureException
import kotlin.test.Test
import kotlin.test.assertEquals

class ControllerErrorHandlerTest {
    private val handler = ControllerErrorHandler(mock())

    @Test
    fun `optimistic locking failure - 409 with the code of the entity`() {
        mapOf(
            AssignmentEntity::class.java to ErrorCode.ASSIGNMENT_NOT_PENDING,
            MandateEntity::class.java to ErrorCode.MANDATE_VERSION_CONFLICT,
            AgentEntity::class.java to ErrorCode.AGENT_VERSION_CONFLICT,
            ContractEntity::class.java to ErrorCode.CONTRACT_VERSION_CONFLICT,
            KycCaseEntity::class.java to ErrorCode.KYC_CASE_VERSION_CONFLICT,
            PartyEntity::class.java to ErrorCode.HTTP_VERSION_CONFLICT, // Not @Version-ed: the generic code
        ).forEach { (type, code) ->
            val response = handler.onObjectOptimisticLockingFailureException(
                MockHttpServletRequest(),
                ObjectOptimisticLockingFailureException(type, 1L),
            )

            assertEquals(HttpStatus.CONFLICT, response.statusCode, type.simpleName)
            assertEquals(code, response.body?.error?.code, type.simpleName)
        }
    }
}
