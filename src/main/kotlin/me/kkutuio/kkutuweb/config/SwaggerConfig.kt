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

package me.kkutuio.kkutuweb.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig(
    private val setting: KKuTuSetting
) {
    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .openapi("4.2.2")
        .info(
            Info()
                .title("끄투리오 웹 API")
                .description("글자로 놀자, 끄투리오 웹 API 문서입니다.")
                .version(setting.getVersion())
        )
        .components(
            Components().addSecuritySchemes(
                BEARER_SCHEME,
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("KKuTuIO-Admin OAuth access token")
            )
        )

    /** Preserve the old contract: only Admin operations advertise bearer authentication. */
    @Bean
    fun adminApiSecurity(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        openApi.paths.orEmpty()
            .filterKeys { it.startsWith("/api/admin/") }
            .values
            .flatMap { it.readOperations() }
            .forEach { it.addSecurityItem(SecurityRequirement().addList(BEARER_SCHEME)) }
    }

    private companion object {
        const val BEARER_SCHEME = "Bearer"
    }
}
