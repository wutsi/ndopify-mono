package com.wutsi.ndopify.match.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.match.server.service.CoBrokerageInterestDigest
import com.wutsi.ndopify.match.server.service.InterestedAgent
import com.wutsi.ndopify.match.server.service.ListingInterest
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import org.springframework.stereotype.Service

/**
 * Tells a listing agent over Telegram that OTHER agents' searches showed interest in its listings: one French digest
 * grouped exactly as the e-mail's ([CoBrokerageInterestDigest], best score first, at most the digest's agents per
 * listing, the others counted), with one `Voir le mandat — <title>` button per listing, each on its own row.
 *
 * One message per run while the digest fits Telegram's [MAX_LENGTH]; over it, split at listing-group boundaries, and a
 * single listing over it at agent-row boundaries with its header repeated: a row is never cut nor lost. The digest
 * never carries the search customer. It names the interested agents without their contact: the mandate page's
 * proposal card carries it. [send]'s recipient is the agent's chat from #135's link
 * (`AgentTelegramService.chatId`), never its e-mail; Telegram refusing throws
 * ([com.wutsi.ndopify.platform.telegram.TelegramException]), and the notifier then falls back to the e-mail.
 */
@Service
class CoBrokerageInterestBotlet(private val client: TelegramClient) {
    companion object {
        const val MAX_LENGTH = 4096
        const val BUTTON_MAX_LENGTH = 64
        private const val BUTTON_PREFIX = "Voir le mandat — "
        private const val PLAIN_BUTTON = "Voir le mandat"
    }

    private data class Message(val text: String, val buttons: List<TelegramButton>)

    /** Sends one or more messages to [chatId]; nothing for a digest without a listing. */
    fun send(agent: AgentEntity, digest: CoBrokerageInterestDigest, chatId: Long) {
        messages(agent, digest).forEach { message ->
            client.sendMessageRows(chatId, message.text, message.buttons.map { button -> listOf(button) })
        }
    }

    private fun messages(agent: AgentEntity, digest: CoBrokerageInterestDigest): List<Message> {
        if (digest.listings.isEmpty()) {
            return emptyList()
        }

        val messages = mutableListOf<Message>()
        var buffer = StringBuilder(lead(agent, digest))
        var buttons = mutableListOf<TelegramButton>()

        fun flush() {
            val text = buffer.toString().trimEnd()
            if (text.isNotEmpty()) {
                messages.add(Message(text, buttons))
            }
            buffer = StringBuilder()
            buttons = mutableListOf()
        }

        digest.listings.forEach { listing ->
            val button = button(listing)
            val header = header(listing)
            val rows = listing.agents.map { interested -> row(interested) }
            val more = more(listing)?.let { "$it\n" } ?: ""
            val block = header + rows.joinToString("") + more + "\n"

            if (buffer.length + block.length <= MAX_LENGTH) {
                buffer.append(block)
                button?.let { buttons.add(it) }
            } else if (block.length <= MAX_LENGTH) {
                flush()
                buffer.append(block)
                button?.let { buttons.add(it) }
            } else {
                // A listing alone over the limit: split it at its rows, the header repeated on each part
                val lead = buffer.toString()
                val prefix = if (messages.isEmpty() && buttons.isEmpty() && lead.length + header.length <= MAX_LENGTH) {
                    buffer = StringBuilder()
                    lead
                } else {
                    flush()
                    ""
                }
                val parts = parts(prefix, header, rows, more)
                parts.dropLast(1).forEachIndexed { index, part ->
                    messages.add(Message(part.trimEnd(), if (index == 0) listOfNotNull(button) else emptyList()))
                }
                buffer.append(parts.last())
            }
        }

        val tail = tail(digest)
        if (buffer.length + tail.length <= MAX_LENGTH) {
            buffer.append(tail)
        } else {
            flush()
            buffer.append(tail)
        }
        flush()

        return messages
    }

    /** The parts of one listing block: its header then its rows, never splitting a row. */
    private fun parts(prefix: String, header: String, rows: List<String>, more: String): List<String> {
        val parts = mutableListOf<String>()
        var part = StringBuilder(prefix).append(header)
        var hasRow = prefix.isNotEmpty()
        rows.forEach { row ->
            if (part.length + row.length > MAX_LENGTH && hasRow) {
                parts.add(part.toString())
                part = StringBuilder(header)
                hasRow = false
            }
            part.append(row)
            hasRow = true
        }
        if (more.isNotEmpty() && part.length + more.length > MAX_LENGTH && hasRow) {
            parts.add(part.toString())
            part = StringBuilder(header)
        }
        part.append(more)
        parts.add(part.toString())
        return parts
    }

    private fun lead(agent: AgentEntity, digest: CoBrokerageInterestDigest): String {
        val count = if (digest.agentCount == 1) {
            "1 agent s'intéresse à vos annonces :"
        } else {
            "${digest.agentCount} agents s'intéressent à vos annonces :"
        }
        return "Bonjour ${agent.party.fullName()},\n\n$count\n\n"
    }

    private fun header(listing: ListingInterest): String {
        val title = listing.title ?: "Bien n° ${listing.listingId}"
        val price = if (listing.place.isBlank()) listing.price else "${listing.price} — ${listing.place}"
        return "$title\n$price\n\n"
    }

    private fun row(agent: InterestedAgent): String =
        "• ${agent.fullName}\n  ${criteria(agent)}\n"

    // Ex: "Location - Appartement à Bastos (Yaounde) - 2 pièces - 100 000 FCFA / mois - 2 recherches"
    private fun criteria(agent: InterestedAgent): String = buildString {
        append(agent.listingType)
        agent.propertyType?.let { propertyType -> append(" - $propertyType") }
        if (agent.place.isNotBlank()) {
            append(" à ${agent.place}")
        }
        agent.roomsLabel?.let { rooms -> append(" - $rooms") }
        append(" - ${agent.budget}")
        agent.searchCount?.let { count -> append(" - $count recherches") }
    }

    private fun more(listing: ListingInterest): String? = listing.more?.let { count ->
        if (count == 1) "et 1 autre agent" else "et $count autres agents"
    }

    private fun tail(digest: CoBrokerageInterestDigest): String =
        "Retrouvez les coordonnées de chaque agent dans le mandat.\n\nCordialement,\nL'équipe ${digest.tenant.name}"

    private fun button(listing: ListingInterest): TelegramButton? {
        val url = listing.mandateUrl ?: return null
        val title = listing.title ?: return TelegramButton(PLAIN_BUTTON, url = url)
        val room = BUTTON_MAX_LENGTH - BUTTON_PREFIX.length
        val label = BUTTON_PREFIX + if (title.length <= room) title else title.take(room - 1) + "…"
        return TelegramButton(label, url = url)
    }
}
