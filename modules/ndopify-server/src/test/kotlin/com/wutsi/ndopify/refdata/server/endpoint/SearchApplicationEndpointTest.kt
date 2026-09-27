package com.wutsi.ndopify.refdata.server.endpoint

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.refdata.dto.SearchApplicationResponse
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/refdata/SearchApplicationEndpoint.sql"])
class SearchApplicationEndpointTest : BaseEndpointIntegrationTest() {
    @Test
    fun all() {
        val result = rest.getForEntity("/v1/applications", SearchApplicationResponse::class.java)

        assertEquals(HttpStatus.OK, result.statusCode)

        val applications = result.body!!.applications.sortedBy { it.id }
        assertEquals(3, applications.size)

        assertEquals(1L, applications[0].id)
        assertEquals(ApplicationCode.PUBLIC_PORTAL, applications[0].code)
        assertEquals(0, applications[0].roles.size)
        assertEquals(listOf(AuthType.PASSWORD, AuthType.GOOGLE_ONE_TAP), applications[0].supportedAuthTypes)

        assertEquals(2L, applications[1].id)
        assertEquals(ApplicationCode.ADMIN_CONSOLE, applications[1].code)
        assertEquals(2, applications[1].roles.size)
        assertEquals("admin", applications[1].roles[0].code)
        assertEquals("support", applications[1].roles[1].code)
        assertEquals(listOf(AuthType.PASSWORD), applications[1].supportedAuthTypes)

        assertEquals(3L, applications[2].id)
        assertEquals(ApplicationCode.PARTNER_CENTRAL, applications[2].code)
        assertEquals(0, applications[2].roles.size)
        assertEquals(listOf(AuthType.PASSWORD), applications[2].supportedAuthTypes)
    }
}
