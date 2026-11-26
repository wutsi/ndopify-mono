package com.wutsi.ndopify.mandate.server.dao

import com.wutsi.ndopify.platform.tenant.TenantContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("qa")
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/MandateDocumentRepositoryTest.sql"])
class MandateDocumentRepositoryTest {
    @Autowired
    private lateinit var dao: MandateDocumentRepository

    @Autowired
    private lateinit var pageDao: MandateDocumentPageRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        TenantContext.set(1L)
    }

    @AfterEach
    fun tearDown() {
        TenantContext.remove()
    }

    @Test
    fun `find by mandate id - this mandate's documents`() {
        val documents = dao.findByMandateIdOrderByIdDesc(1L)

        assertEquals(listOf(1L), documents.map { it.id })
    }

    @Test
    fun `find by mandate id - never another tenant's`() {
        // Mandate 2 lives in tenant 2, so a tenant-1 read finds nothing
        assertTrue(dao.findByMandateIdOrderByIdDesc(2L).isEmpty())
    }

    @Test
    fun `find pages by document id - ordered by page index`() {
        val pages = pageDao.findByDocumentIdOrderByPageIndex(1L)

        assertEquals(listOf(0, 1), pages.map { it.pageIndex })
    }

    @Test
    fun `find pages by document ids - ordered by page index`() {
        val pages = pageDao.findByDocumentIdInOrderByPageIndex(listOf(1L))

        assertEquals(listOf(0, 1), pages.map { it.pageIndex })
    }

    @Test
    fun `find page by path`() {
        val page = pageDao.findByPath("documents/1/page/0.png")

        assertEquals(1L, page?.documentId)
        assertEquals(0, page?.pageIndex)
    }

    @Test
    fun `find max page index`() {
        assertEquals(1, pageDao.findMaxPageIndex(1L))
        assertNull(pageDao.findMaxPageIndex(999L))
    }

    @Test
    fun `saving a page with an existing path fails on the unique constraint`() {
        // The page repository has no saveAndFlush, so the duplicate is inserted straight through the JDBC layer to
        // surface the UNIQUE (path) violation as a DataIntegrityViolationException
        assertFailsWith<DataIntegrityViolationException> {
            jdbcTemplate.update(
                "INSERT INTO T_MANDATE_DOCUMENT_PAGE(tenant_id, document_id, page_index, path, storage_type, " +
                    "mime_type, created_at, modified_at) VALUES (1, 1, 2, 'documents/1/page/0.png', 'S3', " +
                    "'image/png', NOW(), NOW())"
            )
        }
    }
}
