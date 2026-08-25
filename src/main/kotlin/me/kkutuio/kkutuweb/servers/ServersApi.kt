/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.kkutuio.kkutuweb.servers

import me.kkutuio.kkutuweb.extension.isGuest
import me.kkutuio.kkutuweb.game.GameClientManager
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.view.RedirectView
import jakarta.servlet.http.HttpSession

@RestController
class ServersApi(
    @Autowired private val kKuTuSetting: KKuTuSetting,
    @Autowired private val gameClientManager: GameClientManager,
    @Autowired private val recommendedChannelService: RecommendedChannelService
) {
    @GetMapping("/servers")
    fun getServers(): ServersResponse {
        return ServersResponse(gameClientManager.getPlayers(), kKuTuSetting.getMaxPlayers())
    }

    @GetMapping("/game/recommended")
    fun redirectToRecommendedServer(session: HttpSession): RedirectView {
        val servers = gameClientManager.getPlayers()
        val configuration = recommendedChannelService.getConfiguration()
        val allowedChannels = if (session.isGuest()) GUEST_CHANNELS else null
        val channel = selectRecommendedChannel(
            servers,
            kKuTuSetting.getMaxPlayers(),
            configuration,
            allowedChannels
        )

        return RedirectView("/game/server/$channel")
    }
}

internal fun selectRecommendedChannel(
    servers: List<Int?>,
    maxPlayers: Int,
    configuration: RecommendedChannelConfiguration,
    allowedChannels: Set<Int>? = null
): Int {
    val preferredChannel = configuration.channel
    fun isAllowed(channel: Int) = allowedChannels == null || channel in allowedChannels
    val fallbackChannel = preferredChannel.takeIf(::isAllowed)
        ?: allowedChannels?.minOrNull()
        ?: preferredChannel

    if (configuration.overrideRecommendedChannel && isAllowed(preferredChannel)) {
        return if (servers.getOrNull(preferredChannel) != null) {
            preferredChannel
        } else {
            servers.indices.firstOrNull { index -> isAllowed(index) && servers[index] != null }
                ?: fallbackChannel
        }
    }

    var busiestChannel: Int? = null
    var busiestChannelPlayers = -1
    servers.forEachIndexed { index, players ->
        if (isAllowed(index) &&
            players != null &&
            players < maxPlayers &&
            players > busiestChannelPlayers
        ) {
            busiestChannel = index
            busiestChannelPlayers = players
        }
    }

    val preferredChannelPlayers = servers.getOrNull(preferredChannel)
    if (isAllowed(preferredChannel) &&
        preferredChannelPlayers != null &&
        preferredChannelPlayers < maxPlayers &&
        busiestChannelPlayers - preferredChannelPlayers <= RECOMMENDED_CHANNEL_PLAYER_MARGIN
    ) {
        return preferredChannel
    }

    return busiestChannel ?: fallbackChannel
}

private const val RECOMMENDED_CHANNEL_PLAYER_MARGIN = 10
private val GUEST_CHANNELS = setOf(0, 1)
