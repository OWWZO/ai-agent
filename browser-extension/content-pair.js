const PAIR_SOURCE = 'reactor';
const EXTENSION_SOURCE = 'reactor-extension';

// Let an already-open Reactor page know that the extension bridge is present.
// The page may have loaded before the extension was installed, so the
// service worker can inject this script again after installation.
window.postMessage(
  { source: EXTENSION_SOURCE, type: 'browser-extension-ready' },
  window.location.origin,
);

window.addEventListener('message', (event) => {
  if (event.source !== window) return;
  if (event.origin !== window.location.origin) return;
  const msg = event.data;
  if (!msg || msg.source !== PAIR_SOURCE || msg.type !== 'browser-pair') return;
  chrome.runtime.sendMessage(
    { type: 'pair', token: msg.token, relayUrl: msg.relayUrl },
    (resp) => {
      window.postMessage(
        {
          source: EXTENSION_SOURCE,
          type: 'browser-pair-result',
          ok: Boolean(resp && resp.ok),
          error: resp && resp.error,
        },
        event.origin,
      );
    },
  );
});
