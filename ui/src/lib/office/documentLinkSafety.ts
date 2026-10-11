/**
 * Office 文档（docx / xlsx / pptx）预览内的超链接安全收口。
 *
 * 从 ZCode `packages/ui/src/lib/officeFilePreview.ts` 移植。
 *
 * 文档内容来自不受信任的 OOXML relationship，渲染器会把 `<a href>` 原样写进 DOM，
 * 因此必须做二次净化：只允许 http(s) 外链与文档内锚点，其余协议降级为不可点击文本；
 * 外链点击统一交给受控的外部浏览器入口，禁止直接导航预览容器。
 *
 * 这是 docx / xlsx / pptx 三类文档预览共用的**唯一**链接安全实现。
 */

/**
 * 文档内容来自不受信任的 OOXML relationship，不能把 Target 原样交给 DOM。
 * 只允许普通 Web 外链和当前文档内部锚点；其余协议一律降级为不可点击文本。
 */
export function sanitizeDocumentHref(value: unknown): string | null {
  if (typeof value !== "string") {
    return null;
  }

  const href = value.trim();
  if (!href) {
    return null;
  }

  // 控制字符可以把 `java\nscript:` 伪装成看似普通的协议；先拒绝而不是尝试修复。
  // 逐字符判断避免安全正则本身触发 lint 的 no-control-regex 警告。
  for (const character of href) {
    const codePoint = character.codePointAt(0);
    if (
      codePoint !== undefined &&
      (codePoint <= 0x1f || (codePoint >= 0x7f && codePoint <= 0x9f))
    ) {
      return null;
    }
  }

  if (href.startsWith("#")) {
    return href.length > 1 && !/[\s<>"']/u.test(href) ? href : null;
  }

  const scheme = href.match(/^([a-z][a-z0-9+.-]*):/iu)?.[1]?.toLowerCase();
  if (scheme !== "http" && scheme !== "https") {
    return null;
  }

  try {
    const parsed = new URL(href);
    return parsed.protocol === "http:" || parsed.protocol === "https:" ? href : null;
  } catch {
    return null;
  }
}

/** renderer 写入 DOM 后再做一次安全收口，覆盖 parser 生成的 HTML/SVG 超链接。 */
const DOCUMENT_LINK_SELECTOR = "a[href], a[xlink\\:href]";

function sanitizeDocumentLinkElement(element: Element): void {
  for (const attribute of ["href", "xlink:href"] as const) {
    const value = element.getAttribute(attribute);
    if (value === null) {
      continue;
    }

    const safeHref = sanitizeDocumentHref(value);
    if (safeHref === null) {
      element.removeAttribute(attribute);
      if (element instanceof HTMLElement && element.tagName === "A") {
        element.setAttribute("aria-disabled", "true");
      }
      continue;
    }

    if (safeHref !== value) {
      element.setAttribute(attribute, safeHref);
    }
  }
}

function sanitizeDocumentLinks(root: ParentNode): void {
  if (root instanceof Element && root.matches(DOCUMENT_LINK_SELECTOR)) {
    sanitizeDocumentLinkElement(root);
  }
  root.querySelectorAll(DOCUMENT_LINK_SELECTOR).forEach(sanitizeDocumentLinkElement);
}

/**
 * 为文档渲染挂载点安装统一的 DOM 净化和点击阻断；返回卸载函数。
 *
 * 未提供 onOpenBrowserUrl 时，外链走 `window.open(..., "noopener,noreferrer")`，
 * 避免直接在宿主页面里导航。
 */
export function installDocumentLinkSafety(
  root: HTMLElement,
  onOpenBrowserUrl?: (url: string) => void
): () => void {
  const openUrl =
    onOpenBrowserUrl ??
    ((url: string) => {
      window.open(url, "_blank", "noopener,noreferrer");
    });

  const handleClick = (event: Event) => {
    const target = event.target instanceof Element ? event.target.closest("a") : null;
    if (!target) {
      return;
    }

    const safeHref = sanitizeDocumentHref(
      target.getAttribute("href") ?? target.getAttribute("xlink:href")
    );
    if (safeHref === null) {
      event.preventDefault();
      return;
    }
    if (safeHref.startsWith("#")) {
      return;
    }

    // 文档外链不能直接导航主 renderer；统一交给受控入口。
    event.preventDefault();
    openUrl(safeHref);
  };

  sanitizeDocumentLinks(root);
  root.addEventListener("click", handleClick);
  const observer =
    typeof MutationObserver === "undefined"
      ? null
      : new MutationObserver((records) => {
          const addedRoots = new Set<ParentNode>();
          for (const record of records) {
            if (record.type === "attributes" && record.target instanceof Element) {
              sanitizeDocumentLinkElement(record.target);
              continue;
            }
            for (const addedNode of record.addedNodes) {
              if (addedNode instanceof Element || addedNode instanceof DocumentFragment) {
                addedRoots.add(addedNode);
              }
            }
          }
          // 第三方 renderer 分批挂载或虚拟化重挂载时，每批 mutation 都曾扫描
          // 完整预览 DOM，导致大文档重复 O(全树) 查询。这里只扫描实际新增的子树。
          addedRoots.forEach(sanitizeDocumentLinks);
        });
  observer?.observe(root, {
    attributeFilter: ["href", "xlink:href"],
    attributes: true,
    childList: true,
    subtree: true,
  });

  return () => {
    observer?.disconnect();
    root.removeEventListener("click", handleClick);
  };
}
