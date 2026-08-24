package me.kkutuio.kkutuweb.servers

import com.fasterxml.jackson.databind.JsonNode
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
    private var recommendedChannel = 0

    fun getRecommendedChannel(): Int = recommendedChannel

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

            if (channel.canConvertToInt() && channel.asInt() >= 0) {
                recommendedChannel = channel.asInt()
            } else {
                logger.warn("Ignoring invalid recommended channel in $SERVER_CONFIG_URL")
            }
        } catch (e: Exception) {
            logger.warn("Failed to refresh recommended channel; retaining channel $recommendedChannel", e)
        }
    }

    companion object {
        private const val SERVER_CONFIG_URL = "https://static.kkutu.io/server.json"
    }
}
