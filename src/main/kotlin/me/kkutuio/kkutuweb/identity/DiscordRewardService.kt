package me.kkutuio.kkutuweb.identity

import me.kkutuio.kkutuweb.extension.toJson
import me.kkutuio.kkutuweb.shop.ShopService
import me.kkutuio.kkutuweb.user.UserDao
import org.postgresql.util.PGobject
import org.springframework.stereotype.Service

@Service
class DiscordRewardService(
    private val users: UserDao,
    private val shop: ShopService
) {
    fun grantItem(legacyUserId: String, itemId: String) {
        val user = users.getUser(legacyUserId) ?: throw IdpException("not_found", "게임 프로필을 찾을 수 없습니다.", 404)
        shop.obtainGood(user.box, itemId, 1, 0)
        val box = PGobject().apply { type = "json"; value = user.box.toJson() }
        users.updateUser(user.id, mapOf("box" to box))
    }
}
