package com.wutsi.ndopify.platform.momo.mtn

import com.wutsi.ndopify.platform.momo.mtn.model.MtnBasicUserInfoResponse

interface MtnProduct {
    fun authenticate()
    fun userBasicInfo(phoneNumber: String): MtnBasicUserInfoResponse
}
