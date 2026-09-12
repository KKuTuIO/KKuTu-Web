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

package me.kkutuio.kkutuweb.setting

data class GameServerSetting(
    val isSecure: Boolean,
    val publicHost: String,
    val key: String,
    val host: String,
    val port: Int,
    val cid: Short,
    val reconnect: GameServerReconnectSetting,
    val name: String = "채널 $cid",
    val management: GameServerManagementSetting? = null
)

/**
 * One-shot SSH management configuration. The SSH target should normally be an
 * alias in the Web host's ~/.ssh/config so credentials never enter kkutu.json.
 */
data class GameServerManagementSetting(
    val sshTarget: String,
    val workingDirectory: String,
    val pm2ProcessName: String,
    val sshPort: Int? = null
) {
    init {
        require(sshTarget.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.@:-]{0,254}"))) { "management.sshTarget contains unsupported characters" }
        require(workingDirectory.isNotBlank() && !workingDirectory.contains('\n') && !workingDirectory.contains('\r')) {
            "management.workingDirectory must be a single non-empty line"
        }
        require(pm2ProcessName.matches(Regex("[A-Za-z0-9_.:-]{1,128}"))) { "management.pm2ProcessName contains unsupported characters" }
        require(sshPort == null || sshPort in 1..65535) { "management.sshPort must be between 1 and 65535" }
    }
}

data class GameServerReconnectSetting(
    val enabled: Boolean,
    /** Fixed reconnect delay in seconds. */
    val retryInterval: Long
) {
    init {
        require(retryInterval > 0) { "game server reconnect.retryInterval must be greater than zero" }
    }
}
