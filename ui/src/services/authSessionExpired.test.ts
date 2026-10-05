import { afterEach, describe, expect, it, vi } from "vitest";

import {
  AUTH_SESSION_EXPIRED_EVENT,
  emitAuthSessionExpired,
  resetAuthSessionExpiredNotification,
} from "./authSessionExpired";

describe("auth session expiry event", () => {
  afterEach(() => {
    resetAuthSessionExpiredNotification();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("通知应用通过 SPA 路由处理认证失效", () => {
    vi.stubGlobal("window", new EventTarget());
    const listener = vi.fn();
    window.addEventListener(AUTH_SESSION_EXPIRED_EVENT, listener);

    emitAuthSessionExpired("/workspace/models");

    expect(listener).toHaveBeenCalledTimes(1);
    expect(listener.mock.calls[0][0]).toMatchObject({
      type: AUTH_SESSION_EXPIRED_EVENT,
      detail: { returnUrl: "/workspace/models" },
    });
    window.removeEventListener(AUTH_SESSION_EXPIRED_EVENT, listener);
  });
});
