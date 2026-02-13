package me.kkutuio.kkutuweb.record

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class RecordMapper(
    @Autowired private val objectMapper: ObjectMapper
) {
    fun toGameLookupResponse(raw: String): RecordGameLookupResponse? {
        return try {
            objectMapper.readValue(raw, RecordGameLookupResponse::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun toUserHistoryResponse(raw: String): RecordUserHistoryResponse? {
        return try {
            objectMapper.readValue(raw, RecordUserHistoryResponse::class.java)
        } catch (_: Exception) {
            null
        }
    }
}
