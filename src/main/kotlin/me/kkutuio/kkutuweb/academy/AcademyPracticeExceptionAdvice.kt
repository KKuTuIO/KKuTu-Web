package me.kkutuio.kkutuweb.academy

import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(assignableTypes = [AcademyPracticeApi::class])
class AcademyPracticeExceptionAdvice {
    @ExceptionHandler(AcademyRequestException::class)
    fun academyError(error: AcademyRequestException, response: HttpServletResponse): AcademyErrorResponse {
        response.status = error.status
        return AcademyErrorResponse(error.code, error.message)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalid(error: IllegalArgumentException, response: HttpServletResponse): AcademyErrorResponse {
        response.status = 400
        return AcademyErrorResponse("INVALID_REQUEST", error.message ?: "잘못된 요청입니다.")
    }
}
