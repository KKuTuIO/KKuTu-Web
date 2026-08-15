/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package me.kkutuio.kkutuweb.geo

import com.ip2location.IP2Location
import com.ip2location.IPResult
import me.kkutuio.kkutuweb.ip.IpAddressMatcher
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Paths
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

data class GeoIpInfo(
    val countryCode: String?,
    val countryName: String?,
    val regionName: String?,
    val cityName: String?,
    val latitude: Double?,
    val longitude: Double?,
    val zipCode: String?,
    val timeZone: String?,
    val asn: String?,
    val asName: String?,
    val asCidr: String?,
    val domesticExempt: Boolean
)

@Service
class GeoService(
    private val kKuTuSetting: KKuTuSetting
) {
    private val logger = LoggerFactory.getLogger(GeoService::class.java)
    private var locationDatabase: IP2Location? = null
    private var asnDatabase: IP2Location? = null
    private var domesticExemptMatchers: List<IpAddressMatcher> = emptyList()

    @PostConstruct
    fun init() {
        domesticExemptMatchers = kKuTuSetting.getGeoIpDomesticExemptCidrs().map { cidr ->
            runCatching { IpAddressMatcher(cidr) }.getOrElse {
                throw IllegalArgumentException("올바르지 않은 GeoIP 국내 예외 CIDR입니다: $cidr", it)
            }
        }
        locationDatabase = openDatabase(kKuTuSetting.getGeoIpDb11Path(), "DB11")?.also { database ->
            if (database.GetPackageVersion() != "11") {
                database.Close()
                throw IllegalStateException("GeoIP 위치 데이터는 IP2Location DB11이어야 합니다.")
            }
        }
        asnDatabase = openDatabase(kKuTuSetting.getGeoIpAsnPath(), "ASN LITE")
    }

    private fun openDatabase(path: String, label: String): IP2Location? {
        if (path.isBlank() || !Files.isRegularFile(Paths.get(path))) {
            logger.warn("GeoIP $label BIN 파일을 찾을 수 없습니다: $path")
            return null
        }
        return runCatching {
            IP2Location().also { database ->
                database.Open(path, true)
                logger.info("GeoIP $label BIN을 불러왔습니다. package=${database.GetPackageVersion()}, path=$path")
            }
        }.onFailure {
            logger.error("GeoIP $label BIN을 불러오지 못했습니다: $path", it)
        }.getOrNull()
    }

    @Cacheable(value = ["ipGeoInfoCache"], key = "#ip")
    fun getGeoInfo(ip: String): GeoIpInfo? {
        val domesticExempt = isDomesticExempt(ip)
        val location = query(locationDatabase, ip)
        val asn = query(asnDatabase, ip)
        if (location == null && asn == null && !domesticExempt) return null

        return GeoIpInfo(
            countryCode = clean(location?.countryShort),
            countryName = clean(location?.countryLong),
            regionName = clean(location?.region),
            cityName = clean(location?.city),
            latitude = location?.latitude?.toDouble(),
            longitude = location?.longitude?.toDouble(),
            zipCode = clean(location?.zipCode),
            timeZone = clean(location?.timeZone),
            asn = clean(asn?.asn),
            asName = clean(asn?.`as`),
            asCidr = clean(asn?.getASCIDR()),
            domesticExempt = domesticExempt
        )
    }

    fun isDomesticExempt(ip: String): Boolean = domesticExemptMatchers.any { matcher ->
        runCatching { matcher.matches(ip) }.getOrDefault(false)
    }

    private fun query(database: IP2Location?, ip: String): IPResult? {
        if (database == null) return null
        return runCatching {
            synchronized(database) { database.IPQuery(ip) }
        }.onFailure {
            logger.warn("[$ip] 로컬 GeoIP BIN 조회에 실패했습니다: ${it.message}")
        }.getOrNull()?.takeIf { result ->
            result.status.equals("OK", ignoreCase = true)
        }
    }

    private fun clean(value: String?): String? {
        val normalized = value?.trim() ?: return null
        return normalized.takeIf {
            it.isNotEmpty() && it != "?" && it != "-" &&
                !it.equals("NOT SUPPORTED", ignoreCase = true) &&
                !it.equals("INVALID IP ADDRESS", ignoreCase = true)
        }
    }

    @PreDestroy
    fun close() {
        runCatching { locationDatabase?.Close() }
        runCatching { asnDatabase?.Close() }
    }
}
