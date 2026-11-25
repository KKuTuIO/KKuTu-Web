package me.kkutuio.kkutuweb.support

import me.kkutuio.kkutuweb.identity.IdpException
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.util.Base64

/** 요청에 실려 온 첨부 파일 하나. [data] 는 base64 다. */
data class AttachmentUpload(val name: String? = null, val data: String? = null)

/** 검사를 마친 첨부 파일. [contentType] 은 보낸 쪽의 말이 아니라 내용으로 정한다. */
class SupportFile(val name: String, val contentType: String, val bytes: ByteArray)

object SupportFiles {
    const val MAX_BYTES = 2 * 1024 * 1024
    const val MAX_PER_MESSAGE = 3
    /** 계정 하나가 하루에 올릴 수 있는 파일 수. */
    const val UPLOADS_PER_DAY = 20
    private const val NAME_MAX = 120
    private const val MAX_ENCODED = (MAX_BYTES + 2) / 3 * 4

    val EXTENSIONS = listOf("png", "jpg", "jpeg", "gif", "webp", "pdf", "txt", "log")

    private class Kind(val contentType: String, val extensions: List<String>, val matches: (ByteArray) -> Boolean)

    private val signed = listOf(
        Kind("image/png", listOf("png")) { it.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) },
        Kind("image/jpeg", listOf("jpg", "jpeg")) { it.startsWith(0xFF, 0xD8, 0xFF) },
        Kind("image/gif", listOf("gif")) { it.ascii(0, "GIF87a") || it.ascii(0, "GIF89a") },
        Kind("image/webp", listOf("webp")) { it.ascii(0, "RIFF") && it.ascii(8, "WEBP") },
        Kind("application/pdf", listOf("pdf")) { it.ascii(0, "%PDF-") }
    )

    fun read(uploads: List<AttachmentUpload>?): List<SupportFile> {
        if (uploads.isNullOrEmpty()) return emptyList()
        if (uploads.size > MAX_PER_MESSAGE) throw invalid("파일은 한 번에 ${MAX_PER_MESSAGE}개까지 첨부할 수 있습니다.")
        return uploads.map(::readOne)
    }

    private fun readOne(upload: AttachmentUpload): SupportFile {
        val name = cleanName(upload.name)
        val data = upload.data.orEmpty()
        if (data.length > MAX_ENCODED) throw tooLarge()
        val bytes = runCatching { Base64.getDecoder().decode(data) }.getOrElse { throw invalid("파일을 읽지 못했습니다.") }
        if (bytes.isEmpty()) throw invalid("빈 파일은 첨부할 수 없습니다.")
        if (bytes.size > MAX_BYTES) throw tooLarge()
        val extension = name.substringAfterLast('.', "").lowercase()
        val kind = signed.firstOrNull { it.matches(bytes) }
        if (kind != null) {
            // 이름의 확장자가 내용과 다르면 내용에 맞춘다.
            val fixed = if (extension in kind.extensions) name else name.substringBeforeLast('.') + "." + kind.extensions.first()
            return SupportFile(fixed, kind.contentType, bytes)
        }
        if ((extension == "txt" || extension == "log") && bytes.none { it == 0.toByte() }) return SupportFile(name, "text/plain", bytes)
        throw invalid("${EXTENSIONS.joinToString(", ")} 파일만 첨부할 수 있습니다.")
    }

    /** 경로와 제어 문자를 걷어낸 파일 이름. 길면 확장자를 남기고 줄인다. */
    fun cleanName(raw: String?): String {
        val name = SupportText.singleLine(raw?.substringAfterLast('/')?.substringAfterLast('\\'))
            .replace(Regex("[<>:\"|?*]"), "_").trim('.', ' ')
        if (name.isEmpty()) return "file"
        if (name.length <= NAME_MAX) return name
        val extension = name.substringAfterLast('.', "").take(10)
        return name.take(NAME_MAX - extension.length - 1) + "." + extension
    }

    /** 화면 안에서 열리지 않고 항상 내려받게 한다. */
    fun download(name: String, contentType: String, bytes: ByteArray): ResponseEntity<ByteArray> = ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(contentType))
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, Charsets.UTF_8).build().toString())
        .header("X-Content-Type-Options", "nosniff")
        .body(bytes)

    private fun ByteArray.startsWith(vararg signature: Int): Boolean =
        size >= signature.size && signature.indices.all { this[it] == signature[it].toByte() }

    private fun ByteArray.ascii(offset: Int, text: String): Boolean =
        size >= offset + text.length && text.indices.all { this[offset + it] == text[it].code.toByte() }

    private fun invalid(message: String) = IdpException("invalid_attachment", message)
    private fun tooLarge() = IdpException("attachment_too_large", "파일 하나는 ${MAX_BYTES / 1024 / 1024}MB까지 첨부할 수 있습니다.", 413)
}
