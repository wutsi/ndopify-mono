package com.wutsi.ndopify.npc.ui.kyc

import com.wutsi.ndopify.party.dto.KycCase
import com.wutsi.ndopify.party.dto.KycImage
import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KycRulesTest {
    private val front = KycImage(id = "f", imageType = KycImageType.FRONT)
    private val back = KycImage(id = "b", imageType = KycImageType.BACK)

    @Test
    fun status() {
        assertNull(KycRules.status(null))
        assertNull(KycRules.status(KycCase(status = KycStatus.PENDING)))
        assertNull(KycRules.status(KycCase(status = KycStatus.CANCELLED)))
        assertEquals("attente", KycRules.status(KycCase(status = KycStatus.SUBMITTED)))
        assertEquals("attente", KycRules.status(KycCase(status = KycStatus.IN_PROGRESS)))
        assertEquals("attente", KycRules.status(KycCase(status = KycStatus.REQUIRES_MANUAL_REVIEW)))
        assertEquals("attente", KycRules.status(KycCase(status = KycStatus.UNKNOWN)))
        assertEquals("verifie", KycRules.status(KycCase(status = KycStatus.VERIFIED)))
        assertEquals("rejete", KycRules.status(KycCase(status = KycStatus.REJECTED)))
    }

    @Test
    fun `photos of a pending case only`() {
        val images = listOf(front, back)

        assertEquals(setOf(KycImageType.FRONT, KycImageType.BACK), KycRules.photos(KycCase(status = KycStatus.PENDING, images = images)).keys)
        assertTrue(KycRules.photos(KycCase(status = KycStatus.REJECTED, images = images)).isEmpty())
        assertTrue(KycRules.photos(null).isEmpty())
    }

    @Test
    fun firstIncompleteStep() {
        assertEquals("recto", KycRules.firstIncompleteStep(emptySet()))
        assertEquals("verso", KycRules.firstIncompleteStep(setOf(KycImageType.FRONT)))
        assertEquals("recto", KycRules.firstIncompleteStep(setOf(KycImageType.BACK, KycImageType.SELFIE)))
        assertEquals("selfie", KycRules.firstIncompleteStep(setOf(KycImageType.FRONT, KycImageType.BACK)))
        assertEquals("revue", KycRules.firstIncompleteStep(setOf(KycImageType.FRONT, KycImageType.BACK, KycImageType.SELFIE)))
    }

    @Test
    fun photo() {
        assertNull(KycRules.photo(1000, "image/jpeg"))
        assertNull(KycRules.photo(1000, "image/png"))
        assertNull(KycRules.photo(KycRules.MAX_PHOTO_BYTES, "image/webp"))
        assertEquals("Format non accepté. Utilisez une photo JPG, PNG ou WEBP.", KycRules.photo(1000, "application/pdf"))
        assertEquals("Format non accepté. Utilisez une photo JPG, PNG ou WEBP.", KycRules.photo(1000, null))
        assertEquals(
            "Cette photo pèse 6,2 Mo. La taille maximale est de 5 Mo.",
            KycRules.photo((6.2 * 1024 * 1024).toLong(), "image/jpeg"),
        )
    }

    @Test
    fun rejectionReason() {
        assertEquals(
            "votre pièce d'identité n'a pas pu être validée.",
            KycRules.rejectionReason(KycErrorCode.IDENTIFICATION_REJECTED),
        )
        assertEquals(
            "votre selfie ne correspond pas à la photo de votre pièce d'identité.",
            KycRules.rejectionReason(KycErrorCode.SELFIE_REJECTED),
        )
        assertEquals("vos documents n'ont pas pu être vérifiés.", KycRules.rejectionReason(KycErrorCode.EXPIRED))
        assertEquals("vos documents n'ont pas pu être vérifiés.", KycRules.rejectionReason(null))
        assertEquals("vos documents n'ont pas pu être vérifiés.", KycRules.rejectionReason("SOMETHING_NEW"))
    }

    @Test
    fun initials() {
        assertEquals("RS", KycRules.initials("ray", "Sponsible"))
        assertEquals("R", KycRules.initials("Ray", ""))
        assertEquals("NP", KycRules.initials(null, null))
    }
}
