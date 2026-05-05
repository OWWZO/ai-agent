/**
 * 统一前端访问后端时使用的同源地址，避免 localhost / 127.0.0.1 混用导致 Cookie 丢失。
 */
export function resolveServiceBaseUrl(configuredBaseUrl?: string): string {
  const normalized = (configuredBaseUrl || "").trim();
  if (!normalized) {
    return normalized;
  }

  try {
    const parsed = new URL(normalized);
    if (typeof window !== "undefined") {
      const currentHost = window.location.hostname;
      const isLoopbackHost =
        parsed.hostname === "127.0.0.1" || parsed.hostname === "localhost";
      const shouldAlignToCurrentHost =
        isLoopbackHost &&
        (currentHost === "127.0.0.1" || currentHost === "localhost");

      if (shouldAlignToCurrentHost && parsed.hostname !== currentHost) {
        parsed.hostname = currentHost;
        return parsed.toString().replace(/\/$/, "");
      }
    }
    return parsed.toString().replace(/\/$/, "");
  } catch {
    return normalized.replace(/\/$/, "");
  }
}
