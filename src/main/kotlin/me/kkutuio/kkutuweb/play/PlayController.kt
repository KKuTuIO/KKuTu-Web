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

package me.kkutuio.kkutuweb.play

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import me.kkutuio.kkutuweb.AES256
import me.kkutuio.kkutuweb.block.BlockService
import me.kkutuio.kkutuweb.extension.getIp
import me.kkutuio.kkutuweb.extension.getOAuthUser
import me.kkutuio.kkutuweb.oauth.AuthVendor
import me.kkutuio.kkutuweb.geo.GeoService
import me.kkutuio.kkutuweb.identity.AccountService
import me.kkutuio.kkutuweb.ip.IpCheckService
import me.kkutuio.kkutuweb.locale.LocalePropertyLoader
import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.session.GameSessionTicketStore
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import me.kkutuio.kkutuweb.setup.SetupService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.http.MediaTypeFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.support.RequestContextUtils
import java.security.SecureRandom
import java.util.Base64

/**
 * Backs the Svelte game client at `/play/{n}`. The page itself is a static SPA shell;
 * this issues the one-shot websocket ticket that the Thymeleaf `/game/server/{n}` page
 * used to embed in its HTML.
 */
@Controller
class PlayController(
    @Autowired private val kKuTuSetting: KKuTuSetting,
    @Autowired private val loginService: LoginService,
    @Autowired private val accountService: AccountService,
    @Autowired private val setupService: SetupService,
    @Autowired private val blockService: BlockService,
    @Autowired private val ipCheckService: IpCheckService,
    @Autowired private val geoService: GeoService,
    @Autowired private val ticketStore: GameSessionTicketStore,
    @Autowired private val aes: AES256,
    @Autowired private val localePropertyLoader: LocalePropertyLoader,
    @Autowired private val shopService: me.kkutuio.kkutuweb.shop.ShopService
) {
    private val logger = LoggerFactory.getLogger(PlayController::class.java)
    private val secureRandom = SecureRandom()

    /**
     * Preview gate: the new client is only for accounts the game server treats as administrators (`admin_access.game_admin`, the ones that can
     * run admin commands), so the legacy client stays the only one regular players ever see. Everything the new client needs goes through here.
     */
    private fun isPreviewAllowed(session: HttpSession): Boolean {
        val account = loginService.accountUuid(session) ?: return false
        return account in kKuTuSetting.getGameAdminIds()
    }

    private fun notFound(): ResponseEntity<Any> = ResponseEntity.notFound().build()

    @GetMapping("/play", "/play/")
    fun entry(session: HttpSession): Any =
        if (isPreviewAllowed(session)) "redirect:/play/0" else notFound()

    @GetMapping("/play/{server:[0-9]+}")
    fun shell(@PathVariable server: String, session: HttpSession): ResponseEntity<Any> {
        if (!isPreviewAllowed(session)) return notFound()
        val page = ClassPathResource("play/200.html")
        if (!page.exists()) return notFound()
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .header("Cache-Control", "no-store")
            .header("X-Robots-Tag", "noindex")
            .body(page.contentAsByteArray)
    }

    /** The client's own build output (`/_play/...`, hashed file names), served from the jar behind the same gate. Fonts and art come from cdn.kkutu.io. */
    @GetMapping("/_play/**")
    fun asset(request: HttpServletRequest, session: HttpSession): ResponseEntity<Any> {
        if (!isPreviewAllowed(session)) return notFound()
        val path = request.requestURI.removePrefix("/_play/")
        if (path.isEmpty() || path.contains("..") || path.contains('\\') || path.startsWith("/")) return notFound()
        val file = ClassPathResource("play/_play/$path")
        if (!file.exists() || !file.isReadable) return notFound()
        return ResponseEntity.ok()
            .contentType(MediaTypeFactory.getMediaType(file).orElse(MediaType.APPLICATION_OCTET_STREAM))
            .header("Cache-Control", if (path.startsWith("immutable/")) "private, max-age=31536000, immutable" else "no-cache")
            .body(file.contentAsByteArray)
    }

    /**
     * UI strings for the Svelte client, in the viewer's language (the `user.language` cookie, like the
     * Thymeleaf pages). Fetched before the ticket so even connection errors can be shown translated.
     */
    @GetMapping("/api/play/locale")
    @ResponseBody
    fun locale(request: HttpServletRequest, session: HttpSession): ResponseEntity<Any> {
        if (!isPreviewAllowed(session)) return notFound()
        val locale = RequestContextUtils.getLocale(request)
        val messages = localePropertyLoader.getMessages(locale)
            .filterKeys { it.startsWith("play.") || it.startsWith("game.mode.") || it.startsWith("game.option.") || it.startsWith("kkutu.js.error.") || it.startsWith("kkutu.js.wpFail.") || it.startsWith("word.theme.") || it.startsWith("word.class.") || it.startsWith("server.") || it.startsWith("kkutu.js.turn.error.") || it.startsWith("kkutu.js.bonusFrom.") || it.startsWith("kkutu.dialog.result.") || it.startsWith("kkutu.dialog.accountOptions.") || it.startsWith("kkutu.dialog.survey.") || it.startsWith("kkutu.membership.") || it.startsWith("kkutu.js.settingsFile.") || it.startsWith("kkutu.dialog.replay.") || it.startsWith("kkutu.dialog.report.") || it.startsWith("kkutu.dialog.roomInfo.") || it.startsWith("kkutu.js.pform.") || it == "kkutu.js.sRoomInfo" || it.startsWith("kkutu.js.replay") || it.startsWith("kkutu.product.game.") || it.startsWith("item.") || it.startsWith("game.item.") || it.startsWith("kkutu.js.itemLeft") || it.startsWith("kkutu.dialog.purchase.") || it.startsWith("kkutu.product.shopCart.") || it.startsWith("kkutu.product.shopList.draw.") || it.startsWith("kkutu.dialog.obtain.") || it.startsWith("kkutu.js.opts.") || it in GAME_SCREEN_KEYS }
        return ResponseEntity.ok()
            .header("Cache-Control", "private, max-age=60")
            .header("Vary", "Cookie")
            .body(mapOf("language" to locale.language, "messages" to messages))
    }

    /** Item names and descriptions (`kkutu_shop_desc`), keyed by item id: the legacy page's `GoodDetails`. */
    @GetMapping("/api/play/goods")
    @ResponseBody
    fun goodDetails(session: HttpSession): ResponseEntity<Any> =
        if (!isPreviewAllowed(session)) notFound()
        else ResponseEntity.ok().header("Cache-Control", "private, max-age=60").body(shopService.getGoodDetails())

    /** What the game's screens need to know about the game itself (modes, rules, options, moremi parts, themes): the same for everyone. */
    private fun gameMeta(): Map<String, Any?> {
        // Themes a room can be limited to with 단어장 (same lists as the legacy page's pick dialog).
        val pickExcepts = kKuTuSetting.getInjeongPickExcepts().toSet()
        val pickThemes = mapOf(
            "ko" to (kKuTuSetting.getKoThemes() + kKuTuSetting.getKoInjeongThemes() - pickExcepts),
            "en" to (kKuTuSetting.getEnThemes() + kKuTuSetting.getEnInjeongThemes() - pickExcepts)
        )
        return mapOf(
            "modes" to kKuTuSetting.getGameModes(),
            "rules" to kKuTuSetting.getGameRuleNode(),
            "optionDefs" to kKuTuSetting.getGameOptionNode(),
            "moremiParts" to kKuTuSetting.getMoremiParts(),
            "moremiCategories" to kKuTuSetting.getMoremiCategories(),
            "moremiGroups" to kKuTuSetting.getMoremiGroups(),
            "moremiEquips" to kKuTuSetting.getMoremiEquips(),
            "pickThemes" to pickThemes,
            "version" to kKuTuSetting.getVersion(),
            // room.opts key (lower-cased option name) -> option name, i.e. game.option.<name>.name
            "options" to kKuTuSetting.getGameOptionMap().values.associateBy { it.lowercase() }
        )
    }

    /**
     * The game's metadata without a connection (no ticket is issued): the portal's replay page draws games with it. Public, like the
     * locale and the shop.
     */
    @GetMapping("/api/play/meta")
    @ResponseBody
    fun meta(session: HttpSession): ResponseEntity<Any> =
        if (!isPreviewAllowed(session)) notFound() else ResponseEntity.ok().header("Cache-Control", "private, max-age=300").body(gameMeta())

    @GetMapping("/api/play/{server:[0-9]+}/ticket")
    @ResponseBody
    fun ticket(
        @PathVariable server: Int,
        session: HttpSession,
        request: HttpServletRequest
    ): ResponseEntity<Any> {
        if (!isPreviewAllowed(session)) return notFound()
        fun refuse(status: Int, code: String, reason: String? = null): ResponseEntity<Any> =
            ResponseEntity.status(status).body(mapOf<String, Any?>("code" to code, "reason" to reason))

        val profile = loginService.getSessionProfile(session)
        val guest = profile == null
        if (!guest) {
            val account = accountService.currentAccount(session)
            if (account != null && accountService.isServiceAccessBlocked(account)) return refuse(403, "ACCOUNT_BLOCKED")
            if (setupService.needSetup(profile!!)) {
                // Same edge cases the old /setup page handled before showing the nickname form.
                if (session.getOAuthUser().authVendor == AuthVendor.LOCAL) return refuse(409, "ACCOUNT_REQUIRED")
                if (!loginService.getOAuthServiceFromSession(session).allowRegister) {
                    try { session.invalidate() } catch (_: Exception) {}
                    return refuse(403, "REGISTER_DISABLED")
                }
                return refuse(409, "SETUP_REQUIRED")
            }
        }

        val ip = request.getIp()
        if (guest) {
            if (ipCheckService.getBlacklistType(ip) != null) {
                return refuse(401, "LOGIN_REQUIRED", "모바일 네트워크 이용자(3G,4G,LTE,5G)는 로그인 후 게임 이용이 가능합니다.")
            }
            try {
                val geo = geoService.getGeoInfo(ip)
                if (geo != null && !geo.domesticExempt && geo.countryCode != null && geo.countryCode != "KR") {
                    return refuse(401, "LOGIN_REQUIRED", "해외 이용자는 로그인 후 게임 이용이 가능합니다.")
                }
            } catch (e: Exception) {
                logger.warn("[$ip] 국가 정보를 가져오는 중 문제가 발생했습니다.", e)
            }
        }
        if (blockService.getBlockStatus(request).blocked) return refuse(403, "BLOCKED")

        val gameServer = kKuTuSetting.getGameServers().getOrNull(server) ?: return refuse(404, "NO_SUCH_SERVER")

        val account = accountService.currentAccount(session)
        account?.let { accountService.markClientUsed(it, "new") }

        val sid = ByteArray(32).also(secureRandom::nextBytes)
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        ticketStore.issue(
            sid, profile, server.toString(),
            accountUuid = loginService.accountUuid(session),
            accountFlags = accountService.currentAccount(session)?.flags
        )
        val base = (if (gameServer.isSecure) "wss" else "ws") + "://" + gameServer.publicHost + ":" + gameServer.port
        logger.info("[$ip] ${profile?.title ?: "guest"} /play/$server ticket issued")

        return ResponseEntity.ok()
            .header("Cache-Control", "no-store")
            .body(
                mapOf(
                    "websocketUrl" to "$base/${aes.encrypt(sid)}",
                    "guest" to guest,
                    "nickname" to (profile?.title?.split("#")?.firstOrNull()),
                    // one-time hints this account has closed (account.flags); a guest has none, so sees them all
                    "nudgesClosed" to (accountService.currentAccount(session)?.let(accountService::nudgesClosed) ?: emptyList()),
                    // the game clients this account has opened before (`legacy` / `new`); a guest has none
                    "clientsUsed" to (account?.let(accountService::clientsUsed) ?: emptyList()),
                    // tutorial lessons this account has cleared
                    "tutorialDone" to (accountService.currentAccount(session)?.let(accountService::tutorialDone) ?: emptyList())
                ) + gameMeta()
            )
    }
}

/** Legacy messages the game screen reuses word for word (the board is a port of the legacy client's). */
private val GAME_SCREEN_KEYS = setOf(
    "kkutu.js.surePurchase", "kkutu.js.alreadyGot", "kkutu.js.notEnoughMoney", "kkutu.js.purchased", "kkutu.js.ping", "kkutu.js.loading",
    "kkutu.js.resultNotFound", "kkutu.js.resultNotFound.expl", "kkutu.js.day", "kkutu.js.item", "kkutu.dialog.setting.load", "kkutu.js.putCouponCode", "kkutu.js.participate", "kkutu.js.surveyNewTab", "kkutu.js.hidden", "kkutu.js.itemTerm", "kkutu.js.itemTermed",
    "kkutu.js.invalidItem", "kkutu.js.invalidItem.expl", "kkutu.js.exts.shop", "kkutu.js.exts.cost", "kkutu.js.drawObtain",
    "kkutu.js.sureEquip", "kkutu.js.sureUnequip", "kkutu.js.sureConsume", "kkutu.js.consume", "kkutu.js.invalidConsume", "kkutu.js.notEnoughConsume",
    "kkutu.js.surePayback", "kkutu.js.payback", "kkutu.js.dressWhichHand", "kkutu.js.equipped", "kkutu.js.obtainExp", "kkutu.js.obtainMoney",
    "kkutu.dialog.message.left", "kkutu.dialog.message.right", "kkutu.dialog.profile.okgg", "kkutu.dialog.profile.dress",
    "kkutu.js.second", "kkutu.js.gameLoading", "kkutu.js.roundEnd", "kkutu.js.confirmExit", "kkutu.js.confirmSpectate",
    "kkutu.js.level", "kkutu.js.pts", "kkutu.js.lvUp", "kkutu.js.scoreGain", "kkutu.js.moneyGain", "kkutu.js.epGain",
    "kkutu.js.scoreOrigin", "kkutu.js.moneyOrigin", "kkutu.js.accomplished", "kkutu.js.practice", "kkutu.js.players",
    "kkutu.js.rounds", "kkutu.js.trollWarning", "kkutu.js.themeIs", "kkutu.js.wordIs", "kkutu.js.drawingLineWidth", "kkutu.js.drawingColor", "kkutu.js.drawingClear", "kkutu.product.chat.painterChatBlocked", "kkutu.js.cwVert", "kkutu.js.cwHorz", "kkutu.js.cwL", "kkutu.js.leaveReserved", "kkutu.js.leaveReserveDisabled", "kkutu.menu.reserveLeave", "kkutu.js.jqTheme", "kkutu.js.afterRun", "kkutu.js.kpm", "kkutu.js.statSpectate", "kkutu.js.sureRankGame", "kkutu.js.sureRankGame.on", "kkutu.js.sureRankGame.off", "kkutu.js.roomTitleRestricted", "kkutu.dialog.room.room-title.placeholder", "kkutu.product.chat.warning", "kkutu.product.chat.warningMobile",
)
