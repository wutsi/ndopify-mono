package com.wutsi.ndopify.platform.ai

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.Test
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChatClientHealthEndpointGroupsPostProcessorTest {
    private val primary = mock<HealthEndpointGroup>()
    private val llm = mock<HealthEndpointGroup>()
    private val groups = HealthEndpointGroups.of(primary, mapOf("llm" to llm))

    @Test
    fun excludedFromPrimary() {
        doReturn(true).whenever(primary).isMember(ChatClientHealthEndpointGroupsPostProcessor.CONTRIBUTOR)

        val result = ChatClientHealthEndpointGroupsPostProcessor().postProcessHealthEndpointGroups(groups)

        assertFalse(result.primary.isMember(ChatClientHealthEndpointGroupsPostProcessor.CONTRIBUTOR))
    }

    @Test
    fun othersKeptInPrimary() {
        doReturn(true).whenever(primary).isMember("db")

        val result = ChatClientHealthEndpointGroupsPostProcessor().postProcessHealthEndpointGroups(groups)

        assertTrue(result.primary.isMember("db"))
        assertFalse(result.primary.isMember("mail"))
    }

    @Test
    fun groupsKept() {
        val result = ChatClientHealthEndpointGroupsPostProcessor().postProcessHealthEndpointGroups(groups)

        assertEquals(setOf("llm"), result.names)
        assertSame(llm, result.get("llm"))
    }
}
