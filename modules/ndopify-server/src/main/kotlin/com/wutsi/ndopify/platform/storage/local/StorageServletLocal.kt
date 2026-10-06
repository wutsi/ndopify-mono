package com.wutsi.ndopify.platform.storage.local

import com.wutsi.ndopify.util.MimeUtils
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException

class StorageServletLocal(
    private val directory: String,
) : HttpServlet() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(StorageServletLocal::class.java)
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val file = File("$directory${req.pathInfo}")
        try {
            resp.contentType = MimeUtils.getMimeTypeFromExtension(file.extension)
            FileInputStream(file).use { `in` ->
                `in`.copyTo(resp.outputStream)
            }
        } catch (e: FileNotFoundException) {
            resp.sendError(404)
            LOGGER.error("File not found: $file", e)
        } catch (e: Exception) {
            resp.sendError(500)
            LOGGER.error("Unexpected error while processing file $file", e)
        }
    }
}
