package com.wutsi.ndopify.npc.service.security

/**
 * Thrown by a page when [CurrentAgent] or [CurrentParty] finds nobody: the user is gone, has no party or isn't an
 * agent. [com.wutsi.ndopify.npc.ui.layout.HttpClientErrorHandler] signs them out like on a 401, since a redirect to
 * `/logout` can't do it (a redirect is a GET, and `/logout` is a POST so that another site can't sign the agent out).
 */
class SignInRequiredException : RuntimeException()
