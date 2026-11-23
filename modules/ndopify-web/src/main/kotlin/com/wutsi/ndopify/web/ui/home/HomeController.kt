package com.wutsi.ndopify.web.ui.home

import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.web.ui.layout.PartnerCentralUrls
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

/**
 * The public site's root is the goal picker: *Quel est votre projet immobilier ?*, four cards linking the landing
 * pages of the four mandates (Louer, Acheter, Mettre en location, Vendre), nothing to browse.
 *
 * `/louer` is the renter's page, for people looking for a rental: its calls to action open Partner Central's *Trouver
 * un bien* wizard, `{partnerCentralUrl}/find-property`, and are left out without a `partnerCentralUrl`.
 *
 * The tenant is the one [com.wutsi.ndopify.web.ui.layout.TenantAdvice] put in the model, read from there rather than
 * taken as a `@ModelAttribute` parameter, which request parameters could bind.
 */
@Controller
class HomeController {
    companion object {
        const val VIEW = "index"

        /** Today's renter page, moved to `/louer` when `/` became the goal picker. */
        const val RENTER_VIEW = "louer"
    }

    @GetMapping("/")
    fun index(): String = VIEW

    @GetMapping("/louer")
    fun rent(model: Model): String {
        model.addAttribute("findPropertyUrl", PartnerCentralUrls.of(model.getAttribute("tenant") as Tenant, "/find-property"))
        return RENTER_VIEW
    }
}
