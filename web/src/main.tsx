import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import { installMockNativeBridge } from './lib/mockNativeBridge';
import './styles.css';

const searchParams = new URLSearchParams(window.location.search);
const forceMock = searchParams.get('mockBridge') === '1';
const shouldUseMock = import.meta.env.DEV || forceMock;

if (shouldUseMock && typeof window.NativeBridge === 'undefined') {
  installMockNativeBridge();
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
