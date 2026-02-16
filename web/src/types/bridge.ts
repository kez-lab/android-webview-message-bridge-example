export type BridgeAction =
  | 'ping'
  | 'echo'
  | 'getUserInfo'
  | 'getDeviceInfo'
  | 'saveData'
  | 'getData';

export interface BridgeRequest {
  id: string;
  action: BridgeAction;
  payload: Record<string, string>;
}

export interface BridgeResponse {
  id: string;
  success: boolean;
  data: string | null;
  error: string | null;
}

export interface NativeBridgeEvent {
  data: string;
}

export interface NativeBridgeChannel {
  postMessage: (message: string) => void;
  onmessage: ((event: NativeBridgeEvent) => void) | null;
}

export interface PendingRequest {
  action: BridgeAction;
  timeoutId: number;
  resolve: (value: unknown) => void;
  reject: (reason: Error) => void;
}

export interface LogEntry {
  id: string;
  level: 'info' | 'success' | 'error';
  message: string;
  timestamp: string;
}
