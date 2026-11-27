package com.wutsi.ndopify.customer.server.service

/**
 * The mandate relation a customer read needs. Declared by the customer domain and implemented by the mandate domain
 * ([com.wutsi.ndopify.mandate.server.service.MandateCustomerLookup]), so that the customer never depends on the mandate
 * one: the customer access check reads this interface only.
 */
interface CustomerMandateLookup {
    /** The ids of the customers of [agentId]'s mandates, any status. */
    fun findCustomerIds(agentId: Long): List<Long>

    /** Whether one of [customerId]'s mandates is assigned to [agentId]. */
    fun isCustomerOfAgent(customerId: Long, agentId: Long): Boolean
}
