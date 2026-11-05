package com.wutsi.ndopify.npc.ui.visit

import com.wutsi.ndopify.match.dto.MatchRejectionReason
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.FORMATTER
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.NOW
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.TENANT
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.checkedIn
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.completed
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.confirmed
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.date
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.requested
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.window
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VisitRulesTest {
    private val minute = 60_000L
    private val hour = 60 * minute

    @Test
    fun `panel boundaries`() {
        val visit = confirmed()
        val at = visit.scheduledAt!!.time
        assertEquals(VisitRules.Panel.WINDOW, VisitRules.panel(visit, at - 3600_000))
        assertEquals(VisitRules.Panel.CONFIRMED, VisitRules.panel(visit, at - 3600_001))
        assertEquals(VisitRules.Panel.WINDOW, VisitRules.panel(visit, at + 4 * 3600_000))
        assertEquals(VisitRules.Panel.CONFIRMED, VisitRules.panel(visit, at + 4 * 3600_000 + 1))
    }

    @Test
    fun `panel - every status`() {
        assertEquals(VisitRules.Panel.REQUESTED, VisitRules.panel(requested(), NOW))
        assertEquals(VisitRules.Panel.CHECKED_IN, VisitRules.panel(checkedIn(), NOW))
        assertEquals(VisitRules.Panel.COMPLETED, VisitRules.panel(completed(), NOW))
        assertEquals(VisitRules.Panel.CANCELLED, VisitRules.panel(requested().copy(status = VisitStatus.CANCELLED), NOW))
        assertEquals(VisitRules.Panel.EXPIRED, VisitRules.panel(requested().copy(status = VisitStatus.EXPIRED), NOW))
        assertEquals(VisitRules.Panel.NO_SHOW, VisitRules.panel(requested().copy(status = VisitStatus.NO_SHOW), NOW))
        assertEquals(VisitRules.Panel.UNKNOWN, VisitRules.panel(requested().copy(status = VisitStatus.UNKNOWN), NOW))
        assertEquals(VisitRules.Panel.CONFIRMED, VisitRules.panel(confirmed().copy(scheduledAt = null), NOW))
    }

    @Test
    fun `tags`() {
        assertEquals(
            listOf(
                VisitRules.Tag("À confirmer", "warning"),
                VisitRules.Tag("Confirmée", "success"),
                VisitRules.Tag("À démarrer", "info"),
                VisitRules.Tag("En cours", "info"),
                VisitRules.Tag("Terminée", "success"),
                VisitRules.Tag("Annulée", "neutral"),
                VisitRules.Tag("Expirée", "neutral"),
                VisitRules.Tag("Absence", "warning"),
                VisitRules.Tag("Visite", "neutral"),
            ),
            listOf(
                VisitRules.Panel.REQUESTED,
                VisitRules.Panel.CONFIRMED,
                VisitRules.Panel.WINDOW,
                VisitRules.Panel.CHECKED_IN,
                VisitRules.Panel.COMPLETED,
                VisitRules.Panel.CANCELLED,
                VisitRules.Panel.EXPIRED,
                VisitRules.Panel.NO_SHOW,
                VisitRules.Panel.UNKNOWN,
            ).map { VisitRules.tag(it) },
        )
    }

    @Test
    fun `type labels`() {
        assertEquals("Sur place", VisitRules.typeLabel(VisitType.ON_SITE))
        assertEquals("Visite vidéo", VisitRules.typeLabel(VisitType.VIDEO))
        assertEquals("Visite", VisitRules.typeLabel(VisitType.UNKNOWN))
    }

    @Test
    fun `next step - requested`() {
        assertEquals("Confirmez la date avec le client.", VisitRules.nextStep(requested(), NOW))
    }

    @Test
    fun `next step - confirmed`() {
        assertEquals("Le jour J, demandez son code au client.", VisitRules.nextStep(confirmed(), NOW))
        assertEquals(
            "Appelez le client en vidéo, depuis le bien.",
            VisitRules.nextStep(confirmed().copy(type = VisitType.VIDEO), NOW),
        )
    }

    @Test
    fun `next step - window`() {
        // window() is scheduled 30 min before NOW: the no-show is open
        assertEquals("Démarrez avec son code, ou déclarez son absence.", VisitRules.nextStep(window(), NOW))
        assertEquals(
            "Le client ne répond pas ? Déclarez son absence.",
            VisitRules.nextStep(window().copy(type = VisitType.VIDEO), NOW),
        )

        // 5 min before the slot, the no-show is not open yet
        val early = window().copy(scheduledAt = date("2026-11-05T09:55:00Z"))
        assertEquals("Démarrez la visite avec le code du client.", VisitRules.nextStep(early, NOW))
        assertEquals(
            "Appelez le client, puis démarrez avec son code.",
            VisitRules.nextStep(early.copy(type = VisitType.VIDEO), NOW),
        )
    }

    @Test
    fun `next step - checked in`() {
        assertEquals("Terminez la visite avec votre compte rendu.", VisitRules.nextStep(checkedIn(), NOW))
        assertEquals(
            "Faites visiter le bien, puis terminez la visite.",
            VisitRules.nextStep(checkedIn().copy(checkedInAt = date("2026-11-05T09:55:00Z")), NOW),
        )
    }

    @Test
    fun `next step - closed`() {
        listOf(VisitStatus.COMPLETED, VisitStatus.CANCELLED, VisitStatus.EXPIRED, VisitStatus.NO_SHOW).forEach {
            assertNull(VisitRules.nextStep(requested().copy(status = it), NOW))
        }
    }

    @Test
    fun `expires`() {
        val visit = requested()
        assertEquals(date("2026-11-07T04:00:00Z").time, VisitRules.expiresAt(visit))
        assertEquals("42 h", VisitRules.expiresIn(VisitRules.expiresAt(visit) - NOW))
        assertEquals("45 min", VisitRules.expiresIn(45 * minute))
        assertEquals("1 h", VisitRules.expiresIn(hour))
        assertEquals("1 min", VisitRules.expiresIn(0))
        assertEquals("1 min", VisitRules.expiresIn(-5 * minute))
    }

    @Test
    fun `windows`() {
        assertEquals(date("2026-11-07T08:30:00Z").time, VisitRules.checkInFrom(confirmed()))
        assertEquals(date("2026-11-07T09:45:00Z").time, VisitRules.noShowFrom(confirmed()))
        assertEquals(date("2026-11-05T09:15:00Z").time, VisitRules.checkOutFrom(checkedIn()))
        assertNull(VisitRules.checkInFrom(requested()))
        assertNull(VisitRules.noShowFrom(requested()))
        assertNull(VisitRules.checkOutFrom(confirmed()))
    }

    @Test
    fun `can cancel`() {
        assertTrue(VisitRules.canCancel(requested()))
        assertTrue(VisitRules.canCancel(confirmed()))
        assertFalse(VisitRules.canCancel(checkedIn()))
        assertFalse(VisitRules.canCancel(completed()))
    }

    @Test
    fun `can check in`() {
        assertFalse(VisitRules.canCheckIn(confirmed(), NOW))
        assertTrue(VisitRules.canCheckIn(window(), NOW))
        assertTrue(VisitRules.canCheckIn(confirmed(), confirmed().scheduledAt!!.time - hour))
        assertFalse(VisitRules.canCheckIn(confirmed(), confirmed().scheduledAt!!.time - hour - 1))
    }

    @Test
    fun `can no show`() {
        assertTrue(VisitRules.canNoShow(window(), NOW))
        val at = window().scheduledAt!!.time + 15 * minute
        assertTrue(VisitRules.canNoShow(window(), at))
        assertFalse(VisitRules.canNoShow(window(), at - 1))
        assertFalse(VisitRules.canNoShow(confirmed(), NOW))
    }

    @Test
    fun `check out ready`() {
        assertTrue(VisitRules.checkOutReady(checkedIn(), NOW))
        val at = checkedIn().checkedInAt!!.time + 10 * minute
        assertTrue(VisitRules.checkOutReady(checkedIn(), at))
        assertFalse(VisitRules.checkOutReady(checkedIn(), at - 1))
        assertFalse(VisitRules.checkOutReady(confirmed(), NOW))
    }

    @Test
    fun `when label`() {
        val today = VisitSlots.at(LocalDate.of(2026, 11, 5), LocalTime.of(10, 30), TENANT)
        assertEquals("Aujourd'hui · 10:30", VisitRules.whenLabel(today, LocalDate.of(2026, 11, 5), TENANT, FORMATTER))

        val later = VisitSlots.at(LocalDate.of(2026, 11, 7), LocalTime.of(10, 30), TENANT)
        assertEquals(
            "sam. 7 novembre 2026 à 10:30",
            VisitRules.whenLabel(later, LocalDate.of(2026, 11, 5), TENANT, FORMATTER),
        )
    }

    @Test
    fun `duration`() {
        assertEquals("1 h 05", VisitRules.duration(date("2026-11-05T09:00:00Z"), date("2026-11-05T10:05:00Z")))
        assertEquals("25 min", VisitRules.duration(date("2026-11-05T09:00:00Z"), date("2026-11-05T09:25:00Z")))
        assertNull(VisitRules.duration(null, date("2026-11-05T09:25:00Z")))
        assertNull(VisitRules.duration(date("2026-11-05T09:25:00Z"), null))
    }

    @Test
    fun meters() {
        assertEquals("820 m", VisitRules.meters(820))
        assertEquals("1,2 km", VisitRules.meters(1_200))
        assertEquals("1,0 km", VisitRules.meters(1_000))
        assertNull(VisitRules.meters(null))
    }

    @Test
    fun `rejection labels`() {
        assertEquals("trop cher", VisitRules.REJECTION_LABELS[MatchRejectionReason.PRICE])
        assertEquals("le quartier ne convient pas", VisitRules.REJECTION_LABELS[MatchRejectionReason.LOCATION])
        assertEquals("trop petit ou trop grand", VisitRules.REJECTION_LABELS[MatchRejectionReason.SIZE])
        assertEquals("il manque des équipements", VisitRules.REJECTION_LABELS[MatchRejectionReason.AMENITIES])
        assertEquals("disponible trop tard", VisitRules.REJECTION_LABELS[MatchRejectionReason.AVAILABILITY])
        assertEquals("autre raison", VisitRules.REJECTION_LABELS[MatchRejectionReason.OTHER])
        assertNull(VisitRules.REJECTION_LABELS[MatchRejectionReason.UNKNOWN])
    }
}
