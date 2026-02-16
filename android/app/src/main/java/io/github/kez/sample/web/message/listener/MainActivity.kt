package io.github.kez.sample.web.message.listener

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.webkit.WebViewFeature
import io.github.kez.sample.web.message.listener.bridge.WebBridgeHandler
import io.github.kez.sample.web.message.listener.ui.components.SecureWebView
import io.github.kez.sample.web.message.listener.ui.theme.SamplewebmessagelistenerTheme

/**
 * WebMessageListener 데모 앱의 메인 액티비티
 *
 * 이 앱은 [SecureWebView] 컴포넌트를 사용하여
 * JavaScript와 Native 간의 안전한 양방향 통신을 시연합니다.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SamplewebmessagelistenerTheme {
                WebMessageListenerDemoApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebMessageListenerDemoApp() {
    // WebBridgeHandler 인스턴스 (실제 앱에서는 ViewModel에서 관리)
    val bridgeHandler = remember { WebBridgeHandler() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "WebMessageListener Demo",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = getFeatureStatusText(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        SecureWebView(
            url = "file:///android_asset/demo.html",
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding),
            allowedOrigins = setOf(
                // 로컬 assets 허용 (file:///android_asset/ 로드 시 origin이 "null"로 전달됨)
                "null",
                // 실제 앱에서는 신뢰할 수 있는 도메인만 추가
                // "https://your-trusted-domain.com"
            ),
            onAction = { action, payload ->
                bridgeHandler.handleAction(action, payload)
            }
        )
    }
}

/**
 * WebViewFeature 지원 상태 텍스트 생성
 */
private fun getFeatureStatusText(): String {
    val webMessageSupported = WebViewFeature.isFeatureSupported(
        WebViewFeature.WEB_MESSAGE_LISTENER
    )
    val arrayBufferSupported = WebViewFeature.isFeatureSupported(
        WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER
    )

    return buildString {
        append(if (webMessageSupported) "✅" else "❌")
        append(" WebMessage")
        append("  ")
        append(if (arrayBufferSupported) "✅" else "❌")
        append(" ArrayBuffer")
    }
}
