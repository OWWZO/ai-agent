import { describe, expect, it } from "vitest";

import { TOOL_CATALOG } from "./toolCatalog";
import { OPENCLI_SITE_DEFINITIONS } from "./openCliCatalog.generated";
import {
  filterToolCatalog,
  getToolCatalogCardMetadata,
  getToolFunctionCount,
  initializeToolFieldValues,
  serializeToolPromptParameters,
  validateToolFieldValues,
} from "./toolCatalogModel";

function getOpenCliField(site: string, command: string, fieldName: string) {
  const field = TOOL_CATALOG.find((item) => item.id === `opencli-${site}`)
    ?.functions.find((toolFunction) => toolFunction.command === `${site}/${command}`)
    ?.fields.find((entry) => entry.name === fieldName);
  if (!field) throw new Error(`Missing field ${site}/${command}:${fieldName}`);
  return field;
}

describe("toolCatalogModel", () => {
  it("keeps catalog, function, and command identifiers unique", () => {
    const functions = TOOL_CATALOG.flatMap((item) => item.functions);
    const itemIds = TOOL_CATALOG.map((item) => item.id);
    const functionIds = functions.map((toolFunction) => toolFunction.id);
    const commands = functions.map((toolFunction) => toolFunction.command);

    expect(new Set(itemIds).size).toBe(itemIds.length);
    expect(new Set(functionIds).size).toBe(functionIds.length);
    expect(commands.every(Boolean)).toBe(true);
    expect(new Set(commands).size).toBe(commands.length);
    expect(
      functions.every(
        (toolFunction) =>
          Array.isArray(toolFunction.fields) &&
          typeof toolFunction.buildPrompt === "function" &&
          toolFunction.buildPrompt({}).length > 0
      )
    ).toBe(true);
    expect(
      new Set(
        TOOL_CATALOG.flatMap((item) =>
          item.functions.flatMap((toolFunction) =>
            toolFunction.fields.map((field) => field.type)
          )
        )
      )
    ).toEqual(
      new Set([
        "text",
        "textarea",
        "number",
        "select",
        "multi-select",
        "boolean",
        "url",
        "file",
        "files",
        "datetime",
        "local-path",
      ])
    );
    expect(
      commands.some((command) =>
        [
          "code_execution",
          "workspace_write",
          "workspace_edit",
          "Agent",
          "Task",
          "Plan",
          "ToolSearch",
          "Memory",
        ].includes(command ?? "")
      )
    ).toBe(false);
  });

  it("includes every requested OpenCLI site and command from the manifest", () => {
    const expectedSites = [
      "aibase", "apple-podcasts", "archive", "arxiv", "baidu-scholar", "bbc", "dblp",
      "duckduckgo", "geogebra", "github-trending", "google", "google-scholar", "gov-law",
      "hackernews", "juejin", "steam", "wikidata", "wikipedia", "hupu", "nowcoder",
      "producthunt", "sinablog", "sinafinance", "tieba", "toutiao", "weixin", "weread",
      "xiaoyuzhou", "yollomi", "amazon", "bilibili", "facebook", "douyin", "instagram",
      "linkedin", "reddit", "wechat-channels", "youtube", "zhihu", "autohome", "binance",
      "bluesky", "booking", "brave", "coingecko", "confluence", "crates", "defillama",
      "devto", "dictionary", "dockerhub", "dongchedi", "endoflife", "flathub", "goproxy",
      "gov-policy", "guazi", "homebrew", "huodongxing", "imdb", "jira", "lesswrong",
      "lichess", "lobsters", "maven", "mdn", "npm", "nuget", "nvd", "oeis", "openalex",
      "openfda", "openreview", "osv", "packagist", "paperreview", "pubmed", "pypi",
      "rest-countries", "rfc", "rubygems", "semanticscholar", "spotify", "stackoverflow",
      "ths", "tvmaze", "uisdc", "uiverse", "wanfang", "weread-official", "wttr", "yahoo",
      "youdao",
    ];
    expect(OPENCLI_SITE_DEFINITIONS.map((site) => site.site)).toEqual(expectedSites);
    const manifestCommands = OPENCLI_SITE_DEFINITIONS.flatMap((site) => site.commands);
    expect(manifestCommands).toHaveLength(501);
    expect(manifestCommands.filter((command) => command.access === "read")).toHaveLength(411);
    expect(manifestCommands.filter((command) => command.access === "write")).toHaveLength(90);
    expect(manifestCommands.filter((command) => command.browser)).toHaveLength(263);

    for (const site of OPENCLI_SITE_DEFINITIONS) {
      const item = TOOL_CATALOG.find((entry) => entry.id === `opencli-${site.site}`);
      expect(item).toBeDefined();
      expect(item?.functions.map((toolFunction) => toolFunction.command)).toEqual(
        site.commands.map((command) => `${site.site}/${command.name}`),
      );
      for (const command of site.commands) {
        const toolFunction = item?.functions.find(
          (entry) => entry.command === `${site.site}/${command.name}`,
        );
        expect(toolFunction?.access).toBe(command.access);
        expect(toolFunction?.browser).toBe(command.browser);
        expect(toolFunction?.fields.map((field) => field.name)).toEqual(
          command.args.map((argument) => argument.name),
        );
        for (const argument of command.args) {
          const field = toolFunction?.fields.find((entry) => entry.name === argument.name);
          expect(field?.required).toBe(argument.required);
          expect(field?.positional).toBe(argument.positional);
          if (argument.default !== undefined) {
            expect(field?.defaultValue).toBe(
              field?.type === "select" ? String(argument.default) : argument.default,
            );
          }
          if (argument.choices) {
            expect(field?.options?.map((option) => option.value)).toEqual(argument.choices);
          }
        }
      }
    }

    expect(TOOL_CATALOG.filter((item) => item.kind === "opencli-site")).toHaveLength(96);
    const expectedNoLoginSites = new Set([
      "aibase", "apple-podcasts", "archive", "arxiv", "autohome", "baidu-scholar", "bbc",
      "binance", "bluesky", "booking", "brave", "coingecko", "confluence", "crates", "dblp",
      "defillama", "devto", "dictionary", "dockerhub", "dongchedi", "duckduckgo", "endoflife",
      "flathub", "geogebra", "github-trending", "google", "google-scholar", "goproxy", "gov-law",
      "gov-policy", "guazi", "hackernews", "homebrew", "huodongxing", "imdb", "jira", "juejin",
      "lesswrong", "lichess", "lobsters", "maven", "mdn", "npm", "nuget", "nvd", "oeis", "openalex",
      "openfda", "openreview", "osv", "packagist", "paperreview", "pubmed", "pypi", "rest-countries",
      "rfc", "rubygems", "semanticscholar", "spotify", "stackoverflow", "steam", "ths", "tvmaze",
      "uisdc", "uiverse", "wanfang", "weread-official", "wikidata", "wikipedia", "wttr", "yahoo",
      "youdao",
    ]);
    const actualNoLoginSites = TOOL_CATALOG.filter((item) => item.requiresLogin === false)
      .map((item) => item.id.replace(/^opencli-/, ""));
    expect(actualNoLoginSites).toHaveLength(expectedNoLoginSites.size);
    expect(new Set(actualNoLoginSites)).toEqual(expectedNoLoginSites);
    expect(TOOL_CATALOG.map((item) => item.id)).toEqual(
      expect.arrayContaining(["opencli-xiaohongshu", "opencli-twitter", "opencli-weibo"]),
    );

    const hashtag = TOOL_CATALOG.find((item) => item.id === "opencli-douyin")
      ?.functions.find((toolFunction) => toolFunction.command === "douyin/hashtag");
    expect(hashtag?.fields.find((field) => field.name === "action")?.defaultValue).toBe("hot");
  });

  it("filters by category and searches case-insensitively across card and function metadata", () => {
    expect(filterToolCatalog(TOOL_CATALOG, "social").map((item) => item.id)).toContain(
      "opencli-juejin",
    );
    expect(filterToolCatalog(TOOL_CATALOG, "finance").map((item) => item.id)).toEqual([
      "opencli-sinafinance",
      "opencli-binance",
      "opencli-coingecko",
      "opencli-defillama",
      "opencli-ths",
    ]);
    expect(filterToolCatalog(TOOL_CATALOG, "all", "小红书").map((item) => item.id)).toEqual([
      "opencli-xiaohongshu",
    ]);
    expect(filterToolCatalog(TOOL_CATALOG, "all", "WEIBO/PUBLISH").map((item) => item.id)).toEqual([
      "opencli-weibo",
    ]);
  });

  it("maps manifest file, media, URL, and path arguments to appropriate controls", () => {
    expect(getOpenCliField("douyin", "draft", "video")).toMatchObject({
      type: "file",
      accept: "video/*",
      required: true,
    });
    expect(getOpenCliField("weixin", "create-draft", "cover-image")).toMatchObject({
      type: "file",
      accept: "image/*",
    });
    expect(getOpenCliField("instagram", "post", "media")).toMatchObject({
      type: "files",
      accept: "image/*,video/*",
      maxFiles: 10,
    });
    expect(getOpenCliField("yollomi", "upload", "file").type).toBe("file");
    expect(getOpenCliField("yollomi", "edit", "image").type).toBe("url");
    expect(getOpenCliField("youtube", "video", "url").type).toBe("text");
    expect(getOpenCliField("amazon", "bestsellers", "input").type).toBe("text");
  });

  it("derives function counts and card metadata", () => {
    const research = TOOL_CATALOG.find((item) => item.id === "base-research");
    expect(research).toBeDefined();
    expect(getToolFunctionCount(research!)).toBe(3);
    expect(getToolCatalogCardMetadata(research!)).toMatchObject({
      id: "base-research",
      title: "搜索与研究",
      category: "research",
      functionCount: 3,
    });
  });

  it("initializes field values from defaults and type-appropriate empty values", () => {
    const fields = [
      {
        name: "query",
        label: "关键词",
        type: "text" as const
      },
      {
        name: "limit",
        label: "数量",
        type: "number" as const,
        defaultValue: 20
      },
      {
        name: "enabled",
        label: "启用",
        type: "boolean" as const
      },
      {
        name: "topics",
        label: "话题",
        type: "multi-select" as const,
        defaultValue: ["a"]
      },
      {
        name: "file",
        label: "文件",
        type: "file" as const
      },
    ];

    const values = initializeToolFieldValues(fields);
    expect(values).toEqual({
      query: "",
      limit: 20,
      enabled: false,
      topics: ["a"],
      file: [],
    });
    expect(values.topics).not.toBe(fields[3].defaultValue);
  });

  it("validates required fields", () => {
    expect(
      validateToolFieldValues(
        [{
          name: "query",
          label: "关键词",
          type: "text",
          required: true
        }],
        { query: "   " }
      )
    ).toEqual({ query: "此字段为必填项" });
  });

  it("rejects values outside configured select options", () => {
    expect(
      validateToolFieldValues(
        [
          {
            name: "sort",
            label: "排序",
            type: "select",
            options: [
              {
                value: "latest",
                label: "最新"
              },
              {
                value: "popular",
                label: "热门"
              },
            ],
          },
        ],
        { sort: "random" }
      )
    ).toEqual({ sort: "请选择配置中的有效选项" });
    expect(
      validateToolFieldValues(
        [{
          name: "topics",
          label: "话题",
          type: "multi-select"
        }],
        { topics: "工具" }
      )
    ).toEqual({ topics: "请选择配置中的有效选项" });
  });

  it("validates HTTP URLs and numeric ranges", () => {
    const fields = [
      {
        name: "url",
        label: "链接",
        type: "url" as const,
        required: true
      },
      {
        name: "limit",
        label: "数量",
        type: "number" as const,
        min: 1,
        max: 20
      },
    ];

    expect(validateToolFieldValues(fields, {
      url: "not a url",
      limit: 21
    })).toEqual({
      url: "请输入有效的 HTTP 或 HTTPS 链接",
      limit: "请输入 1 到 20 之间的数字",
    });
    expect(validateToolFieldValues(fields, {
      url: "https://example.com",
      limit: 20
    })).toEqual({});
    expect(
      validateToolFieldValues(
        [{
          name: "url",
          label: "链接",
          type: "url"
        }],
        { url: 42 }
      )
    ).toEqual({ url: "请输入有效的 HTTP 或 HTTPS 链接" });
  });

  it("validates file quantity limits", () => {
    expect(
      validateToolFieldValues(
        [{
          name: "files",
          label: "图片",
          type: "files",
          minFiles: 1,
          maxFiles: 2
        }],
        { files: [{ name: "a.png" }, { name: "b.png" }, { name: "c.png" }] }
      )
    ).toEqual({ files: "请上传 1 到 2 个文件" });
  });

  it("serializes arrays, false booleans, file references, zero, and omits empty values", () => {
    const fields = [
      {
        name: "topics",
        label: "话题",
        type: "multi-select" as const
      },
      {
        name: "enabled",
        label: "启用互动",
        type: "boolean" as const
      },
      {
        name: "files",
        label: "图片",
        type: "files" as const
      },
      {
        name: "count",
        label: "数量",
        type: "number" as const
      },
      {
        name: "empty",
        label: "备注",
        type: "text" as const
      },
    ];

    expect(
      serializeToolPromptParameters(fields, {
        topics: ["AI", "工具"],
        enabled: false,
        files: [{ name: "private-name.png" }],
        count: 0,
        empty: "   ",
      })
    ).toBe(
      [
        "- 话题：AI",
        "- 话题：工具",
        "- 启用互动：否",
        "- 图片：本轮上传文件",
        "- 数量：0",
      ].join("\n")
    );
  });

  it("includes the exact OpenCLI command and local-browser notice for write operations", () => {
    const publish = TOOL_CATALOG.find((item) => item.id === "opencli-xiaohongshu")
      ?.functions.find((toolFunction) => toolFunction.command === "xiaohongshu/publish");

    expect(publish).toBeDefined();
    expect(publish?.access).toBe("write");
    const prompt = publish?.buildPrompt({
      title: "周末记录",
      content: "一段分享"
    }) ?? "";
    expect(prompt).toContain("这是一个写入操作");
    expect(prompt).toContain("OpenCLI 的 xiaohongshu/publish");
    expect(prompt).toContain("此任务会操作用户本机浏览器");
    expect(prompt).toContain("- 笔记标题 (--title)：周末记录");
    expect(prompt).not.toContain("opencli xiaohongshu");
  });
});
