import { trimTrailingSlash } from "@/pages/WorkspaceImageGeneration/utils";

// 浏览器端只使用当前页面的 origin，避免服务端返回的旧工具地址把资源请求带回历史端口。
function buildCurrentToolOrigin(): string {
  if (typeof window === "undefined") {
    return "";
  }
  return `${window.location.protocol}//${window.location.host}`;
}

function buildCurrentToolBaseUrl(): string {
  const origin = buildCurrentToolOrigin();
  if (!origin) {
    return "";
  }
  return `${origin}/tool`;
}

// 本地开发端口、旧域名和当前工具路径都可能出现在历史产物中，需要统一迁移到当前页面。
function shouldRewriteToCurrentTool(url: URL): boolean {
  if (typeof window === "undefined") {
    return false;
  }

  const currentHost = window.location.host;
  const currentHostname = window.location.hostname;
  const isLoopback =
    url.hostname === "127.0.0.1" ||
    url.hostname === "localhost";
  const isLegacyTopDomain =
    url.hostname === "owwzo.top" ||
    url.hostname === "www.owwzo.top" ||
    url.hostname === "owwzo.cloud" ||
    url.hostname === "www.owwzo.cloud";
  const isToolPort = url.port === "1601";

  if (isToolPort || (isLoopback && !url.port)) {
    return true;
  }

  if (isLegacyTopDomain && currentHostname !== url.hostname) {
    return true;
  }

  return url.host === currentHost && url.pathname.startsWith("/tool/");
}

export function normalizeToolBaseUrlForBrowser(rawUrl?: string | null): string {
  const normalized = trimTrailingSlash(rawUrl || "");
  const currentToolBaseUrl = buildCurrentToolBaseUrl();
  const currentOrigin = buildCurrentToolOrigin();

  if (!normalized) {
    return currentToolBaseUrl;
  }

  try {
    // 正常分支保留查询参数和 hash；只有命中旧地址规则时才替换 origin/path。
    const parsed = new URL(normalized, currentOrigin || "https://workspace.local");
    if (!shouldRewriteToCurrentTool(parsed)) {
      return parsed.toString().replace(/\/$/, "");
    }

    if (!currentToolBaseUrl) {
      return parsed.toString().replace(/\/$/, "");
    }

    const currentToolUrl = new URL(currentToolBaseUrl);
    currentToolUrl.pathname = parsed.pathname.startsWith("/tool")
      ? parsed.pathname
      : `/tool${parsed.pathname}`;
    currentToolUrl.search = parsed.search;
    currentToolUrl.hash = parsed.hash;
    return currentToolUrl.toString().replace(/\/$/, "");
  } catch {
    // 部分历史值不是完整 URL，按路径前缀兜底，保证坏数据不会阻断页面渲染。
    if (!currentToolBaseUrl) {
      return normalized;
    }
    if (normalized === "/tool") {
      return currentToolBaseUrl;
    }
    if (normalized.startsWith("/tool/")) {
      return `${currentOrigin}${normalized}`;
    }
    if (normalized.startsWith("/")) {
      return `${currentOrigin}${normalized}`;
    }
    return normalized;
  }
}

export function normalizeFileUrlForBrowser(rawUrl?: string | null): string {
  const normalized = (rawUrl || "").trim();
  if (!normalized) {
    return "";
  }

  try {
    // 文件资源沿用同一套旧地址迁移规则，但保留文件自身的查询参数和 hash。
    const parsed = new URL(normalized);
    if (!shouldRewriteToCurrentTool(parsed)) {
      return parsed.toString();
    }

    const currentToolBaseUrl = buildCurrentToolBaseUrl();
    if (!currentToolBaseUrl) {
      return parsed.toString();
    }

    const currentToolUrl = new URL(currentToolBaseUrl);
    currentToolUrl.pathname = parsed.pathname.startsWith("/tool/")
      ? parsed.pathname
      : `/tool${parsed.pathname}`;
    currentToolUrl.search = parsed.search;
    currentToolUrl.hash = parsed.hash;
    return currentToolUrl.toString();
  } catch {
    // 相对路径无法被 URL 解析时，仅补当前 origin，不猜测其它外部主机。
    if (normalized.startsWith("/tool/")) {
      const currentOrigin = buildCurrentToolOrigin();
      return currentOrigin ? `${currentOrigin}${normalized}` : normalized;
    }
    return normalized;
  }
}
