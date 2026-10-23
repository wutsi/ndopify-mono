package com.wutsi.ndopify.contract.server.job

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.storage.local.StorageServiceLocal
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The signed PDFs not generated on their last signature, generated into a local storage of a temporary directory. */
@Sql(value = ["/db/test/clean.sql", "/db/test/contract/GenerateSignedContentJob.sql"])
class GenerateSignedContentJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: ContractJobs

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @MockitoBean
    private lateinit var storageProvider: StorageServiceProvider

    @TempDir
    lateinit var dir: File

    private lateinit var storage: StorageServiceLocal

    @BeforeTest
    fun setUpStorage() {
        storage = StorageServiceLocal(dir.absolutePath, "/storage", 8080)
        doReturn(storage).whenever(storageProvider).get()

        // Every contract has its content, so that only the selection decides which ones are generated
        jdbc.queryForList("SELECT id FROM T_CONTRACT", String::class.java).forEach { id ->
            val path = "contracts/$id/content.html"
            storage.store(path, ByteArrayInputStream("<html><body><p>$id</p></body></html>".toByteArray()), "text/html")
            jdbc.update("UPDATE T_CONTRACT SET content_url=? WHERE id=?", storage.publicUrl(path).toString(), id)
        }
    }

    @Test
    fun generateSignedContent() {
        jobs.generateSignedContent()

        val urls = signedContentUrls()
        assertEquals(setOf("c-1", "c-4", "c-21"), urls.filterValues { url -> url != null }.keys)
        assertEquals("https://s.com/c-4.pdf", urls["c-4"]) // Unchanged
        assertTrue(File(dir, storage.pathOf(urls["c-1"]!!)!!).exists())
        assertTrue(File(dir, storage.pathOf(urls["c-21"]!!)!!).exists())
    }

    @Test
    fun `nothing left to generate on the next run`() {
        jobs.generateSignedContent()
        val urls = signedContentUrls()

        jobs.generateSignedContent()

        assertEquals(urls, signedContentUrls())
    }

    // Read with SQL rather than the repository, whose queries are filtered by the tenant
    private fun signedContentUrls(): Map<String, String?> =
        jdbc.queryForList("SELECT id, signed_content_url FROM T_CONTRACT").associate { row ->
            row["id"] as String to row["signed_content_url"] as String?
        }
}
