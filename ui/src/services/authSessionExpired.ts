export const AUTH_SESSION_EXPIRED_EVENT = "reactor-auth-session-expired";

export type AuthSessionExpiredDetail = {
  returnUrl?: string;
};

let authSessionExpiredPending = false;

export function emitAuthSessionExpired(returnUrl?: string) {
  if (typeof window === "undefined") {
    return;
  }
  if (authSessionExpiredPending) {
    return;
  }
  authSessionExpiredPending = true;

  window.dispatchEvent(new CustomEvent<AuthSessionExpiredDetail>(
    AUTH_SESSION_EXPIRED_EVENT,
    { detail: { returnUrl } }
  ));
}

export function resetAuthSessionExpiredNotification() {
  authSessionExpiredPending = false;
}
