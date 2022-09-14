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

package me.kkutuio.kkutuweb

import me.kkutuio.kkutuweb.block.BlockService
import me.kkutuio.kkutuweb.extension.getIp
import me.kkutuio.kkutuweb.geo.GeoService
import me.kkutuio.kkutuweb.ip.IpCheckService
import me.kkutuio.kkutuweb.locale.LocalePropertyLoader
import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.ranking.NicknameCacheService
import me.kkutuio.kkutuweb.session.SessionDao
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import me.kkutuio.kkutuweb.setup.SetupService
import me.kkutuio.kkutuweb.view.View
import me.kkutuio.kkutuweb.view.Views.getView
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.support.RequestContextUtils
import java.util.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpSession
import kotlin.streams.asSequence

@Controller
class MainController(
    @Autowired private val kKuTuSetting: KKuTuSetting,
    @Autowired private val loginService: LoginService,
    @Autowired private val setupService: SetupService,
    @Autowired private val blockService: BlockService,
    @Autowired private val ipCheckService: IpCheckService,
    @Autowired private val geoService: GeoService,
    @Autowired private val nicknameCacheService: NicknameCacheService,
    @Autowired private val sessionDao: SessionDao,
    @Autowired private val aeS256: AES256,
    @Autowired private val localePropertyLoader: LocalePropertyLoader
) {
    private val logger = LoggerFactory.getLogger(MainController::class.java)

    @GetMapping
    fun main(
        @RequestParam(required = false) server: Short?,
        model: Model, session: HttpSession, request: HttpServletRequest
    ): String {
        val isMobile = model.getAttribute("mobile") as Boolean
        val mobileLogText = if (isMobile) " (모바일)" else ""

        val sessionProfile = loginService.getSessionProfile(session)
        val isGuest = sessionProfile == null

        if (!isGuest && setupService.needSetup(sessionProfile!!)) {
            return "redirect:/setup"
        }

        if (server == null) {
            model.addAttribute("viewName", request.getView(View.REACT))
        } else {
            val ip = request.getIp()
            if (isGuest) {
                val blacklistType = ipCheckService.getBlacklistType(ip)
                if (blacklistType != null) {
                    session.setAttribute("loginReason", "모바일 네트워크 이용자(3G,4G,LTE,5G)는 로그인 후 게임 이용이 가능합니다.")
                    return "redirect:/login"
                }
                try {
                    val geoCountry = geoService.getGeoCountry(ip)
                    if (geoCountry != null && geoCountry != "KR") {
                        session.setAttribute("loginReason", "해외 이용자는 로그인 후 게임 이용이 가능합니다.")
                        logger.info("[$ip] 해외에서 접속하여 로그인 페이지로 이동합니다. 국가: $geoCountry")
                        return "redirect:/login"
                    }
                } catch (e: Exception) {
                    logger.warn("[$ip] 국가 정보를 가져오는 중 문제가 발생했습니다.", e)
                }
            }

            if (blockService.getBlockStatus(request).blocked) {
                logger.info("[$ip] 정지된 상태로 서버 접속을 시도했습니다.$mobileLogText - 서버: $server")
                return "redirect:/"
            }

            val randomSid = generateRandomSid()
            if (!isGuest) {
                sessionDao.insert(sessionProfile!!, randomSid)
            }

            val locale = RequestContextUtils.getLocale(request)
            val messages = localePropertyLoader.getMessages(locale)

            val gameServers = kKuTuSetting.getGameServers()
            val gameServer = gameServers[if (gameServers.size <= server) 0 else server.toInt()]
            val webSocketUrl =
                (if (gameServer.isSecure) "wss" else "ws") + "://" + gameServer.publicHost + ":" + gameServer.port

            val nickname: String = sessionProfile?.title ?: (messages["kkutu.dialog.room.room-title.guest"]
                ?: error("kkutu.dialog.room.room-title.guest 언어 설정을 찾을 수 없습니다."))

            model.addAttribute("version", kKuTuSetting.getVersion())
            model.addAttribute("websocketUrl", webSocketUrl + "/" + aeS256.encrypt(randomSid))
            model.addAttribute("nickname", nickname)
            model.addAttribute("moremiParts", kKuTuSetting.getMoremiParts().joinToString(","))
            model.addAttribute("moremiCategories", kKuTuSetting.getMoremiCategories())
            model.addAttribute("moremiEquips", kKuTuSetting.getMoremiEquips().joinToString(","))
            model.addAttribute("moremiGroups", kKuTuSetting.getMoremiGroups())
            model.addAttribute("gameRules", kKuTuSetting.getGameRules())
            model.addAttribute("gameOptions", kKuTuSetting.getGameOptions())
            model.addAttribute("gameOptionMap", kKuTuSetting.getGameOptionMap())
            model.addAttribute("gameModes", kKuTuSetting.getGameModes())

            val injeongPickExcepts = kKuTuSetting.getInjeongPickExcepts()
            val koThemes = ArrayList<String>()
            koThemes.addAll(kKuTuSetting.getKoThemes())
            koThemes.addAll(kKuTuSetting.getKoInjeongThemes())
            koThemes.removeAll(injeongPickExcepts)

            val enThemes = ArrayList<String>()
            enThemes.addAll(kKuTuSetting.getEnThemes())
            enThemes.addAll(kKuTuSetting.getEnInjeongThemes())
            enThemes.removeAll(injeongPickExcepts)

            model.addAttribute("koThemes", koThemes)
            model.addAttribute("enThemes", enThemes)

            model.addAttribute("viewName", request.getView(View.KKUTU))

            if (isGuest) {
                logger.info("[$ip] 손님으로 게임에 접속했습니다.$mobileLogText - 서버: $server")
            } else {
                nicknameCacheService.clearNicknameCache(sessionProfile!!.id)

                logger.info("[$ip] $nickname(${sessionProfile.id}) 님이 게임에 접속했습니다.$mobileLogText - 서버: $server")
            }
        }

        return request.getView(View.LAYOUT)
    }

    fun generateRandomSid(): String {
        val source = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return Random().ints(32, 0, source.length)
            .asSequence()
            .map(source::get)
            .joinToString("")
    }
}