package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.service.TenantFixtures
import com.wutsi.ndopify.visit.dto.VisitType
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MandateVisitRulesTest {
    private val match = Match(id = 9001, mandateId = 201, listingId = 3012, status = MatchStatus.ACCEPTED)
    private val matches = listOf(match, match.copy(id = 9002, listingId = 3027))

    @Test
    fun `a valid form has no error, and books the slot in the tenant's zone`() {
        val form = MandateVisitForm(matchId = "9001", date = "2026-11-21", time = "10:30", type = "VIDEO")

        assertEquals(emptyMap(), MandateVisitRules.errors(form, matches))

        val request = MandateVisitRules.request(form, TenantFixtures.TENANT)
        assertEquals(VisitType.VIDEO, request.type)
        assertEquals(
            Date.from(LocalDateTime.of(2026, 11, 21, 10, 30).atZone(ZoneId.of("Africa/Douala")).toInstant()),
            request.scheduledAt,
        )
    }

    @Test
    fun `a match that isn't one of the schedulable ones`() {
        val form = MandateVisitForm(matchId = "9999", date = "2026-11-21", time = "10:30", type = "ON_SITE")

        assertTrue(MandateVisitRules.errors(form, matches).containsKey("matchId"))
    }

    @Test
    fun `a missing or malformed match id`() {
        listOf(null, "", "abc").forEach { matchId ->
            val form = MandateVisitForm(matchId = matchId, date = "2026-11-21", time = "10:30", type = "ON_SITE")

            assertTrue(MandateVisitRules.errors(form, matches).containsKey("matchId"), "match id $matchId")
        }
    }

    @Test
    fun `a missing or malformed date`() {
        listOf(null, "", "2026-13-01", "demain").forEach { date ->
            val form = MandateVisitForm(matchId = "9001", date = date, time = "10:30", type = "ON_SITE")

            assertTrue(MandateVisitRules.errors(form, matches).containsKey("date"), "date $date")
        }
    }

    @Test
    fun `a missing or malformed time`() {
        listOf(null, "", "25:00", "10h30").forEach { time ->
            val form = MandateVisitForm(matchId = "9001", date = "2026-11-21", time = time, type = "ON_SITE")

            assertTrue(MandateVisitRules.errors(form, matches).containsKey("time"), "time $time")
        }
    }

    @Test
    fun `a missing or unknown type`() {
        listOf(null, "", "UNKNOWN", "PIGEON").forEach { type ->
            val form = MandateVisitForm(matchId = "9001", date = "2026-11-21", time = "10:30", type = type)

            assertTrue(MandateVisitRules.errors(form, matches).containsKey("type"), "type $type")
        }
    }

    @Test
    fun `a valid form has no error with the second property either`() {
        val form = MandateVisitForm(matchId = "9002", date = "2026-11-21", time = "08:00", type = "ON_SITE")

        assertEquals(emptyMap(), MandateVisitRules.errors(form, matches))
    }
}
