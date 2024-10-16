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

package me.kkutuio.kkutuweb.admin.api.response

enum class RestResult(val message: String) {
    SUCCESS("요청하신 작업을 성공했습니다."),
    UNAUTHENTICATED("인증되지 않은 요청입니다."),
    UNAUTHORIZED("권한이 없는 회원입니다."),
    INVALID_DATA("유효하지 않은 요청입니다."),
    UID_MISMATCH("보안 코드가 일치하지 않습니다."),
    NO_MEMBERSHIP("멤버십을 구독한 이력이 없는 회원입니다."),
    INTERNAL_ERROR("내부 오류가 발생했습니다.")
}