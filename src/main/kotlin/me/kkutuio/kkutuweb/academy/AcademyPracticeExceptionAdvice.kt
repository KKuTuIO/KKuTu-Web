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
