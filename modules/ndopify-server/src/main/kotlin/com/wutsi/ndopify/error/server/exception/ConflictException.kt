package com.wutsi.ndopify.error.server.exception

import com.wutsi.ndopify.error.dto.Error
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(value = HttpStatus.CONFLICT)
class ConflictException(error: Error, ex: Throwable? = null) : WutsiException(error, ex)
