import { FormEvent, useMemo, useState } from 'react';
import { WebBridgeClient } from './lib/webBridge';
import type { LogEntry } from './types/bridge';

type ResultState = {
  kind: 'idle' | 'loading' | 'success' | 'error';
  text: string;
};

const defaultResult = (text: string): ResultState => ({ kind: 'idle', text });

function App() {
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [bridgeStatus, setBridgeStatus] = useState<'connected' | 'missing'>(() =>
    typeof window.NativeBridge !== 'undefined' ? 'connected' : 'missing'
  );

  const [basicResult, setBasicResult] = useState<ResultState>(
    defaultResult('버튼을 눌러 테스트하세요')
  );
  const [infoResult, setInfoResult] = useState<ResultState>(
    defaultResult('버튼을 눌러 정보를 조회하세요')
  );
  const [dataResult, setDataResult] = useState<ResultState>(
    defaultResult('데이터를 저장하거나 조회하세요')
  );

  const [keyInput, setKeyInput] = useState('myKey');
  const [valueInput, setValueInput] = useState('Hello from Web!');

  const bridge = useMemo(
    () =>
      new WebBridgeClient((level, message) => {
        const timestamp = new Date().toLocaleTimeString();
        setLogs((prev) => [
          {
            id: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
            level,
            message,
            timestamp
          },
          ...prev
        ].slice(0, 60));
      }),
    []
  );

  const runAction = async (
    section: 'basic' | 'info' | 'data',
    action: Parameters<WebBridgeClient['send']>[0],
    payload: Record<string, string> = {}
  ) => {
    setBridgeStatus(bridge.isAvailable() ? 'connected' : 'missing');

    const setResult =
      section === 'basic' ? setBasicResult : section === 'info' ? setInfoResult : setDataResult;

    setResult({ kind: 'loading', text: '요청 중...' });

    try {
      const result = await bridge.send(action, payload);
      setResult({ kind: 'success', text: JSON.stringify(result, null, 2) });
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error';
      setResult({ kind: 'error', text: `Error: ${message}` });
    }
  };

  const onSaveData = async (event: FormEvent) => {
    event.preventDefault();

    if (!keyInput || !valueInput) {
      setDataResult({ kind: 'error', text: 'Key와 Value를 모두 입력하세요' });
      return;
    }

    await runAction('data', 'saveData', { key: keyInput, value: valueInput });
  };

  const onGetData = async () => {
    if (!keyInput) {
      setDataResult({ kind: 'error', text: 'Key를 입력하세요' });
      return;
    }

    await runAction('data', 'getData', { key: keyInput });
  };

  return (
    <main className="page">
      <div className="background-glow" />
      <section className="container">
        <header className="header">
          <h1>WebMessageListener Bridge</h1>
          <p>Android WebViewCompat.WebMessageListener 연동 웹 앱</p>
        </header>

        <article className="card">
          <h2>Bridge 상태</h2>
          <span className={`badge ${bridgeStatus}`}>
            {bridgeStatus === 'connected' ? '연결됨' : '연결 안됨'}
          </span>
        </article>

        <article className="card">
          <h2>기본 테스트</h2>
          <div className="button-group">
            <button onClick={() => runAction('basic', 'ping')}>Ping</button>
            <button onClick={() => runAction('basic', 'echo', { message: 'Hello from React!' })}>
              Echo
            </button>
          </div>
          <ResultBox state={basicResult} />
        </article>

        <article className="card">
          <h2>정보 조회</h2>
          <div className="button-group">
            <button onClick={() => runAction('info', 'getUserInfo')}>사용자 정보</button>
            <button onClick={() => runAction('info', 'getDeviceInfo')}>디바이스 정보</button>
          </div>
          <ResultBox state={infoResult} />
        </article>

        <article className="card">
          <h2>데이터 저장/조회</h2>
          <form className="form" onSubmit={onSaveData}>
            <label>
              Key
              <input value={keyInput} onChange={(event) => setKeyInput(event.target.value)} />
            </label>
            <label>
              Value
              <input value={valueInput} onChange={(event) => setValueInput(event.target.value)} />
            </label>
            <div className="button-group">
              <button type="submit">저장</button>
              <button type="button" className="secondary" onClick={onGetData}>
                조회
              </button>
            </div>
          </form>
          <ResultBox state={dataResult} />
        </article>

        <article className="card">
          <div className="card-header-row">
            <h2>통신 로그</h2>
            <button className="secondary" onClick={() => setLogs([])}>
              로그 지우기
            </button>
          </div>
          <div className="log-list">
            {logs.length === 0 ? <p className="empty">로그가 없습니다.</p> : null}
            {logs.map((log) => (
              <p key={log.id} className={`log ${log.level}`}>
                [{log.timestamp}] {log.message}
              </p>
            ))}
          </div>
        </article>
      </section>
    </main>
  );
}

function ResultBox({ state }: { state: ResultState }) {
  return <pre className={`result ${state.kind}`}>{state.text}</pre>;
}

export default App;
