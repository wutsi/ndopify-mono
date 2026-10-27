package com.wutsi.ndopify.platform.ai

import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroupsPostProcessor

/**
 * Keeps the LLMs out of the default group (`/actuator/health`): each check is a paid call to the model, and a model
 * being down must not take the server out of service. They're only checked by the `llm` group
 * (`/actuator/health/llm`, see `management.endpoint.health.group.llm` in `application.yml`).
 *
 * @param contributors names of the [ChatClientHealthIndicator] contributors (see `AIConfiguration`)
 */
class ChatClientHealthEndpointGroupsPostProcessor(
    private val contributors: Set<String>,
) : HealthEndpointGroupsPostProcessor {
    override fun postProcessHealthEndpointGroups(groups: HealthEndpointGroups): HealthEndpointGroups =
        HealthEndpointGroups.of(
            ExcludingGroup(groups.primary, contributors),
            groups.names.associateWith { name -> groups.get(name)!! },
        )

    private class ExcludingGroup(
        private val delegate: HealthEndpointGroup,
        private val excluded: Set<String>,
    ) : HealthEndpointGroup by delegate {
        override fun isMember(name: String): Boolean = name !in excluded && delegate.isMember(name)
    }
}
