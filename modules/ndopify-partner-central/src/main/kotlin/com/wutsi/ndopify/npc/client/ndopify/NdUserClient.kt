package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.security.dto.GetUserResponse
import com.wutsi.ndopify.security.dto.User
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client for the ndopify-server user endpoints (`/v1/users`). Users of another home tenant than `X-Tenant-ID`
 * (see `RestConfiguration`) are reported as not found.
 */
@Service
class NdUserClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,

    builder: RestClient.Builder,
) {
    private val rest: RestClient = builder
        .baseUrl(baseUrl)
        .build()

    /** The user with its party, or null when it doesn't exist (any more). */
    fun get(id: Long): User? =
        try {
            rest.get()
                .uri("/v1/users/{id}", id)
                .retrieve()
                .body<GetUserResponse>()
                ?.user
        } catch (ex: HttpClientErrorException) {
            if (ex.statusCode == HttpStatus.NOT_FOUND) null else throw ex
        }
}
