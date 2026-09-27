package com.wutsi.ndopify.platform.momo.mtn

import com.wutsi.ndopify.platform.momo.mtn.impl.AbstractMtnProduct
import com.wutsi.ndopify.platform.momo.mtn.model.MtnEnvironment
import com.wutsi.ndopify.util.http.Http

open class MtnCollectionProduct(
    environment: MtnEnvironment,
    subscriptionKey: String,
    callbackUrl: String?,
    userProvider: MtnUserProvider,
    http: Http,
) : AbstractMtnProduct(environment, subscriptionKey, callbackUrl, userProvider, http) {
    override fun uri(path: String): String {
        return environment.baseUrl + "/collection/" + path.removePrefix("/")
    }
}
