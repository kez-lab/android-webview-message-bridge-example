package io.github.kez.sample.web.message.listener.bridge

import android.os.Build
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WebBridgeHandler {

    private val json = Json { encodeDefaults = true }

    private val dataStore = mutableMapOf<String, String>()

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
