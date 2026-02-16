package io.github.kez.sample.web.message.listener.bridge

import android.net.Uri
import android.util.Log
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SecureWebMessageListener(
    private val allowedOrigins: Set<String>,
    private val coroutineScope: CoroutineScope,
    private val onAction: suspend (action: String, payload: Map<String, String>) -> Result<String>
) : WebViewCompat.WebMessageListener {

    companion object {
        private const val TAG = "SecureWebMessageListener"
        private const val ERROR_UNAUTHORIZED = "UNAUTHORIZED"
        private const val ERROR_FRAME_DENIED = "FRAME_DENIED"
        private const val ERROR_INVALID_MESSAGE = "INVALID_MESSAGE"
        private const val ERROR_PARSE = "PARSE_ERROR"
    }

    override fun onPostMessage(
        webView: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy
    ) {
        val originString = sourceOrigin.toString()
        Log.d(TAG, "Received message from: '$originString', isMainFrame: $isMainFrame")

        val rawData = message.data
        val requestId = rawData?.let(WebBridgeSerializer::extractRequestId)

        if (!OriginValidator.isAllowed(originString, allowedOrigins)) {
            Log.w(TAG, "Rejected message from unauthorized origin: $originString")
            sendErrorResponse(
                replyProxy = replyProxy,
                id = requestId ?: ERROR_UNAUTHORIZED,
                message = "Origin not allowed: $originString"
            )
            return
        }

        if (!isMainFrame) {
            Log.w(TAG, "Rejected message from non-main frame")
            sendErrorResponse(
                replyProxy = replyProxy,
                id = requestId ?: ERROR_FRAME_DENIED,
                message = "Only main frame is allowed to send messages"
            )
            return
        }

        if (rawData.isNullOrBlank()) {
            Log.w(TAG, "Received empty message")
            sendErrorResponse(
                replyProxy = replyProxy,
                id = requestId ?: ERROR_INVALID_MESSAGE,
                message = "Message data is empty"
            )
            return
        }

        Log.d(TAG, "Processing message: $rawData")

        val request = WebBridgeSerializer.parseRequest(rawData)
        if (request == null) {
            Log.w(TAG, "Failed to parse message: $rawData")
            sendErrorResponse(
                replyProxy = replyProxy,
                id = requestId ?: ERROR_PARSE,
                message = "Invalid JSON format"
            )
            return
        }

        coroutineScope.launch {
            processAction(request, replyProxy)
        }
    }

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
