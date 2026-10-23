package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.SearchAssignmentResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import java.text.SimpleDateFormat
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/SearchAssignmentEndpoint.sql"])
class SearchAssignmentEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Test
    fun `all, newest first`() {
        val assignments = search("")

        // 9 belongs to another tenant
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), assignments.map { it.id })
    }

    @Test
    fun summary() {
        val assignment = search("?id=4").single()

        assertEquals(4L, assignment.id)
        assertEquals(1L, assignment.mandateId)
        assertEquals(20L, assignment.agentId)
        assertEquals(AssignmentStatus.PENDING, assignment.status)
        assertEquals(0.6, assignment.score)

        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm")
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        assertEquals("2024-06-10 13:00", fmt.format(assignment.createdAt))
        assertEquals("2024-06-10 14:00", fmt.format(assignment.expiresAt!!))
    }

    @Test
    fun `by id`() {
        assertEquals(listOf(3L, 1L), search("?id=1&id=3&id=9").map { it.id })
    }

    @Test
    fun `by agent`() {
        assertEquals(listOf(4L, 3L, 1L), search("?agent-id=20").map { it.id })
    }

    @Test
    fun `by mandate`() {
        assertEquals(listOf(5L, 3L), search("?mandate-id=2").map { it.id })
    }

    @Test
    fun `by status`() {
        assertEquals(listOf(5L, 4L), search("?status=PENDING").map { it.id })
        assertEquals(listOf(2L, 1L), search("?status=DECLINED&status=EXPIRED").map { it.id })
    }

    @Test
    fun combined() {
        assertEquals(listOf(4L), search("?agent-id=20&status=PENDING").map { it.id })
        assertEquals(listOf(2L), search("?agent-id=30&mandate-id=1").map { it.id })
    }

    @Test
    fun `limit and offset`() {
        assertEquals(listOf(5L, 4L), search("?limit=2").map { it.id })
        assertEquals(listOf(3L, 2L), search("?limit=2&offset=2").map { it.id })
    }

    @Test
    fun `other tenant never returned`() {
        assertEquals(emptyList(), search("?id=9").map { it.id })
    }

    private fun search(query: String) =
        rest.getForEntity("/v1/assignments$query", SearchAssignmentResponse::class.java).let { response ->
            assertEquals(HttpStatus.OK, response.statusCode)
            response.body!!.assignments
        }
}
