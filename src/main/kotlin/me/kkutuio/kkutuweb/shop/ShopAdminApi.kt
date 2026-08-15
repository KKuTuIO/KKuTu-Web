package me.kkutuio.kkutuweb.shop

import me.kkutuio.kkutuweb.admin.api.response.ListResponse
import me.kkutuio.kkutuweb.moderation.AdminModerationAuthorizer
import me.kkutuio.kkutuweb.setting.AdminSetting
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpSession

@RestController
@RequestMapping("/api/admin")
class ShopAdminApi(
    private val authorizer: AdminModerationAuthorizer,
    private val service: ShopAdminService
) {
    @GetMapping("/shop")
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(defaultValue = "updatedAt,DESC") sort: String,
        @RequestParam(defaultValue = "") query: String,
        @RequestParam(defaultValue = "ALL") queryTarget: String,
        @RequestParam(defaultValue = "CONTAINS") queryMatch: String,
        @RequestParam(defaultValue = "") groups: String,
        @RequestParam(defaultValue = "ALL") saleStatus: String,
        @RequestParam(defaultValue = "ALL") itemStatus: String,
        @RequestParam(defaultValue = "") flags: String,
        @RequestParam(required = false) minCost: Long?,
        @RequestParam(required = false) maxCost: Long?,
        @RequestParam(required = false) minHit: Int?,
        @RequestParam(required = false) maxHit: Int?,
        @RequestParam(required = false) minTerm: Int?,
        @RequestParam(required = false) maxTerm: Int?,
        session: HttpSession
    ): ListResponse<ShopAdminItem> {
        authorizer.require(session, AdminSetting.Privilege.SHOP)
        return service.list(
            page, size, sort, query, queryTarget, queryMatch, groups, saleStatus,
            itemStatus, flags, minCost, maxCost, minHit, maxHit, minTerm, maxTerm
        )
    }

    @PostMapping("/shop")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody body: ShopAdminItemRequest, session: HttpSession): ShopAdminItem {
        val actor = authorizer.require(session, AdminSetting.Privilege.SHOP)
        return service.create(body, actor)
    }

    @PutMapping("/shop/{itemId}")
    fun update(
        @PathVariable itemId: String,
        @RequestBody body: ShopAdminItemRequest,
        session: HttpSession
    ): ShopAdminItem {
        val actor = authorizer.require(session, AdminSetting.Privilege.SHOP)
        return service.update(itemId, body, actor)
    }

    @DeleteMapping("/shop/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable itemId: String, session: HttpSession) {
        val actor = authorizer.require(session, AdminSetting.Privilege.SHOP)
        service.delete(itemId, actor)
    }

    @PostMapping("/shop/refresh")
    fun refresh(session: HttpSession): ShopRefreshResponse {
        val actor = authorizer.require(session, AdminSetting.Privilege.SHOP)
        return service.refresh(actor)
    }

    @GetMapping("/shop-audits")
    fun audits(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "15") size: Int,
        @RequestParam(defaultValue = "created_at,DESC") sort: String,
        @RequestParam(name = "item_id", defaultValue = "") itemId: String,
        @RequestParam(defaultValue = "") action: String,
        @RequestParam(defaultValue = "") admin: String,
        session: HttpSession
    ): ListResponse<ShopAuditEntry> {
        authorizer.require(session, AdminSetting.Privilege.SHOP)
        return service.audits(page, size, sort, itemId, action, admin)
    }
}
