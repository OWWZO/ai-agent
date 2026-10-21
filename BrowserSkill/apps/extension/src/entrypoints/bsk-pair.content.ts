/**
 * bsk 扩展的网页配对桥（021）：让宿主页面（Reactor 前端）一键完成 remote 配对。
 *
 * 对齐老 Reactor 扩展 `content-pair.js` 的消息协议（source === "reactor"），
 * 但监听 bsk 专属的 `bsk-browser-pair` 类型，避免两条扩展在同一页面上
 * 互相消费对方的配对消息：
 *
 *   window.postMessage({ source: "reactor", type: "bsk-browser-pair",
 *                        pairingUrl: "wss://host/api/agent/browser/bsk-relay#<token>" },
 *                      origin)
 *
 * 收到后经 chrome.runtime.sendMessage 转给 background，由
 * {@link watchWebPairing} 调 `updateRemoteConnection(pairingUrl)` ——
 * 与 popup「保存配对」走完全相同的验证/存储/重连路径，唯一区别是
 * 配对串来自网页而不是 popup 输入框。
 *
 * 安全校验（对齐 content-pair.js）：只接受同窗口同源消息；配对串的
 * 校验（WSS 强制、短码或长 token）由 parseRemoteEndpoint 承担，
 * 本脚本不做重复校验——非法串会被 background 拒绝并回传错误。
 */

const PAIR_SOURCE = "reactor";
const EXTENSION_SOURCE = "reactor-bsk-extension";
const PAIR_TYPE = "bsk-browser-pair";

export default defineContentScript({
  // 与前端"连接浏览器"按钮所在页面一致：本机开发与任意 https 站点。
  matches: ["http://localhost:*/*", "http://127.0.0.1:*/*", "https://*/*"],
  runAt: "document_idle",

  main(ctx) {
    const onMessage = (event: MessageEvent) => {
      if (event.source !== window) return;
      if (event.origin !== window.location.origin) return;
      const msg = event.data;
      if (!msg || msg.source !== PAIR_SOURCE || msg.type !== PAIR_TYPE) return;
      if (typeof msg.pairingUrl !== "string" || msg.pairingUrl.trim() === "") return;

      chrome.runtime.sendMessage(
        { kind: "bsk-web-pairing", pairing: msg.pairingUrl },
        (resp) => {
          void resp; // 结果经 storage 变化驱动连接重建；页面侧无需等待
          window.postMessage(
            {
              source: EXTENSION_SOURCE,
              type: "bsk-browser-pair-result",
              ok: Boolean(resp && !resp.error),
              error: resp?.error,
            },
            event.origin,
          );
        },
      );
    };
    window.addEventListener("message", onMessage);

    // 页面先于扩展加载时，通知页面本桥已就绪（对齐 content-pair.js）。
    window.postMessage(
      { source: EXTENSION_SOURCE, type: "bsk-extension-ready" },
      window.location.origin,
    );

    ctx.onInvalidated(() => {
      window.removeEventListener("message", onMessage);
    });
  },
});
