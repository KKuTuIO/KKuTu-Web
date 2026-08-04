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

package me.kkutuio.kkutuweb.geo

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class GeoIpInfo(
    val countryCode: String?,
    val countryName: String?,
    val asn: String?,
    val asName: String?,
    val isp: String?,
    val network: String?
)

@Service
class GeoService(
    @Value("\${geo.api-key}") private val apiKey: String,
    @Value("\${geo.api-domain}") private val apiDomain: String,
    @Autowired private val objectMapper: ObjectMapper
) {
    @Cacheable(value = ["ipGeoInfoCache"], key = "#ip")
    fun getGeoInfo(ip: String): GeoIpInfo {
        val response = requestHttp("${apiDomain}/lookup/${ip}?key=${apiKey}")
        val jsonNode = objectMapper.readTree(response)
        fun text(vararg paths: String): String? = paths.asSequence()
            .map { jsonNode.at(it) }
            .firstOrNull { !it.isMissingNode && !it.isNull && it.asText().isNotBlank() }
            ?.asText()
        return GeoIpInfo(
            countryCode = text("/geoLocation/country", "/countryCode", "/country_code"),
            countryName = text("/geoLocation/countryName", "/countryName", "/country_name"),
            asn = text(
                "/network/asn", "/asn", "/traits/autonomous_system_number",
                "/connection/asn"
            )?.removePrefix("AS"),
            asName = text(
                "/network/asName", "/network/organization", "/asName", "/org",
                "/traits/autonomous_system_organization", "/connection/org"
            ),
            isp = text("/network/isp", "/isp", "/connection/isp"),
            network = text("/network/cidr", "/network/network", "/cidr")
        )
    }

    @Cacheable(value = ["ipGeoCountryCache"], key = "#ip")
    fun getGeoCountry(ip: String): String? = getGeoInfo(ip).countryCode

    fun requestHttp(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 2000
        conn.readTimeout = 3000
        conn.addRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11; RMX3242 Build/RP1A.200720.011; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/90.0.4430.210 Mobile Safari/537.36 GSA/12.34.17.23.arm64")

        BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { br ->
            val readLine = br.readLine()

            conn.disconnect()
            return readLine
        }
    }
}
