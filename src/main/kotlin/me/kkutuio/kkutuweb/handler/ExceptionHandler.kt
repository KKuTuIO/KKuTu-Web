package me.kkutuio.kkutuweb.handler

import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ResponseStatusException::class)
    fun responseStatusException(e: ResponseStatusException): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.status(e.status).body(
            mapOf("error" to e.status.value(), "message" to e.reason)
        )

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalidArgument(e: IllegalArgumentException): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.badRequest().body(
            mapOf("error" to 400, "message" to e.message)
        )

    // 오류가 발생했지만, 핸들링이 되지 않았을 경우 이곳에서 처리한다.
    @ExceptionHandler(Exception::class)
    fun unhandledException(e: Exception) : String {
        logger.error("Got unhandled exception : ", e)
        return "{\"error\":470}"
    }
}
