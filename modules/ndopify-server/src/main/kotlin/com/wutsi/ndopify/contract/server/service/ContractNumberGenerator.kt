package com.wutsi.ndopify.contract.server.service

import com.wutsi.ndopify.contract.dto.ContractType
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId

/**
 * Generates the contract numbers, `<prefix>-<year>-<sequence>` (ex: BAIL-2026-000042): one sequence per tenant, prefix
 * and year, in T_CONTRACT_SEQUENCE. Must run in the transaction creating the contract: the increment locks the
 * sequence's row until it commits, and is undone if it rolls back, so the numbers have no gap.
 */
@Service
class ContractNumberGenerator(private val em: EntityManager) {
    companion object {
        const val PREFIX_LEASE_AGREEMENT = "BAIL"
        const val PREFIX_MANDATE = "MNDT"
    }

    fun generate(tenantId: Long, type: ContractType, now: Instant, timeZone: ZoneId): String {
        val prefix = prefix(type)
        val year = now.atZone(timeZone).year // The tenant's year: matters around New Year
        return "%s-%d-%06d".format(prefix, year, next(tenantId, prefix, year))
    }

    fun prefix(type: ContractType): String {
        return when (type) {
            ContractType.LEASE_AGREEMENT -> PREFIX_LEASE_AGREEMENT
            else -> PREFIX_MANDATE
        }
    }

    // One statement creates the year's sequence or increments it, so two first contracts of a year can't race.
    // LAST_INSERT_ID(expr) is per connection: concurrent transactions never read each other's value
    private fun next(tenantId: Long, prefix: String, year: Int): Long {
        em.createNativeQuery(
            "INSERT INTO T_CONTRACT_SEQUENCE(tenant_id, prefix, year, value) " +
                "VALUES (:tenantId, :prefix, :year, LAST_INSERT_ID(1)) " +
                "ON DUPLICATE KEY UPDATE value = LAST_INSERT_ID(value + 1)"
        )
            .setParameter("tenantId", tenantId)
            .setParameter("prefix", prefix)
            .setParameter("year", year)
            .executeUpdate()
        return (em.createNativeQuery("SELECT LAST_INSERT_ID()").singleResult as Number).toLong()
    }
}
