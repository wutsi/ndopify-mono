package com.wutsi.ndopify.esignature.client

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.net.URI
import java.security.MessageDigest
import java.util.HexFormat

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

        // relaxed() plus the presentational attributes of the MJML-generated contract, needed for its layout
        private val SAFELIST =
            Safelist
                .relaxed()
                .addAttributes(":all", "style", "class", "align", "valign")
    }

    /**
     * The contract's document (HTML), cleaned to be shown in the page, and the hash of its bytes as stored. Null when
     * it can't be shown: the page then can't sign.
     */
    fun fetch(url: String): ContractContent? =
        try {
            val response = rest.get().uri(URI.create(url)).retrieve().toEntity(ByteArray::class.java)
            val contentType = response.headers.contentType
            val bytes = response.body
            if (contentType?.isCompatibleWith(MediaType.TEXT_HTML) != true || bytes == null) {
                null
            } else {
                // Body only, and no script or event handler: the page renders it with th:utext
                val html = String(bytes, contentType.charset ?: Charsets.UTF_8)
                ContractContent(
                    html = Jsoup.clean(Jsoup.parse(html).body().html(), SAFELIST),
                    hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                )
            }
        } catch (ex: Exception) {
            LOGGER.warn("Unable to fetch the contract content: $url", ex)
            null
        }
}
