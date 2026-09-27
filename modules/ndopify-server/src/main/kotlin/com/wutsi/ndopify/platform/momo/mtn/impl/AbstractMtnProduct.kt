package com.wutsi.ndopify.platform.momo.mtn.impl

import com.wutsi.ndopify.platform.momo.mtn.MtnProduct
import com.wutsi.ndopify.platform.momo.mtn.MtnUserProvider
import com.wutsi.ndopify.platform.momo.mtn.model.MtnBasicUserInfoResponse
import com.wutsi.ndopify.platform.momo.mtn.model.MtnEnvironment
import com.wutsi.ndopify.platform.momo.mtn.model.MtnTokenResponse
import com.wutsi.ndopify.util.PhoneNumberUtils
import com.wutsi.ndopify.util.http.Http
import java.util.Base64
import java.util.UUID

abstract class AbstractMtnProduct(
    protected val environment: MtnEnvironment,
    protected val subscriptionKey: String,
    protected val callbackUrl: String?,
    protected val userProvider: MtnUserProvider,
    protected val http: Http,
) : MtnProduct {
    protected var accessToken: String? = null

    protected abstract fun uri(path: String): String

    override fun authenticate() {
        val response = http.post(
            uri = uri("/token/"),
            headers = mapOf(
                "Authorization" to basicAuth(),
                "Ocp-Apim-Subscription-Key" to subscriptionKey,
            ),
            requestPayload = mapOf("foo" to "bar"),
            responseType = MtnTokenResponse::class.java,
        )
        this.accessToken = response?.accessToken
    }

    override fun userBasicInfo(phoneNumber: String): MtnBasicUserInfoResponse {
        val referenceId = UUID.randomUUID().toString()
        val normalizedPhoneNumber = PhoneNumberUtils.normalize(phoneNumber)
        return http.get(
            uri = uri("/v1_0/accountholder/msisdn/$normalizedPhoneNumber/basicuserinfo"),
            headers = headers(referenceId),
            responseType = MtnBasicUserInfoResponse::class.java,
        )!!
    }

    protected fun headers(referenceId: String?): Map<String, String?> {
        return mapOf(
            "Authorization" to accessToken?.let { bearerAuth(it) },
            "X-Callback-Url" to callbackUrl,
            "X-Reference-Id" to referenceId,
            "X-Target-Environment" to environment.name.lowercase(),
            "Ocp-Apim-Subscription-Key" to subscriptionKey,
        )
    }

    private fun bearerAuth(accessToken: String): String {
        return "Bearer $accessToken"
    }

    private fun basicAuth(): String {
        val user = userProvider.get()
        val str = "${user.id}:${user.apiKey}"
        return "Basic " + Base64.getEncoder().encodeToString(str.toByteArray())
    }
}
