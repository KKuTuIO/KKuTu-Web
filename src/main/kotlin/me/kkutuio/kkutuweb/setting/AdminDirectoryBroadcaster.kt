package me.kkutuio.kkutuweb.setting

import me.kkutuio.kkutuweb.game.GameClientManager
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/** Pushes the cached game-admin UUID snapshot to the Game control sockets. */
@Component
class AdminDirectoryBroadcaster(
    private val setting: KKuTuSetting,
    private val gameClientManager: GameClientManager
) {
    @EventListener(ApplicationReadyEvent::class)
    fun broadcastInitial() = broadcast()

    fun broadcast() {
        gameClientManager.publishAdminDirectory(setting.getGameAdminIds())
    }
}
