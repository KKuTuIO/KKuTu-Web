package me.kkutuio.kkutuweb.support

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** 고객센터(별도 출처의 공개 OAuth 앱) 설정. 기본값만으로 동작해 운영 설정 파일을 고치지 않아도 된다. */
@Component
class SupportSettings(
    /** 고객센터 공개 OAuth 클라이언트의 client_id. */
    @Value("\${idp.support.client-id:kkutuio-cs}") val clientId: String,
    @Value("\${idp.support.allowed-origins:https://cs.kkutu.io}") allowedOrigins: String,
    /** 알림 메일에 넣는 고객센터 주소. */
    @Value("\${kkutu.support.portal-url:https://cs.kkutu.io}") portalUrl: String
) {
    val allowedOrigins: List<String> = allowedOrigins.split(',').map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }
    val portalUrl: String = portalUrl.trim().trimEnd('/')

    companion object {
        const val SCOPE = "support"
    }
}
