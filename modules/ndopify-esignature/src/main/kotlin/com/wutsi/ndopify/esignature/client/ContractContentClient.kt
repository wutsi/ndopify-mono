package com.wutsi.ndopify.esignature.client

import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.net.URI

/**
 * Downloads a contract's document (`Contract.contentUrl`, in file storage) to show it in the sign page. HTML only,
 * sanitized. Goes through the plain `contentRestClient`, without the ndopify-server interceptors.
 */
@Service
class ContractContentClient(
    @Qualifier("contentRestClient") private val rest: RestClient,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ContractContentClient::class.java)
    }

    /** The contract's document (HTML), cleaned to be shown in the page. Null when it can't be: the page then can't sign. */
    fun fetch(url: String): String? =
        try {
            val response = rest.get().uri(URI.create(url)).retrieve().toEntity(String::class.java)
            if (response.headers.contentType?.isCompatibleWith(MediaType.TEXT_HTML) != true) {
                null
            } else {
                // Body only
                response.body?.let { html -> Jsoup.parse(html).body().html() }
            }
        } catch (ex: Exception) {
            LOGGER.warn("Unable to fetch the contract content: $url", ex)
            null
        }
}
