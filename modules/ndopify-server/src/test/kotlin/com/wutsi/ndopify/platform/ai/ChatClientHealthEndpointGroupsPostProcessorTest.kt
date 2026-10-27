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
    private val processor = ChatClientHealthEndpointGroupsPostProcessor(setOf("visionChatClient", "textChatClient"))

    @Test
    fun excludedFromPrimary() {
        doReturn(true).whenever(primary).isMember("visionChatClient")
        doReturn(true).whenever(primary).isMember("textChatClient")

        val result = processor.postProcessHealthEndpointGroups(groups)

        assertFalse(result.primary.isMember("visionChatClient"))
        assertFalse(result.primary.isMember("textChatClient"))
    }

    @Test
    fun othersKeptInPrimary() {
        doReturn(true).whenever(primary).isMember("db")

        val result = processor.postProcessHealthEndpointGroups(groups)

        assertTrue(result.primary.isMember("db"))
        assertFalse(result.primary.isMember("mail"))
    }

    @Test
    fun groupsKept() {
        val result = processor.postProcessHealthEndpointGroups(groups)

        assertEquals(setOf("llm"), result.names)
        assertSame(llm, result.get("llm"))
    }
}
