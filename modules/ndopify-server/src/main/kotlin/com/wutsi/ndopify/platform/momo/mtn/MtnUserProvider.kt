package com.wutsi.ndopify.platform.momo.mtn

import com.wutsi.ndopify.platform.momo.mtn.model.MtnUser

interface MtnUserProvider {
    fun get(): MtnUser
}
