package com.wutsi.ndopify.platform.mail

import com.github.mustachejava.MustacheFactory
import dev.jcputney.mjml.MjmlRenderer
import okio.FileNotFoundException
import org.apache.commons.io.IOUtils
import java.io.StringReader
import java.io.StringWriter

class MailBodyResolver(
    private val renderer: MjmlRenderer,
    private val mustache: MustacheFactory,
    private val filters: List<MailFilter>,
) {
    fun resolve(path: String, context: Map<String, Any?>): String {
        val input = this::class.java.getResourceAsStream(path)
            ?: throw FileNotFoundException(path)

        val mjml = IOUtils.toString(input, "UTF-8")
        val css = IOUtils.toString(this::class.java.getResourceAsStream("/mail/mail.css"), "UTF-8")
        val xmjml = apply(mjml, context + mapOf("__css__" to css))
            .replace("&#10;", "\n")
        val html = renderer.renderTemplate(xmjml).html()

        return filter(html)
    }

    private fun apply(text: String, data: Map<String, Any?>): String {
        val reader = StringReader(text)
        val writer = StringWriter()
        mustache.compile(reader, "text")
            .execute(writer, data)
        return writer.toString()
    }

    private fun filter(html: String): String {
        var result = html
        filters.forEach { filter ->
            result = filter.filter(result)
        }
        return result
    }
}
