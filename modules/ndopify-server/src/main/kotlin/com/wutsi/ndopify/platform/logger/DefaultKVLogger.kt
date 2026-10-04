package com.wutsi.koki.platform.logger

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Collections
import java.util.Optional
import java.util.StringJoiner

open class DefaultKVLogger(
    private val logger: Logger = LOGGER,
    private val encoder: LoggerEncoder = LoggerEncoder(),
) : KVLogger {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(DefaultKVLogger::class.java)
        const val EXCEPTION = "exception"
        const val EXCEPTION_MESSAGE = "exception_message"
        const val KVPAIR_SEPARATOR = "="
        const val ITEM_SEPARATOR = " "
        const val MAX_LENGTH = 10000
    }

    private val data: MutableMap<String, MutableList<String>> = Collections.synchronizedMap(mutableMapOf())
    private var exception: Throwable? = null

    override fun log() {
        if (data.isEmpty() && exception == null) {
            return
        }

        if (exception == null) {
            logger.info(toString())
        } else {
            add(EXCEPTION, exception!!.javaClass.name)
            add(EXCEPTION_MESSAGE, exception!!.message)
            logger.error(toString(), exception)
        }

        data.clear()
        exception = null
    }

    override fun add(key: String, value: String?) {
        if (value == null) {
            return
        }

        if (!data.containsKey(key)) {
            data[key] = mutableListOf()
        }
        data[key]!!.add(value)
    }

    override fun add(key: String, value: Long?) {
        add(key, value?.toString())
    }

    override fun add(key: String, value: Double?) {
        add(key, value?.toString())
    }

    override fun add(key: String, value: Optional<*>) {
        if (value.isPresent) {
            add(key, value.get())
        }
    }

    override fun add(key: String, values: Collection<*>?) {
        if (values == null) {
            return
        }

        for (value in values) {
            add(key, value)
        }
    }

    override fun add(key: String, value: Any?) {
        add(key, value?.toString())
    }

    override fun setException(ex: Throwable) {
        exception = ex
    }

    override fun toString(): String {
        // iterate over keys and define the order in which it should get logged
        val keys = data.keys.sorted()

        // convert to string
        val buffer = StringBuilder()
        for (key in keys) {
            val value: List<String> = data[key] ?: continue

            val encodedValue = encoder.encode(toString(value))
            if (encodedValue != null) {
                if (buffer.isNotEmpty()) {
                    buffer.append(ITEM_SEPARATOR)
                }

                buffer.append(key).append(KVPAIR_SEPARATOR).append(encodedValue)
            }
        }

        // Put the stacktrace at the end!
        val str = buffer.toString()
        return if (str.length > MAX_LENGTH) str.substring(0, MAX_LENGTH) else str
    }

    private fun toString(values: List<String>): String {
        if (values.size == 1) {
            return values[0]
        } else {
            val joiner = StringJoiner(ITEM_SEPARATOR)
            for (value in values) {
                joiner.add(value)
            }
            return joiner.toString()
        }
    }
}
