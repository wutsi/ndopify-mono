package com.wutsi.ndopify.listing.server.image

import com.wutsi.ndopify.listing.dto.ImageVariant
import com.wutsi.ndopify.platform.image.Crop
import com.wutsi.ndopify.platform.image.CropMode
import com.wutsi.ndopify.platform.image.Format
import com.wutsi.ndopify.platform.image.ImageService
import com.wutsi.ndopify.platform.image.Transformation
import java.awt.Dimension

/**
 * The listing-page renditions, exact 3:2 and generated on demand by [ImageService]. The widths are the ladder the
 * browser chooses from with `srcset`; a width the source can't fill is left out, so nothing is upscaled.
 */
object ListingImages {
    val WIDTHS = listOf(240, 480, 960, 1440, 2048)

    val SOCIAL = transformation(Dimension(1200, 630), 1200, 630)

    fun transformation(width: Int): Transformation = transformation(Dimension(width, width * 2 / 3), 3, 2)

    /** [url] in each ladder width that fits [sourceWidth]; the whole ladder when it is null, its own width when it's tiny. */
    fun variants(url: String, sourceWidth: Int?, imageService: ImageService): List<ImageVariant> {
        if (url.isBlank()) return emptyList()
        val widths = if (sourceWidth == null || sourceWidth <= 0) {
            WIDTHS
        } else {
            WIDTHS.filter { it <= sourceWidth }.ifEmpty { listOf(sourceWidth) }
        }
        return widths.map { width ->
            ImageVariant(width = width, height = width * 2 / 3, url = imageService.transform(url, transformation(width)))
        }
    }

    fun social(url: String, imageService: ImageService): String =
        if (url.isBlank()) "" else imageService.transform(url, SOCIAL)

    private fun transformation(dimension: Dimension, aspectWidth: Int, aspectHeight: Int) = Transformation(
        dimension = dimension,
        aspectRatio = Dimension(aspectWidth, aspectHeight),
        format = Format.AUTO,
        crop = Crop.MAINTAIN_RATIO,
        cropMode = CropMode.EXTRACT,
        quality = 75,
    )
}
