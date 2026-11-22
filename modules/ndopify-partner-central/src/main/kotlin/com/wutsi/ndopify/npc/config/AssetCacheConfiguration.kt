package com.wutsi.ndopify.npc.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AssetCacheConfiguration {
    @Bean
    fun assetCacheFilter(): AssetCacheFilter = AssetCacheFilter()
}
