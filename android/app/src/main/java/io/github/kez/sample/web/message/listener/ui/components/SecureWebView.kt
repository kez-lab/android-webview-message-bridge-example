package io.github.kez.sample.web.message.listener.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.kez.sample.web.message.listener.bridge.SecureWebMessageListener

/**
 * WebMessageListener가 통합된 Compose WebView 컴포넌트
 *
 * 이 컴포넌트는 Jetpack Compose에서 WebView를 사용하면서
 * [SecureWebMessageListener]를 통해 JavaScript와 안전하게 통신합니다.
 *
 * ## 특징
 * - Origin 기반 보안 검증
 * - 비동기 메시지 처리
 * - 로딩 상태 및 에러 표시
 * - 생명주기 자동 관리
 *
 * @param url 로드할 URL
 * @param modifier Compose modifier
 * @param allowedOrigins 허용된 Origin 목록
 * @param onAction 액션 핸들러 콜백
 * @param onWebViewCreated WebView 생성 시 콜백 (선택적)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SecureWebView(
    url: String,
    modifier: Modifier = Modifier,
    allowedOrigins: Set<String>,
    onAction: suspend (action: String, payload: Map<String, String>) -> Result<String>,
    onWebViewCreated: ((WebView) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()

    // 상태 관리
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // WebView 생명주기 관리
    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.let { webView ->
                Log.d("SecureWebView", "Disposing WebView")

                // WebMessageListener 제거
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                    runCatching {
                        WebViewCompat.removeWebMessageListener(webView, BRIDGE_NAME)
                    }
                }

                // WebView 정리
                webView.stopLoading()
                webView.destroy()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // WebView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // WebView 설정
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        // 보안 설정
                        allowFileAccess = false
                        allowContentAccess = false
                    }

                    // WebViewClient 설정
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: Bitmap?
                        ) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                            errorMessage = null
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                isLoading = false
                                errorMessage = "Error: ${error?.description}"
                            }
                        }
                    }

                    // WebMessageListener 설정
                    setupWebMessageListener(
                        webView = this,
                        allowedOrigins = allowedOrigins,
                        coroutineScope = coroutineScope,
                        onAction = onAction
                    )

                    webViewInstance = this
                    onWebViewCreated?.invoke(this)

                    // URL 로드
                    loadUrl(url)
                }
            },
            update = { webView ->
                // URL 변경 시 리로드
                if (webView.url != url && url.isNotBlank()) {
                    webView.loadUrl(url)
                }
            }
        )

        // 로딩 인디케이터
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 에러 메시지
        errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

private const val BRIDGE_NAME = "NativeBridge"

/**
 * WebMessageListener 설정 헬퍼 함수
 *
 * 참고: file:///android_asset/ URL에서는 origin이 null로 전달됨.
 * WebViewCompat.addWebMessageListener에는 "*"를 전달하고,
 * SecureWebMessageListener 내부에서 실제 origin 검증을 수행함.
 */
private fun setupWebMessageListener(
    webView: WebView,
    allowedOrigins: Set<String>,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onAction: suspend (action: String, payload: Map<String, String>) -> Result<String>
) {
    // Feature 지원 확인
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        Log.w("SecureWebView", "WEB_MESSAGE_LISTENER feature is not supported on this device")
        return
    }

    Log.d("SecureWebView", "Setting up WebMessageListener with origins: $allowedOrigins")

    val listener = SecureWebMessageListener(
        allowedOrigins = allowedOrigins,
        coroutineScope = coroutineScope,
        onAction = onAction
    )

    // WebViewCompat에 전달할 origin 규칙 생성
    // "null" origin(로컬 파일)을 허용하려면 "*"를 사용해야 함
    // 실제 보안 검증은 SecureWebMessageListener 내부에서 수행
    val webViewOrigins = if (allowedOrigins.contains("null") || allowedOrigins.contains("*")) {
        setOf("*")
    } else {
        allowedOrigins
    }

    runCatching {
        WebViewCompat.addWebMessageListener(
            webView,
            BRIDGE_NAME,
            webViewOrigins,
            listener
        )
        Log.d("SecureWebView", "WebMessageListener registered successfully with webViewOrigins: $webViewOrigins")
    }.onFailure { e ->
        Log.e("SecureWebView", "Failed to register WebMessageListener", e)
    }
}
