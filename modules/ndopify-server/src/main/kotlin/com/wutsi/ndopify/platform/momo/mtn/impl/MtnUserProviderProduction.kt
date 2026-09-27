package com.wutsi.ndopify.platform.momo.mtn.impl

import com.wutsi.ndopify.platform.momo.mtn.MtnUserProvider
import com.wutsi.ndopify.platform.momo.mtn.model.MtnUser

class MtnUserProviderProduction(
    private val userId: String,
    private val apiKey: String,
) : MtnUserProvider {
    private val user = MtnUser(userId, apiKey)

    override fun get(): MtnUser =
        user
}
