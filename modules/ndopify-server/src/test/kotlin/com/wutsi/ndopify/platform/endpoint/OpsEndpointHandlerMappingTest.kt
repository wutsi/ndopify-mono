package com.wutsi.ndopify.platform.endpoint

import org.springframework.web.bind.annotation.GetMapping
import java.lang.reflect.Method
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OpsEndpointHandlerMappingTest {
    class Endpoints {
        @OpsEndpoint
        @GetMapping("/ops")
        fun ops() {}

        @GetMapping("/regular")
        fun regular() {}
    }

    private val ops = Endpoints::class.java.getMethod("ops")
    private val regular = Endpoints::class.java.getMethod("regular")

    private fun OpsEndpointHandlerMapping.mappingFor(method: Method) =
        getMappingForMethod(method, Endpoints::class.java)

    @Test
    fun `enabled - ops endpoints are mapped`() {
        val mapping = OpsEndpointHandlerMapping(opsEnabled = true)

        assertNotNull(mapping.mappingFor(ops))
        assertNotNull(mapping.mappingFor(regular))
    }

    @Test
    fun `disabled - ops endpoints are not mapped, the others are`() {
        val mapping = OpsEndpointHandlerMapping(opsEnabled = false)

        assertNull(mapping.mappingFor(ops))
        assertNotNull(mapping.mappingFor(regular))
    }
}
