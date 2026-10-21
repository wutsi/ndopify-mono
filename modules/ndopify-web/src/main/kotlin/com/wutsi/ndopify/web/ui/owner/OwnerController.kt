package com.wutsi.ndopify.web.ui.owner

import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.web.ui.layout.PartnerCentralUrls
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

/**
 * The page for property owners. Its calls to action open Partner Central's offer-property page,
 * `{partnerCentralUrl}/offer-property`; without a `partnerCentralUrl`, they're left out.
 *
 * The tenant is the one [com.wutsi.ndopify.web.ui.layout.TenantAdvice] put in the model, read from there rather than
 * taken as a `@ModelAttribute` parameter, which request parameters could bind.
 */
@Controller
class OwnerController {
    companion object {
        const val VIEW = "owner"
    }

    @GetMapping("/owner")
    fun index(model: Model): String {
        model.addAttribute("offerPropertyUrl", PartnerCentralUrls.of(model.getAttribute("tenant") as Tenant, "/offer-property"))
        return VIEW
    }
}
