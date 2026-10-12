package com.wutsi.ndopify.security.server.endpoint

import com.wutsi.ndopify.security.dto.GetUserResponse
import com.wutsi.ndopify.security.server.mapper.UserMapper
import com.wutsi.ndopify.security.server.service.UserService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/users")
class UserEndpoints(
    private val service: UserService,
    private val mapper: UserMapper,
) {
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ): GetUserResponse {
        val user = service.findById(id)
        return GetUserResponse(user = mapper.toUser(user))
    }
}
