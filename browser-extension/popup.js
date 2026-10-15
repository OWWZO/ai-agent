const API_ORIGIN_KEY = 'reactor_api_origin';
const originInput = document.getElementById('origin');
const codeInput = document.getElementById('code');
const statusEl = document.getElementById('status');
const dot = document.getElementById('dot');
const mainView = document.getElementById('mainView');
const settingsView = document.getElementById('settingsView');
const settingsStatus = document.getElementById('settingsStatus');

let apiOrigin = '';

function normalizeApiOrigin(value) {
  try {
    const url = new URL(value.trim());
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
      return '';
    }
    const path = url.pathname.replace(/\/+$/, '');
    return `${url.origin}${path}`;
  } catch {
    return '';
  }
}

async function getActiveTabOrigin() {
  const tabs = await chrome.tabs.query({ active: true, lastFocusedWindow: true });
  const tabUrl = tabs.find((tab) => tab.url)?.url;
  if (!tabUrl) return '';
  try {
    const url = new URL(tabUrl);
    return ['http:', 'https:'].includes(url.protocol) ? url.origin : '';
  } catch {
    return '';
  }
}

async function initializeApiOrigin() {
  const stored = await chrome.storage.local.get([API_ORIGIN_KEY]);
  const configuredOrigin = typeof stored[API_ORIGIN_KEY] === 'string'
    ? normalizeApiOrigin(stored[API_ORIGIN_KEY])
    : '';
  if (configuredOrigin) {
    apiOrigin = configuredOrigin;
  } else {
    apiOrigin = await getActiveTabOrigin();
    if (apiOrigin) await chrome.storage.local.set({ [API_ORIGIN_KEY]: apiOrigin });
  }
  originInput.value = apiOrigin;
}

const apiOriginReady = initializeApiOrigin().catch(() => {
  originInput.value = '';
});

function setConnected(connected) {
  statusEl.textContent = connected ? '已连接' : '未连接';
  dot.classList.toggle('connected', connected);
}

chrome.runtime.sendMessage({ type: 'getStatus' }, (resp) => {
  setConnected(Boolean(resp && resp.connected));
});

document.getElementById('connectBtn').addEventListener('click', async () => {
  await apiOriginReady;
  const origin = apiOrigin;
  const code = codeInput.value.trim();
  if (!origin) {
    statusEl.textContent = '请先设置网页 API 地址';
    return;
  }
  if (code.length !== 6) {
    statusEl.textContent = '请填写 6 位配对码';
    return;
  }
  try {
    const res = await fetch(`${origin}/api/agent/browser/claim`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code }),
    });
    const responseText = await res.text();
    let json;
    try {
      json = JSON.parse(responseText);
    } catch {
      statusEl.textContent = `服务响应不是 JSON（HTTP ${res.status}）`;
      return;
    }
    if (!res.ok) {
      statusEl.textContent = json.info || json.msg || `配对请求失败（HTTP ${res.status}）`;
      return;
    }
    const data = json.data || json;
    if (!data || !data.token || !data.relayUrl) {
      statusEl.textContent = json.info || json.msg || '配对失败：验证码无效、已过期或 API 地址错误';
      return;
    }
    chrome.runtime.sendMessage({ type: 'pair', token: data.token, relayUrl: data.relayUrl }, (resp) => {
      if (chrome.runtime.lastError) {
        statusEl.textContent = chrome.runtime.lastError.message || '扩展后台连接失败';
        return;
      }
      if (!resp || !resp.ok) {
        statusEl.textContent = (resp && resp.error) || '配对成功但 WSS 连接失败';
        return;
      }
      setConnected(true);
    });
  } catch (err) {
    statusEl.textContent = err instanceof Error ? err.message : '配对失败';
  }
});

document.getElementById('disconnectBtn').addEventListener('click', () => {
  chrome.runtime.sendMessage({ type: 'disconnect' }, () => setConnected(false));
});

document.getElementById('settingsBtn').addEventListener('click', async () => {
  await apiOriginReady;
  mainView.hidden = true;
  settingsView.hidden = false;
  settingsStatus.textContent = '';
  settingsStatus.className = 'hint';
  originInput.focus();
});

document.getElementById('backBtn').addEventListener('click', () => {
  settingsView.hidden = true;
  mainView.hidden = false;
});

document.getElementById('saveSettingsBtn').addEventListener('click', async () => {
  const normalizedOrigin = normalizeApiOrigin(originInput.value);
  if (!normalizedOrigin) {
    settingsStatus.textContent = '请输入有效的 HTTP 或 HTTPS 地址';
    settingsStatus.className = 'hint error';
    return;
  }

  await chrome.storage.local.set({ [API_ORIGIN_KEY]: normalizedOrigin });
  apiOrigin = normalizedOrigin;
  originInput.value = normalizedOrigin;
  settingsStatus.textContent = '已保存';
  settingsStatus.className = 'hint success';
});
