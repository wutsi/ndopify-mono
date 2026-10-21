package com.wutsi.ndopify.web.ui.agent

import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.web.ui.layout.PartnerCentralUrls
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

/**
 * The page for agents. Its calls to action open Partner Central's *Rejoindre Ndopify* wizard,
 * `{partnerCentralUrl}/join`; without a `partnerCentralUrl`, they're left out.
 *
 * The tenant is the one [com.wutsi.ndopify.web.ui.layout.TenantAdvice] put in the model, read from there rather than
 * taken as a `@ModelAttribute` parameter, which request parameters could bind.
 */
@Controller
class AgentController {
    companion object {
        const val VIEW = "agent"
    }

    @GetMapping("/agent")
    fun index(model: Model): String {
        model.addAttribute("joinUrl", PartnerCentralUrls.of(model.getAttribute("tenant") as Tenant, "/join"))
        return VIEW
    }
}
