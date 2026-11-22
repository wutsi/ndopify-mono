package com.wutsi.ndopify.web.ui.pwa

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.wutsi.ndopify.refdata.dto.Tenant
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The tenant-branded web manifest: the install name and the icons a tenant actually has.
 *
 * Icons are all-or-nothing: the tenant's variant set when it sets at least one, else its `iconUrl` alone (the browser
 * reads the intrinsic size), else Ndopify's bundled set. Never a guessed size and never a mix, or a browser could pick
 * Ndopify's 512 icon and mis-brand the tenant's install.
 */
@RestController
class ManifestController {
    @GetMapping("/manifest.json", produces = ["application/manifest+json"])
    fun manifest(model: Model): WebManifest {
        val tenant = model.getAttribute("tenant") as Tenant? ?: Tenant()
        val name = tenant.name.ifBlank { "Ndopify" }
        return WebManifest(
            name = name,
            shortName = name,
            icons = icons(tenant),
        )
    }

    private fun icons(tenant: Tenant): List<Icon> {
        val variants = listOfNotNull(
            tenant.icon192Url?.takeIf { it.isNotBlank() }?.let { Icon(src = it, sizes = "192x192", purpose = "any") },
            tenant.icon512Url?.takeIf { it.isNotBlank() }?.let { Icon(src = it, sizes = "512x512", purpose = "any") },
            tenant.maskableIconUrl?.takeIf { it.isNotBlank() }
                ?.let { Icon(src = it, sizes = "512x512", purpose = "maskable") },
        )
        if (variants.isNotEmpty()) {
            return variants
        }

        tenant.iconUrl?.takeIf { it.isNotBlank() }?.let {
            return listOf(Icon(src = it, purpose = "any"))
        }

        return listOf(
            Icon(src = "/assets/images/icon-192.png", sizes = "192x192", purpose = "any"),
            Icon(src = "/assets/images/icon-512.png", sizes = "512x512", purpose = "any"),
            Icon(src = "/assets/images/icon-maskable-512.png", sizes = "512x512", purpose = "maskable"),
        )
    }
}

data class WebManifest(
    val name: String,
    @get:JsonProperty("short_name")
    @field:JsonProperty("short_name")
    val shortName: String,
    val lang: String = "fr",
    @get:JsonProperty("start_url")
    @field:JsonProperty("start_url")
    val startUrl: String = "/",
    val scope: String = "/",
    val display: String = "standalone",
    @get:JsonProperty("theme_color")
    @field:JsonProperty("theme_color")
    val themeColor: String = "#1d7edf",
    @get:JsonProperty("background_color")
    @field:JsonProperty("background_color")
    val backgroundColor: String = "#ffffff",
    val icons: List<Icon> = emptyList(),
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Icon(
    val src: String,
    val sizes: String? = null,
    val type: String = "image/png",
    val purpose: String,
)
