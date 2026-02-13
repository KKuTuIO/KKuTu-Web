package me.kkutuio.kkutuweb.record

import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class RecordClientManager(
    @Autowired private val kKuTuSetting: KKuTuSetting
) {
    private val recordClientList = ArrayList<RecordClient>()

    @PostConstruct
    fun init() {
        for (gameServer in kKuTuSetting.getGameServers()) {
            recordClientList.add(
                RecordClient(
                    gameServer.isSecure,
                    gameServer.host,
                    gameServer.port,
                    gameServer.key,
                    gameServer.cid
                )
            )
        }
    }

    fun requestReplayByGameId(gameId: String, includePayload: Boolean): String? {
        for (client in recordClientList) {
            if (!client.isConnected()) continue
            val response = client.requestReplayByGameId(gameId, includePayload)
            if (response != null) return response
        }
        return null
    }

    fun requestReplayUserHistory(userId: String, page: Int, pageSize: Int): String? {
        for (client in recordClientList) {
            if (!client.isConnected()) continue
            val response = client.requestReplayUserHistory(userId, page, pageSize)
            if (response != null) return response
        }
        return null
    }
}
