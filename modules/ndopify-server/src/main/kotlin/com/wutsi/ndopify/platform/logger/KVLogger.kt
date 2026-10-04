package com.wutsi.koki.platform.logger

import java.util.Optional

/**
 * Key-Value logging for structured logs.
 *
 * LLMUsage:
 * ```
 * val logger: KVLogger = ...
 * logger.add("key1", "value1")
 * logger.add("key2", 123)
 * logger.setException(exception) // If exception needs to be logged
 * logger.log()
 * ```
 */
interface KVLogger {
    fun log()
    fun add(key: String, value: String?)
    fun add(key: String, value: Long?)
    fun add(key: String, value: Double?)
    fun add(key: String, value: Optional<*>)
    fun add(key: String, values: Collection<*>?)
    fun add(key: String, value: Any?)
    fun setException(ex: Throwable)
}
