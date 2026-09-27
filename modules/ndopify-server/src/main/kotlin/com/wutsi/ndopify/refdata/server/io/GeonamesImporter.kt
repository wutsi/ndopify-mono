package com.wutsi.ndopify.refdata.server.io

import com.wutsi.ndopify.common.dto.ImportMessage
import com.wutsi.ndopify.common.dto.ImportResponse
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile

@Service
class GeonamesImporter(
    private val service: LocationService,
    @param:Value("\${ndopify.ref-data.geonames.connect-timeout}") private val connectTimeout: Int,
    @param:Value("\${ndopify.ref-data.geonames.read-timeout}") private val readTimeout: Int
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(GeonamesImporter::class.java)

        private val MIN_POPULATION = 1000
        private const val RECORD_ID = 0
        private const val RECORD_NAME = 1
        private const val RECORD_LATITUDE = 4
        private const val RECORD_LONGITUDE = 5
        private const val RECORD_FEATURE_CLASS = 6
        private const val RECORD_FEATURE_CODE = 7
        private const val RECORD_COUNTRY = 8
        private const val RECORD_ADMIN1_CODE = 10
        private const val RECORD_POPULATION = 14
        private const val RECORD_TIMEZONE = 17
    }

    fun import(country: String): ImportResponse {
        var row = 0
        var added = 0
        var updated = 0
        var errors = mutableListOf<ImportMessage>()

        // Import states
        val admin1Codes = importAdmin1Codes(country.uppercase())

        // Import Location
        val file = downloadZip(
            URI("https://download.geonames.org/export/dump/${country.uppercase()}.zip").toURL(),
            "$country.txt"
        )
        val parser = createParser(file)
        var countryId: Long = -1
        var stateIds = mutableListOf<Long>()
        for (record in parser) {
            if (accept(record, country)) {
                try {
                    val id = record.get(RECORD_ID).toLong()
                    var location = service.findByIdOrNull(id)
                    if (location == null) {
                        location = add(id, record, admin1Codes)
                        added++
                    } else {
                        update(location, record, admin1Codes)
                        updated++
                    }

                    if (location.type == LocationType.COUNTRY) {
                        countryId = id
                    } else if (location.type == LocationType.STATE) {
                        stateIds.add(id)
                    }
                } catch (ex: Exception) {
                    errors.add(ImportMessage(row.toString(), "", ex.message))
                } finally {
                    row++
                }
            }
        }

        // Link states -> countries
        stateIds.forEach { stateId -> service.link(countryId, stateId) }

        LOGGER.info("${added + updated} location(s) imported with ${errors.size} error(s)")
        return ImportResponse(
            added = added,
            updated = updated,
            errors = errors.size,
            errorMessages = errors
        )
    }

    private fun importAdmin1Codes(country: String): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        val url = URL("https://download.geonames.org/export/dump/admin1CodesASCII.txt")
        val file = download(url, ".txt")
        val parser = createParser(file)
        for (record in parser) {
            val code = record.get(0)
            val stateId = record.get(3).toLong()
            if (code.startsWith(country)) {
                result[code] = stateId
            }
        }

        LOGGER.info(" ${result.size} admin1 loaded")
        return result
    }

    private fun createParser(file: File): CSVParser {
        return CSVParser.parse(
            file,
            Charsets.UTF_8,
            CSVFormat.Builder.create()
                .setDelimiter("\t")
                .get(),
        )
    }

    private fun add(id: Long, record: CSVRecord, admin1Codes: Map<String, Long>): LocationEntity {
        val type = toLocationType(record)
        val name = toLocationName(record, type)

        return service.save(
            LocationEntity(
                id = id,
                name = name,
                asciiName = service.toAscii(name),
                country = record.get(RECORD_COUNTRY),
                type = type,
                population = record.get(RECORD_POPULATION).toLong(),
                parentId = if (type == LocationType.CITY) getStateId(record, admin1Codes) else null,
                latitude = record.get(RECORD_LATITUDE).toDouble(),
                longitude = record.get(RECORD_LONGITUDE).toDouble(),
            )
        )
    }

    private fun update(location: LocationEntity, record: CSVRecord, admin1Codes: Map<String, Long>) {
        val type = toLocationType(record)
        val name = toLocationName(record, type)

        location.name = name
        location.asciiName = service.toAscii(name)
        location.country = record.get(RECORD_COUNTRY)
        location.type = type
        location.population = record.get(RECORD_POPULATION).toLong()
        location.parentId = if (type == LocationType.CITY) getStateId(record, admin1Codes) else location.parentId
        location.latitude = record.get(RECORD_LATITUDE).toDouble()
        location.longitude = record.get(RECORD_LONGITUDE).toDouble()

        service.save(location)
    }

    private fun getStateId(record: CSVRecord, admin1Codes: Map<String, Long>): Long? {
        val country = record.get(RECORD_COUNTRY)
        val code = record.get(RECORD_ADMIN1_CODE)
        if (code.isNullOrEmpty()) {
            return null
        }

        return admin1Codes["$country.$code"]
            ?: admin1Codes["$country.0$code"]
    }

    private fun toLocationName(record: CSVRecord, type: LocationType): String {
        return if (type == LocationType.COUNTRY) {
            Locale("en", record.get(RECORD_COUNTRY)).displayCountry
        } else {
            val name = record.get(RECORD_NAME)
            val i = name.indexOf("/")
            return if (i > 0) name.substring(0, i) else name
        }
    }

    private fun toLocationType(record: CSVRecord): LocationType {
        val code = record.get(RECORD_FEATURE_CODE)
        if (record.get(RECORD_FEATURE_CLASS) == "P") {
            return LocationType.CITY
        } else if (record.get(RECORD_FEATURE_CLASS) == "A") {
            if (code == "ADM1") {
                return LocationType.STATE
            } else if (code == "PCLI") {
                return LocationType.COUNTRY
            }
        }
        return LocationType.UNKNOWN
    }

    private fun accept(record: CSVRecord, country: String): Boolean {
        return country ==
            record.get(RECORD_COUNTRY) &&
            ( // Cities
                record.get(RECORD_FEATURE_CLASS) == "P" &&
                    listOf(
                        "PPL",
                        "PPLA",
                        "PPLA2",
                        "PPLA3",
                        "PPLA4",
                        "PPLC",
                        "PPLCH",
                    ).contains(record.get(RECORD_FEATURE_CODE)) &&
                    !record.get(RECORD_POPULATION).isNullOrEmpty() &&
                    record.get(RECORD_POPULATION).toLong() >= MIN_POPULATION
                ) ||
            ( // State
                record.get(RECORD_FEATURE_CLASS) == "A" &&
                    (record.get(RECORD_FEATURE_CODE) == "ADM1" || record.get(RECORD_FEATURE_CODE) == "PCLI"))
    }

    private fun downloadZip(url: URL, filename: String): File {
        val zip = download(url, ".zip")

        // Extract
        ZipFile(zip).use { zip ->
            val entry = zip.entries().asSequence().find { it.name == filename }
            if (entry != null) {
                val file = Files.createTempFile("file", ".txt").toFile()
                zip.getInputStream(entry).use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                    return file
                }
            }
        }

        throw NotFoundException(
            error = Error(
                code = ErrorCode.LOCATION_FEED_NOT_FOUND,
                data = mapOf("url" to url.toString())
            )
        )
    }

    private fun download(url: URL, extension: String): File {
        LOGGER.info("Downloading $url")
        val cnn = url.openConnection() as HttpURLConnection
        try {
            cnn.connectTimeout = readTimeout
            cnn.readTimeout = connectTimeout
            val input = cnn.inputStream
            input.use {
                val file = Files.createTempFile(UUID.randomUUID().toString(), extension).toFile()
                LOGGER.info("...Storing $url to ${file.absolutePath}")

                val output = FileOutputStream(file)
                output.use {
                    input.copyTo(output)
                }

                return file
            }
        } finally {
            cnn.disconnect()
        }
    }
}
