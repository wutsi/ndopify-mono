package com.wutsi.ndopify.web.ui.home

import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.web.ui.layout.PartnerCentralUrls
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

/**
 * The home page, for people looking for a rental. Its calls to action open Partner Central's *Trouver un bien*
 * wizard, `{partnerCentralUrl}/find-property`; without a `partnerCentralUrl`, they're left out.
 *
 * The tenant is the one [com.wutsi.ndopify.web.ui.layout.TenantAdvice] put in the model, read from there rather than
 * taken as a `@ModelAttribute` parameter, which request parameters could bind.
 */
@Controller
class HomeController {
    companion object {
        const val VIEW = "index"
    }

    @GetMapping("/")
    fun index(model: Model): String {
        model.addAttribute("findPropertyUrl", PartnerCentralUrls.of(model.getAttribute("tenant") as Tenant, "/find-property"))
        return VIEW
    }
}
