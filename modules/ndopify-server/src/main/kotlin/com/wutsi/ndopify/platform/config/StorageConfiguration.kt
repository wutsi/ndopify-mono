package com.wutsi.ndopify.platform.config

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.storage.local.StorageServiceLocal
import com.wutsi.ndopify.platform.storage.s3.S3HealthIndicator
import com.wutsi.ndopify.platform.storage.s3.StorageServiceS3
import com.wutsi.ndopify.refdata.dto.StorageType
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class StorageConfiguration(
    @Value("\${ndopify.storage.default-type}") private val defaultType: String,
    @Value("\${ndopify.storage.local.directory}") private val localDirectory: String,
    @Value("\${ndopify.storage.s3.bucket}") private val s3Bucket: String,
    @Value("\${ndopify.storage.s3.region}") private val s3Region: String,
) {
    @Bean
    fun storageServiceProvider(): StorageServiceProvider {
        return StorageServiceProvider(
            defaultType = StorageType.valueOf(defaultType.uppercase()),
            local = storageServiceLocal(),
            s3 = storageServiceS3()
        )
    }

    @Bean
    fun storageServiceLocal(): StorageService {
        return StorageServiceLocal(localDirectory)
    }

    @Bean
    open fun storageServiceS3(): StorageService {
        return StorageServiceS3(s3Bucket, amazonS3())
    }

    @Bean
    open fun amazonS3(): AmazonS3 {
        return AmazonS3ClientBuilder
            .standard()
            .withRegion(s3Region)
            .build()
    }

    @Bean
    open fun s3StorageHealthIndicator(): HealthIndicator {
        return S3HealthIndicator(s3Bucket, amazonS3())
    }
}
