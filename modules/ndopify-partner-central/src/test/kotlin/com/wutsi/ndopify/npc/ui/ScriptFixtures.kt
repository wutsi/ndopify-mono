package com.wutsi.ndopify.npc.ui

import org.jsoup.nodes.Document
import java.io.File

/**
 * Saves a page rendered by a template test for the page-script test (`src/test/js/`, `npm test`), which loads it in
 * jsdom with the built `app.min.js`: the scripts run on the HTML the templates really produce, so a script that throws
 * on a step (#304: the seller's features step) fails the build. The files go to `target/script-fixtures/<name>.html`,
 * never committed; `npm test` runs after the Kotlin tests (Maven's `test` phase) and fails when one is missing.
 */
object ScriptFixtures {
    private val dir = File("target/script-fixtures")

    fun write(name: String, page: Document) {
        dir.mkdirs()
        File(dir, "$name.html").writeText(page.outerHtml(), Charsets.UTF_8)
    }
}
