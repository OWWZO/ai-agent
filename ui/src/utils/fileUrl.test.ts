import { afterEach, describe, expect, it, vi } from "vitest";

import { normalizeFileUrlForBrowser } from "./fileUrl";

describe("normalizeFileUrlForBrowser", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("should rewrite loopback tool url to current origin tool path", () => {
    vi.stubGlobal("window", {
      location: {
        host: "localhost:3000",
        hostname: "localhost",
        protocol: "http:",
      },
    });

    expect(
      normalizeFileUrlForBrowser("http://127.0.0.1:1601/v1/file_tool/preview/req/demo.html")
    ).toBe("http://localhost:3000/tool/v1/file_tool/preview/req/demo.html");
  });
});
