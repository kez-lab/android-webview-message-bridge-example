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
import io.github.kez.sample.web.message.listener.bridge.WebBridgeConfig

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

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.let { webView ->
                Log.d("SecureWebView", "Disposing WebView")

                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                    runCatching {
                        WebViewCompat.removeWebMessageListener(webView, WebBridgeConfig.BRIDGE_NAME)
                    }
                }

                webView.stopLoading()
                webView.destroy()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                    }

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

                    registerWebMessageListener(
                        webView = this,
                        allowedOrigins = allowedOrigins,
                        coroutineScope = coroutineScope,
                        onAction = onAction
                    )

                    webViewInstance = this
                    onWebViewCreated?.invoke(this)

                    loadUrl(url)
                }
            },
            update = { webView ->
                if (webView.url != url && url.isNotBlank()) {
                    webView.loadUrl(url)
                }
            }
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }

        errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

private fun registerWebMessageListener(
    webView: WebView,
    allowedOrigins: Set<String>,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onAction: suspend (action: String, payload: Map<String, String>) -> Result<String>
) {
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

    val webViewOrigins = if (allowedOrigins.contains("null") || allowedOrigins.contains("*")) {
        setOf("*")
    } else {
        allowedOrigins
    }

    runCatching {
        WebViewCompat.addWebMessageListener(
            webView,
            WebBridgeConfig.BRIDGE_NAME,
            webViewOrigins,
            listener
        )
        Log.d("SecureWebView", "WebMessageListener registered successfully with webViewOrigins: $webViewOrigins")
    }.onFailure { e ->
        Log.e("SecureWebView", "Failed to register WebMessageListener", e)
    }
}
