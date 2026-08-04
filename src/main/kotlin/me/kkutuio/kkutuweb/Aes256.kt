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

import me.kkutuio.kkutuweb.extension.toHexString
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.text.Charsets.UTF_8

private const val IV_LENGTH = 16

@Component
class AES256(
    @Autowired private val kKuTuSetting: KKuTuSetting
) {
    private val secretKeySpec: SecretKeySpec? = try {
        SecretKeySpec(kKuTuSetting.getCryptoKey().toByteArray(), "AES")
    } catch (e: Exception) {
        println("Error while generating key: $e")
        null
    }

    fun encrypt(data: String): String {
        val iv = ByteArray(IV_LENGTH)
        SecureRandom.getInstanceStrong().nextBytes(iv)

        val ivParameterSpec = IvParameterSpec(iv)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
        val encrypted = cipher.doFinal(data.toByteArray(UTF_8))

        return iv.toHexString() + ":" + encrypted.toHexString()
    }

    fun decrypt(data: String): String {
        val parts = data.split(':', limit = 2)
        require(parts.size == 2) { "Invalid encrypted value" }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKeySpec,
            IvParameterSpec(parts[0].hexToBytes())
        )
        return String(cipher.doFinal(parts[1].hexToBytes()), UTF_8)
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Invalid hexadecimal value" }
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
