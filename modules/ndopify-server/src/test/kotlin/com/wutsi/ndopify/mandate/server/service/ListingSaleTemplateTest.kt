package com.wutsi.ndopify.mandate.server.service

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Renders the sale listing mandate with the same MJML + mustache engines as the e-mails. */
class ListingSaleTemplateTest {
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
        "budget" to "15 000 000 XAF",
        "commission" to "5 %",
    )

    @Test
    fun `a sale, its asking price and its commission`() {
        val html = resolver.resolve("/mandate/contract/listing_sale.mjml", required + mapOf("area" to "75"))

        assertTrue(html.contains("MANDAT EXCLUSIF DE VENTE"))
        assertTrue(html.contains("rechercher un acquéreur"))
        assertTrue(html.contains("Prix de vente demandé"))
        assertTrue(html.contains("15 000 000 XAF"))
        assertTrue(html.contains("5 % du prix de vente"))
        assertTrue(html.contains("EXCLUSIVITÉ"))
        assertTrue(html.contains("s'interdit de vendre le bien"))
        assertFalse(html.contains("/ mois"))
        assertFalse(html.contains("1 mois de loyer"))
        assertFalse(html.contains("Caution"))
        assertFalse(html.contains("Avance sur loyer"))
        assertFalse(html.contains("Durée du bail"))
        assertFalse(html.contains("Préavis"))
        assertFalse(html.contains("Disponible à partir du"))
        assertFalse(html.contains("Meublé"))
    }

    @Test
    fun `every field`() {
        val html = resolver.resolve(
            "/mandate/contract/listing_sale.mjml",
            required + mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "client_phone" to "+237671234567",
                "client_email" to "ray.sponsible@gmail.com",
                "agent_name" to "Kim Possible",
                "property_category" to "Résidentiel",
                "rooms" to "2 chambres",
                "parkings" to "1 place de parking",
                "area" to "75",
                "neighbourhoods" to "Bastos",
                "has_amenities" to true,
                "water" to "Compteur d'eau individuel, Forage",
                "electricity" to "Groupe électrogène",
                "road_access" to "Route bitumée",
                "security" to "Gardien, Vidéosurveillance",
                "has_contact" to true,
                "preferred_contact_period" to "le matin",
                "comments" to "Portail bleu",
            ),
        )

        listOf(
            "MANDAT EXCLUSIF DE VENTE",
            "MNDT-2026-000001",
            "<b>Le Mandant (Propriétaire)</b> : Ray Sponsible",
            "+237671234567",
            "ray.sponsible@gmail.com",
            "représenté par Kim Possible",
            "Résidentiel",
            "Appartement",
            "2 chambres",
            "1 place de parking",
            "75 m²",
            "Yaoundé",
            "<b>Quartier</b> : Bastos",
            "Forage",
            "Groupe électrogène",
            "Route bitumée",
            "Vidéosurveillance",
            "<b>Prix de vente demandé</b> : 15 000 000 XAF",
            "5 % du prix de vente",
            "signature de l'acte de vente avec un acquéreur présenté par le Mandataire",
            "le matin",
            "Portail bleu",
            "6 mois, à compter de la date de sa",
            "s'interdit de vendre le bien",
            "<b>Visite d'évaluation</b>",
            "avant sa mise en vente",
            "à photographier l'intérieur et",
            "sur le site de Ndopify et ses supports de diffusion",
            "conditions de vente et localisation approximative",
            "L'adresse exacte",
            "n'est pas publiée",
            "qu'aux acquéreurs, pour les visites",
            "dès la vente du bien ou à la fin du mandat",
            "données à titre indicatif",
            "<b>Mise à jour des informations</b>",
            "sans qu'il soit nécessaire de signer un nouveau mandat",
            "du prix de vente ou des conditions de",
            "acquéreurs sérieux",
            "habilité à le vendre",
            "si la vente est conclue avec un acquéreur présenté par Ndopify",
        ).forEach { text -> assertTrue(html.contains(text), "missing: $text") }
    }
}
