package com.wutsi.ndopify.web.ui.owner

import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.web.ui.layout.PartnerCentralUrls
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

/**
 * The pages for property owners. `/owner` is the letting side: its calls to action open Partner Central's offer-property
 * page, `{partnerCentralUrl}/offer-property`. `/owner/sell` is the sale side: they open it in its sale branch,
 * `{partnerCentralUrl}/offer-property?type=SALE`. Without a `partnerCentralUrl`, they're left out.
 *
 * The tenant is the one [com.wutsi.ndopify.web.ui.layout.TenantAdvice] put in the model, read from there rather than
 * taken as a `@ModelAttribute` parameter, which request parameters could bind.
 */
@Controller
class OwnerController {
    companion object {
        const val VIEW = "owner"

        /** The seller's page, at `/owner/sell`; `/owner` stays the rental owners' page. */
        const val SELL_VIEW = "owner/sell"
    }

    @GetMapping("/owner")
    fun index(model: Model): String {
        model.addAttribute("offerPropertyUrl", PartnerCentralUrls.of(model.getAttribute("tenant") as Tenant, "/offer-property"))
        return VIEW
    }

    @GetMapping("/owner/sell")
    fun sell(model: Model): String {
        model.addAttribute(
            "offerPropertyUrl",
            PartnerCentralUrls.of(model.getAttribute("tenant") as Tenant, "/offer-property?type=SALE"),
        )
        return SELL_VIEW
    }
}
