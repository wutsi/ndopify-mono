package com.wutsi.ndopify.util.jpa

import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * The JSON of a `@JdbcTypeCode(SqlTypes.JSON)` column, which stays a [String] in the entity: the domain serializes its
 * own class with [write] and reads it back with [read]. The [mapper] is the application's (Spring Boot's), so the
 * serialization is the same as everywhere else.
 */
@Component
class JsonHelper(private val mapper: JsonMapper) {
    /** The JSON of [value]; null for null. */
    fun write(value: Any?): String? = value?.let { mapper.writeValueAsString(it) }

    /** [json] as a [clazz], or null for a blank payload; a malformed payload throws the mapper's exception. */
    fun <T> read(json: String?, clazz: Class<T>): T? = json
        ?.takeIf { it.isNotBlank() }
        ?.let { mapper.readValue(it, clazz) }
}
