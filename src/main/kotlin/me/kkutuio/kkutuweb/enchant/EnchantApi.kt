package me.kkutuio.kkutuweb.enchant

import jakarta.servlet.http.HttpSession
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*

/** 강화: `GET /enchant` the panel's data, `POST /enchant {item, instance, ticket}` one try on a copy, `POST /enchant/resolve` keep or discard a success on a full copy, `POST /enchant/wear` which copy is worn. */
@RestController
class EnchantApi(
    @Autowired private val enchantService: EnchantService
) {
    @GetMapping("/enchant", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun overview(session: HttpSession) = enchantService.overview(session)

    @GetMapping("/enchant/summary", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun summary(session: HttpSession) = enchantService.summary(session)

    /** `instance` 0 is a plain copy of the item; a number is one of my enchanted copies. */
    @PostMapping("/enchant", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun enchant(@RequestParam item: String, @RequestParam(defaultValue = "0") instance: Int, @RequestParam ticket: String, session: HttpSession) =
        enchantService.enchant(session, item, instance, ticket)

    /** `slot` is the line to replace; leave it out to discard the new one. */
    @PostMapping("/enchant/resolve", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun resolve(@RequestParam item: String, @RequestParam instance: Int, @RequestParam(required = false) slot: Int?, session: HttpSession) =
        enchantService.resolve(session, item, instance, slot)

    /** Which copy of an item I wear (its lines are the ones that count): an enchanted copy's number, or 0 for a plain one. */
    @PostMapping("/enchant/wear", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun wear(@RequestParam item: String, @RequestParam instance: Int, session: HttpSession) =
        enchantService.wear(session, item, instance)
}
