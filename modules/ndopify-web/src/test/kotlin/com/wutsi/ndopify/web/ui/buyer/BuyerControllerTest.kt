package com.wutsi.ndopify.web.ui.buyer

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.web.core.service.TenantContext
import com.wutsi.ndopify.web.ui.layout.TenantAdvice
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.servlet.view.InternalResourceViewResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BuyerControllerTest {
    private fun page(context: TenantContext): MvcResult =
        MockMvcBuilders.standaloneSetup(BuyerController())
            .setControllerAdvice(TenantAdvice(context))
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
            .perform(get("/acheter"))
            .andReturn()

    private fun context(tenant: Tenant) = mock<TenantContext> { on { getTenant() } doReturn tenant }

    @Test
    fun `buyer page carries the sale find-property URL`() {
        val tenant = Tenant(id = 1, partnerCentralUrl = "https://partner.ndopify.cm/")

        val result = page(context(tenant))

        assertEquals("acheter", result.modelAndView?.viewName)
        assertEquals(tenant, result.modelAndView?.model?.get("tenant"))
        assertEquals("https://partner.ndopify.cm/find-property?type=SALE", result.modelAndView?.model?.get("findPropertyUrl"))
    }

    @Test
    fun `no URL without a partner central URL`() {
        assertNull(page(context(Tenant(id = 1, partnerCentralUrl = " "))).modelAndView?.model?.get("findPropertyUrl"))
        assertNull(page(context(Tenant(id = 1, partnerCentralUrl = null))).modelAndView?.model?.get("findPropertyUrl"))
    }
}
