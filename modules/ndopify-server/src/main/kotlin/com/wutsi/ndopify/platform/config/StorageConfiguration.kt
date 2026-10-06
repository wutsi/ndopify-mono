package com.wutsi.ndopify.config

import com.wutsi.ndopify.platform.momo.MoMoGatewayProvider
import com.wutsi.ndopify.platform.momo.mtn.MoMoGatewayMtn
import com.wutsi.ndopify.platform.momo.mtn.MtnCollectionProduct
import com.wutsi.ndopify.platform.momo.mtn.MtnUserProvider
import com.wutsi.ndopify.platform.momo.mtn.impl.MtnUserProviderProduction
import com.wutsi.ndopify.platform.momo.mtn.impl.MtnUserProviderSandbox
import com.wutsi.ndopify.platform.momo.mtn.model.MtnEnvironment
import com.wutsi.ndopify.util.http.Http
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient

@Configuration
class MoMoConfiguration(
    @Value("\${ndopify.mobile-money.mtn.environment}") private val environment: String,
    @Value("\${ndopify.mobile-money.mtn.callback-url}") private val callbackUrl: String,
    @Value("\${ndopify.mobile-money.mtn.collection.user-id}") private val collectionUserId: String,
    @Value("\${ndopify.mobile-money.mtn.collection.api-key}") private val collectionApiKey: String,
    @Value("\${ndopify.mobile-money.mtn.collection.subscription-key}") private val collectionSubscriptionKey: String,

    private val objectMapper: JsonMapper
) {
    @Bean
    fun moMoGatewayProvider(): MoMoGatewayProvider {
        return MoMoGatewayProvider(
            mtn = moMoGatewayMtn()
        )
    }
    
    @Bean
    fun moMoGatewayMtn(): MoMoGatewayMtn {
        return MoMoGatewayMtn(collection = mtnCollectionProduct())
    }

    @Bean
    fun mtnCollectionProduct(): MtnCollectionProduct {
        val env = getMTNEnvironment()
        val http = Http(
            client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(),
            objectMapper = objectMapper,
        )
        return MtnCollectionProduct(
            environment = env,
            subscriptionKey = collectionSubscriptionKey,
            callbackUrl = callbackUrl,
            userProvider = createUserProvider(env, collectionUserId, collectionApiKey, collectionSubscriptionKey, http),
            http = http
        )
    }

    private fun createUserProvider(
        env: MtnEnvironment,
        userId: String,
        apiKey: String,
        subscriptionKey: String,
        http: Http
    ): MtnUserProvider {
        return if (env == MtnEnvironment.PRODUCTION) {
            MtnUserProviderProduction(userId, apiKey)
        } else {
            MtnUserProviderSandbox(subscriptionKey, callbackUrl, http)
        }
    }

    private fun getMTNEnvironment(): MtnEnvironment {
        return if (environment.equals("sandbox", ignoreCase = true)) {
            MtnEnvironment.SANDBOX
        } else {
            MtnEnvironment.PRODUCTION
        }
    }
}
