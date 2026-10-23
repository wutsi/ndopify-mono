package com.wutsi.ndopify.platform.ai

import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroupsPostProcessor

/**
 * Keeps the LLM out of the default group (`/actuator/health`): each check is a paid call to the model, and the model
 * being down must not take the server out of service. It's only checked by the `llm` group
 * (`/actuator/health/llm`, see `management.endpoint.health.group.llm` in `application.yml`).
 */
class ChatClientHealthEndpointGroupsPostProcessor : HealthEndpointGroupsPostProcessor {
    companion object {
        // Named after the bean, chatClientHealthIndicator (AIConfiguration)
        const val CONTRIBUTOR = "chatClient"
    }

    override fun postProcessHealthEndpointGroups(groups: HealthEndpointGroups): HealthEndpointGroups =
        HealthEndpointGroups.of(
            ExcludingGroup(groups.primary),
            groups.names.associateWith { name -> groups.get(name)!! },
        )

    private class ExcludingGroup(private val delegate: HealthEndpointGroup) : HealthEndpointGroup by delegate {
        override fun isMember(name: String): Boolean = name != CONTRIBUTOR && delegate.isMember(name)
    }
}
