/**
 * 在同源 iframe 里执行注入脚本并回传结果。
 *
 * 与 ZCode 的差异：ZCode 走 Electron `<webview>.executeJavaScript()`（主进程可注入任意源）；
 * 本项目是纯浏览器 iframe，只能操作**同源**文档。
 *
 * 可行性依据：`utils/fileUrl.ts` 的 `normalizeFileUrlForBrowser()` 把文件 URL 统一改写成
 * `${window.location.origin}/tool/...`（dev 走 vite `/tool` 代理、生产走 www 反代），
 * 因此 iframe 与主应用同源；`srcDoc` 渲染的内联 HTML 更是直接继承父窗口 origin。
 *
 * 跨源场景（将来 HTML 来源变成第三方站）`contentDocument` 会抛 SecurityError —— 这里
 * 明确失败并给出可读原因，而不是静默卡住。
 */

export class CrossOriginPreviewError extends Error {
  constructor() {
    super("预览页面与主应用不同源，无法注入元素选择脚本");
    this.name = "CrossOriginPreviewError";
  }
}

type PickerBridge = Record<string, unknown>;

export async function executeScriptInIframe(
  iframe: HTMLIFrameElement | null | undefined,
  script: string,
  timeoutMs = 120_000
): Promise<unknown> {
  if (!iframe) {
    throw new Error("预览尚未就绪");
  }

  let win: Window | null = null;
  let doc: Document | null = null;
  try {
    win = iframe.contentWindow;
    doc = iframe.contentDocument;
  } catch {
    throw new CrossOriginPreviewError();
  }

  if (!win || !doc || !doc.documentElement) {
    throw new Error("预览尚未就绪");
  }

  const targetWin = win;
  const targetDoc = doc;
  const bridge = targetWin as unknown as PickerBridge;
  const token = `__cutePickerBridge_${Date.now()}_${Math.random().toString(36).slice(2)}`;
  const rejectKey = `${token}_reject`;

  return new Promise<unknown>((resolve, reject) => {
    let settled = false;
    let timer = 0;
    let scriptEl: HTMLScriptElement | null = null;

    const cleanup = () => {
      if (timer) window.clearTimeout(timer);
      try {
        delete bridge[token];
        delete bridge[rejectKey];
      } catch {
        bridge[token] = undefined;
        bridge[rejectKey] = undefined;
      }
      scriptEl?.remove();
      scriptEl = null;
    };

    const settle = (fn: () => void) => {
      if (settled) return;
      settled = true;
      cleanup();
      fn();
    };

    timer = window.setTimeout(() => {
      settle(() => reject(new Error("网页元素选择超时")));
    }, timeoutMs);

    bridge[token] = (result: unknown) => settle(() => resolve(result));
    bridge[rejectKey] = (message: unknown) => settle(() => reject(new Error(String(message))));

    // 注入的脚本本身返回一个 Promise（选择脚本 resolve 于用户点击或 Esc），
    // 这里用 contentWindow 上的桥接函数把结果送回父窗口。
    scriptEl = targetDoc.createElement("script");
    scriptEl.textContent = [
      "(function(){",
      `var t=${JSON.stringify(token)};`,
      `Promise.resolve().then(function(){return (${script});}).then(function(r){`,
      'var f=window[t];if(typeof f==="function")f(r);',
      "},function(e){",
      'var f=window[t+"_reject"];if(typeof f==="function")f(String((e&&e.message)||e));',
      "});})();",
    ].join("");
    targetDoc.documentElement.appendChild(scriptEl);
  });
}
