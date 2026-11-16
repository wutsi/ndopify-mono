package com.wutsi.ndopify.match.server.bot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeast
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.match.server.service.CoBrokerageInterestDigest
import com.wutsi.ndopify.match.server.service.InterestedAgent
import com.wutsi.ndopify.match.server.service.ListingInterest
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoBrokerageInterestBotletTest {
    private val client = mock<TelegramClient>()
    private val botlet = CoBrokerageInterestBotlet(client)
    private val textCaptor = argumentCaptor<String>()
    private val rowsCaptor = argumentCaptor<List<List<TelegramButton>>>()

    private val tenant = TenantEntity(id = 1L, name = "Ndopify")
    private val listingAgent = AgentEntity(
        id = 30L,
        party = PartyEntity(id = 300L, firstName = "John", lastName = "Doe", email = "john.doe@gmail.com"),
    )
    private val kim = InterestedAgent(
        fullName = "Kim Possible",
        whatsapp = "+237 6 99 00 00 01",
        whatsappUrl = "https://wa.me/237699000001",
        email = null,
        emailUrl = null,
        photoUrl = null,
        listingType = "Location",
        propertyType = "Appartement",
        place = "Bastos (Yaounde)",
        roomsLabel = "2 pièces",
        budget = "100 000 FCFA / mois",
        searchCount = null,
    )
    private val kimByEmail = kim.copy(
        whatsapp = null,
        whatsappUrl = null,
        email = "kim.possible@example.com",
        emailUrl = "mailto:kim.possible@example.com",
    )

    @Test
    fun `one listing - text and one-row button`() {
        send(digest(listOf(listing())))

        val (text, rows) = sent().single()
        assertTrue(text.startsWith("Bonjour John Doe,"))
        assertTrue(text.contains("1 agent s'intéresse à vos annonces :"))
        assertTrue(text.contains("Villa Bastos"))
        assertTrue(text.contains("90 000 FCFA / mois — Bastos (Yaounde)"))
        assertTrue(text.contains("• Kim Possible\n"))
        assertFalse(text.contains("WhatsApp"))
        assertTrue(text.contains("Location - Appartement à Bastos (Yaounde) - 2 pièces - 100 000 FCFA / mois"))
        assertTrue(
            text.endsWith(
                "Retrouvez les coordonnées de chaque agent dans le mandat.\n\nCordialement,\nL'équipe Ndopify",
            ),
        )
        assertEquals(
            listOf(listOf(TelegramButton("Voir le mandat — Villa Bastos", url = "https://pc.ndopify.com/mandates/11"))),
            rows,
        )
    }

    @Test
    fun `several listings - one message with one row per button`() {
        send(
            digest(
                listOf(
                    listing(),
                    listing(id = 102L, title = "Studio Mvan", mandateUrl = "https://pc.ndopify.com/mandates/12"),
                ),
            ),
        )

        val (text, rows) = sent().single()
        assertTrue(text.contains("Villa Bastos"))
        assertTrue(text.contains("Studio Mvan"))
        assertEquals(2, rows.size)
        assertEquals("Voir le mandat — Villa Bastos", rows[0].single().text)
        assertEquals("Voir le mandat — Studio Mvan", rows[1].single().text)
    }

    @Test
    fun `long title - the button label is at most 64 characters`() {
        val title = "Villa avec vue sur le fleuve et piscine chauffée au coeur de Bastos, Yaoundé, Cameroun"

        send(digest(listOf(listing(title = title))))

        val label = sent().single().second.single().single().text
        assertTrue(label.length <= 64, "label is ${label.length} characters")
        assertTrue(label.endsWith("…"))
        assertTrue(label.startsWith("Voir le mandat — "))
    }

    @Test
    fun `no title - Bien number and a plain button`() {
        send(digest(listOf(listing(title = null))))

        val (text, rows) = sent().single()
        assertTrue(text.contains("Bien n° 101"))
        assertEquals("Voir le mandat", rows.single().single().text)
    }

    @Test
    fun `no partner portal - no button`() {
        send(digest(listOf(listing(mandateUrl = null))))

        val (text, rows) = sent().single()
        assertTrue(text.contains("Villa Bastos"))
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `more than 5 agents - et 1 autre agent`() {
        val agents = (1..5).map { i -> kim.copy(fullName = "Kim Possible $i") }
        send(digest(listOf(listing(agents = agents, more = 1)), agentCount = 6))

        val (text, _) = sent().single()
        assertTrue(text.contains("6 agents s'intéressent à vos annonces :"))
        assertTrue(text.contains("et 1 autre agent"))
        assertEquals(5, text.lines().count { line -> line.startsWith("•") })
    }

    @Test
    fun `an agent without contact keeps its name and criteria`() {
        send(digest(listOf(listing(agents = listOf(kim)))))

        val (text, _) = sent().single()
        assertTrue(text.contains("• Kim Possible\n"))
        assertTrue(text.contains("Location - Appartement à Bastos (Yaounde)"))
    }

    @Test
    fun `an agent contacted by e-mail leaks no contact either`() {
        send(digest(listOf(listing(agents = listOf(kimByEmail)))))

        val (text, _) = sent().single()
        assertTrue(text.contains("• Kim Possible\n"))
        assertFalse(text.contains("kim.possible@example.com"))
        assertFalse(text.contains("mailto:"))
    }

    @Test
    fun `over 4096 - split at listing boundaries`() {
        val listings = (1..12).map { i ->
            val suffix = String.format("%02d", i)
            listing(
                id = 100L + i,
                title = "Annonce $suffix",
                mandateUrl = "https://pc.ndopify.com/mandates/$i",
                agents = (1..5).map { a ->
                    kim.copy(fullName = "Agent $i-$a", budget = "10$i 000 FCFA / mois")
                },
            )
        }

        send(digest(listings))

        val messages = sent()
        assertTrue(messages.size > 1)
        messages.forEach { (text, _) -> assertTrue(text.length <= 4096, "a message has ${text.length} characters") }

        val all = messages.joinToString("\n") { (text, _) -> text }
        listings.forEach { listing ->
            assertEquals(1, Regex(Regex.escape(listing.title!!)).findAll(all).count(), "title of ${listing.title}")
        }
        (1..12).forEach { i ->
            (1..5).forEach { a ->
                assertEquals(1, all.lines().count { line -> line.startsWith("• Agent $i-$a") }, "row of $i-$a")
            }
        }
        // Each message carries only its own listings' buttons, each on its own row
        messages.forEach { (text, rows) ->
            rows.forEach { row ->
                assertEquals(1, row.size)
                val title = row.single().text.removePrefix("Voir le mandat — ")
                assertTrue(text.contains(title), "the message of $title doesn't hold its group")
            }
        }
    }

    @Test
    fun `one listing over 4096 - split by rows, header repeated`() {
        val title = "V".repeat(3900)

        send(digest(listOf(listing(title = title, agents = (1..5).map { i -> kim.copy(fullName = "Agent $i") }, more = 1))))

        val messages = sent()
        assertTrue(messages.size >= 2)
        messages.forEach { (text, _) ->
            assertTrue(text.length <= 4096, "a message has ${text.length} characters")
        }
        val groups = messages.filter { (text, _) -> text.contains("•") }
        assertTrue(groups.size >= 2)
        groups.forEach { (text, _) ->
            assertTrue(text.contains(title))
            assertTrue(text.contains("90 000 FCFA / mois — Bastos (Yaounde)"))
        }
        val all = messages.joinToString("\n") { (text, _) -> text }
        (1..5).forEach { i ->
            assertEquals(1, all.lines().count { line -> line.startsWith("• Agent $i") }, "row of $i")
        }
        assertEquals(1, messages.first().second.size)
        assertEquals(0, messages.drop(1).sumOf { (_, rows) -> rows.size })
        val lastGroup = messages.single { (text, _) -> text.contains("• Agent 5") }
        assertTrue(lastGroup.first.contains("et 1 autre agent"))
    }

    @Test
    fun `empty digest - nothing sent`() {
        send(CoBrokerageInterestDigest(tenant, 0, emptyList()))

        verify(client, never()).sendMessageRows(any(), any(), any())
    }

    private fun send(digest: CoBrokerageInterestDigest) = botlet.send(listingAgent, digest, 777L)

    private fun digest(listings: List<ListingInterest>, agentCount: Int = 1): CoBrokerageInterestDigest =
        CoBrokerageInterestDigest(tenant, agentCount, listings)

    private fun listing(
        id: Long = 101L,
        title: String? = "Villa Bastos",
        mandateUrl: String? = "https://pc.ndopify.com/mandates/11",
        agents: List<InterestedAgent> = listOf(kim),
        more: Int? = null,
    ): ListingInterest = ListingInterest(
        listingId = id,
        title = title,
        price = "90 000 FCFA / mois",
        place = "Bastos (Yaounde)",
        imageUrl = null,
        mandateUrl = mandateUrl,
        agents = agents,
        more = more,
    )

    private fun sent(): List<Pair<String, List<List<TelegramButton>>>> {
        verify(client, atLeast(1)).sendMessageRows(eq(777L), textCaptor.capture(), rowsCaptor.capture())
        return textCaptor.allValues.zip(rowsCaptor.allValues)
    }
}
