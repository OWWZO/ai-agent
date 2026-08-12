import { describe, expect, it } from "vitest";

import {
  collectChatArtifactFiles,
  collectSessionArtifactFiles,
  isAbsoluteOrSpecialMarkdownUrl,
  normalizeMarkdownArtifactRef,
  resolveMarkdownArtifactHref,
  resolveMarkdownMediaExtension,
  resolveMarkdownMediaKind,
  rewriteMarkdownArtifactRefs,
} from "./markdownArtifacts";

const sampleFile = (
  name: string,
  url: string,
  extra?: Partial<CHAT.TFile>
): CHAT.TFile => ({
  name,
  url,
  type: name.split(".").pop() || "",
  size: 1,
  downloadUrl: url.replace("/preview/", "/download/"),
  ...extra,
});

describe("markdownArtifacts", () => {
  it("detects media kind from extension", () => {
    expect(resolveMarkdownMediaKind("demo.mp4")).toBe("video");
    expect(resolveMarkdownMediaKind("./clip.webm?v=1")).toBe("video");
    expect(resolveMarkdownMediaKind("http://x/a.mp3")).toBe("audio");
    expect(resolveMarkdownMediaKind("tone.wav")).toBe("audio");
    expect(resolveMarkdownMediaKind("chart.png")).toBe("other");
    expect(resolveMarkdownMediaExtension("http://x/v1/file_tool/preview/s/a.MP4")).toBe(
      "mp4"
    );
  });

  it("detects absolute and special urls", () => {
    expect(isAbsoluteOrSpecialMarkdownUrl("https://a.com/x.png")).toBe(true);
    expect(isAbsoluteOrSpecialMarkdownUrl("http://a.com/x.png")).toBe(true);
    expect(isAbsoluteOrSpecialMarkdownUrl("//cdn/x.png")).toBe(true);
    expect(isAbsoluteOrSpecialMarkdownUrl("#section")).toBe(true);
    expect(isAbsoluteOrSpecialMarkdownUrl("data:image/png;base64,aa")).toBe(
      true
    );
    expect(isAbsoluteOrSpecialMarkdownUrl("mailto:a@b.com")).toBe(true);
    expect(isAbsoluteOrSpecialMarkdownUrl("chart.png")).toBe(false);
    expect(isAbsoluteOrSpecialMarkdownUrl("./report.md")).toBe(false);
  });

  it("normalizes relative refs to basename", () => {
    expect(normalizeMarkdownArtifactRef("./outputs/chart.png")).toBe(
      "chart.png"
    );
    expect(normalizeMarkdownArtifactRef("report.md?v=1#top")).toBe("report.md");
    expect(normalizeMarkdownArtifactRef("https://a.com/x.png")).toBe("");
  });

  it("resolves relative markdown href via artifact files", () => {
    const files = [
      sampleFile(
        "chart.png",
        "http://127.0.0.1:1601/v1/file_tool/preview/s1/chart.png"
      ),
      sampleFile(
        "report.md",
        "http://127.0.0.1:1601/v1/file_tool/preview/s1/report.md"
      ),
    ];

    expect(resolveMarkdownArtifactHref("chart.png", files)).toBe(
      "http://127.0.0.1:1601/v1/file_tool/preview/s1/chart.png"
    );
    expect(resolveMarkdownArtifactHref("./report.md", files)).toBe(
      "http://127.0.0.1:1601/v1/file_tool/preview/s1/report.md"
    );
    expect(
      resolveMarkdownArtifactHref("https://cdn.example/keep.png", files)
    ).toBe("https://cdn.example/keep.png");
    expect(resolveMarkdownArtifactHref("missing.png", files)).toBe(
      "missing.png"
    );
  });

  it("prefers later non-missing file when names collide", () => {
    const files = [
      sampleFile("chart.png", "http://old/preview/chart.png"),
      sampleFile("chart.png", "http://new/preview/chart.png"),
      sampleFile("gone.png", "", {
        missing: true,
        url: "",
      }),
    ];

    expect(resolveMarkdownArtifactHref("chart.png", files)).toBe(
      "http://new/preview/chart.png"
    );
    expect(resolveMarkdownArtifactHref("gone.png", files)).toBe("gone.png");
  });

  it("collects artifact files from chat tasks and conclusion", () => {
    const chat = {
      tasks: [
        [
          {
            messageType: "file",
            artifactRefs: [
              {
                fileName: "chart.png",
                previewUrl:
                  "http://127.0.0.1:1601/v1/file_tool/preview/s1/chart.png",
                downloadUrl:
                  "http://127.0.0.1:1601/v1/file_tool/download/s1/chart.png",
              },
            ],
          },
        ],
      ],
      conclusion: {
        messageType: "result",
        fileInfo: [
          {
            fileName: "report.md",
            domainUrl:
              "http://127.0.0.1:1601/v1/file_tool/preview/s1/report.md",
            ossUrl: "http://127.0.0.1:1601/v1/file_tool/download/s1/report.md",
          },
        ],
      },
      generatedFiles: [
        sampleFile(
          "extra.html",
          "http://127.0.0.1:1601/v1/file_tool/preview/s1/extra.html"
        ),
      ],
      multiAgent: { tasks: [] },
    } as unknown as CHAT.ChatItem;

    const files = collectChatArtifactFiles(chat);
    const names = files.map((file) => file.name).sort();
    expect(names).toEqual(["chart.png", "extra.html", "report.md"]);
  });

  it("includes user-uploaded chat.files for markdown resolve", () => {
    const chat = {
      files: [
        sampleFile(
          "upload-photo.png",
          "http://127.0.0.1:1601/v1/file_tool/preview/s1/upload-photo.png"
        ),
      ],
      tasks: [],
      multiAgent: { tasks: [] },
    } as unknown as CHAT.ChatItem;

    const files = collectChatArtifactFiles(chat);
    expect(files.map((file) => file.name)).toEqual(["upload-photo.png"]);
    expect(resolveMarkdownArtifactHref("upload-photo.png", files)).toBe(
      "http://127.0.0.1:1601/v1/file_tool/preview/s1/upload-photo.png"
    );
    expect(
      rewriteMarkdownArtifactRefs("见图 ![](upload-photo.png)", files)
    ).toContain(
      "http://127.0.0.1:1601/v1/file_tool/preview/s1/upload-photo.png"
    );
  });

  it("rewrites relative markdown links and images in source text", () => {
    const files = [
      sampleFile(
        "report.md",
        "http://127.0.0.1:1601/v1/file_tool/preview/s1/report.md"
      ),
      sampleFile(
        "chart.png",
        "http://127.0.0.1:1601/v1/file_tool/preview/s1/chart.png"
      ),
    ];

    const rewritten = rewriteMarkdownArtifactRefs(
      "见 [报告](report.md)\n\n![图](./chart.png)\n\n外链 [x](https://a.com/x)\n\n```md\n[别动](report.md)\n```",
      files
    );

    expect(rewritten).toContain(
      "[报告](http://127.0.0.1:1601/v1/file_tool/preview/s1/report.md)"
    );
    expect(rewritten).toContain(
      "![图](http://127.0.0.1:1601/v1/file_tool/preview/s1/chart.png)"
    );
    expect(rewritten).toContain("[x](https://a.com/x)");
    expect(rewritten).toContain("```md\n[别动](report.md)\n```");
  });

  it("collects nested children files under agent timeline containers", () => {
    const chat = {
      tasks: [
        [
          {
            messageType: "task",
            task: "写报告",
            children: [
              {
                messageType: "file",
                fileInfo: [
                  {
                    fileName: "report.md",
                    domainUrl:
                      "http://127.0.0.1:1601/v1/file_tool/preview/s1/report.md",
                    ossUrl:
                      "http://127.0.0.1:1601/v1/file_tool/download/s1/report.md",
                  },
                ],
              },
            ],
          },
        ],
      ],
      multiAgent: { tasks: [] },
    } as unknown as CHAT.ChatItem;

    const files = collectChatArtifactFiles(chat);
    expect(files.map((file) => file.name)).toEqual(["report.md"]);
    expect(resolveMarkdownArtifactHref("report.md", files)).toBe(
      "http://127.0.0.1:1601/v1/file_tool/preview/s1/report.md"
    );
  });

  it("collects session-level artifacts across chat turns", () => {
    const chatList = [
      {
        tasks: [
          [
            {
              messageType: "file",
              fileInfo: [
                {
                  fileName: "report.md",
                  domainUrl:
                    "http://127.0.0.1:1601/v1/file_tool/preview/s1/report.md",
                  ossUrl:
                    "http://127.0.0.1:1601/v1/file_tool/download/s1/report.md",
                },
              ],
            },
          ],
        ],
        multiAgent: { tasks: [] },
      },
      {
        tasks: [
          [
            {
              messageType: "file",
              fileInfo: [
                {
                  fileName: "chart.png",
                  domainUrl:
                    "http://127.0.0.1:1601/v1/file_tool/preview/s1/chart.png",
                  ossUrl:
                    "http://127.0.0.1:1601/v1/file_tool/download/s1/chart.png",
                },
              ],
            },
          ],
        ],
        multiAgent: { tasks: [] },
      },
    ] as unknown as CHAT.ChatItem[];

    const files = collectSessionArtifactFiles(chatList);
    expect(files.map((file) => file.name).sort()).toEqual([
      "chart.png",
      "report.md",
    ]);
  });
});
