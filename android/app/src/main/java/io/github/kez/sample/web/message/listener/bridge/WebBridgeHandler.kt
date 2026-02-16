package io.github.kez.sample.web.message.listener.bridge

import android.os.Build
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * JavaScript에서 호출 가능한 액션들을 처리하는 핸들러
 *
 * 이 클래스는 JavaScript에서 Native로 전송되는 다양한 액션 요청을
 * 처리하고 적절한 응답을 반환합니다.
 *
 * ## 지원 액션
 * - `getUserInfo`: 사용자 정보 조회
 * - `getDeviceInfo`: 디바이스 정보 조회
 * - `saveData`: 데이터 저장
 * - `echo`: 에코 테스트
 */
class WebBridgeHandler {

    private val json = Json { encodeDefaults = true }

    // 임시 저장소 (실제 앱에서는 Repository 사용)
    private val dataStore = mutableMapOf<String, String>()

    /**
     * 액션 요청 처리
     *
     * @param action 실행할 액션 이름
     * @param payload 액션에 필요한 추가 데이터
     * @return 처리 결과 (성공 시 JSON 문자열, 실패 시 Exception)
     */
    fun handleAction(
        action: String,
        payload: Map<String, String>
    ): Result<String> {
        return runCatching {
            when (action) {
                "getUserInfo" -> getUserInfo()
                "getDeviceInfo" -> getDeviceInfo()
                "saveData" -> saveData(payload)
                "getData" -> getData(payload)
                "echo" -> echo(payload)
                "ping" -> ping()
                else -> throw IllegalArgumentException("Unknown action: $action")
            }
        }
    }

    // ========== 액션 구현 ==========

    @Serializable
    private data class UserInfo(
        val id: String,
        val name: String,
        val email: String,
        val isLoggedIn: Boolean
    )

    private fun getUserInfo(): String {
        val userInfo = UserInfo(
            id = "user_123",
            name = "홍길동",
            email = "hong@example.com",
            isLoggedIn = true
        )
        return json.encodeToString(userInfo)
    }

    @Serializable
    private data class DeviceInfo(
        val platform: String,
        val sdkVersion: Int,
        val manufacturer: String,
        val model: String,
        val isEmulator: Boolean
    )

    private fun getDeviceInfo(): String {
        val deviceInfo = DeviceInfo(
            platform = "Android",
            sdkVersion = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            isEmulator = isEmulator()
        )
        return json.encodeToString(deviceInfo)
    }

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.HARDWARE.contains("goldfish") ||
                Build.HARDWARE.contains("ranchu") ||
                Build.PRODUCT.contains("sdk_google") ||
                Build.PRODUCT.contains("vbox86p")
    }

    @Serializable
    private data class SaveResult(
        val saved: Boolean,
        val key: String,
        val timestamp: Long
    )

    private fun saveData(payload: Map<String, String>): String {
        val key = payload["key"]
            ?: throw IllegalArgumentException("Missing required parameter: key")
        val value = payload["value"]
            ?: throw IllegalArgumentException("Missing required parameter: value")

        dataStore[key] = value

        val result = SaveResult(
            saved = true,
            key = key,
            timestamp = System.currentTimeMillis()
        )
        return json.encodeToString(result)
    }

    @Serializable
    private data class GetDataResult(
        val found: Boolean,
        val key: String,
        val value: String?
    )

    private fun getData(payload: Map<String, String>): String {
        val key = payload["key"]
            ?: throw IllegalArgumentException("Missing required parameter: key")

        val value = dataStore[key]

        val result = GetDataResult(
            found = value != null,
            key = key,
            value = value
        )
        return json.encodeToString(result)
    }

    @Serializable
    private data class EchoResult(
        val echo: String,
        val receivedAt: Long
    )

    private fun echo(payload: Map<String, String>): String {
        val message = payload["message"] ?: "empty"
        val result = EchoResult(
            echo = message,
            receivedAt = System.currentTimeMillis()
        )
        return json.encodeToString(result)
    }

    @Serializable
    private data class PingResult(
        val pong: Boolean,
        val timestamp: Long
    )

    private fun ping(): String {
        val result = PingResult(
            pong = true,
            timestamp = System.currentTimeMillis()
        )
        return json.encodeToString(result)
    }
}
