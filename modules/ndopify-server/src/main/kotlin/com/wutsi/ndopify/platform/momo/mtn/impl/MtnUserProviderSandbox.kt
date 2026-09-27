package com.wutsi.ndopify.platform.momo.mtn.impl

import com.wutsi.ndopify.platform.momo.mtn.MtnUserProvider
import com.wutsi.ndopify.platform.momo.mtn.model.MtnApiKeyResponse
import com.wutsi.ndopify.platform.momo.mtn.model.MtnEnvironment
import com.wutsi.ndopify.platform.momo.mtn.model.MtnUser
import com.wutsi.ndopify.util.http.Http
import java.net.URL
import java.util.UUID

class MtnUserProviderSandbox(
    private val subscriptionKey: String,
    private val callbackUrl: String,
    private val http: Http,
) : MtnUserProvider {
    private var user: MtnUser? = null

    override fun get(): MtnUser {
        if (user == null) {
            user = createUser()
        }

        return user!!
    }

    private fun createUser(): MtnUser {
        val userId = UUID.randomUUID().toString()
        http.post(
            uri = uri(),
            headers = mapOf(
                "X-Reference-Id" to userId,
                "Ocp-Apim-Subscription-Key" to subscriptionKey,
            ),
            requestPayload = mapOf(
                "providerCallbackHost" to URL(callbackUrl).host,
            ),
            responseType = Any::class.java,
        )

        // Get API Key
        val apiKey = http.post(
            uri = uri("/$userId/apikey"),
            headers = mapOf(
                "Ocp-Apim-Subscription-Key" to subscriptionKey,
            ),
            requestPayload = emptyMap<String, String>(),
            responseType = MtnApiKeyResponse::class.java,
        )!!.apiKey

        return MtnUser(id = userId, apiKey = apiKey)
    }

    private fun uri(path: String = ""): String =
        MtnEnvironment.SANDBOX.baseUrl + "/v1_0/apiuser$path"
}
