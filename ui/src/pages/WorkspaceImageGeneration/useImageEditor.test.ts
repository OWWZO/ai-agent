import { describe, expect, it, vi } from "vitest";

import { revokeEditorImageObjectUrls } from "./useImageEditor";

describe("useImageEditor helpers", () => {
  it("卸载编辑器时会释放所有 objectUrl", () => {
    const revokeSpy = vi
      .spyOn(URL, "revokeObjectURL")
      .mockImplementation(() => {});

    revokeEditorImageObjectUrls([
      { objectUrl: "blob:img-1" },
      { objectUrl: "blob:img-2" },
    ]);

    expect(revokeSpy).toHaveBeenCalledWith("blob:img-1");
    expect(revokeSpy).toHaveBeenCalledWith("blob:img-2");
  });
});
