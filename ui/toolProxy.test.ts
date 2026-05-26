import { describe, expect, it } from "vitest";

import { createToolProxyConfig } from "./toolProxy";

describe("createToolProxyConfig", () => {
  it("should proxy local tool requests to the default reactor-tool server", () => {
    const proxy = createToolProxyConfig();

    expect(proxy.target).toBe("http://127.0.0.1:1601");
    expect(proxy.rewrite("/tool/v1/file_tool/preview/req/demo.html")).toBe(
      "/v1/file_tool/preview/req/demo.html"
    );
  });

  it("should preserve configured tool base path in rewrite result", () => {
    const proxy = createToolProxyConfig("https://www.owwzo.top/tool");

    expect(proxy.target).toBe("https://www.owwzo.top");
    expect(proxy.rewrite("/tool/v1/file_tool/preview/req/demo.html")).toBe(
      "/tool/v1/file_tool/preview/req/demo.html"
    );
  });
});
