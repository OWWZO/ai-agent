import { describe, expect, it } from "vitest";
import {
  buildEditCodeViewerSource,
  buildReadCodeViewerSource,
  buildTextCodeViewerSource,
  pathBaseName,
  pathBreadcrumb,
  sourceTabKey,
  sourceTabMeta,
  type CodeViewerSource,
} from "./codeViewerSource";

describe("buildEditCodeViewerSource —— 对齐 ZCode edit.tsx", () => {
  it("有 patch 时产出 patch source，并保留真实路径", () => {
    const source = buildEditCodeViewerSource({
      path: "src/app/main.ts",
      fileName: "main.ts",
      patch: "--- a/x\n+++ b/x\n@@\n-a\n+b",
    });
    expect(source).toEqual({
      type: "patch",
      title: "main.ts",
      path: "src/app/main.ts",
      patch: "--- a/x\n+++ b/x\n@@\n-a\n+b",
    });
  });

  it("无 patch（如 replace_all / write 兜底）时降级为 file source", () => {
    const source = buildEditCodeViewerSource({
      path: "src/app/main.ts",
      fileName: "main.ts",
    });
    expect(source).toEqual({
      type: "file",
      title: "main.ts",
      path: "src/app/main.ts",
    });
  });

  it("path 用的是真实路径而非展示目录名", () => {
    const source = buildEditCodeViewerSource({
      path: "a/b/c/deep.ts",
      fileName: "deep.ts",
    });
    expect(source.type === "file" && source.path).toBe("a/b/c/deep.ts");
  });

  it("fileName 缺失时从 path 兜底取 basename", () => {
    const source = buildEditCodeViewerSource({
      path: "src\\windows\\style.ts",
      fileName: "",
    });
    expect(source.title).toBe("style.ts");
  });
});

describe("buildReadCodeViewerSource", () => {
  it("总是产出 file source", () => {
    expect(
      buildReadCodeViewerSource({ path: "docs/readme.md" })
    ).toEqual({
      type: "file",
      title: "readme.md",
      path: "docs/readme.md",
    });
  });

  it("fileName 优先于 path basename", () => {
    const source = buildReadCodeViewerSource({
      path: "some/where/index.tsx",
      fileName: "入口.tsx",
    });
    expect(source.title).toBe("入口.tsx");
  });
});

describe("buildTextCodeViewerSource", () => {
  it("按 path 扩展名推断语言", () => {
    const source = buildTextCodeViewerSource({
      title: "片段",
      path: "a/b.py",
      content: "print(1)",
    });
    expect(source).toEqual({
      type: "text",
      title: "片段",
      path: "a/b.py",
      content: "print(1)",
      language: "python",
    });
  });

  it("显式 language 优先于推断", () => {
    const source = buildTextCodeViewerSource({
      title: "片段",
      path: "a/b.py",
      content: "x",
      language: "plaintext",
    });
    expect(source.type === "text" && source.language).toBe("plaintext");
  });

  it("未知扩展名回退到 text", () => {
    const source = buildTextCodeViewerSource({
      title: "blob.weirdext",
      content: "x",
    });
    expect(source.type === "text" && source.language).toBe("text");
  });
});

describe("sourceTabKey —— tab 去重", () => {
  const patchA: CodeViewerSource = {
    type: "patch",
    title: "a.ts",
    path: "src/a.ts",
    patch: "P1",
  };

  it("同文件同 patch → 同 key（不重复开 tab）", () => {
    expect(sourceTabKey(patchA)).toBe(sourceTabKey({ ...patchA }));
  });

  it("同文件不同 patch → 不同 key", () => {
    expect(sourceTabKey(patchA)).not.toBe(
      sourceTabKey({
        ...patchA,
        patch: "P2"
      })
    );
  });

  it("不同文件同 patch → 不同 key", () => {
    expect(sourceTabKey(patchA)).not.toBe(
      sourceTabKey({
        ...patchA,
        path: "src/b.ts"
      })
    );
  });

  it("file 按路径去重", () => {
    const f: CodeViewerSource = {
      type: "file",
      title: "a.ts",
      path: "src/a.ts"
    };
    expect(sourceTabKey(f)).toBe("file:src/a.ts");
    expect(sourceTabKey(f)).toBe(sourceTabKey({
      ...f,
      title: "改名了"
    }));
  });

  it("text 按路径 + 内容哈希去重", () => {
    const t: CodeViewerSource = {
      type: "text",
      title: "snip",
      path: "src/a.ts",
      content: "1",
      language: "typescript",
    };
    expect(sourceTabKey(t)).toBe(sourceTabKey({ ...t }));
    expect(sourceTabKey(t)).not.toBe(sourceTabKey({
      ...t,
      content: "2"
    }));
  });

  it("image 按路径去重", () => {
    const i: CodeViewerSource = {
      type: "image",
      title: "p.png",
      path: "p.png"
    };
    expect(sourceTabKey(i)).toBe("image:p.png");
  });

  it("四种类型的 key 前缀互不冲突", () => {
    const keys = [
      sourceTabKey({
        type: "file",
        title: "a",
        path: "x"
      }),
      sourceTabKey({
        type: "patch",
        title: "a",
        path: "x",
        patch: "p"
      }),
      sourceTabKey({
        type: "text",
        title: "a",
        path: "x",
        content: "c",
        language: "text",
      }),
      sourceTabKey({
        type: "image",
        title: "a",
        path: "x"
      }),
    ];
    expect(new Set(keys).size).toBe(4);
  });
});

describe("sourceTabMeta", () => {
  it("patch：标题 = 展示名，副标题 = 路径", () => {
    const meta = sourceTabMeta({
      type: "patch",
      title: "a.ts",
      path: "src/a.ts",
      patch: "p",
    });
    expect(meta).toEqual({
      title: "a.ts",
      subtitle: "src/a.ts",
      kind: "patch"
    });
  });

  it("patch 无路径时副标题回退为「补丁」", () => {
    const meta = sourceTabMeta({
      type: "patch",
      title: "a.ts",
      patch: "p"
    });
    expect(meta.subtitle).toBe("补丁");
  });

  it("text 无路径时副标题回退为 language", () => {
    const meta = sourceTabMeta({
      type: "text",
      title: "snip",
      content: "x",
      language: "python",
    });
    expect(meta.subtitle).toBe("python");
  });

  it("四种类型的 kind 与输入一致", () => {
    const sources: CodeViewerSource[] = [
      {
        type: "file",
        title: "a",
        path: "x"
      },
      {
        type: "patch",
        title: "a",
        patch: "p"
      },
      {
        type: "text",
        title: "a",
        content: "c",
        language: "text"
      },
      {
        type: "image",
        title: "a",
        path: "x"
      },
    ];
    expect(sources.map((s) => sourceTabMeta(s).kind)).toEqual([
      "file",
      "patch",
      "text",
      "image",
    ]);
  });
});

describe("pathBaseName", () => {
  it.each([
    ["src/a.ts", "a.ts"],
    ["src\\a.ts", "a.ts"],
    ["/abs/path/x/y.png", "y.png"],
    ["C:\\Users\\me\\f.txt", "f.txt"],
    ["trailing/dir/", "dir"],
    ["bare.ts", "bare.ts"],
  ])("%s → %s", (input, expected) => {
    expect(pathBaseName(input)).toBe(expected);
  });
});

describe("pathBreadcrumb —— 对齐 ZCode PreviewPane 单行面包屑", () => {
  it("拆出父级段与文件名", () => {
    expect(pathBreadcrumb("src/app/main.ts")).toEqual({
      parents: ["src", "app"],
      file: "main.ts",
    });
  });

  it("裸文件名没有父级段（避免旧版把同一个名字渲染两遍）", () => {
    expect(pathBreadcrumb("generate_fixtures.py")).toEqual({
      parents: [],
      file: "generate_fixtures.py",
    });
  });

  it("兼容反斜杠路径与首尾斜杠", () => {
    expect(pathBreadcrumb("\\scripts\\fixtures\\a.ts")).toEqual({
      parents: ["scripts", "fixtures"],
      file: "a.ts",
    });
    expect(pathBreadcrumb("/abs/path/b.ts/")).toEqual({
      parents: ["abs", "path"],
      file: "b.ts",
    });
  });

  it("空路径不炸", () => {
    const empty = {
      parents: [],
      file: "",
    };
    expect(pathBreadcrumb("")).toEqual(empty);
    expect(pathBreadcrumb("///")).toEqual(empty);
  });
});
