package com.wutsi.ndopify.platform.mail

import com.wutsi.ndopify.platform.image.Format
import com.wutsi.ndopify.platform.image.ImageService
import com.wutsi.ndopify.platform.image.Transformation
import java.awt.Dimension

/**
 * The image sizes of the e-mails, and the only ones: the two transformations a mailet asks [ImageService] for.
 *
 * [COVER] is the full-width `mj-image` (2× the 600 px MJML body), [THUMBNAIL] the 80×60 `<img>` (2×). Both are JPEG at
 * quality 75 — Outlook desktop and older clients don't show WebP — with width and height both set, so ImageKit crops
 * to the exact 3:2 / 4:3 ratio and no client needs `object-fit`. A portrait cover is center-cropped to 3:2.
 */
object MailImages {
    val COVER = Transformation(
        dimension = Dimension(1200, 800),
        format = Format.JPG,
        quality = 75,
    )

    val THUMBNAIL = Transformation(
        dimension = Dimension(160, 120),
        format = Format.JPG,
        quality = 75,
    )

    /** [url] resized for [slot], or null for a blank one: what a mailet passes as `image_url`. */
    fun imageUrl(url: String?, imageService: ImageService, slot: Transformation): String? =
        url?.takeIf { it.isNotBlank() }
            ?.let { imageService.transform(it, slot) }
            ?.takeIf { it.isNotBlank() }
}
