package com.wutsi.ndopify.web.ui.home

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.web.service.TenantContext
import com.wutsi.ndopify.web.ui.layout.TenantAdvice
import jakarta.servlet.ServletException
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.servlet.view.InternalResourceViewResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeControllerTest {
    private fun index(context: TenantContext, path: String = "/"): MvcResult =
        MockMvcBuilders.standaloneSetup(HomeController())
            .setControllerAdvice(TenantAdvice(context))
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
            .perform(get(path))
            .andReturn()

    private fun context(tenant: Tenant) = mock<TenantContext> { on { getTenant() } doReturn tenant }

    @Test
    fun `find-property URL of the tenant's partner central`() {
        val tenant = Tenant(id = 1, partnerCentralUrl = "https://partner.ndopify.cm")

        val result = index(context(tenant))

        assertEquals(HomeController.VIEW, result.modelAndView?.viewName)
        assertEquals(tenant, result.modelAndView?.model?.get("tenant"))
        assertEquals("https://partner.ndopify.cm/find-property", result.modelAndView?.model?.get("findPropertyUrl"))
    }

    @Test
    fun `trailing slash of the partner central URL`() {
        val result = index(context(Tenant(id = 1, partnerCentralUrl = "https://partner.ndopify.cm/")))

        assertEquals("https://partner.ndopify.cm/find-property", result.modelAndView?.model?.get("findPropertyUrl"))
    }

    @Test
    fun `no find-property URL without partner central URL`() {
        assertNull(index(context(Tenant(id = 1, partnerCentralUrl = null))).modelAndView?.model?.get("findPropertyUrl"))
        assertNull(index(context(Tenant(id = 1, partnerCentralUrl = " "))).modelAndView?.model?.get("findPropertyUrl"))
    }

    @Test
    fun `fails without tenant`() {
        val context = mock<TenantContext> { on { getTenant() } doThrow IllegalStateException("No tenant") }

        val ex = assertFailsWith<ServletException> { index(context) }
        assertTrue(ex.cause is IllegalStateException)
    }
}
