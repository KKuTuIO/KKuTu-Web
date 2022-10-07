package me.kkutuio.kkutuweb.handler

import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.slf4j.LoggerFactory

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    // 오류가 발생했지만, 핸들링이 되지 않았을 경우 이곳에서 처리한다.
    @ExceptionHandler(Exception::class)
    fun unhandledException(e: Exception) : String {
        logger.error("Got unhandled exception : ", e)
        return "{\"error\":470}"
    }
}