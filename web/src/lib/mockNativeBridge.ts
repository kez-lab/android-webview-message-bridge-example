import type {
  BridgeAction,
  BridgeRequest,
  NativeBridgeChannel,
  NativeBridgeEvent
} from '../types/bridge';

declare global {
  interface Window {
    NativeBridge?: NativeBridgeChannel;
  }
}

const store = new Map<string, string>();

function createResponse(
  request: BridgeRequest,
  success: boolean,
  data: unknown,
  error: string | null = null
): string {
  return JSON.stringify({
    id: request.id,
    success,
    data: data === null ? null : JSON.stringify(data),
    error
  });
}

function handleAction(action: BridgeAction, payload: Record<string, string>): unknown {
  switch (action) {
    case 'ping':
      return { pong: true, timestamp: Date.now() };
    case 'echo':
      return { echo: payload.message ?? 'empty', receivedAt: Date.now() };
    case 'getUserInfo':
      return {
        id: 'user_123',
        name: '홍길동',
        email: 'hong@example.com',
        isLoggedIn: true
      };
    case 'getDeviceInfo':
      return {
        platform: 'Browser',
        sdkVersion: 0,
        manufacturer: 'Web',
        model: navigator.userAgent,
        isEmulator: false
      };
    case 'saveData': {
      const key = payload.key;
      const value = payload.value;
      if (!key || !value) {
        throw new Error('Missing required parameter: key/value');
      }
      store.set(key, value);
      return { saved: true, key, timestamp: Date.now() };
    }
    case 'getData': {
      const key = payload.key;
      if (!key) {
        throw new Error('Missing required parameter: key');
      }
      const value = store.get(key);
      return { found: Boolean(value), key, value: value ?? null };
    }
    default:
      throw new Error(`Unknown action: ${action}`);
  }
}

export function installMockNativeBridge(): void {
  if (window.NativeBridge) {
    return;
  }

  const bridge: NativeBridgeChannel = {
    onmessage: null,
    postMessage: (rawMessage: string) => {
      let responsePayload: string;

      try {
        const request = JSON.parse(rawMessage) as BridgeRequest;
        const data = handleAction(request.action, request.payload ?? {});
        responsePayload = createResponse(request, true, data);
      } catch (error) {
        const fallbackRequest = {
          id: `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`,
          action: 'ping' as const,
          payload: {}
        };
        const message = error instanceof Error ? error.message : 'Unknown error';
        responsePayload = createResponse(fallbackRequest, false, null, message);
      }

      window.setTimeout(() => {
        const event: NativeBridgeEvent = { data: responsePayload };
        bridge.onmessage?.(event);
      }, 180);
    }
  };

  window.NativeBridge = bridge;
}
