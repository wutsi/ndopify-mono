package com.wutsi.ndopify.platform.util.http

import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpRequest.Builder
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

open class Http(
    private val client: HttpClient,
    private val objectMapper: JsonMapper,
    private val readTimeout: Duration? = null,
) {
    open fun <T> get(
        uri: String,
        responseType: Class<T>,
        headers: Map<String, String?>,
    ): T? {
        val request = newRequest(uri, headers)
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
        val request = newRequest(uri, headers)
            .POST(BodyPublishers.ofString(requestBody))
            .build()

        return handle(responseType, request)
    }

    /**
     * Posts [fields] and [file] as a `multipart/form-data` body, the JSON [post]'s counterpart. A [String] field is
     * written as-is, any other value is JSON-serialized. The response is handled like [post]'s.
     */
    open fun <T> postMultipart(
        uri: String,
        fields: Map<String, Any?>,
        file: HttpFile,
        responseType: Class<T>,
        headers: Map<String, String?>,
    ): T? {
        val boundary = "----ndopify-" + UUID.randomUUID()
        val request = newRequest(uri, headers, "multipart/form-data; boundary=$boundary")
            .POST(BodyPublishers.ofByteArray(multipartBody(boundary, fields, file)))
            .build()

        return handle(responseType, request)
    }

    private fun multipartBody(boundary: String, fields: Map<String, Any?>, file: HttpFile): ByteArray {
        val out = ByteArrayOutputStream()
        fields.forEach { (name, value) ->
            out.write("--$boundary\r\n".toByteArray())
            out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
            val text = if (value is String) value else objectMapper.writeValueAsString(value)
            out.write(text.toByteArray())
            out.write("\r\n".toByteArray())
        }
        out.write("--$boundary\r\n".toByteArray())
        out.write(
            "Content-Disposition: form-data; name=\"${file.fieldName}\"; filename=\"${file.filename}\"\r\n"
                .toByteArray()
        )
        out.write("Content-Type: ${file.contentType}\r\n\r\n".toByteArray())
        out.write(file.content)
        out.write("\r\n--$boundary--\r\n".toByteArray())
        return out.toByteArray()
    }

    private fun newRequest(
        uri: String,
        headers: Map<String, String?>,
        contentType: String = "application/json",
    ): Builder {
        val builder = HttpRequest.newBuilder()
            .uri(URI(uri))
            .headers(headers + mapOf("Content-Type" to contentType))
        readTimeout?.let { builder.timeout(it) }
        return builder
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
