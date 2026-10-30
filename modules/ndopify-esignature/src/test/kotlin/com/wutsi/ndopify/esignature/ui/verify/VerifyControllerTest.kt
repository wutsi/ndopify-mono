package com.wutsi.ndopify.esignature.ui.verify

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.contract.dto.ContractSignature
import com.wutsi.ndopify.esignature.client.ndopify.NdContractClient
import com.wutsi.ndopify.esignature.service.TenantFixtures.FORMATTER
import com.wutsi.ndopify.esignature.service.TenantFixtures.TENANT
import com.wutsi.ndopify.web.core.service.TenantContext
import jakarta.servlet.ServletException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.servlet.view.InternalResourceViewResolver
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VerifyControllerTest {
    private val client = mock<NdContractClient>()
    private val tenantContext = mock<TenantContext> {
        on { getTenant() } doReturn TENANT
    }
    private lateinit var mvc: MockMvc

    private val hash = "9f2c4e7a1b8d03f65e2a7c9b4d1e8f0a3c6b5d2e9f1a4c7b0d3e6f9a2c5b8e1d"
    private val signedAt = Date(1_792_000_000_000)
    private val signature = ContractSignature(
        contractId = "c-1",
        contractNumber = "MNDT-2026-000042",
        legalName = "Ray Sponsible",
        signedAt = signedAt,
    )

    @BeforeEach
    fun setUp() {
        mvc = MockMvcBuilders.standaloneSetup(VerifyController(client, tenantContext))
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
    }

    private fun view(result: MvcResult): VerifyView = result.modelAndView?.model?.get("view") as VerifyView

    @Test
    fun valid() {
        doReturn(signature).whenever(client).getSignature("c-1", hash)

        val result = mvc.perform(get("/verify/c-1/$hash")).andReturn()

        assertEquals(HttpStatus.OK.value(), result.response.status)
        assertEquals("verify", result.modelAndView?.viewName)
        val view = view(result)
        assertTrue(view.valid)
        assertEquals("MNDT-2026-000042", view.contractNumber)
        assertEquals("Ray Sponsible", view.signerName)
        assertEquals(FORMATTER.date(signedAt) + " à " + FORMATTER.time(signedAt), view.signedAt)
        assertEquals(hash, view.signatureHash)
    }

    @Test
    fun `hash is normalized`() {
        doReturn(signature).whenever(client).getSignature("c-1", hash)

        val result = mvc.perform(get("/verify/{id}/{hash}", "c-1", " ${hash.uppercase()} ")).andReturn()

        verify(client).getSignature("c-1", hash)
        val view = view(result)
        assertTrue(view.valid)
        assertEquals(hash, view.signatureHash)
    }

    @Test
    fun `not found`() {
        doReturn(null).whenever(client).getSignature("c-1", hash)

        val result = mvc.perform(get("/verify/c-1/$hash")).andReturn()

        assertEquals(HttpStatus.OK.value(), result.response.status)
        assertEquals(VerifyView(valid = false), view(result))
        assertNull(view(result).signatureHash)
    }

    @Test
    fun `malformed hash`() {
        val result = mvc.perform(get("/verify/c-1/xyz")).andReturn()

        assertEquals(VerifyView(valid = false), view(result))
        verifyNoInteractions(client)
    }

    @Test
    fun `server error is not invalid`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(client).getSignature("c-1", hash)

        assertFailsWith<ServletException> {
            mvc.perform(get("/verify/c-1/$hash"))
        }
    }
}
