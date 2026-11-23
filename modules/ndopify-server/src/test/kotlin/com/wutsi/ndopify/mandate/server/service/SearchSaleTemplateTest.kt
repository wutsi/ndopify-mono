package com.wutsi.ndopify.mandate.server.service

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Renders the sale search mandate with the same MJML + mustache engines as the e-mails. */
class SearchSaleTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
    )

    private val required = mapOf(
        "contract_number" to "MNDT-2026-000001",
        "contract_date" to "17/10/2026",
        "duration_months" to "6",
        "client_name" to "Ray Sponsible",
        "tenant_name" to "Ndopify",
        "property_type" to "Appartement",
        "city" to "Yaoundé",
        "budget" to "10 000 000 XAF",
        "commission" to "5 %",
    )

    @Test
    fun `a purchase, its budget and its commission`() {
        val html = resolver.resolve("/mandate/contract/search_sale.mjml", required + mapOf("rooms" to "3 chambres"))

        assertTrue(html.contains("MANDAT DE RECHERCHE IMMOBILIÈRE (ACHAT)"))
        assertTrue(html.contains("rechercher un bien immobilier à acheter"))
        assertTrue(html.contains("10 000 000 XAF"))
        assertTrue(html.contains("5 % du prix de vente"))
        assertTrue(html.contains("signature de l'acte de vente"))
        assertFalse(html.contains("/ mois"))
        assertFalse(html.contains("1 mois de loyer"))
        assertFalse(html.contains("Caution"))
        assertFalse(html.contains("Durée du bail"))
        assertFalse(html.contains("Préavis"))
        assertFalse(html.contains("Emménagement"))
        assertFalse(html.contains("EXCLUSIVITÉ"))
        assertFalse(html.contains("Meublé"))
    }

    @Test
    fun `every optional criterion`() {
        val html = resolver.resolve(
            "/mandate/contract/search_sale.mjml",
            required + mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "client_phone" to "+237671234567",
                "client_email" to "ray.sponsible@gmail.com",
                "agent_name" to "Kim Possible",
                "property_category" to "Résidentiel",
                "rooms" to "2 chambres",
                "parkings" to "1 place de parking",
                "area" to "75",
                "neighbourhoods" to "Bastos, Mvan",
                "has_amenities" to true,
                "water" to "Compteur d'eau individuel, Forage",
                "electricity" to "Groupe électrogène",
                "road_access" to "Route bitumée",
                "security" to "Gardien, Vidéosurveillance",
                "has_visit_fee" to true,
                "visit_fee" to "5 000 XAF",
                "has_contact" to true,
                "preferred_contact_period" to "le matin",
                "comments" to "Proche du marché",
            ),
        )

        listOf(
            "MNDT-2026-000001",
            "Ray Sponsible",
            "Tél : +237671234567",
            "Email : ray.sponsible@gmail.com",
            "représenté par Kim Possible",
            "Résidentiel",
            "Appartement",
            "2 chambres",
            "1 place de parking",
            "75 m²",
            "Yaoundé",
            "Bastos, Mvan",
            "Forage",
            "Groupe électrogène",
            "Route bitumée",
            "Vidéosurveillance",
            "10 000 000 XAF",
            "5 % du prix de vente",
            "Aucun paiement n'est effectué via Ndopify.",
            "5 000 XAF par visite",
            "le matin",
            "Proche du marché",
            "6 mois, à compter de la date de sa",
        ).forEach { text -> assertTrue(html.contains(text), "missing: $text") }
    }
}
