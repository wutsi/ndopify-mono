package com.wutsi.ndopify.refdata.server.io

import com.wutsi.ndopify.common.dto.ImportMessage
import com.wutsi.ndopify.common.dto.ImportResponse
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.util.StringUtils
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.InputStream

@Service
class NeighbourhoodImporter(
    private val locationService: LocationService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(NeighbourhoodImporter::class.java)

        private val REGEX_UNACCENT = "\\p{InCombiningDiacriticalMarks}+".toRegex()
        private const val RECORD_ID = 0
        private const val RECORD_NAME = 1
        private const val RECORD_CITY = 2
        private const val RECORD_LATITUDE = 3
        private const val RECORD_LONGITUDE = 4
    }

    fun import(country: String): ImportResponse {
        var added = 0
        var updated = 0
        var errors = mutableListOf<ImportMessage>()

        /* import */
        val filename = "/refdata/neighbourhood/$country.csv"
        val input = NeighbourhoodImporter::class.java.getResourceAsStream(filename)
        if (input == null) {
            errors.add(ImportMessage("", "No neighbourhood feed found for $country"))
            return ImportResponse(
                errors = errors.size,
                errorMessages = errors
            )
        }

        var row = 0
        val cities = locationService
            .search(country = country, types = listOf(LocationType.CITY), limit = Integer.MAX_VALUE)
            .associateBy { city -> city.asciiName.lowercase() }

        val parser = createParser(input)
        for (record in parser) {
            try {
                if (record.recordNumber == 1L) {
                    continue
                }
                val id = record.get(RECORD_ID).toLong()
                val cityName = record.get(RECORD_CITY)
                val city = cities[locationService.toAscii(cityName.lowercase())]
                if (city == null) {
                    errors.add(ImportMessage(row.toString(), "Invalid city: $city"))
                    continue
                }

                var location = locationService.findByIdOrNull(id)
                if (location == null) {
                    add(record, city)
                    added++
                } else {
                    if (location.type == LocationType.NEIGHBORHOOD) {
                        update(location, record, city)
                        updated++
                    } else {
                        errors.add(
                            ImportMessage(
                                row.toString(),
                                "The neighbourhood id<$id> associated to another location"
                            )
                        )
                    }
                }
            } catch (ex: Exception) {
                errors.add(ImportMessage(row.toString(), "", ex.message))
            } finally {
                row++
            }
        }

        LOGGER.info("${added + updated} neighborhood(s) for $country imported with ${errors.size} error(s)")
        return ImportResponse(
            added = added,
            updated = updated,
            errors = errors.size,
            errorMessages = errors
        )
    }

    private fun createParser(input: InputStream): CSVParser {
        return CSVParser.parse(
            input,
            Charsets.UTF_8,
            CSVFormat.Builder.create()
                .setDelimiter(",")
                .get(),
        )
    }

    private fun add(record: CSVRecord, city: LocationEntity): LocationEntity {
        return locationService.save(
            LocationEntity(
                id = record.get(RECORD_ID).toLong(),
                name = record.get(RECORD_NAME),
                asciiName = StringUtils.toAscii(record.get(RECORD_NAME)),
                parentId = city.id,
                type = LocationType.NEIGHBORHOOD,
                country = city.country,
                latitude = toDouble(record, RECORD_LATITUDE),
                longitude = toDouble(record, RECORD_LONGITUDE),
            )
        )
    }

    private fun update(neighbourhood: LocationEntity, record: CSVRecord, city: LocationEntity) {
        neighbourhood.name = record.get(RECORD_NAME)
        neighbourhood.parentId = city.id
        neighbourhood.country = city.country
        neighbourhood.latitude = toDouble(record, RECORD_LATITUDE)
        neighbourhood.longitude = toDouble(record, RECORD_LONGITUDE)
        locationService.save(neighbourhood)
    }

    private fun toDouble(record: CSVRecord, colum: Int): Double? {
        return try {
            record.get(colum)?.toDouble()
        } catch (ex: Exception) {
            null
        }
    }
}
