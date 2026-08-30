/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package me.kkutuio.kkutuweb.academy

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.time.Duration
import kotlin.math.ln
import kotlin.math.roundToInt

private const val CORPUS_SCHEMA = 1
private const val CORPUS_MAX_AGE_SECONDS = 15_552_000 // 180 days

data class AcademyCorpusManifest(
    val schema: Int,
    val lang: String,
    val version: String,
    val wordCount: Int,
    val url: String
)

private data class AcademyCorpusPackKey(val lang: String, val generation: Long)
private data class AcademyCorpusPack(
    val lang: String,
    val version: String,
    val wordCount: Int,
    val bytes: ByteArray
)

@Service
class AcademyCorpusPackService(private val corpusService: AcademyCorpusService) {
    private val packs = Caffeine.newBuilder()
        .maximumSize(8)
        .expireAfterAccess(Duration.ofHours(24))
        .build<AcademyCorpusPackKey, AcademyCorpusPack>()

    fun manifest(rawLang: String): AcademyCorpusManifest {
        val pack = current(rawLang)
        return AcademyCorpusManifest(
            schema = CORPUS_SCHEMA,
            lang = pack.lang,
            version = pack.version,
            wordCount = pack.wordCount,
            url = "/api/academy/corpus/v1/${pack.lang}/${pack.version}.kwdb"
        )
    }

    fun find(rawLang: String, version: String): ByteArray? {
        val lang = normalizeLang(rawLang)
        packs.asMap().values.firstOrNull { it.lang == lang && it.version == version }?.let { return it.bytes }
        val current = current(lang)
        return current.bytes.takeIf { current.version == version }
    }

    private fun current(rawLang: String): AcademyCorpusPack {
        val lang = normalizeLang(rawLang)
        val key = AcademyCorpusPackKey(lang, corpusService.currentVersion())
        return packs.get(key) { build(lang) }!!
    }

    private fun build(lang: String): AcademyCorpusPack {
        val words = corpusService.clientPackWords(lang).sortedBy(AcademyCorpusWord::word)
        val buffer = ByteArrayOutputStream(words.size.coerceAtLeast(1) * 12)
        DataOutputStream(buffer).use { out ->
            out.write(byteArrayOf('K'.code.toByte(), 'W'.code.toByte(), 'D'.code.toByte(), 'B'.code.toByte()))
            out.writeByte(CORPUS_SCHEMA)
            out.writeByte(if (lang == "ko") 0 else 1)
            out.writeInt(words.size)
            for (word in words) {
                writeUtf8(out, word.word)
                out.writeShort(word.flags and 0xFFFF)
                out.writeByte(popularityBucket(word.hit))
                out.writeByte(if (word.publishedOverride) 1 else 0)
                writeUtf8(out, word.theme)
            }
        }
        val bytes = buffer.toByteArray()
        val version = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
            .take(24)
        return AcademyCorpusPack(lang, version, words.size, bytes)
    }

    private fun writeUtf8(out: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 65_535) { "코퍼스 문자열이 너무 깁니다." }
        out.writeShort(bytes.size)
        out.write(bytes)
    }

    private fun popularityBucket(hit: Int): Int {
        val score = if (hit <= 0) 0.0 else ln(hit.toDouble() + 1.0) / ln(2.0)
        return (score * 16.0).roundToInt().coerceIn(0, 255)
    }

    private fun normalizeLang(rawLang: String): String = rawLang.lowercase().takeIf { it == "ko" || it == "en" }
        ?: throw IllegalArgumentException("지원하지 않는 언어입니다.")
}

@RestController
class AcademyCorpusPackApi(private val packService: AcademyCorpusPackService) {
    @GetMapping("/api/academy/corpus/manifest/{lang}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun manifest(@PathVariable lang: String): ResponseEntity<AcademyCorpusManifest> = ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
        .header("CDN-Cache-Control", "public, max-age=3600, stale-while-revalidate=86400")
        .body(packService.manifest(lang))

    @GetMapping("/api/academy/corpus/v1/{lang}/{version}.kwdb", produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun corpus(@PathVariable lang: String, @PathVariable version: String): ResponseEntity<ByteArray> {
        val bytes = packService.find(lang, version)
            ?: return ResponseEntity.status(404)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build<ByteArray>()
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=$CORPUS_MAX_AGE_SECONDS, immutable")
            .header("CDN-Cache-Control", "public, max-age=$CORPUS_MAX_AGE_SECONDS, immutable")
            .header(HttpHeaders.ETAG, "\"$version\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(bytes.size.toLong())
            .body(bytes)
    }
}
