package com.wutsi.ndopify.contract.server.service

import com.wutsi.ndopify.contract.dto.ContractType
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.time.Instant
import java.time.ZoneId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ContractNumberGeneratorTest {
    private val update = mock(Query::class.java)
    private val select = mock(Query::class.java)
    private val em = mock(EntityManager::class.java)
    private val generator = ContractNumberGenerator(em)
    private val douala = ZoneId.of("Africa/Douala")

    @BeforeTest
    fun setUp() {
        doReturn(update).`when`(em).createNativeQuery(ArgumentMatchers.startsWith("INSERT"))
        doReturn(select).`when`(em).createNativeQuery("SELECT LAST_INSERT_ID()")
        doReturn(update).`when`(update).setParameter(ArgumentMatchers.anyString(), ArgumentMatchers.any())
        doReturn(42L).`when`(select).singleResult
    }

    @Test
    fun `lease agreement`() {
        val number = generator.generate(1L, ContractType.LEASE_AGREEMENT, Instant.parse("2026-06-15T10:00:00Z"), douala)

        assertEquals("BAIL-2026-000042", number)
        verify(update).setParameter("tenantId", 1L)
        verify(update).setParameter("prefix", "BAIL")
        verify(update).setParameter("year", 2026)
    }

    @Test
    fun `other types are mandates`() {
        assertEquals("MNDT", generator.prefix(ContractType.SEARCH))
        assertEquals("MNDT", generator.prefix(ContractType.LISTING))
        assertEquals("MNDT", generator.prefix(ContractType.UNKNOWN))
    }

    @Test
    fun `year of the tenant time zone`() {
        // Still 2026 in UTC, already 2027 in Douala (UTC+1)
        val number = generator.generate(1L, ContractType.SEARCH, Instant.parse("2026-12-31T23:30:00Z"), douala)

        assertEquals("MNDT-2027-000042", number)
        verify(update).setParameter("year", 2027)
    }
}
