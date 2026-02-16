package io.github.kez.sample.web.message.listener.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 웹-네이티브 통신을 위한 타입 안전한 메시지 프로토콜
 *
 * 이 sealed interface는 JavaScript와 Android 간의 양방향 통신에서
 * 사용되는 모든 메시지 유형을 정의합니다.
 */
@Serializable
sealed interface WebBridgeMessage {
    /** 요청-응답 매칭을 위한 고유 식별자 */
    val id: String

    /**
     * JavaScript에서 Native로 전송되는 요청 메시지
     *
     * @property id 고유 요청 ID (응답 매칭용)
     * @property action 실행할 액션 이름 (예: "getUserInfo", "saveData")
     * @property payload 액션에 필요한 추가 데이터
     */
    @Serializable
    data class Request(
        override val id: String,
        val action: String,
        val payload: Map<String, String> = emptyMap()
    ) : WebBridgeMessage

    /**
     * Native에서 JavaScript로 전송되는 응답 메시지
     *
     * @property id 원본 요청과 매칭되는 ID
     * @property success 요청 처리 성공 여부
     * @property data 성공 시 반환 데이터 (JSON 문자열)
     * @property error 실패 시 에러 메시지
     */
    @Serializable
    data class Response(
        override val id: String,
        val success: Boolean,
        val data: String? = null,
        val error: String? = null
    ) : WebBridgeMessage
}

/**
 * WebBridgeMessage의 직렬화/역직렬화를 담당하는 유틸리티 객체
 */
object WebBridgeSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    /**
     * JSON 문자열을 Request 객체로 파싱
     *
     * @param raw JavaScript에서 전송된 JSON 문자열
     * @return 파싱된 Request 또는 파싱 실패 시 null
     */
    fun parseRequest(raw: String): WebBridgeMessage.Request? {
        return runCatching {
            json.decodeFromString<WebBridgeMessage.Request>(raw)
        }.getOrNull()
    }

    fun extractRequestId(raw: String): String? {
        return runCatching {
            json.parseToJsonElement(raw).jsonObject["id"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    /**
     * Response 객체를 JSON 문자열로 직렬화
     *
     * @param response JavaScript로 전송할 Response 객체
     * @return JSON 문자열
     */
    fun serializeResponse(response: WebBridgeMessage.Response): String {
        return json.encodeToString(WebBridgeMessage.Response.serializer(), response)
    }
}
