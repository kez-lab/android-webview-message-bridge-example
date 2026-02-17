# Android WebMessageListener Sample

`WebViewCompat.WebMessageListener` 기반의 Android-Web 양방향 브리지 샘플입니다.

## Demo

[<img src="docs/media/bridge-demo-preview.gif" alt="Demo Preview" width="280" />](docs/media/bridge-demo-20260216.mp4)
- [Full video (MP4)](docs/media/bridge-demo-20260216.mp4)

## 프로젝트 구성

- `android/`: Android 앱 (Compose + WebView + Native Bridge)
- `web/`: React/Vite 웹 앱 (Android 브리지 호출 UI)

## 현재 Android 런타임 설정

기본값은 원격 웹을 로드합니다.

- Bridge name: `NativeBridge`
- `PROD_WEB_URL`: `https://android-webview-message-bridge-example.kez-lab.org`
- `PROD_ALLOWED_ORIGINS`:
  - `https://android-webview-message-bridge-example.kez-lab.org`
  - `https://*.android-webview-message-bridge-example.pages.dev` (preview/임시 배포 허용)

설정 위치:
- `android/app/src/main/java/io/github/kez/sample/web/message/listener/bridge/WebBridgeConfig.kt`

## 핵심 Android 파일

- `android/app/src/main/java/io/github/kez/sample/web/message/listener/MainActivity.kt`
- `android/app/src/main/java/io/github/kez/sample/web/message/listener/ui/components/SecureWebView.kt`
- `android/app/src/main/java/io/github/kez/sample/web/message/listener/bridge/SecureWebMessageListener.kt`
- `android/app/src/main/java/io/github/kez/sample/web/message/listener/bridge/WebBridgeMessage.kt`
- `android/app/src/main/java/io/github/kez/sample/web/message/listener/bridge/WebBridgeHandler.kt`

## 메시지 프로토콜

요청(웹 -> Android):

```json
{
  "id": "request-id",
  "action": "ping",
  "payload": {}
}
```

응답(Android -> 웹):

```json
{
  "id": "request-id",
  "success": true,
  "data": "{\"pong\":true,\"timestamp\":1700000000000}",
  "error": null
}
```

- `payload`는 `Record<string, string>` 형태로 처리됩니다.
- `data`는 JSON 문자열로 내려오며, 웹에서 다시 파싱해 사용합니다.

## 지원 액션 (`WebBridgeHandler`)

- `ping`
- `echo` (`payload.message`)
- `getUserInfo`
- `getDeviceInfo`
- `saveData` (`payload.key`, `payload.value`)
- `getData` (`payload.key`)
- `checkPermission` (`payload.permission`)
- `requestPermission` (`payload.permission`)
- `shareText` (`payload.text`, `payload.subject?`)
- `copyToClipboard` (`payload.text`, `payload.label?`)
- `getClipboardText`
- `openSystemSettings` (`payload.target`, `payload.channelId?`)

`openSystemSettings`의 `target` 지원값:
- `app`
- `notification`
- `notificationChannel`
- `wifi`
- `bluetooth`
- `location`
- `batteryOptimization`
- `overlay`

## 로컬 자산 페이지로 전환 (선택)

로컬 데모 파일도 포함되어 있습니다.

- `LOCAL_DEMO_URL`: `file:///android_asset/demo.html`
- `LOCAL_ALLOWED_ORIGINS`: `null`

`MainActivity.kt`에서 `SecureWebView`의 `url`/`allowedOrigins`를 `LOCAL_*` 값으로 바꾸면 로컬 자산으로 테스트할 수 있습니다.

## 보안 포인트

- Origin 화이트리스트 검증
- `isMainFrame` 검증
- 메시지 파싱/검증 실패 시 에러 응답 반환
- `WebSettings`에서 `allowFileAccess=false`, `allowContentAccess=false`

## 참고 문서

- https://developer.android.com/reference/androidx/webkit/WebViewCompat
- https://developer.android.com/reference/androidx/webkit/WebViewCompat.WebMessageListener
- https://developer.android.com/reference/androidx/webkit/JavaScriptReplyProxy
- https://developer.android.com/reference/androidx/webkit/WebMessageCompat
