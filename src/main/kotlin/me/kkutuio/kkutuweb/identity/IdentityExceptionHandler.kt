package me.kkutuio.kkutuweb.identity

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class IdentityExceptionHandler {
    @ExceptionHandler(IdpException::class)
    fun idp(error: IdpException): ResponseEntity<Map<String, String>> = ResponseEntity.status(error.status).body(mapOf("error" to error.error, "error_description" to (error.message ?: "request failed")))
    @ExceptionHandler(IllegalArgumentException::class)
    fun invalid(error: IllegalArgumentException): ResponseEntity<Map<String, String>> = ResponseEntity.badRequest().body(mapOf("error" to "invalid_request", "error_description" to (error.message ?: "invalid request")))
}
