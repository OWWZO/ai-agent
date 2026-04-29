import api from "./index";

const DEFAULT_DEVICE_ID = "device-default";

let runtimeDeviceId: string | null = DEFAULT_DEVICE_ID;

/**
 * 当前前端已经移除历史对话持久化，只保留一个轻量设备标识，
 * 兼容仍然需要该请求头的上传和流式接口。
 */
export function getDeviceId(): string {
  if (!runtimeDeviceId) {
    runtimeDeviceId = DEFAULT_DEVICE_ID;
  }
  return runtimeDeviceId;
}

export function getDeviceHeaders(): Record<string, string> {
  return { "X-Device-Id": getDeviceId() };
}

export interface FixRoleItem {
  agentId: string;
  agentName: string;
  description?: string;
  defaultRole: boolean;
}

export const roleLibraryApi = {
  list: () => api.get<FixRoleItem[]>(`/api/agent/role-library/list`),
};
