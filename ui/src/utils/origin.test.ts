import { afterEach, describe, expect, it, vi } from "vitest";

import { resolveServiceBaseUrl } from "./origin";

describe("resolveServiceBaseUrl", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("should align loopback backend host to current localhost page host", () => {
    vi.stubGlobal("window", {
      location: {
        hostname: "localhost",
      },
    });

    expect(resolveServiceBaseUrl("http://127.0.0.1:8100")).toBe("http://localhost:8100");
  });

  it("should keep non-loopback host untouched", () => {
    vi.stubGlobal("window", {
      location: {
        hostname: "localhost",
      },
    });

    expect(resolveServiceBaseUrl("https://api.example.com")).toBe("https://api.example.com");
  });

  it("should keep configured host when current page is not loopback", () => {
    vi.stubGlobal("window", {
      location: {
        hostname: "workspace.example.com",
      },
    });

    expect(resolveServiceBaseUrl("http://127.0.0.1:8100")).toBe("http://127.0.0.1:8100");
  });
});
