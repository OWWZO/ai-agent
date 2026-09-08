import { afterEach, describe, expect, it, vi } from "vitest";
import { resolveGenUiAssetUrl } from "./assetUrl";

describe("resolveGenUiAssetUrl", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("rewrites loopback preview urls onto the current origin", () => {
    vi.stubGlobal("window", {
      location: {
        host: "localhost:3000",
        hostname: "localhost",
        protocol: "http:",
      },
    });

    expect(
      resolveGenUiAssetUrl(
        "http://127.0.0.1:1601/v1/file_tool/preview/session-1/bear 3d model.glb"
      )
    ).toBe(
      "http://localhost:3000/tool/v1/file_tool/preview/session-1/bear%203d%20model.glb"
    );
  });

  it("turns a workspace filename into a preview url", () => {
    vi.stubGlobal("window", {
      location: {
        host: "localhost:3000",
        hostname: "localhost",
        protocol: "http:",
      },
    });

    expect(
      resolveGenUiAssetUrl("bear 3d model.glb", "session-1788772118804-7086")
    ).toBe(
      "http://localhost:3000/tool/v1/file_tool/preview/session-1788772118804-7086/bear%203d%20model.glb"
    );
  });

  it("keeps blob and data urls unchanged", () => {
    expect(resolveGenUiAssetUrl("blob:http://localhost/abc")).toBe(
      "blob:http://localhost/abc"
    );
    expect(resolveGenUiAssetUrl("data:model/gltf-binary;base64,xx")).toBe(
      "data:model/gltf-binary;base64,xx"
    );
  });
});
