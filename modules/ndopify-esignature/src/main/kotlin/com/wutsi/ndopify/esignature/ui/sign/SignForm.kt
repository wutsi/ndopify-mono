package com.wutsi.ndopify.esignature.ui.sign

/** Consent only: the contract party is the URL's, and ndopify-server checks it's the signed-in signer's. */
data class SignForm(
    val consent: String? = null,
)
