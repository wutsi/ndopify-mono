package com.wutsi.ndopify.platform.momo

data class MoMoError(
    val code: MoMoErrorCode = MoMoErrorCode.NONE,
    val transactionId: String = "",
    val supplierErrorCode: String? = null,
    val message: String? = null,
    val errorId: String? = null,
)
