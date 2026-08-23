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

import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping
import springfox.documentation.builders.ApiInfoBuilder
import springfox.documentation.builders.PathSelectors
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.service.ApiInfo
import springfox.documentation.service.ApiKey
import springfox.documentation.service.AuthorizationScope
import springfox.documentation.service.SecurityReference
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spi.service.contexts.SecurityContext
import springfox.documentation.spring.web.plugins.Docket
import springfox.documentation.spring.web.plugins.WebMvcRequestHandlerProvider
import springfox.documentation.swagger2.annotations.EnableSwagger2

@Configuration
@EnableSwagger2
class SwaggerConfig(
    @Autowired private val setting: KKuTuSetting
) {
    @Bean
    fun springfoxHandlerProviderBeanPostProcessor(): BeanPostProcessor {
        return object : BeanPostProcessor {
            override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
                if (bean is WebMvcRequestHandlerProvider) {
                    val field = bean.javaClass.getDeclaredField("handlerMappings")
                    field.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val mappings = field.get(bean) as List<RequestMappingInfoHandlerMapping>
                    field.set(bean, mappings.filter { it.patternParser == null })
                }
                return bean
            }
        }
    }

    @Bean
    fun api(): Docket {
        return Docket(DocumentationType.SWAGGER_2)
            .select()
            .apis(RequestHandlerSelectors.basePackage("me.kkutuio.kkutuweb"))
            .paths(PathSelectors.ant("/api/**"))
            .build()
            .apiInfo(apiInfo())
            .useDefaultResponseMessages(false)
            .securitySchemes(listOf(ApiKey("Bearer", "Authorization", "header")))
            .securityContexts(listOf(
                SecurityContext.builder()
                    .securityReferences(listOf(
                        SecurityReference("Bearer", arrayOf(AuthorizationScope("global", "KKuTuIO-Admin OAuth access token")))
                    ))
                    .forPaths(PathSelectors.ant("/api/admin/**"))
                    .build()
            ))
    }

    private fun apiInfo(): ApiInfo {
        return ApiInfoBuilder()
            .title("끄투리오 API")
            .description("서버 컨트롤러에서 자동 생성되는 끄투리오 API 명세")
            .version(setting.getVersion())
            .build()
    }
}
