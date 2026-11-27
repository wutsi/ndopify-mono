package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.customer.server.service.CustomerMandateLookup
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import org.springframework.stereotype.Service

/**
 * The mandate domain's [CustomerMandateLookup]: which customers an agent's mandates cover. The customer access check
 * reads the interface only, so the customer domain never depends on the mandate one.
 */
@Service
class MandateCustomerLookup(
    private val dao: MandateRepository,
) : CustomerMandateLookup {
    override fun findCustomerIds(agentId: Long): List<Long> {
        return dao.findCustomerIdsByAgentId(agentId)
    }

    override fun isCustomerOfAgent(customerId: Long, agentId: Long): Boolean {
        return dao.existsByCustomerIdAndAgentId(customerId, agentId)
    }
}
