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

 package me.kkutuio.kkutuweb.user

 import org.springframework.beans.factory.annotation.Autowired
 import org.springframework.http.MediaType
 import org.springframework.web.bind.annotation.*
 import javax.servlet.http.HttpSession
 import me.kkutuio.kkutuweb.extension.isGuest
 import me.kkutuio.kkutuweb.extension.getOAuthUser
 
 @RestController
 class UserApi(
     @Autowired private val userService: UserService
 ) {
     @GetMapping("/box", produces = [MediaType.APPLICATION_JSON_VALUE])
     fun getBox(session: HttpSession): String {
         return userService.getBox(session)
     }
 
     @PostMapping("/exordial", produces = [MediaType.APPLICATION_JSON_VALUE])
     fun exordial(
         @RequestParam data: String,
         session: HttpSession
     ): String {
         return userService.exordial(data, session)
     }
 
     @PostMapping("/equip/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
     fun equip(
         @PathVariable id: String,
         @RequestParam(required = false, defaultValue = "false") isLeft: Boolean,
         session: HttpSession
     ): String {
         return userService.equip(id, isLeft, session)
     }
 
     @GetMapping("/user/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
     fun getUserData(
         @PathVariable id: String,
         session: HttpSession
     ): String {
         return userService.getUserData(id)
     }
 
    @GetMapping("/user/oauth", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getUserOAuthData(
        session: HttpSession
    ): Map<String, Any> {
        return if (!session.isGuest()) {
            val userString = session.getOAuthUser().toString()
            val regex = Regex("""OAuthUser\(authVendor=(\w+), vendorId=(\w+), name=([^,]+), profileImage=([^,]+), gender=([^,]+), minAge=([^,]+), maxAge=([^,]+)\)""")
            val matchResult = regex.find(userString)

            if (matchResult != null) {
                val (authVendor, vendorId, name, profileImage, gender, minAge, maxAge) = matchResult.destructured
                mapOf(
                    "authVendor" to authVendor,
                    "vendorId" to vendorId,
                    "name" to name,
                    "image" to profileImage.replace("=s50", ""),
                    "gender" to gender,
                    "minAge" to minAge,
                    "maxAge" to maxAge
                )
            } else {
                mapOf("status" to "Parsing error")
            }
        } else {
            mapOf("status" to "Guest user")
        }
    }
 
     @GetMapping("/idFromNick/{nick}", produces = [MediaType.APPLICATION_JSON_VALUE])
     fun getIdFromNick(
         @PathVariable nick: String,
         session: HttpSession
     ): String {
         return userService.getIdFromNick(nick)
     }
 }