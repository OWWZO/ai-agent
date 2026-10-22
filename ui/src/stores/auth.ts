import { create } from "zustand";
import { useShallow } from "zustand/react/shallow";
import { resetAuthSessionExpiredNotification } from "@/services/authSessionExpired";

export interface AuthUser {
  id?: string | number;
  userId?: string | number;
  account?: string;
  username?: string;
  nickname?: string;
  email?: string;
  role?: string;
}

export type AuthStatus = "initializing" | "authenticated" | "anonymous";

export interface AuthState {
  status: AuthStatus;
  accessToken: string | null;
  user: AuthUser | null;
}

interface AuthStore extends AuthState {
  setSession: (accessToken: string, user?: AuthUser | null) => void;
  setUser: (user: AuthUser) => void;
  setInitializing: () => void;
  clear: () => void;
}

/**
 * 登录态。
 *
 * 用 zustand 而非 `useSyncExternalStore` 手写订阅：
 * 非 React 场景（axios 拦截器、SSE、鉴权传输层）直接 `useAuthStore.getState()`
 * 取当前值，与组件内 `useAuth()` 读到的是同一份状态。
 */
export const useAuthStore = create<AuthStore>((set, get) => ({
  status: "initializing",
  accessToken: null,
  user: null,

  setSession: (accessToken, user) => {
    resetAuthSessionExpiredNotification();
    set({
      status: "authenticated",
      accessToken,
      user: user === undefined ? get().user : user,
    });
  },

  setUser: (user) =>
    set({
      status: "authenticated",
      user,
    }),

  setInitializing: () => set({ status: "initializing" }),

  clear: () =>
    set({
      status: "anonymous",
      accessToken: null,
      user: null,
    }),
}));

/** 省略参数时读当前 store 里的用户。 */
export function isAdminUser(user?: AuthUser | null) {
  const target = user === undefined ? useAuthStore.getState().user : user;
  return target?.role?.trim().toUpperCase() === "ADMIN";
}

export function getAccessToken() {
  return useAuthStore.getState().accessToken;
}

/** 非 React 场景读取完整登录态。 */
export function getAuthState(): AuthState {
  return useAuthStore.getState();
}

export function setAuthSession(accessToken: string, user?: AuthUser | null) {
  useAuthStore.getState().setSession(accessToken, user);
}

export function setAuthUser(user: AuthUser) {
  useAuthStore.getState().setUser(user);
}

export function setAuthInitializing() {
  useAuthStore.getState().setInitializing();
}

export function clearAuthSession() {
  useAuthStore.getState().clear();
}

/** 组件内读取登录态。返回整个 state，与改造前的返回形状一致。 */
export function useAuth(): AuthState {
  // 只订阅这三个字段：store 上的 action 引用不会变，但显式挑选能避免
  // 将来新增非展示字段时把组件一起重渲染。
  return useAuthStore(
    useShallow((state) => ({
      status: state.status,
      accessToken: state.accessToken,
      user: state.user,
    }))
  );
}
