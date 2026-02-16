package io.github.kez.sample.web.message.listener.bridge

import android.net.Uri
import android.util.Log
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 보안 강화된 WebMessageListener 구현
 *
 * 이 클래스는 [WebViewCompat.WebMessageListener]를 구현하여
 * JavaScript와 Native 코드 간의 안전한 양방향 통신을 제공합니다.
 *
 * ## 보안 특성
 * - **Origin 검증**: 허용된 Origin에서만 메시지 수신
 * - **메인 프레임 검증**: iframe 공격 방지
 * - **비동기 처리**: UI 스레드 블로킹 방지
 *
 * ## 사용 예시
 * ```kotlin
 * val listener = SecureWebMessageListener(
 *     allowedOrigins = setOf("https://trusted.com"),
 *     coroutineScope = viewModelScope,
 *     onAction = { action, payload ->
 *         when (action) {
 *             "getUserInfo" -> Result.success("""{"name": "홍길동"}""")
 *             else -> Result.failure(Exception("Unknown action"))
 *         }
 *     }
 * )
 * ```
 *
 * @param allowedOrigins 허용된 Origin 목록 (HTTPS 권장)
 * @param coroutineScope 비동기 작업을 위한 CoroutineScope
 * @param onAction 액션 핸들러 - 액션 이름과 페이로드를 받아 Result 반환
 */
class SecureWebMessageListener(
    private val allowedOrigins: Set<String>,
    private val coroutineScope: CoroutineScope,
    private val onAction: suspend (action: String, payload: Map<String, String>) -> Result<String>
) : WebViewCompat.WebMessageListener {

    companion object {
        private const val TAG = "SecureWebMessageListener"
    }

    /**
     * JavaScript에서 postMessage로 전송된 메시지를 처리
     *
     * @param webView 메시지를 수신한 WebView
     * @param message JavaScript에서 전송한 메시지 (JSON 형식)
     * @param sourceOrigin 메시지 출처 URI (보안 검증용)
     * @param isMainFrame 메인 프레임 여부 (iframe 공격 방지)
     * @param replyProxy 응답 전송을 위한 프록시 객체
     */
    override fun onPostMessage(
        webView: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy
    ) {
        val originString = sourceOrigin.toString()
        Log.d(TAG, "Received message from: '$originString', isMainFrame: $isMainFrame")

        // 보안 검증 1: Origin 확인
        // 참고: file:///android_asset/ 에서 로드 시 sourceOrigin은 "null" 문자열로 전달됨
        if (!isOriginAllowed(originString)) {
            Log.w(TAG, "Rejected message from unauthorized origin: $originString")
            sendErrorResponse(
                replyProxy = replyProxy,
                id = "UNAUTHORIZED",
                message = "Origin not allowed: $originString"
            )
            return
        }

        // 보안 검증 2: 메인 프레임 검증 (iframe 공격 방지)
        if (!isMainFrame) {
            Log.w(TAG, "Rejected message from non-main frame")
            sendErrorResponse(
                replyProxy = replyProxy,
                id = "FRAME_DENIED",
                message = "Only main frame is allowed to send messages"
            )
            return
        }

        // 메시지 데이터 추출
        val rawData = message.data
        if (rawData.isNullOrBlank()) {
            Log.w(TAG, "Received empty message")
            sendErrorResponse(
                replyProxy = replyProxy,
                id = "INVALID_MESSAGE",
                message = "Message data is empty"
            )
            return
        }

        Log.d(TAG, "Processing message: $rawData")

        // JSON 파싱
        val request = WebBridgeSerializer.parseRequest(rawData)
        if (request == null) {
            Log.w(TAG, "Failed to parse message: $rawData")
            sendErrorResponse(
                replyProxy = replyProxy,
                id = "PARSE_ERROR",
                message = "Invalid JSON format"
            )
            return
        }

        // 비동기로 액션 처리
        coroutineScope.launch(Dispatchers.Main) {
            processAction(request, replyProxy)
        }
    }

    /**
     * Origin이 허용 목록에 있는지 확인
     *
     * 참고: file:///android_asset/ 에서 로드 시 origin은 "null" 문자열로 전달됨
     */
    private fun isOriginAllowed(origin: String): Boolean {
        return allowedOrigins.any { allowed ->
            when {
                // 와일드카드: 모든 origin 허용 (개발 환경에서만 사용)
                allowed == "*" -> true
                // 로컬 assets 허용: "null" origin 허용
                allowed == "null" && origin == "null" -> true
                // 정확한 매칭
                origin == allowed -> true
                // prefix 매칭 (https://example.com 이 https://example.com/path 허용)
                origin.startsWith(allowed) -> true
                else -> false
            }
        }
    }

    /**
     * 액션 처리 및 응답 전송
     */
    private suspend fun processAction(
        request: WebBridgeMessage.Request,
        replyProxy: JavaScriptReplyProxy
    ) {
        Log.d(TAG, "Processing action: ${request.action}")

        val result = onAction(request.action, request.payload)

        result.fold(
            onSuccess = { data ->
                Log.d(TAG, "Action ${request.action} succeeded")
                val response = WebBridgeMessage.Response(
                    id = request.id,
                    success = true,
                    data = data
                )
                replyProxy.postMessage(WebBridgeSerializer.serializeResponse(response))
            },
            onFailure = { error ->
                Log.e(TAG, "Action ${request.action} failed", error)
                sendErrorResponse(
                    replyProxy = replyProxy,
                    id = request.id,
                    message = error.message ?: "Unknown error occurred"
                )
            }
        )
    }

    /**
     * 에러 응답 전송 헬퍼
     */
    private fun sendErrorResponse(
        replyProxy: JavaScriptReplyProxy,
        id: String,
        message: String
    ) {
        val response = WebBridgeMessage.Response(
            id = id,
            success = false,
            error = message
        )
        replyProxy.postMessage(WebBridgeSerializer.serializeResponse(response))
    }
}
