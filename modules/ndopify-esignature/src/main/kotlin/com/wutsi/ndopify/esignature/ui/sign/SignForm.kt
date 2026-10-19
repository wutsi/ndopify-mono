package com.wutsi.ndopify.esignature.ui.sign

/**
 * Consent, and the `contentUrl` of the version the page showed: only compared to the contract's, so that a signer
 * never signs a version they didn't see. The contract party is the URL's, and ndopify-server checks it's the
 * signed-in signer's.
 */
data class SignForm(
    val consent: String? = null,
    val contentUrl: String? = null,
)
