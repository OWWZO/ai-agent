// import React, { StrictMode } from 'react'; // 暂时移除严格模式
import { createRoot } from 'react-dom/client';
import App from './App';
import './global.css';

const root = document.getElementById('root');

if (root) {
  createRoot(root).render(
    <App />
  );
} else {
  console.error('Root element not found');
}
