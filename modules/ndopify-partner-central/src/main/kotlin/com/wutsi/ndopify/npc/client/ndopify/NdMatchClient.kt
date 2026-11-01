package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.match.dto.GetMatchResponse
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.dto.SearchMatchResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client for the ndopify-server match endpoints (`/v1/matches`): the PUBLISHED listings matched to a signed search
 * mandate. Matches are tenant-scoped (`X-Tenant-ID`, see `RestConfiguration`). ndopify-server lets only the mandate's
 * agent (or an admin) read or act on them (the listing's agent for a search by listing), a 403 otherwise; the pages still check the match is of the mandate shown.
 */
@Service
class NdMatchClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,

    builder: RestClient.Builder,
) {
    private val rest: RestClient = builder
        .baseUrl(baseUrl)
        .build()

    /** The matches of [mandateIds] in [statuses], best score first. Query parameter names are `SearchMatchRequest`'s. */
    fun search(mandateIds: List<Long>, statuses: List<MatchStatus>, limit: Int = 100): List<Match> =
        rest.get()
            .uri { uri ->
                mandateIds.forEach { uri.queryParam("mandate-id", it) }
                statuses.forEach { uri.queryParam("status", it) }
                uri.path("/v1/matches")
                    .queryParam("limit", limit)
                    .build()
            }
            .retrieve()
            .body<SearchMatchResponse>()
            ?.matches
            ?: emptyList()

    /** The matches of the listings [listingIds] in [statuses] (ex: ACCEPTED, who they interest), for their agent only. */
    fun searchByListings(listingIds: List<Long>, statuses: List<MatchStatus>, limit: Int = 100): List<Match> =
        rest.get()
            .uri { uri ->
                listingIds.forEach { uri.queryParam("listing-id", it) }
                statuses.forEach { uri.queryParam("status", it) }
                uri.path("/v1/matches")
                    .queryParam("limit", limit)
                    .build()
            }
            .retrieve()
            .body<SearchMatchResponse>()
            ?.matches
            ?: emptyList()

    fun get(id: Long): Match =
        rest.get()
            .uri("/v1/matches/{id}", id)
            .retrieve()
            .body<GetMatchResponse>()
            ?.match
            ?: Match()

    /** E-mails the listing to the mandate's customer (PROPOSED). 409 once the match isn't ACTIVE. */
    fun propose(id: Long) {
        rest.post().uri("/v1/matches/{id}/propose", id).retrieve().toBodilessEntity()
    }

    /** Sets the listing aside: never matched again to this mandate (DISMISSED). 409 once the match isn't ACTIVE. */
    fun dismiss(id: Long) {
        rest.post().uri("/v1/matches/{id}/dismiss", id).retrieve().toBodilessEntity()
    }
}
