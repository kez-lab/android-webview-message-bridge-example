# Web 프로젝트 (WebMessageListener 데모)

Android `WebViewCompat.WebMessageListener`와 연동되는 실제 배포용 웹 앱입니다.

## 스택

- React 18
- TypeScript
- Vite 5

## 실행 방법

```bash
cd web
npm install
npm run dev
```

기본 개발 주소: `http://localhost:5173`

## 빌드

```bash
npm run build
npm run preview
```

배포 산출물: `web/dist`

## 브리지 프로토콜

웹 -> Android 요청:

```json
{
  "id": "request-id",
  "action": "ping",
  "payload": {}
}
```

Android -> 웹 응답:

```json
{
  "id": "request-id",
  "success": true,
  "data": "{\"pong\":true}",
  "error": null
}
```

`action`은 Android `WebBridgeHandler` 기준으로 다음을 지원합니다.

- `ping`
- `echo`
- `getUserInfo`
- `getDeviceInfo`
- `saveData`
- `getData`

## 로컬 브라우저 테스트

브라우저에서 `NativeBridge` 객체가 없으면 개발 모드에서 자동으로 mock bridge를 주입합니다.

운영 환경에서 강제로 mock 사용:

- URL에 `?mockBridge=1` 추가

예: `https://your-web-url.example.com/?mockBridge=1`

## Android 연동 변경점

로컬 파일 대신 원격 웹을 로드하려면 Android 코드에서 URL과 허용 Origin을 변경하세요.

`android/app/src/main/java/io/github/kez/sample/web/message/listener/MainActivity.kt`

```kotlin
SecureWebView(
    url = "https://your-domain.com",
    allowedOrigins = setOf(
        "https://your-domain.com"
    ),
    onAction = { action, payload ->
        bridgeHandler.handleAction(action, payload)
    }
)
```

배포 도메인과 `allowedOrigins`는 반드시 일치시켜야 합니다.
