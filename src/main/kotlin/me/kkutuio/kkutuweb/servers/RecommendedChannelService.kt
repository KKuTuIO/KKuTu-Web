package me.kkutuio.kkutuweb.servers

import tools.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import jakarta.annotation.PostConstruct

@Service
class RecommendedChannelService {
    private val logger = LoggerFactory.getLogger(RecommendedChannelService::class.java)
    private val restTemplate = RestTemplate(SimpleClientHttpRequestFactory().apply {
        setConnectTimeout(3_000)
        setReadTimeout(5_000)
    })

    @Volatile
    private var configuration = RecommendedChannelConfiguration()

    fun getConfiguration(): RecommendedChannelConfiguration = configuration

    @PostConstruct
    fun initialize() {
        refresh()
    }

    @Scheduled(fixedDelay = 300_000)
    fun refresh() {
        try {
            val response = restTemplate.getForObject(SERVER_CONFIG_URL, JsonNode::class.java)
                ?: return
            val channel = response.path("recommendedChannel").takeUnless { it.isMissingNode || it.isNull }
                ?: response.path("recommendedChannelNumber")
            val overrideRecommendedChannel = response.path("overrideRecommendedChannel")

            if (!channel.canConvertToInt() || channel.asInt() < 0) {
                logger.warn("Ignoring invalid recommended channel in $SERVER_CONFIG_URL")
            }
            if (!overrideRecommendedChannel.isMissingNode &&
                !overrideRecommendedChannel.isNull &&
                !overrideRecommendedChannel.isBoolean
            ) {
                logger.warn("Ignoring invalid overrideRecommendedChannel in $SERVER_CONFIG_URL")
            }

            configuration = parseRecommendedChannelConfiguration(response, configuration)
        } catch (e: Exception) {
            logger.warn(
                "Failed to refresh recommended channel; retaining channel ${configuration.channel}",
                e
            )
        }
    }

    companion object {
        private const val SERVER_CONFIG_URL = "https://static.kkutu.io/server.json"
    }
}

data class RecommendedChannelConfiguration(
    val channel: Int = 0,
    val overrideRecommendedChannel: Boolean = true
)

internal fun parseRecommendedChannelConfiguration(
    response: JsonNode,
    current: RecommendedChannelConfiguration
): RecommendedChannelConfiguration {
    val channelNode = response.path("recommendedChannel").takeUnless { it.isMissingNode || it.isNull }
        ?: response.path("recommendedChannelNumber")
    val channel = channelNode.takeIf { it.canConvertToInt() && it.asInt() >= 0 }?.asInt()
        ?: current.channel

    val overrideNode = response.path("overrideRecommendedChannel")
    val overrideRecommendedChannel = when {
        overrideNode.isMissingNode || overrideNode.isNull -> true
        overrideNode.isBoolean -> overrideNode.booleanValue()
        else -> current.overrideRecommendedChannel
    }

    return RecommendedChannelConfiguration(channel, overrideRecommendedChannel)
}
