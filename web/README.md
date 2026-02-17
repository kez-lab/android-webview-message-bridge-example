# Web 프로젝트 (Bridge 데모 UI)

Android `WebViewCompat.WebMessageListener` 브리지와 통신하는 React 앱입니다.

## 스택

- React 18
- TypeScript 5
- Vite 5

## 실행

```bash
cd web
npm install
npm run dev
```

- 개발 주소: `http://localhost:5173`

## 빌드

```bash
npm run build
npm run preview
```

- 산출물: `web/dist`

## 브리지 프로토콜

요청:

```json
{
  "id": "request-id",
  "action": "ping",
  "payload": {}
}
```

응답:

```json
{
  "id": "request-id",
  "success": true,
  "data": "{\"pong\":true,\"timestamp\":1700000000000}",
  "error": null
}
```

- `payload`는 `Record<string, string>`
- `data`는 JSON 문자열이며 웹에서 파싱해서 사용

## 지원 액션

- `ping`
- `echo`
- `getUserInfo`
- `getDeviceInfo`
- `saveData`
- `getData`
- `checkPermission`
- `requestPermission`
- `shareText`
- `copyToClipboard`
- `getClipboardText`
- `openSystemSettings`

타입 정의 위치:
- `web/src/types/bridge.ts`

## Mock NativeBridge

다음 조건에서 mock bridge가 자동 주입됩니다.

- 개발 모드(`import.meta.env.DEV`)
- 또는 URL 쿼리 `?mockBridge=1`

이미 `window.NativeBridge`가 존재하면 mock은 주입하지 않습니다.
