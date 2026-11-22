package com.wutsi.ndopify.web.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AssetCacheConfiguration {
    @Bean
    fun assetCacheFilter(): AssetCacheFilter = AssetCacheFilter()
}
