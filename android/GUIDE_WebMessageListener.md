# WebMessageListener 완벽 가이드: Jetpack Compose에서의 안전한 웹-네이티브 통신

## 개요

`androidx.webkit.WebViewCompat.WebMessageListener`는 Android WebView에서 JavaScript와 네이티브 코드 간 통신을 위한 **최신 보안 표준**입니다. 기존 `addJavascriptInterface` 방식의 심각한 보안 취약점을 해결하면서도, 비동기 양방향 통신을 우아하게 지원합니다.

---

## 1. 왜 마이그레이션해야 하는가?

### 1.1 addJavascriptInterface의 보안 취약점

```kotlin
// ❌ 레거시 방식 - 보안 위험
webView.addJavascriptInterface(object {
    @JavascriptInterface
    fun sensitiveOperation(data: String) {
        // API 16 이하에서는 리플렉션을 통해
        // 모든 public 메서드가 JavaScript에서 호출 가능!
    }
}, "Android")
```

**핵심 문제점:**

| 문제 | 설명 | 위험도 |
|------|------|--------|
| **리플렉션 공격** | API 16 이하에서 `@JavascriptInterface` 없이도 모든 메서드 호출 가능 | 🔴 Critical |
| **Origin 미검증** | 어떤 출처의 JavaScript든 네이티브 코드 호출 가능 | 🔴 Critical |
| **동기 실행 블로킹** | UI 스레드 블로킹으로 ANR 유발 가능 | 🟡 Medium |
| **메모리 누수** | WebView 생명주기와 분리된 객체 참조 | 🟡 Medium |

### 1.2 WebMessageListener의 보안 개선

```kotlin
// ✅ 최신 방식 - 보안 강화
WebViewCompat.addWebMessageListener(
    webView,
    "secureChannel",           // 고유 네임스페이스
    setOf("https://trusted.com"), // 허용 Origin 명시
    webMessageListener
)
```

**보안 강점:**

| 특성 | WebMessageListener | addJavascriptInterface |
|------|-------------------|----------------------|
| Origin 검증 | ✅ 허용 목록 기반 | ❌ 없음 |
| 리플렉션 방어 | ✅ 메시지 기반 격리 | ❌ API 16 이하 취약 |
| 비동기 처리 | ✅ 네이티브 지원 | ❌ 동기 블로킹 |
| 스레드 안전성 | ✅ UI 스레드 보장 | ⚠️ 수동 관리 필요 |

---

## 2. 핵심 API 이해하기

### 2.1 WebMessageListener 인터페이스

```kotlin
interface WebViewCompat.WebMessageListener {
    @UiThread
    fun onPostMessage(
        webView: WebView,           // 메시지를 수신한 WebView
        message: WebMessageCompat,  // JavaScript에서 전송한 메시지
        sourceOrigin: Uri,          // 메시지 출처 (보안 검증용)
        isMainFrame: Boolean,       // 메인 프레임 여부
        replyProxy: JavaScriptReplyProxy  // 응답 전송 채널
    )
}
```

### 2.2 주요 클래스 역할

```
┌─────────────────────────────────────────────────────────────┐
│                    JavaScript (Web)                          │
│  secureChannel.postMessage("request")                       │
│  secureChannel.onmessage = (e) => console.log(e.data)       │
└──────────────────────┬──────────────────────────────────────┘
                       │ postMessage
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              WebMessageListener.onPostMessage()              │
│  - message: WebMessageCompat (요청 데이터)                   │
│  - sourceOrigin: Uri (출처 검증)                            │
│  - replyProxy: JavaScriptReplyProxy (응답 채널)             │
└──────────────────────┬──────────────────────────────────────┘
                       │ replyProxy.postMessage("response")
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    JavaScript (Web)                          │
│  onmessage 콜백에서 응답 수신                                │
└─────────────────────────────────────────────────────────────┘
```

### 2.3 Feature 지원 확인

```kotlin
fun isWebMessageListenerSupported(): Boolean {
    return WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
}

fun isArrayBufferSupported(): Boolean {
    return WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER)
}
```

---

## 3. Jetpack Compose 통합 구현

### 3.1 프로젝트 설정

```kotlin
// build.gradle.kts (app level)
dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
}
```

### 3.2 메시지 프로토콜 정의

```kotlin
// domain/model/WebBridgeMessage.kt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 웹-네이티브 통신을 위한 타입 안전한 메시지 프로토콜
 */
@Serializable
sealed interface WebBridgeMessage {
    val id: String  // 요청-응답 매칭용 고유 ID

    @Serializable
    data class Request(
        override val id: String,
        val action: String,
        val payload: Map<String, String> = emptyMap()
    ) : WebBridgeMessage

    @Serializable
    data class Response(
        override val id: String,
        val success: Boolean,
        val data: String? = null,
        val error: String? = null
    ) : WebBridgeMessage
}

object WebBridgeSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun parseRequest(raw: String): WebBridgeMessage.Request? {
        return runCatching {
            json.decodeFromString<WebBridgeMessage.Request>(raw)
        }.getOrNull()
    }

    fun serializeResponse(response: WebBridgeMessage.Response): String {
        return json.encodeToString(WebBridgeMessage.Response.serializer(), response)
    }
}
```

### 3.3 WebMessageListener 구현

```kotlin
// data/bridge/SecureWebMessageListener.kt
import android.net.Uri
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
 * @param allowedOrigins 허용된 Origin 목록 (HTTPS 필수 권장)
 * @param coroutineScope 비동기 작업을 위한 스코프
 * @param onAction 액션 핸들러 콜백
 */
class SecureWebMessageListener(
    private val allowedOrigins: Set<String>,
    private val coroutineScope: CoroutineScope,
    private val onAction: suspend (action: String, payload: Map<String, String>) -> Result<String>
) : WebViewCompat.WebMessageListener {

    override fun onPostMessage(
        webView: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy
    ) {
        // 🔐 보안 검증 1: Origin 확인
        val originString = sourceOrigin.toString()
        if (originString !in allowedOrigins) {
            sendErrorResponse(replyProxy, "UNAUTHORIZED", "Origin not allowed: $originString")
            return
        }

        // 🔐 보안 검증 2: 메인 프레임만 허용 (iframe 공격 방지)
        if (!isMainFrame) {
            sendErrorResponse(replyProxy, "FRAME_DENIED", "Only main frame allowed")
            return
        }

        // 메시지 파싱
        val rawData = message.data ?: run {
            sendErrorResponse(replyProxy, "INVALID_MESSAGE", "Empty message")
            return
        }

        val request = WebBridgeSerializer.parseRequest(rawData) ?: run {
            sendErrorResponse(replyProxy, "PARSE_ERROR", "Invalid JSON format")
            return
        }

        // 비동기 액션 처리
        coroutineScope.launch(Dispatchers.Main) {
            val result = onAction(request.action, request.payload)

            result.fold(
                onSuccess = { data ->
                    val response = WebBridgeMessage.Response(
                        id = request.id,
                        success = true,
                        data = data
                    )
                    replyProxy.postMessage(WebBridgeSerializer.serializeResponse(response))
                },
                onFailure = { error ->
                    sendErrorResponse(replyProxy, request.id, error.message ?: "Unknown error")
                }
            )
        }
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
```

### 3.4 Compose WebView Wrapper

```kotlin
// ui/components/SecureWebView.kt
import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CoroutineScope

/**
 * 보안 메시징이 통합된 Compose WebView 컴포넌트
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

    // WebView 참조 관리 (recomposition 안정성)
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Lifecycle 정리
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.let { webView ->
                // 리스너 제거
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                    WebViewCompat.removeWebMessageListener(webView, BRIDGE_NAME)
                }
                webView.destroy()
            }
        }
    }

    AndroidView(
        modifier = modifier,
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
                    // 추가 보안 설정
                    allowFileAccess = false
                    allowContentAccess = false
                }

                webViewClient = WebViewClient()

                // WebMessageListener 등록
                setupWebMessageListener(
                    webView = this,
                    allowedOrigins = allowedOrigins,
                    coroutineScope = coroutineScope,
                    onAction = onAction
                )

                webViewRef = this
                onWebViewCreated?.invoke(this)

                loadUrl(url)
            }
        },
        update = { webView ->
            // URL 변경 시 리로드
            if (webView.url != url) {
                webView.loadUrl(url)
            }
        }
    )
}

private const val BRIDGE_NAME = "NativeBridge"

private fun setupWebMessageListener(
    webView: WebView,
    allowedOrigins: Set<String>,
    coroutineScope: CoroutineScope,
    onAction: suspend (action: String, payload: Map<String, String>) -> Result<String>
) {
    // Feature 지원 확인
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        android.util.Log.w("SecureWebView", "WEB_MESSAGE_LISTENER not supported")
        return
    }

    val listener = SecureWebMessageListener(
        allowedOrigins = allowedOrigins,
        coroutineScope = coroutineScope,
        onAction = onAction
    )

    WebViewCompat.addWebMessageListener(
        webView,
        BRIDGE_NAME,
        allowedOrigins,
        listener
    )
}
```

### 3.5 ViewModel 통합

```kotlin
// ui/viewmodel/WebBridgeViewModel.kt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WebBridgeViewModel : ViewModel() {

    private val _bridgeState = MutableStateFlow<BridgeState>(BridgeState.Ready)
    val bridgeState: StateFlow<BridgeState> = _bridgeState.asStateFlow()

    /**
     * JavaScript에서 호출되는 액션 핸들러
     * 각 액션에 대한 비즈니스 로직 처리
     */
    suspend fun handleAction(
        action: String,
        payload: Map<String, String>
    ): Result<String> {
        return when (action) {
            "getUserInfo" -> getUserInfo()
            "saveData" -> saveData(payload)
            "getDeviceInfo" -> getDeviceInfo()
            "authenticate" -> authenticate(payload)
            else -> Result.failure(IllegalArgumentException("Unknown action: $action"))
        }
    }

    private suspend fun getUserInfo(): Result<String> {
        // 실제 구현에서는 Repository 호출
        return Result.success("""{"name": "홍길동", "email": "hong@example.com"}""")
    }

    private suspend fun saveData(payload: Map<String, String>): Result<String> {
        val key = payload["key"] ?: return Result.failure(IllegalArgumentException("Missing key"))
        val value = payload["value"] ?: return Result.failure(IllegalArgumentException("Missing value"))
        // 데이터 저장 로직
        return Result.success("""{"saved": true}""")
    }

    private suspend fun getDeviceInfo(): Result<String> {
        return Result.success("""{"platform": "Android", "version": "${android.os.Build.VERSION.SDK_INT}"}""")
    }

    private suspend fun authenticate(payload: Map<String, String>): Result<String> {
        val token = payload["token"] ?: return Result.failure(IllegalArgumentException("Missing token"))
        // 인증 로직
        return Result.success("""{"authenticated": true}""")
    }

    sealed interface BridgeState {
        data object Ready : BridgeState
        data object Loading : BridgeState
        data class Error(val message: String) : BridgeState
    }
}
```

### 3.6 Screen 컴포저블

```kotlin
// ui/screen/WebContentScreen.kt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun WebContentScreen(
    viewModel: WebBridgeViewModel = viewModel()
) {
    val bridgeState by viewModel.bridgeState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // 상태 표시 바
        TopAppBar(
            title = { Text("Secure WebView Demo") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )

        // 상태 인디케이터
        when (bridgeState) {
            is WebBridgeViewModel.BridgeState.Loading -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is WebBridgeViewModel.BridgeState.Error -> {
                Text(
                    text = (bridgeState as WebBridgeViewModel.BridgeState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
            else -> {}
        }

        // SecureWebView
        SecureWebView(
            url = "https://your-trusted-domain.com/app",
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            allowedOrigins = setOf(
                "https://your-trusted-domain.com",
                "https://api.your-trusted-domain.com"
            ),
            onAction = { action, payload ->
                viewModel.handleAction(action, payload)
            }
        )
    }
}
```

---

## 4. JavaScript 측 구현

### 4.1 타입 안전한 Bridge 클래스

```typescript
// web/src/bridge/NativeBridge.ts

interface BridgeRequest {
  id: string;
  action: string;
  payload: Record<string, string>;
}

interface BridgeResponse {
  id: string;
  success: boolean;
  data?: string;
  error?: string;
}

type MessageHandler = (response: BridgeResponse) => void;

/**
 * Android WebMessageListener와 통신하기 위한 타입 안전한 Bridge
 */
class NativeBridge {
  private pendingRequests = new Map<string, MessageHandler>();
  private messagePort: MessagePort | null = null;

  constructor() {
    this.setupMessageHandler();
  }

  private setupMessageHandler(): void {
    // NativeBridge는 Android에서 주입됨
    if (typeof (window as any).NativeBridge !== 'undefined') {
      (window as any).NativeBridge.onmessage = (event: MessageEvent) => {
        this.handleResponse(event.data);
      };
    }
  }

  private handleResponse(rawData: string): void {
    try {
      const response: BridgeResponse = JSON.parse(rawData);
      const handler = this.pendingRequests.get(response.id);

      if (handler) {
        handler(response);
        this.pendingRequests.delete(response.id);
      }
    } catch (error) {
      console.error('Failed to parse bridge response:', error);
    }
  }

  private generateId(): string {
    return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   * 네이티브 앱으로 메시지 전송
   */
  async send<T = unknown>(
    action: string,
    payload: Record<string, string> = {}
  ): Promise<T> {
    return new Promise((resolve, reject) => {
      const id = this.generateId();

      const request: BridgeRequest = { id, action, payload };

      // 타임아웃 설정 (10초)
      const timeout = setTimeout(() => {
        this.pendingRequests.delete(id);
        reject(new Error(`Request timeout: ${action}`));
      }, 10000);

      this.pendingRequests.set(id, (response) => {
        clearTimeout(timeout);

        if (response.success && response.data) {
          try {
            resolve(JSON.parse(response.data) as T);
          } catch {
            resolve(response.data as unknown as T);
          }
        } else {
          reject(new Error(response.error || 'Unknown error'));
        }
      });

      // Android로 메시지 전송
      if (typeof (window as any).NativeBridge !== 'undefined') {
        (window as any).NativeBridge.postMessage(JSON.stringify(request));
      } else {
        clearTimeout(timeout);
        this.pendingRequests.delete(id);
        reject(new Error('NativeBridge not available'));
      }
    });
  }

  /**
   * Bridge 사용 가능 여부 확인
   */
  isAvailable(): boolean {
    return typeof (window as any).NativeBridge !== 'undefined';
  }
}

export const nativeBridge = new NativeBridge();
```

### 4.2 React Hook으로 래핑

```typescript
// web/src/hooks/useNativeBridge.ts
import { useState, useCallback } from 'react';
import { nativeBridge } from '../bridge/NativeBridge';

interface UseNativeBridgeResult<T> {
  data: T | null;
  loading: boolean;
  error: Error | null;
  execute: (payload?: Record<string, string>) => Promise<T>;
}

export function useNativeBridge<T>(action: string): UseNativeBridgeResult<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const execute = useCallback(async (payload: Record<string, string> = {}) => {
    setLoading(true);
    setError(null);

    try {
      const result = await nativeBridge.send<T>(action, payload);
      setData(result);
      return result;
    } catch (err) {
      const error = err instanceof Error ? err : new Error(String(err));
      setError(error);
      throw error;
    } finally {
      setLoading(false);
    }
  }, [action]);

  return { data, loading, error, execute };
}

// 사용 예시
function UserProfile() {
  const { data, loading, error, execute } = useNativeBridge<{
    name: string;
    email: string;
  }>('getUserInfo');

  useEffect(() => {
    execute();
  }, [execute]);

  if (loading) return <div>Loading...</div>;
  if (error) return <div>Error: {error.message}</div>;
  if (!data) return null;

  return (
    <div>
      <h1>{data.name}</h1>
      <p>{data.email}</p>
    </div>
  );
}
```

---

## 5. 고급 패턴

### 5.1 ArrayBuffer 지원 (바이너리 데이터)

```kotlin
// 바이너리 데이터 전송 (이미지, 파일 등)
class BinaryWebMessageListener : WebViewCompat.WebMessageListener {

    override fun onPostMessage(
        webView: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy
    ) {
        // ArrayBuffer 지원 확인
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER)) {
            message.arrayBuffer?.let { buffer ->
                // 바이너리 데이터 처리
                processImageData(buffer)

                // 바이너리 응답
                val responseBuffer = createResponseBuffer()
                replyProxy.postMessage(responseBuffer)
                return
            }
        }

        // 폴백: 문자열 처리
        message.data?.let { data ->
            // Base64 인코딩된 데이터 처리
            processBase64Data(data)
        }
    }
}
```

### 5.2 에러 처리 전략

```kotlin
sealed class BridgeError(message: String) : Exception(message) {
    class UnauthorizedOrigin(origin: String) : BridgeError("Unauthorized origin: $origin")
    class InvalidMessage(reason: String) : BridgeError("Invalid message: $reason")
    class ActionNotFound(action: String) : BridgeError("Action not found: $action")
    class Timeout(action: String) : BridgeError("Timeout for action: $action")
}

// 중앙화된 에러 핸들링
fun handleBridgeError(error: BridgeError, replyProxy: JavaScriptReplyProxy) {
    val errorCode = when (error) {
        is BridgeError.UnauthorizedOrigin -> "E001"
        is BridgeError.InvalidMessage -> "E002"
        is BridgeError.ActionNotFound -> "E003"
        is BridgeError.Timeout -> "E004"
    }

    val response = WebBridgeMessage.Response(
        id = "error",
        success = false,
        error = "[${errorCode}] ${error.message}"
    )
    replyProxy.postMessage(WebBridgeSerializer.serializeResponse(response))
}
```

### 5.3 레거시 폴백 전략

```kotlin
/**
 * WebMessageListener를 지원하지 않는 구형 WebView를 위한 폴백
 */
object WebBridgeFactory {

    fun setupBridge(
        webView: WebView,
        allowedOrigins: Set<String>,
        coroutineScope: CoroutineScope,
        onAction: suspend (String, Map<String, String>) -> Result<String>
    ) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            // 최신 방식: WebMessageListener
            setupWebMessageListener(webView, allowedOrigins, coroutineScope, onAction)
        } else {
            // 레거시 폴백: addJavascriptInterface (추가 보안 레이어 적용)
            setupLegacyInterface(webView, coroutineScope, onAction)
        }
    }

    @SuppressLint("JavascriptInterface")
    private fun setupLegacyInterface(
        webView: WebView,
        coroutineScope: CoroutineScope,
        onAction: suspend (String, Map<String, String>) -> Result<String>
    ) {
        // 경고: 레거시 방식은 보안 위험이 있음
        android.util.Log.w(
            "WebBridge",
            "Using legacy JavascriptInterface - security limitations apply"
        )

        webView.addJavascriptInterface(
            LegacyBridge(coroutineScope, onAction),
            "NativeBridge"
        )
    }
}

class LegacyBridge(
    private val coroutineScope: CoroutineScope,
    private val onAction: suspend (String, Map<String, String>) -> Result<String>
) {
    @JavascriptInterface
    fun postMessage(message: String) {
        // 레거시 구현 (보안 제한적)
    }
}
```

---

## 6. 테스트 전략

### 6.1 단위 테스트

```kotlin
@Test
fun `WebMessageListener should reject unauthorized origins`() = runTest {
    val listener = SecureWebMessageListener(
        allowedOrigins = setOf("https://trusted.com"),
        coroutineScope = this,
        onAction = { _, _ -> Result.success("ok") }
    )

    val mockProxy = mockk<JavaScriptReplyProxy>()
    val capturedResponse = slot<String>()
    every { mockProxy.postMessage(capture(capturedResponse)) } just Runs

    listener.onPostMessage(
        webView = mockk(),
        message = WebMessageCompat("test"),
        sourceOrigin = Uri.parse("https://malicious.com"),
        isMainFrame = true,
        replyProxy = mockProxy
    )

    val response = WebBridgeSerializer.parseResponse(capturedResponse.captured)
    assertFalse(response.success)
    assertTrue(response.error?.contains("Origin not allowed") == true)
}
```

### 6.2 통합 테스트

```kotlin
@Test
fun `SecureWebView should handle bidirectional messaging`() {
    composeTestRule.setContent {
        SecureWebView(
            url = "file:///android_asset/test.html",
            allowedOrigins = setOf("file://"),
            onAction = { action, _ ->
                if (action == "ping") Result.success("""{"pong": true}""")
                else Result.failure(Exception("Unknown"))
            }
        )
    }

    // JavaScript에서 postMessage 호출 시뮬레이션
    // 응답 검증
}
```

---

## 7. 마이그레이션 체크리스트

### Phase 1: 준비
- [ ] `androidx.webkit:webkit` 의존성 추가
- [ ] `WebViewFeature.isFeatureSupported()` 확인 로직 구현
- [ ] 메시지 프로토콜 (JSON 스키마) 정의

### Phase 2: 구현
- [ ] `WebMessageListener` 구현 클래스 작성
- [ ] Origin 화이트리스트 설정
- [ ] Compose `AndroidView` 래퍼 구현
- [ ] JavaScript 측 Bridge 클래스 구현

### Phase 3: 마이그레이션
- [ ] 기존 `@JavascriptInterface` 메서드를 액션으로 변환
- [ ] 동기 호출을 Promise 기반으로 변경
- [ ] 에러 처리 통합

### Phase 4: 검증
- [ ] Origin 검증 테스트
- [ ] iframe 공격 시나리오 테스트
- [ ] 성능 벤치마크 (latency 비교)
- [ ] 레거시 기기 호환성 확인

---

## 8. 결론

`WebMessageListener`는 단순한 API 업그레이드가 아닌 **보안 패러다임의 전환**입니다:

| 측면 | 개선 효과 |
|------|----------|
| **보안** | Origin 기반 접근 제어로 XSS 공격 표면 감소 |
| **안정성** | 비동기 처리로 UI 블로킹 제거 |
| **유지보수** | 명확한 메시지 프로토콜로 디버깅 용이 |
| **확장성** | ArrayBuffer 지원으로 바이너리 데이터 처리 가능 |

Jetpack Compose 환경에서 `AndroidView`를 통해 자연스럽게 통합되며, 적절한 폴백 전략으로 레거시 지원도 가능합니다. 새로운 프로젝트라면 `WebMessageListener`를 기본으로, 기존 프로젝트라면 점진적 마이그레이션을 권장합니다.

---

## 참고 자료

- [AndroidX WebKit 공식 문서](https://developer.android.com/reference/androidx/webkit/package-summary)
- [WebViewCompat API Reference](https://developer.android.com/reference/androidx/webkit/WebViewCompat)
- [Jetpack Compose Interoperability](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/views-in-compose)
