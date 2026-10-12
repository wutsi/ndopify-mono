package com.wutsi.ndopify.util.http

import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpRequest.Builder
import java.net.http.HttpResponse

open class Http(
    private val client: HttpClient,
    private val objectMapper: JsonMapper,
) {
    open fun <T> get(
        uri: String,
        responseType: Class<T>,
        headers: Map<String, String?>,
    ): T? {
        val request = HttpRequest.newBuilder()
            .uri(URI(uri))
            .headers(headers + mapOf("Content-Type" to "application/json"))
            .GET()
            .build()

        return handle(responseType, request)
    }

    open fun <T> post(
        uri: String,
        requestPayload: Any,
        responseType: Class<T>,
        headers: Map<String, String?>,
    ): T? {
        val requestBody = objectMapper.writeValueAsString(requestPayload)
        val request = HttpRequest.newBuilder()
            .uri(URI(uri))
            .headers(headers + mapOf("Content-Type" to "application/json"))
            .POST(BodyPublishers.ofString(requestBody))
            .build()

        return handle(responseType, request)
    }

    private fun <T> handle(
        responseType: Class<T>,
        request: HttpRequest,
    ): T? {
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return if (response.statusCode() / 100 == 2) {
            val body = response.body()
            if (body.isNullOrEmpty()) {
                null
            } else {
                objectMapper.readValue(body, responseType)
            }
        } else {
            throw HttpException(response.statusCode(), response.body())
        }
    }

    private fun Builder.headers(headers: Map<String, String?>): Builder {
        headers.keys.forEach {
            if (headers[it] != null) {
                this.header(it, headers[it])
            }
        }
        return this
    }
}
