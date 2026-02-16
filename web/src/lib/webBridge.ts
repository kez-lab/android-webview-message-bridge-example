import type {
  BridgeAction,
  BridgeRequest,
  BridgeResponse,
  NativeBridgeChannel,
  PendingRequest
} from '../types/bridge';

declare global {
  interface Window {
    NativeBridge?: NativeBridgeChannel;
  }
}

const DEFAULT_TIMEOUT_MS = 10_000;

export class WebBridgeClient {
  private readonly pending = new Map<string, PendingRequest>();
  private readonly timeoutMs: number;
  private readonly onLog: (level: 'info' | 'success' | 'error', message: string) => void;
  private handlerAttached = false;

  constructor(
    onLog: (level: 'info' | 'success' | 'error', message: string) => void,
    timeoutMs = DEFAULT_TIMEOUT_MS
  ) {
    this.onLog = onLog;
    this.timeoutMs = timeoutMs;
    this.attachResponseHandler();
  }

  isAvailable(): boolean {
    const available = typeof window.NativeBridge !== 'undefined';
    if (available) {
      this.attachResponseHandler();
    }
    return available;
  }

  async send(action: BridgeAction, payload: Record<string, string> = {}): Promise<unknown> {
    const bridge = window.NativeBridge;
    if (!bridge) {
      throw new Error('NativeBridge not available');
    }
    this.attachResponseHandler();

    const requestId = this.generateId();
    const request: BridgeRequest = {
      id: requestId,
      action,
      payload
    };

    this.onLog('info', `-> [${action}] request`);

    return new Promise((resolve, reject) => {
      const timeoutId = window.setTimeout(() => {
        this.pending.delete(requestId);
        this.onLog('error', `<- [${action}] timeout`);
        reject(new Error('Request timeout'));
      }, this.timeoutMs);

      this.pending.set(requestId, {
        action,
        timeoutId,
        resolve,
        reject
      });

      bridge.postMessage(JSON.stringify(request));
    });
  }

  private attachResponseHandler(): void {
    const bridge = window.NativeBridge;
    if (!bridge || this.handlerAttached) {
      return;
    }

    bridge.onmessage = (event) => {
      try {
        const response = JSON.parse(event.data) as BridgeResponse;
        const pendingRequest = this.pending.get(response.id);

        if (!pendingRequest) {
          return;
        }

        window.clearTimeout(pendingRequest.timeoutId);
        this.pending.delete(response.id);

        if (!response.success) {
          const message = response.error ?? 'Unknown native error';
          this.onLog('error', `<- [${pendingRequest.action}] error: ${message}`);
          pendingRequest.reject(new Error(message));
          return;
        }

        this.onLog('success', `<- [${pendingRequest.action}] success`);
        pendingRequest.resolve(this.parseResponseData(response.data));
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Unknown parse error';
        this.onLog('error', `response parse failed: ${message}`);
      }
    };
    this.handlerAttached = true;
  }

  private parseResponseData(data: string | null): unknown {
    if (!data) {
      return null;
    }

    try {
      return JSON.parse(data);
    } catch {
      return data;
    }
  }

  private generateId(): string {
    return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
  }
}
