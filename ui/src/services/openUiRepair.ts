import { getDeviceId } from "@/services/agentConversation";
import { getAccessToken } from "@/stores/auth";
import { resolveServiceBaseUrl } from "@/utils/origin";

export type OpenUiFixStatus = "fixed" | "already_valid" | "fix_failed" | "disabled" | "error";

export interface OpenUiFixError {
  code?: string;
  message?: string;
  statementId?: string;
}

export interface OpenUiFixResponse {
  status: OpenUiFixStatus;
  content?: string | null;
  errors?: OpenUiFixError[];
  message?: string;
}

function resolveBaseUrl() {
  const configured = typeof SERVICE_BASE_URL === "string" ? SERVICE_BASE_URL : "";
  return resolveServiceBaseUrl(configured);
}

/**
 * 把渲染不了的 openui-lang 程序交给后端修一次（Thesys Autofix，key 在服务端）。
 *
 * 修复是增强能力：任何失败都返回 status 由调用方降级，不抛异常打断渲染。
 */
export async function repairOpenUiProgram(
  program: string,
  options: { context?: { role: string; content: string }[]; signal?: AbortSignal } = {}
): Promise<OpenUiFixResponse> {
  const { context, signal } = options;
  // 鉴权走 Bearer（AuthenticationFilter 只认这个，不认 cookie）；修复是后台行为，
  // 不走 utils/request 的 axios 实例，避免失败时弹 toast 打断对话。
  const token = getAccessToken();
  try {
    const response = await fetch(`${resolveBaseUrl()}/api/agent/openui/repair`, {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        "X-Device-Id": getDeviceId(),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ program, context: context ?? [] }),
      signal,
    });
    if (!response.ok) {
      return { status: "error", message: `HTTP ${response.status}` };
    }
    return (await response.json()) as OpenUiFixResponse;
  } catch (error) {
    return { status: "error", message: error instanceof Error ? error.message : String(error) };
  }
}
