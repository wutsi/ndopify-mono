package com.wutsi.ndopify.match.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.dto.SearchMatchResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/match/SearchMatchEndpoint.sql"])
class SearchMatchEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Test
    fun `active matches, best score first`() {
        val response = rest.getForEntity("/v1/matches?mandate-id=1", SearchMatchResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val matches = response.body!!.matches
        assertEquals(listOf(1L, 2L), matches.map { it.id }) // 3: its listing is rented   4: dismissed
        assertEquals(listOf(101L, 102L), matches.map { it.listingId })
        assertTrue(matches.all { it.status == MatchStatus.ACTIVE && it.mandateId == 1L })
        assertEquals(0.9, matches[0].score)
    }

    @Test
    fun `several mandates`() {
        val matches = rest.getForEntity("/v1/matches?mandate-id=1&mandate-id=4", SearchMatchResponse::class.java)
            .body!!.matches

        assertEquals(listOf(1L, 7L, 2L), matches.map { it.id }) // Best score first, all mandates together
        assertEquals(listOf(1L, 4L, 1L), matches.map { it.mandateId })
    }

    @Test
    fun `several mandates, one of another agent`() {
        val response = rest.getForEntity("/v1/matches?mandate-id=1&mandate-id=2", ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `several mandates, one not found`() {
        val response = rest.getForEntity("/v1/matches?mandate-id=1&mandate-id=999", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `no mandate`() {
        val response = rest.getForEntity("/v1/matches", ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_MISSING_PARAMETER, response.body?.error?.code)
        assertEquals("mandate-id", response.body?.error?.parameter?.name)
    }

    @Test
    fun dismissed() {
        val matches = rest.getForEntity("/v1/matches?mandate-id=1&status=DISMISSED", SearchMatchResponse::class.java)
            .body!!.matches

        assertEquals(listOf(4L), matches.map { it.id })
    }

    @Test
    fun `closed search`() {
        val matches = rest.getForEntity("/v1/matches?mandate-id=3", SearchMatchResponse::class.java).body!!.matches

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `limit and offset`() {
        val matches = rest.getForEntity("/v1/matches?mandate-id=1&limit=1&offset=1", SearchMatchResponse::class.java)
            .body!!.matches

        assertEquals(listOf(2L), matches.map { it.id })
    }

    @Test
    fun `search of another agent`() {
        val response = rest.getForEntity("/v1/matches?mandate-id=2", ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `by listing`() {
        val matches = rest.getForEntity("/v1/matches?listing-id=101", SearchMatchResponse::class.java).body!!.matches

        assertEquals(listOf(1L), matches.map { it.id }) // 6: closed search   10: accepted
    }

    @Test
    fun `by listing, accepted`() {
        val matches = rest.getForEntity("/v1/matches?listing-id=101&listing-id=102&status=ACCEPTED", SearchMatchResponse::class.java)
            .body!!.matches

        assertEquals(listOf(10L), matches.map { it.id })
        assertEquals(listOf(101L), matches.map { it.listingId })
    }

    @Test
    fun `by mandate and listing`() {
        val matches = rest.getForEntity("/v1/matches?mandate-id=1&listing-id=102", SearchMatchResponse::class.java)
            .body!!.matches

        assertEquals(listOf(2L), matches.map { it.id })
    }

    @Test
    fun `listing of another agent`() {
        val response = rest.getForEntity("/v1/matches?listing-id=101&listing-id=109", ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `listing not found`() {
        val response = rest.getForEntity("/v1/matches?listing-id=999", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `listing of another tenant is not found`() {
        val response = rest.getForEntity("/v1/matches?listing-id=901", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.getForEntity("/v1/matches?mandate-id=1", ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
    }

    @Test
    fun `mandate not found`() {
        val response = rest.getForEntity("/v1/matches?mandate-id=999", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `mandate of another tenant is not found`() {
        val response = rest.getForEntity("/v1/matches?mandate-id=9", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }
}
