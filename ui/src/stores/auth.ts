import { useSyncExternalStore } from "react";
import { resetAuthSessionExpiredNotification } from "@/services/authSessionExpired";

export interface AuthUser {
  id?: string | number;
  userId?: string | number;
  account?: string;
  username?: string;
  nickname?: string;
  email?: string;
}

export type AuthStatus = "initializing" | "authenticated" | "anonymous";

export interface AuthState {
  status: AuthStatus;
  accessToken: string | null;
  user: AuthUser | null;
}

let state: AuthState = {
  status: "initializing",
  accessToken: null,
  user: null,
};

const listeners = new Set<() => void>();

function updateState(nextState: AuthState) {
  state = nextState;
  listeners.forEach((listener) => listener());
}

export function subscribeAuth(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getAuthState() {
  return state;
}

export function getAccessToken() {
  return state.accessToken;
}

export function setAuthSession(accessToken: string, user?: AuthUser | null) {
  resetAuthSessionExpiredNotification();
  updateState({
    status: "authenticated",
    accessToken,
    user: user === undefined ? state.user : user,
  });
}

export function setAuthUser(user: AuthUser) {
  updateState({
    ...state,
    status: "authenticated",
    user,
  });
}

export function setAuthInitializing() {
  updateState({
    ...state,
    status: "initializing",
  });
}

export function clearAuthSession() {
  updateState({
    status: "anonymous",
    accessToken: null,
    user: null,
  });
}

export function useAuth() {
  return useSyncExternalStore(subscribeAuth, getAuthState, getAuthState);
}
