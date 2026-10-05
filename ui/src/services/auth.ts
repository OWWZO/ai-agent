import api from "./index";
import { refreshAccessToken, type AuthSessionResponse } from "./authTransport";
import {
  clearAuthSession,
  getAuthState,
  setAuthSession,
  setAuthUser,
  setAuthInitializing,
  type AuthUser,
} from "@/stores/auth";

export interface LoginPayload {
  loginName: string;
  password: string;
}

export interface RegisterPayload extends LoginPayload {
  nickname?: string;
}

export interface ChangePasswordPayload {
  oldPassword: string;
  newPassword: string;
}

const post = <T>(url: string, data?: unknown, config?: object) =>
  api.post<T>(url, data, config) as unknown as Promise<T>;

export const authApi = {
  register: (payload: RegisterPayload) =>
    post<AuthSessionResponse>("/api/auth/register", payload),

  login: async (payload: LoginPayload) => {
    const session = await post<AuthSessionResponse>("/api/auth/login", payload);
    if (!session?.accessToken) {
      throw new Error("登录响应缺少访问令牌");
    }
    setAuthSession(session.accessToken, session.user ?? null);
    return session;
  },

  refresh: async () => {
    const accessToken = await refreshAccessToken();
    return {
      accessToken,
      user: getAuthState().user,
    };
  },

  logout: async () => {
    try {
      await post<void>("/api/auth/logout");
    } finally {
      clearAuthSession();
    }
  },

  logoutAll: async () => {
    try {
      await post<void>("/api/auth/logout-all");
    } finally {
      clearAuthSession();
    }
  },

  me: () =>
    api.get<AuthUser>("/api/auth/me") as unknown as Promise<AuthUser>,

  password: (payload: ChangePasswordPayload) =>
    api.put<void>("/api/auth/password", payload) as unknown as Promise<void>,
};

export async function loadCurrentUser() {
  const user = await authApi.me();
  setAuthUser(user);
  return user;
}

let restoreRequest: Promise<void> | null = null;

export function restoreAuthSession() {
  if (restoreRequest) {
    return restoreRequest;
  }

  setAuthInitializing();
  restoreRequest = (async () => {
    try {
      await authApi.refresh();
      if (!getAuthState().user) {
        await loadCurrentUser().catch(() => undefined);
      }
    } catch {
      clearAuthSession();
    }
  })().finally(() => {
    restoreRequest = null;
  });

  return restoreRequest;
}
