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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.spring") version "2.4.10"
}

group = "me.kkutuio"
version = "v4"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

springBoot {
    mainClass.set("me.kkutuio.kkutuweb.KkutuWebApplicationKt")
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

tasks.withType<BootJar> {
    manifest {
        attributes["Enable-Native-Access"] = "ALL-UNNAMED"
    }
}

sourceSets {
    main {
        resources {
            exclude("kkutu.json", "kkutu.default.json")
            exclude("oauth.json", "oauth.default.json")
            exclude(
                "**/*.jsx",
                "**/in_game_kkutu_help.js",
                "**/in_game_kkutu_security.js",
                "**/in_game_kkutu.js",
                "**/in_login.js",
                "**/in_portal.js",
                "**/oauth-buttons.js"
            )
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("nz.net.ultraq.thymeleaf:thymeleaf-layout-dialect")

    implementation("de.codecentric:spring-boot-admin-starter-client:4.1.2")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("com.networknt:json-schema-validator:3.0.6")
    implementation("com.neovisionaries:nv-websocket-client:2.14")
    implementation("com.ip2location:ip2location-java:8.13.0")
    implementation("com.github.scribejava:scribejava-apis:8.3.3")
    implementation("org.postgresql:postgresql")
    implementation("com.googlecode.htmlcompressor:htmlcompressor:1.5.2")
    implementation("de.mkammerer:argon2-jvm:2.12")
    implementation("com.nimbusds:nimbus-jose-jwt:10.9.1")
    implementation("com.upokecenter:cbor:4.5.5")

    implementation("io.sentry:sentry-spring-boot-4-starter:8.53.0")
    implementation("io.github.resilience4j:resilience4j-spring-boot4:2.4.0")
    implementation("com.github.ben-manes.caffeine:caffeine")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:3.1.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("org.springframework.security:spring-security-test")
}
