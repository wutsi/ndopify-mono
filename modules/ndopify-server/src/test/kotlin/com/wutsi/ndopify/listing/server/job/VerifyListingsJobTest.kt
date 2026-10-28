package com.wutsi.ndopify.listing.server.job

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.server.service.verifier.ListingImageInfoExtractor
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end check of a listing's verification: sent by its agent (`POST /v1/listings/{id}/verify`), then verified
 * by the job (`ListingJobs.verify`), against the real database.
 *
 * The endpoint and the job are each tested on their own, but only this test checks that they agree on the status
 * between them: the endpoint once set a status the job never picked, so every listing sent stayed stuck, while
 * every other test passed. Keep it end-to-end: no mock of the services, the job or the verifiers.
 *
 * Only the vision LLM is mocked, never called anyway: the photos are already VERIFIED, but one that can't be
 * downloaded.
 */
@Sql(value = ["/db/test/clean.sql", "/db/test/listing/VerifyListingsJob.sql"])
class VerifyListingsJobTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN) // To send the listing through POST /v1/listings/{id}/verify

    @Autowired
    private lateinit var jobs: ListingJobs

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @MockitoBean
    private lateinit var extractor: ListingImageInfoExtractor

    @Test
    fun verified() {
        send(100L)

        jobs.verify()

        val listing = listing(100L)
        assertEquals("VERIFIED", listing["status"])
        assertEquals(null, listing["error_codes"])
        assertEquals(0, listing["retries"])
        verify(extractor, never()).extract(any())
    }

    @Test
    fun `verifier error - back to draft, with the error code`() {
        send(101L)

        jobs.verify()

        val listing = listing(101L)
        assertEquals("DRAFT", listing["status"])
        assertEquals(ErrorCode.LISTING_OUTSIDE_NEIGHBORHOOD, listing["error_codes"])
    }

    @Test
    fun `verifier failure - left to be retried by the next run`() {
        send(102L)

        jobs.verify()

        val listing = listing(102L)
        assertEquals("UNDER_VERIFICATION", listing["status"])
        assertEquals(1, listing["retries"])
        assertEquals(ErrorCode.INTERNAL_ERROR, listing["error_codes"])

        // The next run picks it up again
        jobs.verify()

        assertEquals(2, listing(102L)["retries"])
    }

    private fun send(id: Long) {
        val response = rest.postForEntity("/v1/listings/$id/verify", null, ErrorResponse::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    // Read without the tenant filter: the test thread has no tenant
    private fun listing(id: Long): Map<String, Any?> =
        jdbc.queryForMap("SELECT status, error_codes, retries FROM T_LISTING WHERE id=?", id)
}
