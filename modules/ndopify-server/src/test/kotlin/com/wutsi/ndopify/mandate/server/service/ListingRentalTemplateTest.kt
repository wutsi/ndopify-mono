package com.wutsi.ndopify.mandate.server.service

import com.github.mustachejava.DefaultMustacheFactory
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Renders the listing mandate with the same MJML + mustache engines as the e-mails. */
class ListingRentalTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(),
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
        "budget" to "150 000 XAF / mois",
    )

    @Test
    fun `every field`() {
        val html = resolver.resolve(
            "/mandate/contract/listing.mjml",
            required + mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "client_phone" to "+237671234567",
                "client_email" to "ray.sponsible@gmail.com",
                "agent_name" to "Kim Possible",
                "property_category" to "Résidentiel",
                "furniture" to "Non",
                "rooms" to "2 chambres",
                "parkings" to "1 place de parking",
                "area" to "75",
                "neighbourhoods" to "Bastos",
                "has_amenities" to true,
                "water" to "Compteur d'eau individuel, Forage",
                "electricity" to "Groupe électrogène",
                "road_access" to "Route bitumée",
                "security" to "Gardien, Vidéosurveillance",
                "security_deposit" to "2 mois",
                "advance_rent" to "3 mois",
                "lease_term" to "12 mois",
                "notice_period" to "1 mois",
                "available_from" to "01/11/2026",
                "has_contact" to true,
                "preferred_contact_period" to "le matin",
                "comments" to "Portail bleu",
            ),
        )

        listOf(
            "MANDAT EXCLUSIF DE MISE EN LOCATION",
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
            "<b>Loyer demandé</b> : 150 000 XAF / mois",
            "<b>Caution</b> : 2 mois",
            "<b>Avance sur loyer</b> : 3 mois",
            "<b>Durée du bail</b> : 12 mois",
            "<b>Préavis</b> : 1 mois",
            "<b>Disponible à partir du</b> : 01/11/2026",
            "le matin",
            "Portail bleu",
            "6 mois, à compter de la date de sa",
            "s'interdit de louer le bien", // Exclusive
            "1 mois de loyer",
        ).forEach { text -> assertTrue(html.contains(text), "missing: $text") }
    }

    @Test
    fun `optional fields left out`() {
        val html = resolver.resolve("/mandate/contract/listing.mjml", required)

        assertTrue(html.contains("Appartement"))
        assertTrue(html.contains("150 000 XAF / mois"))
        listOf(
            "Tél :",
            "représenté par",
            "Catégorie",
            "Meublé",
            "Surface",
            "<b>Quartier</b>",
            "Les équipements du bien",
            "Caution",
            "Avance sur loyer",
            "Durée du bail",
            "Préavis",
            "Disponible à partir du",
            "Informations complémentaires",
        ).forEach { text -> assertFalse(html.contains(text), "unexpected: $text") }
    }
}
