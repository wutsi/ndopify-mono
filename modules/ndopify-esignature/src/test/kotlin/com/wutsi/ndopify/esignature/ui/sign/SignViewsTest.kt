package com.wutsi.ndopify.esignature.ui.sign

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.esignature.client.ContractContent
import com.wutsi.ndopify.esignature.service.TenantContext
import com.wutsi.ndopify.esignature.service.TenantFixtures.TENANT
import com.wutsi.ndopify.esignature.ui.sign.SignFixtures.AGENT
import com.wutsi.ndopify.esignature.ui.sign.SignFixtures.CONTENT
import com.wutsi.ndopify.esignature.ui.sign.SignFixtures.CONTENT_HASH
import com.wutsi.ndopify.esignature.ui.sign.SignFixtures.CONTENT_HTML
import com.wutsi.ndopify.esignature.ui.sign.SignFixtures.CONTENT_URL
import com.wutsi.ndopify.esignature.ui.sign.SignFixtures.CONTRACT
import com.wutsi.ndopify.esignature.ui.sign.SignFixtures.CONTRACT_PARTY_ID
import com.wutsi.ndopify.esignature.ui.sign.SignFixtures.CUSTOMER
import com.wutsi.ndopify.esignature.ui.sign.SignFixtures.NOW
import com.wutsi.ndopify.esignature.ui.sign.SignFixtures.signed
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignViewsTest {
    private val tenantContext = mock<TenantContext> {
        on { getTenant() } doReturn TENANT
    }
    private val views = SignViews(Clock.fixed(NOW, ZoneOffset.UTC), tenantContext)

    private val fetched = mutableListOf<String>()
    private val fetch: (String) -> ContractContent? = { url ->
        fetched.add(url)
        CONTENT
    }

    @Test
    fun `pending signature shows the contract to sign`() {
        val view = views.build(CONTRACT, CONTRACT_PARTY_ID, null, fetch)

        assertEquals(SignView.STEP_SIGN, view.step)
        assertNull(view.blocked)
        assertEquals("Mandat de recherche", view.contractType)
        assertEquals("MNDT-2026-000042", view.contractNumber)
        assertEquals("25 oct. 2026", view.deadline)
        assertEquals("Ray Sponsible", view.signerName)
        assertEquals("Ray", view.signerFirstName)
        assertEquals("18 oct. 2026", view.signDate)
        assertEquals(CONTENT_HTML, view.contentHtml)
        assertEquals(CONTENT_URL, view.contentUrl)
        assertEquals(CONTENT_HASH, view.contentHash)
        assertTrue(view.canSign)
        assertEquals(listOf(CONTENT_URL), fetched)
    }

    @Test
    fun `content that can't be fetched can't be signed`() {
        val view = views.build(CONTRACT, CONTRACT_PARTY_ID, null) { null }

        assertEquals(SignView.STEP_SIGN, view.step)
        assertNull(view.contentHtml)
        assertNull(view.contentUrl)
        assertNull(view.contentHash)
        assertFalse(view.canSign)
    }

    @Test
    fun `contract without content can't be signed`() {
        val view = views.build(CONTRACT.copy(contentUrl = null), CONTRACT_PARTY_ID, null, fetch)

        assertEquals(SignView.STEP_SIGN, view.step)
        assertFalse(view.canSign)
        assertTrue(fetched.isEmpty())
    }

    @Test
    fun `sender is agent party`() {
        val view = views.build(CONTRACT, CONTRACT_PARTY_ID, null, fetch)

        assertEquals("Marie Ngo Biyong", view.senderName)
    }

    @Test
    fun `no sender without agent party`() {
        val view = views.build(CONTRACT.copy(parties = listOf(CUSTOMER)), CONTRACT_PARTY_ID, null, fetch)

        assertNull(view.senderName)
    }

    @Test
    fun `no contract is not yours`() {
        val view = views.build(null, CONTRACT_PARTY_ID, null, fetch)

        assertEquals(SignView(step = SignView.STEP_BLOCKED, blocked = SignView.BLOCKED_NOT_YOURS), view)
        assertTrue(fetched.isEmpty())
    }

    @Test
    fun `unknown contract party is unavailable`() {
        val view = views.build(CONTRACT, "cp-other", null, fetch)

        assertBlocked(SignView.BLOCKED_UNAVAILABLE, view)
    }

    @Test
    fun `signed party is signed`() {
        val view = views.build(signed(), CONTRACT_PARTY_ID, null, fetch)

        assertBlocked(SignView.BLOCKED_SIGNED, view)
    }

    @Test
    fun `signed party sees done after signing`() {
        val contract = signed().copy(status = ContractStatus.SIGNED)

        val view = views.build(contract, CONTRACT_PARTY_ID, SignView.STEP_DONE, fetch)

        assertEquals(SignView.STEP_DONE, view.step)
        assertNull(view.blocked)
        assertEquals("MNDT-2026-000042", view.contractNumber)
        assertEquals("Ray", view.signerFirstName)
        assertEquals("18 oct. 2026 à 14:32", view.signedAt)
        assertNull(view.contentHtml)
        assertTrue(fetched.isEmpty())
    }

    @Test
    fun `done step of a party that hasn't signed is the sign step`() {
        val view = views.build(CONTRACT, CONTRACT_PARTY_ID, SignView.STEP_DONE, fetch)

        assertEquals(SignView.STEP_SIGN, view.step)
    }

    @Test
    fun `party whose signature isn't required is not required`() {
        val view = views.build(CONTRACT, AGENT.id, null, fetch)

        assertBlocked(SignView.BLOCKED_NOT_REQUIRED, view)
    }

    @Test
    fun `cancelled contract is cancelled`() {
        val view = views.build(CONTRACT.copy(status = ContractStatus.CANCELLED), CONTRACT_PARTY_ID, null, fetch)

        assertBlocked(SignView.BLOCKED_CANCELLED, view)
        assertEquals("Marie Ngo Biyong", view.senderName)
    }

    @Test
    fun `expired contract is expired`() {
        val view = views.build(CONTRACT.copy(status = ContractStatus.EXPIRED), CONTRACT_PARTY_ID, null, fetch)

        assertBlocked(SignView.BLOCKED_EXPIRED, view)
    }

    @Test
    fun `deadline past is expired`() {
        val contract = CONTRACT.copy(signatureDeadline = Date.from(Instant.parse("2026-10-18T13:31:59Z")))

        val view = views.build(contract, CONTRACT_PARTY_ID, null, fetch)

        assertBlocked(SignView.BLOCKED_EXPIRED, view)
    }

    @Test
    fun `contract not pending signature is unavailable`() {
        val view = views.build(CONTRACT.copy(status = ContractStatus.DRAFT), CONTRACT_PARTY_ID, null, fetch)

        assertBlocked(SignView.BLOCKED_UNAVAILABLE, view)
    }

    @Test
    fun intro() {
        assertEquals(SignView(step = SignView.STEP_INTRO), views.intro())
    }

    @Test
    fun code() {
        val pending = PendingSignature(CONTRACT_PARTY_ID, "ray.sponsible@gmail.com", NOW.toEpochMilli() + 300_000)

        val view = views.code(pending)

        assertEquals(SignView.STEP_CODE, view.step)
        assertEquals("ra•••@gmail.com", view.maskedEmail)
        assertEquals("14:37", view.codeExpiresAt)
        assertNull(view.contractNumber)
    }

    @Test
    fun maskEmail() {
        assertEquals("ra•••@gmail.com", SignViews.maskEmail("ray@gmail.com"))
        assertEquals("r•••@gmail.com", SignViews.maskEmail("ry@gmail.com"))
        assertEquals("ray", SignViews.maskEmail("ray"))
    }

    // Blocked views never carry the contract's content, nor fetch it
    private fun assertBlocked(reason: String, view: SignView) {
        assertEquals(SignView.STEP_BLOCKED, view.step)
        assertEquals(reason, view.blocked)
        assertNull(view.contentHtml)
        assertFalse(view.canSign)
        assertTrue(fetched.isEmpty())
    }
}
