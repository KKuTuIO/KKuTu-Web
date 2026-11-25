package me.kkutuio.kkutuweb.support

import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/** 잘못된 요청과 저장소 오류가 "알 수 없는 오류(500)"로 나가지 않게 한다. */
@RestControllerAdvice(assignableTypes = [SupportApi::class, SupportAdminApi::class])
@Order(Ordered.HIGHEST_PRECEDENCE)
class SupportExceptionAdvice {
    private val logger = LoggerFactory.getLogger(SupportExceptionAdvice::class.java)

    @ExceptionHandler(HttpMessageNotReadableException::class, MethodArgumentTypeMismatchException::class, MissingRequestHeaderException::class)
    fun malformed(): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to "invalid_request", "error_description" to "요청 형식이 올바르지 않습니다."))

    @ExceptionHandler(DataAccessException::class)
    fun storage(error: DataAccessException): ResponseEntity<Map<String, String>> {
        logger.error("문의 저장소 오류", error)
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(mapOf("error" to "temporarily_unavailable", "error_description" to "지금은 문의를 처리할 수 없습니다. 잠시 후 다시 시도해 주세요."))
    }
}
