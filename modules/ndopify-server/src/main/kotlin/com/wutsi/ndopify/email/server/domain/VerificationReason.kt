package com.wutsi.ndopify.email.server.domain

/**
 * The provider-neutral reason of an e-mail verdict, stored by name and never renamed.
 *
 * The precedence when several checks fail is `SYNTAX_INVALID` > `DOMAIN_INVALID` > `MAILBOX_UNREACHABLE` >
 * `DISPOSABLE` > `ROLE_ADDRESS` > `RANDOM_INPUT` > `NONE`; `UNAVAILABLE` is used when SES could not answer at all.
 */
enum class VerificationReason {
    NONE,
    SYNTAX_INVALID,
    DOMAIN_INVALID,
    MAILBOX_UNREACHABLE,
    DISPOSABLE,
    ROLE_ADDRESS,
    RANDOM_INPUT,
    UNAVAILABLE,
}
