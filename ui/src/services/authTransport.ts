import axios from "axios";

import {
  setAuthSession,
  type AuthUser,
} from "@/stores/auth";
import { resolveServiceBaseUrl } from "@/utils/origin";

export interface AuthSessionResponse {
  accessToken: string;
  user?: AuthUser | null;
}

interface ApiEnvelope<T> {
  code: number | string;
  data: T;
  msg?: string;
  info?: string;
}

const refreshClient = axios.create({
  baseURL: resolveServiceBaseUrl(SERVICE_BASE_URL),
  timeout: 10000,
  withCredentials: true,
  headers: { "Content-Type": "application/json" },
});

let refreshRequest: Promise<string> | null = null;

function unwrapResponse<T>(response: unknown): T {
  if (response && typeof response === "object" && "code" in response) {
    const envelope = response as ApiEnvelope<T>;
    if (envelope.code === 200 || envelope.code === "0000") {
      return envelope.data;
    }
    throw new Error(envelope.msg || envelope.info || "登录状态已失效");
  }
  return response as T;
}

export function refreshAccessToken() {
  if (refreshRequest) {
    return refreshRequest;
  }

  refreshRequest = refreshClient
    .post<AuthSessionResponse>("/api/auth/refresh", {}, { withCredentials: true })
    .then(({ data }) => unwrapResponse<AuthSessionResponse>(data))
    .then((session) => {
      if (!session?.accessToken) {
        throw new Error("刷新登录状态失败");
      }
      setAuthSession(session.accessToken, session.user);
      return session.accessToken;
    })
    .finally(() => {
      refreshRequest = null;
    });

  return refreshRequest;
}
