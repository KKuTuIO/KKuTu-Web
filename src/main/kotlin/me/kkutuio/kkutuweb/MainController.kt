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
import me.kkutuio.kkutuweb.session.GameSessionTicketStore
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import me.kkutuio.kkutuweb.setup.SetupService
import me.kkutuio.kkutuweb.view.View
import me.kkutuio.kkutuweb.view.Views.getView
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.support.RequestContextUtils
import java.util.*
import java.security.SecureRandom
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
    @Autowired private val gameSessionTicketStore: GameSessionTicketStore,
    @Autowired private val aeS256: AES256,
    @Autowired private val localePropertyLoader: LocalePropertyLoader
) {
    private val logger = LoggerFactory.getLogger(MainController::class.java)

    @GetMapping
    fun index(@RequestParam(required = false) server: Short?): String {
        return if (server != null) {
            "redirect:/game/server/$server"
        } else {
            if (kKuTuSetting.getServiceMode() == "SIMPLIFIED") {
                "forward:/simplified.html"
            } else {
                "forward:/index.html"
            }
        }
    }

    @GetMapping("/game/server/{server:[0-9]+}")
    fun game(
        @PathVariable server: Short,
        model: Model, session: HttpSession, request: HttpServletRequest
    ): String {
        val isMobile = model.getAttribute("mobile") as Boolean
        val mobileLogText = if (isMobile) " (모바일)" else ""

        val sessionProfile = loginService.getSessionProfile(session)
        val isGuest = sessionProfile == null

        if (!isGuest && setupService.needSetup(sessionProfile!!)) {
            return "redirect:/setup"
        }

        val runnerVersion = kKuTuSetting.getRunnerVersion()
        val cdnHost = kKuTuSetting.getCdnHost()

        model.addAttribute("runnerVersion", runnerVersion)
        model.addAttribute("cdnHost", cdnHost)

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

            val locale = RequestContextUtils.getLocale(request)
            val messages = localePropertyLoader.getMessages(locale)

            val gameServers = kKuTuSetting.getGameServers()
            val gameServer = gameServers.getOrNull(server.toInt())
            if (gameServer == null) {
                return "redirect:/game/server/0"
            }

            val randomSid = generateRandomSid()
            // The route index selects Game cluster SID. `cid` identifies the
            // persistent WebServerClient connection and is not a Game SID.
            gameSessionTicketStore.issue(randomSid, sessionProfile, server.toString())
            val webSocketUrl =
                (if (gameServer.isSecure) "wss" else "ws") + "://" + gameServer.publicHost + ":" + gameServer.port
            val nickname: String = sessionProfile?.title ?: (messages["kkutu.dialog.room.room-title.guest"]
                ?: error("kkutu.dialog.room.room-title.guest 언어 설정을 찾을 수 없습니다."))
            val redactedNickname = nickname.split("#").firstOrNull() ?: nickname

            model.addAttribute("version", kKuTuSetting.getVersion())
            model.addAttribute("websocketUrl", webSocketUrl + "/" + aeS256.encrypt(randomSid))
            model.addAttribute("nickname", redactedNickname)
            model.addAttribute("moremiParts", kKuTuSetting.getMoremiParts().joinToString(","))
            model.addAttribute("moremiCategories", kKuTuSetting.getMoremiCategories())
            model.addAttribute("moremiEquips", kKuTuSetting.getMoremiEquips().joinToString(","))
            model.addAttribute("moremiGroups", kKuTuSetting.getMoremiGroups())
            model.addAttribute("gameRules", kKuTuSetting.getGameRules())
            model.addAttribute("gameOptions", kKuTuSetting.getGameOptions())
            model.addAttribute("gameOptionMap", kKuTuSetting.getGameOptionMap())
            model.addAttribute("gameModes", kKuTuSetting.getGameModes())

            val injeongPickExcepts = kKuTuSetting.getInjeongPickExcepts().toSet()

            val koThemes = kKuTuSetting.getKoThemes() + kKuTuSetting.getKoInjeongThemes() - injeongPickExcepts
            val enThemes = kKuTuSetting.getEnThemes() + kKuTuSetting.getEnInjeongThemes() - injeongPickExcepts

            val calendar = Calendar.getInstance()
            val month = calendar.get(Calendar.MONTH) + 1
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val baseUrl = "https://dl.kkutu.io/img/intro"
            val baseUrl2 = "https://cdn.kkutu.io/img/kkutu/intro/"

            // 절기별 PC/모바일 이미지 매핑
            val pcImage = when (month) {
                in 3..5 -> "$baseUrl2/spring.png"
                in 6..8 -> "$baseUrl/summer.png"
                in 9..11 -> "$baseUrl/fall.png"
                else -> "$baseUrl2/winter.png"
            }
            val mobileImage = when (month) {
                in 3..5 -> "$baseUrl2/m_spring.png"
                in 6..8 -> "$baseUrl/summer_m.png"
                in 9..11 -> "$baseUrl/fall_m.png"
                else -> "$baseUrl2/m_winter.png"
            }

            // 9주년 행사 (PC 전용, 2월 1일~14일)
            val eventImage = null
            // if (month == 2 && day in 1..14) "$baseUrl/9th_1.png" else null

            // 모델에 전달
            model.addAttribute("randomIntroImage", eventImage ?: pcImage)
            model.addAttribute("mobileIntroImage", mobileImage)

            model.addAttribute("koThemes", koThemes)
            model.addAttribute("enThemes", enThemes)

            model.addAttribute("viewName", request.getView(View.KKUTU))

            if (isGuest) {
                logger.info("[$ip] 손님으로 게임에 접속했습니다.$mobileLogText - 서버: $server")
            } else {
                nicknameCacheService.clearNicknameCache(sessionProfile!!.id)

                logger.info("[$ip] $nickname(${sessionProfile.id}) 님이 게임에 접속했습니다.$mobileLogText - 서버: $server")
            }

        return request.getView(View.LAYOUT)
    }

    fun generateRandomSid(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        private val secureRandom = SecureRandom()
    }
}
