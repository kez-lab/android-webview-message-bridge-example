package io.github.kez.sample.web.message.listener.bridge

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WebBridgeHandler(
    private val activity: ComponentActivity,
    private val permissionRequester: PermissionRequester
) {

    private val json = Json { encodeDefaults = true }

    private val dataStore = mutableMapOf<String, String>()

    private val supportedPermissions = setOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS
    )

    suspend fun handleAction(
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
                "checkPermission" -> checkPermission(payload)
                "requestPermission" -> requestPermission(payload)
                "shareText" -> shareText(payload)
                "copyToClipboard" -> copyToClipboard(payload)
                "getClipboardText" -> getClipboardText()
                "openSystemSettings" -> openSystemSettings(payload)
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

    @Serializable
    private data class PermissionResult(
        val permission: String,
        val granted: Boolean,
        val shouldShowRationale: Boolean
    )

    private fun checkPermission(payload: Map<String, String>): String {
        val permission = requirePermission(payload)
        val granted = ContextCompat.checkSelfPermission(
            activity,
            permission
        ) == PackageManager.PERMISSION_GRANTED

        val result = PermissionResult(
            permission = permission,
            granted = granted,
            shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                permission
            )
        )
        return json.encodeToString(result)
    }

    private suspend fun requestPermission(payload: Map<String, String>): String {
        val permission = requirePermission(payload)
        val alreadyGranted = ContextCompat.checkSelfPermission(
            activity,
            permission
        ) == PackageManager.PERMISSION_GRANTED

        val granted = if (alreadyGranted) true else permissionRequester.request(permission)
        val result = PermissionResult(
            permission = permission,
            granted = granted,
            shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                permission
            )
        )
        return json.encodeToString(result)
    }

    @Serializable
    private data class ShareResult(
        val opened: Boolean,
        val timestamp: Long
    )

    private fun shareText(payload: Map<String, String>): String {
        val text = payload["text"]
            ?: throw IllegalArgumentException("Missing required parameter: text")
        val subject = payload["subject"]

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        }

        val chooser = Intent.createChooser(shareIntent, "공유할 앱 선택")
        activity.startActivity(chooser)

        return json.encodeToString(
            ShareResult(
                opened = true,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    @Serializable
    private data class ClipboardResult(
        val text: String?,
        val hasText: Boolean
    )

    private fun copyToClipboard(payload: Map<String, String>): String {
        val text = payload["text"]
            ?: throw IllegalArgumentException("Missing required parameter: text")
        val label = payload["label"] ?: "WebBridge"
        val clipboardManager = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText(label, text))
        return json.encodeToString(
            ClipboardResult(
                text = text,
                hasText = true
            )
        )
    }

    private fun getClipboardText(): String {
        val clipboardManager = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipText = clipboardManager.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(activity)
            ?.toString()

        return json.encodeToString(
            ClipboardResult(
                text = clipText,
                hasText = !clipText.isNullOrBlank()
            )
        )
    }

    @Serializable
    private data class OpenSettingsResult(
        val target: String,
        val opened: Boolean,
        val details: String? = null
    )

    private fun openSystemSettings(payload: Map<String, String>): String {
        val target = payload["target"] ?: "app"
        val channelId = payload["channelId"]
        val intent = when (target) {
            "app" -> Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.packageName, null)
            )

            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "notification" -> createNotificationSettingsIntent()
            "notificationChannel" -> createNotificationChannelSettingsIntent(channelId)
            "overlay" -> createOverlaySettingsIntent()
            "batteryOptimization" -> createBatteryOptimizationSettingsIntent()
            else -> throw IllegalArgumentException("Unsupported settings target: $target")
        }

        val canOpen = intent.resolveActivity(activity.packageManager) != null
        if (!canOpen) {
            throw IllegalStateException("No app can handle settings target: $target")
        }

        activity.startActivity(intent)
        return json.encodeToString(
            OpenSettingsResult(
                target = target,
                opened = true,
                details = channelId
            )
        )
    }

    private fun createNotificationSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            }
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.packageName, null)
            )
        }
    }

    private fun createNotificationChannelSettingsIntent(channelId: String?): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !channelId.isNullOrBlank()) {
            return Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            }
        }
        return createNotificationSettingsIntent()
    }

    private fun createOverlaySettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.packageName, null)
            )
        }
    }

    private fun createBatteryOptimizationSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.packageName, null)
            )
        }
    }

    private fun requirePermission(payload: Map<String, String>): String {
        val permission = payload["permission"]
            ?: throw IllegalArgumentException("Missing required parameter: permission")

        if (permission !in supportedPermissions) {
            throw IllegalArgumentException("Unsupported permission: $permission")
        }
        return permission
    }
}
