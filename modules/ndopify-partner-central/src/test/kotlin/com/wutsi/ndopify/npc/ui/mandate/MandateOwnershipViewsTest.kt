package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.OwnershipLocation
import com.wutsi.ndopify.mandate.dto.OwnershipLocationStatus
import com.wutsi.ndopify.mandate.dto.OwnershipVerification
import com.wutsi.ndopify.mandate.dto.OwnershipVerificationReason
import com.wutsi.ndopify.mandate.dto.OwnershipVerificationStatus.FAILED
import com.wutsi.ndopify.mandate.dto.OwnershipVerificationStatus.PENDING
import com.wutsi.ndopify.mandate.dto.OwnershipVerificationStatus.VERIFIED
import com.wutsi.ndopify.npc.ui.document.DocumentFieldLabels
import org.springframework.context.support.ResourceBundleMessageSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MandateOwnershipViewsTest {
    private val labels = DocumentFieldLabels(
        ResourceBundleMessageSource().apply {
            setBasename("messages")
            setDefaultEncoding("UTF-8")
        },
    )

    private val mandate = Mandate(
        id = 301,
        status = MandateStatus.APPROVED,
        mandateType = MandateType.LISTING,
        listingType = ListingType.SALE,
    )

    @Test
    fun `pending names the missing pieces`() {
        val view = MandateOwnershipViews.section(
            mandate,
            OwnershipVerification(PENDING, listOf(reason("OWNERSHIP_ID_MISSING"), reason("OWNERSHIP_DOCUMENT_MISSING"))),
            listingId = null,
            labels = labels,
        )

        assertEquals("En cours", view.tagLabel)
        assertEquals("info", view.tagStyle)
        assertEquals(
            listOf(
                "Il manque la pièce d'identité du propriétaire : une carte nationale d'identité (recto et verso) " +
                    "ou un passeport, à son nom.",
                "Il manque le document de propriété : un titre foncier ou un certificat de propriété, à son nom.",
            ),
            view.missing,
        )
        assertEquals("Ajouter ces pièces dans Documents", view.documentsLabel)
        assertNull(view.waiting)
        assertTrue(view.reasons.isEmpty())
    }

    @Test
    fun `pending with no reason is in flight`() {
        val view = MandateOwnershipViews.section(mandate, OwnershipVerification(PENDING), null, labels)

        assertTrue(view.missing.isEmpty())
        assertEquals(
            "La lecture des pièces est en cours : la vérification se fera d'elle-même dès qu'elles seront analysées.",
            view.waiting,
        )
        assertEquals("Voir les documents", view.documentsLabel)
    }

    @Test
    fun `verified has no date and links the listing`() {
        val view = MandateOwnershipViews.section(mandate, OwnershipVerification(VERIFIED), 501, labels)

        assertEquals("Vérifié", view.tagLabel)
        assertEquals("success", view.tagStyle)
        assertEquals(501L, view.listingId)
        assertTrue(view.reasons.isEmpty())
        // The verdict has no timestamp: the copy carries no date.
        assertFalse(Regex("\\d{2}/\\d{2}/\\d{4}").containsMatchIn(view.toString()))
    }

    @Test
    fun `each failed reason names its check, its wording and its action`() {
        val view = MandateOwnershipViews.section(
            mandate,
            OwnershipVerification(
                FAILED,
                listOf(
                    reason("OWNERSHIP_ID_EXPIRED"),
                    reason("OWNERSHIP_DOCUMENT_UNUSABLE", listOf("DOCUMENT_TYPE_MISMATCH", "DOCUMENT_PAGE_MISSING")),
                ),
            ),
            null,
            labels,
        )

        assertEquals("Non vérifié", view.tagLabel)
        assertEquals("warning", view.tagStyle)
        assertEquals("La pièce d'identité", view.reasons[0].check)
        assertEquals("La pièce est expirée : elle ne peut plus servir à vérifier le propriétaire.", view.reasons[0].text)
        assertEquals("Le document de propriété", view.reasons[1].check)
        assertEquals(
            listOf(
                "Le document ne correspond pas au type choisi. Supprimez-le, puis ajoutez-le avec le bon type.",
                "Il manque une page du document, son verso par exemple. Remplacez la page manquante par une photo, " +
                    "ou supprimez le document et ajoutez-le avec toutes ses pages.",
            ),
            view.reasons[1].problems,
        )
    }

    @Test
    fun `an unknown code reads the generic line with no kind`() {
        val view = MandateOwnershipViews.section(
            mandate,
            OwnershipVerification(FAILED, listOf(reason("OWNERSHIP_NEW"))),
            null,
            labels,
        )

        assertNull(view.reasons[0].check)
        assertEquals("La vérification du propriétaire a échoué sur ce document.", view.reasons[0].text)
        assertTrue(view.reasons[0].problems.isEmpty())
    }

    @Test
    fun `an unavailable verdict has no tag and no reason`() {
        val nulled = MandateOwnershipViews.section(mandate, null, null, labels)
        val unknown = MandateOwnershipViews.section(mandate, OwnershipVerification(), null, labels)

        for (view in listOf(nulled, unknown)) {
            assertTrue(view.unavailable)
            assertNull(view.tagLabel)
            assertNull(view.tagStyle)
            assertTrue(view.reasons.isEmpty())
            assertTrue(view.missing.isEmpty())
        }
    }

    @Test
    fun `the intro names the owner`() {
        assertEquals(
            "Ndopify vérifie que le vendeur est bien le propriétaire du bien, à partir des pièces de la section " +
                "Documents.",
            MandateOwnershipViews.section(mandate, OwnershipVerification(VERIFIED), null, labels).intro,
        )
        assertEquals(
            "Ndopify vérifie que le bailleur est bien le propriétaire du bien, à partir des pièces de la section " +
                "Documents.",
            MandateOwnershipViews.section(
                mandate.copy(listingType = ListingType.RENTAL),
                OwnershipVerification(VERIFIED),
                null,
                labels,
            ).intro,
        )
    }

    @Test
    fun `an unresolved comparison adds the neutral hint`() {
        val view = section(
            OwnershipVerification(
                VERIFIED,
                location = OwnershipLocation("Yaounde", "Bonabéri", OwnershipLocationStatus.UNRESOLVED),
            ),
        )

        assertEquals(OwnershipPlacesView("Yaounde", "Bonabéri"), view.location!!.places)
        assertEquals(
            "La localité du titre n'a pas pu être rattachée à une ville connue : vérifiez que le titre et le mandat " +
                "désignent le même bien.",
            view.location!!.text,
        )
        assertEquals("Vérifier le titre dans Documents", view.location!!.action)
    }

    @Test
    fun `a blank place reads non precisée`() {
        val view = section(
            OwnershipVerification(
                VERIFIED,
                location = OwnershipLocation("", "Bonabéri", OwnershipLocationStatus.UNRESOLVED),
            ),
        )

        assertEquals("non précisée", view.location!!.places.mandateCity)
        assertEquals("Bonabéri", view.location!!.places.documentLocality)
    }

    @Test
    fun `both places blank shows no hint`() {
        val view = section(
            OwnershipVerification(VERIFIED, location = OwnershipLocation("", "", OwnershipLocationStatus.UNRESOLVED)),
        )

        assertNull(view.location)
    }

    @Test
    fun `a match and an unknown comparison add no hint`() {
        assertNull(
            section(
                OwnershipVerification(VERIFIED, location = OwnershipLocation("Yaounde", "Yaounde", OwnershipLocationStatus.MATCH)),
            ).location,
        )
        assertNull(section(OwnershipVerification(VERIFIED)).location)
    }

    @Test
    fun `a property mismatch shows the places in its reason`() {
        val view = section(
            OwnershipVerification(
                FAILED,
                listOf(reason("OWNERSHIP_PROPERTY_MISMATCH")),
                location = OwnershipLocation("Douala", "Yaoundé", OwnershipLocationStatus.MISMATCH),
            ),
        )

        assertEquals("Le document de propriété", view.reasons[0].check)
        assertEquals(OwnershipPlacesView("Douala", "Yaoundé"), view.reasons[0].places)
        assertEquals(
            "Le document désigne un bien dans une autre ville que celle du mandat : il ne justifie pas la propriété de " +
                "ce bien.",
            view.reasons[0].text,
        )
        assertEquals(
            "Vérifiez le document dans Documents : il doit désigner un bien situé dans la ville du mandat.",
            view.reasons[0].action,
        )
        assertNull(view.location)
    }

    private fun reason(code: String, details: List<String> = emptyList()) = OwnershipVerificationReason(code, details)

    private fun section(verification: OwnershipVerification) =
        MandateOwnershipViews.section(mandate, verification, null, labels)
}
